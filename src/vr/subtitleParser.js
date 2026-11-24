/**
 * Lightweight SRT & WebVTT Subtitle Parser and Lookup
 */

function parseTimestamp(raw) {
  if (!raw) return NaN;
  const cleaned = raw.trim().replace(',', '.');
  const parts = cleaned.split(':');
  if (parts.length === 2) {
    const minutes = parseInt(parts[0], 10);
    const seconds = parseFloat(parts[1]);
    return minutes * 60 + seconds;
  }
  if (parts.length === 3) {
    const hours = parseInt(parts[0], 10);
    const minutes = parseInt(parts[1], 10);
    const seconds = parseFloat(parts[2]);
    return hours * 3600 + minutes * 60 + seconds;
  }
  return NaN;
}

function cleanCueText(rawText) {
  return rawText
    // Strip ASS/SSA style blocks like {\an8}
    .replace(/\{\\[^}]*\}/g, '')
    // Strip HTML / WebVTT voice and style tags
    .replace(/<\/?[^>]+>/g, '')
    // Decode common HTML entities
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .join('\n');
}

/**
 * Parse .srt or .vtt content into sorted cue array:
 * [{ start: number, end: number, text: string }]
 */
export function parseSubtitles(content) {
  if (!content || typeof content !== 'string') return [];

  const normalized = content
    .replace(/^\uFEFF/, '') // Remove UTF-8 BOM
    .replace(/\r\n?/g, '\n')
    .trim();

  const blocks = normalized.split(/\n{2,}/);
  const cues = [];
  const timeRegex = /^((?:\d{1,2}:)?\d{1,2}:\d{2}(?:[.,]\d{1,3})?)\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}(?:[.,]\d{1,3})?)/;

  for (const block of blocks) {
    const lines = block.split('\n').map((l) => l.trimEnd());
    if (lines.length === 0) continue;

    // Skip WebVTT header and metadata blocks
    const first = lines[0].trim();
    if (
      first.startsWith('WEBVTT') ||
      first.startsWith('NOTE') ||
      first.startsWith('STYLE') ||
      first.startsWith('REGION')
    ) {
      continue;
    }

    // Find the timestamp line (usually line 0 in VTT or line 1 after index in SRT)
    let timeLineIdx = -1;
    let match = null;
    for (let i = 0; i < Math.min(lines.length, 3); i++) {
      match = lines[i].match(timeRegex);
      if (match) {
        timeLineIdx = i;
        break;
      }
    }

    if (timeLineIdx === -1 || !match) continue;

    const start = parseTimestamp(match[1]);
    const end = parseTimestamp(match[2]);
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
      continue;
    }

    const rawText = lines.slice(timeLineIdx + 1).join('\n');
    const text = cleanCueText(rawText);
    if (text) {
      cues.push({ start, end, text });
    }
  }

  cues.sort((a, b) => a.start - b.start);
  return cues;
}

/**
 * Binary-search lookup for active subtitle text at `timeSeconds`.
 * Combines overlapping simultaneous cues cleanly.
 */
export function getActiveCueText(cues, timeSeconds) {
  if (!cues || cues.length === 0 || !Number.isFinite(timeSeconds)) {
    return '';
  }

  // Binary search for last cue with start <= timeSeconds
  let lo = 0;
  let hi = cues.length - 1;
  let idx = -1;

  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (cues[mid].start <= timeSeconds) {
      idx = mid;
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }

  if (idx === -1) return '';

  // Walk backwards briefly to catch overlapping cues started slightly earlier
  const activeTexts = [];
  for (let i = idx; i >= 0; i--) {
    const cue = cues[i];
    if (cue.end >= timeSeconds) {
      activeTexts.unshift(cue.text);
    } else if (timeSeconds - cue.start > 30) {
      // Cues don't typically span >30s without overlapping; stop scanning far back
      break;
    }
  }

  return activeTexts.join('\n');
}
