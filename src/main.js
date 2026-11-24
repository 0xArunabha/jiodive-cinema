import * as THREE from 'three';
import { StereoRig } from './vr/stereoRig.js';
import { CinemaScreen, SCREEN_PRESETS } from './vr/screenMesh.js';
import { AmbientLighting } from './vr/ambientLighting.js';
import { Environments } from './vr/environments.js';
import { GazeUI } from './vr/gazeUI.js';
import { parseSubtitles } from './vr/subtitleParser.js';

// Elements
const canvas = document.getElementById('vr-canvas');
const video = document.getElementById('video-player');
const launcher = document.getElementById('launcher');
const btnEnterVr = document.getElementById('btn-enter-vr');

// App Bar & Modal Bottom Sheets
const opticsDialog = document.getElementById('optics-dialog');
const tipsDialog = document.getElementById('tips-dialog');
const btnOpenOptics = document.getElementById('btn-open-optics');
const btnOpticsPill = document.getElementById('btn-optics-pill');
const btnOpenTips = document.getElementById('btn-open-tips');

// Hero Stage Preview
const stagePreview = document.getElementById('stage-preview');
const heroEnvBadge = document.getElementById('hero-env-badge');
const heroSourceTitle = document.getElementById('hero-source-title');
const heroSourceMeta = document.getElementById('hero-source-meta');

// Source tabs & panels
const sourceTabs = Array.from(document.querySelectorAll('.source-tab[role="tab"]'));
const sourcePanels = Array.from(document.querySelectorAll('.source-panel[role="tabpanel"]'));
const fileDropzone = document.getElementById('file-dropzone');
const fileInput = document.getElementById('video-file-input');
const btnLoadDemo = document.getElementById('btn-load-demo');
const streamUrlInput = document.getElementById('stream-url-input');
const btnLoadUrl = document.getElementById('btn-load-url');
const fileInfo = document.getElementById('file-info');

// Subtitle controls
const subtitleFileInput = document.getElementById('subtitle-file-input');
const subtitleChipLabel = document.getElementById('subtitle-chip-label');
const subtitleBtnText = document.getElementById('subtitle-btn-text');
const btnClearSubtitles = document.getElementById('btn-clear-subtitles');
const subtitleInfo = document.getElementById('subtitle-info');

// Headset & Optics controls
const swapEyesToggle = document.getElementById('swap-eyes-toggle');
const distortionToggle = document.getElementById('distortion-toggle');
const dwellToggle = document.getElementById('dwell-toggle');
const opticsSummaryMeta = document.getElementById('optics-summary-meta');

// Rotate hint inside VR
const rotateHint = document.getElementById('rotate-hint');
const btnDismissRotate = document.getElementById('btn-dismiss-rotate');

// Inline SVG status icons
const STATUS_ICONS = {
  ok: '<svg class="status-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
  error: '<svg class="status-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>',
  loading: '<svg class="status-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>'
};

let currentSourceLabel = '';
let hasMkvWarning = false;
let rotateHintDismissed = false;

function setBanner(el, state, message) {
  if (!el) return;
  if (!message) {
    el.textContent = '';
    el.className = 'status-banner hidden';
    return;
  }

  let stateClass = 'status-banner';
  let iconSvg = STATUS_ICONS.ok;
  if (state === 'error') {
    stateClass = 'status-banner is-error';
    iconSvg = STATUS_ICONS.error;
  } else if (state === 'loading') {
    stateClass = 'status-banner is-loading';
    iconSvg = STATUS_ICONS.loading;
  }

  el.className = stateClass;
  el.innerHTML = `${iconSvg}<span></span>`;
  el.querySelector('span').textContent = message;
}

// Native Modal Dialog Triggers + Safari light-dismiss fallback for closedby="any"
[btnOpenOptics, btnOpticsPill].forEach((btn) => {
  if (btn && opticsDialog) {
    btn.addEventListener('click', () => opticsDialog.showModal());
  }
});

if (btnOpenTips && tipsDialog) {
  btnOpenTips.addEventListener('click', () => tipsDialog.showModal());
}

if (!('closedBy' in HTMLDialogElement.prototype)) {
  const isOutsideDialog = (e, dialog) => {
    const r = dialog.getBoundingClientRect();
    return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
  };
  for (const dialog of document.querySelectorAll('dialog[closedby="any"]')) {
    let pressedOutside = false;
    dialog.addEventListener('pointerdown', (e) => {
      pressedOutside = isOutsideDialog(e, dialog);
    });
    dialog.addEventListener('pointerup', (e) => {
      if (pressedOutside && isOutsideDialog(e, dialog)) dialog.close();
    });
  }
}

// Accessible Tablist Management
function selectSourceTab(targetTab, moveFocus = false) {
  if (!targetTab) return;
  const targetPanelId = targetTab.dataset.panel;

  for (const tab of sourceTabs) {
    const isSelected = tab === targetTab;
    tab.setAttribute('aria-selected', String(isSelected));
    tab.tabIndex = isSelected ? 0 : -1;
  }

  for (const panel of sourcePanels) {
    if (panel.id === targetPanelId) {
      panel.removeAttribute('hidden');
    } else {
      panel.setAttribute('hidden', 'until-found');
    }
  }

  if (moveFocus) {
    targetTab.focus();
  }
}

sourceTabs.forEach((tab, idx) => {
  tab.addEventListener('click', () => selectSourceTab(tab, false));
  tab.addEventListener('keydown', (e) => {
    let nextIdx = -1;
    if (e.key === 'ArrowRight') nextIdx = (idx + 1) % sourceTabs.length;
    else if (e.key === 'ArrowLeft') nextIdx = (idx - 1 + sourceTabs.length) % sourceTabs.length;
    else if (e.key === 'Home') nextIdx = 0;
    else if (e.key === 'End') nextIdx = sourceTabs.length - 1;

    if (nextIdx !== -1) {
      e.preventDefault();
      selectSourceTab(sourceTabs[nextIdx], true);
    }
  });
});

sourcePanels.forEach((panel) => {
  panel.addEventListener('beforematch', () => {
    const ownerTab = sourceTabs.find((t) => t.dataset.panel === panel.id);
    if (ownerTab) selectSourceTab(ownerTab, false);
  });
});

// Form Control Helpers (Radio pills + Switches)
function getRadioValue(name, fallback) {
  const checked = document.querySelector(`input[name="${name}"]:checked`);
  return checked ? checked.value : fallback;
}

function setRadioValue(name, value) {
  const inputs = document.querySelectorAll(`input[name="${name}"]`);
  const targetStr = String(value);
  for (const input of inputs) {
    if (input.value === targetStr) {
      input.checked = true;
      return true;
    }
  }
  return false;
}

function updateHeroPreview() {
  const env = getRadioValue('env', 'cinema');
  const size = getRadioValue('screenSize', 'standard');
  const mode = getRadioValue('mode', '2d');

  if (stagePreview) {
    stagePreview.dataset.env = env;
  }
  if (heroEnvBadge) {
    const envNames = { cinema: 'Cinema Hall', void: 'OLED Void', bed: 'Bed Ceiling' };
    const sizeLabel = SCREEN_PRESETS[size]?.label || 'Standard';
    const modeLabel = mode === '2d' ? '2D' : mode.replace('3d-', '').toUpperCase();
    heroEnvBadge.textContent = `${envNames[env] || 'Cinema Hall'} · ${sizeLabel} · ${modeLabel}`;
  }
}

function setHeroMediaInfo(title, meta) {
  if (heroSourceTitle) heroSourceTitle.textContent = title;
  if (heroSourceMeta) heroSourceMeta.textContent = meta;
}

function updateOpticsSummary() {
  if (!opticsSummaryMeta) return;
  const ipdMm = Math.round(parseFloat(getRadioValue('ipd', '0.062')) * 1000);
  const fovDeg = getRadioValue('fov', '75');
  const opticsState = distortionToggle && distortionToggle.checked ? 'Optics On' : 'Flat';
  opticsSummaryMeta.textContent = `${ipdMm} mm · ${fovDeg}° · ${opticsState}`;
}

// Verified high-reliability sample video with full CORS support (VideoJS CDN)
const DEMO_VIDEO_MP4 = 'https://vjs.zencdn.net/v/oceans.mp4';
const DEMO_VIDEO_WEBM = 'https://vjs.zencdn.net/v/oceans.webm';

function pickSupportedDemoUrl() {
  if (video.canPlayType('video/mp4; codecs="avc1.42E01E"') !== '') {
    return DEMO_VIDEO_MP4;
  }
  if (video.canPlayType('video/webm') !== '') {
    return DEMO_VIDEO_WEBM;
  }
  return DEMO_VIDEO_MP4;
}

function loadDemoVideo(showBanner = true) {
  hasMkvWarning = false;
  currentSourceLabel = 'Oceans HD Sample';
  video.crossOrigin = 'anonymous';
  video.src = pickSupportedDemoUrl();
  video.load();
  setHeroMediaInfo(currentSourceLabel, 'Connecting stream...');
  if (showBanner) {
    setBanner(fileInfo, 'loading', `Connecting to ${currentSourceLabel}...`);
  }
}

function loadLocalVideoFile(file) {
  if (!file) return;
  const isMkv = file.name.toLowerCase().endsWith('.mkv');
  const sizeMb = `${(file.size / (1024 * 1024)).toFixed(1)} MB`;
  hasMkvWarning = isMkv;
  currentSourceLabel = file.name;
  setHeroMediaInfo(file.name, isMkv ? `${sizeMb} · MKV container` : `${sizeMb} · Local device`);

  if (isMkv) {
    setBanner(
      fileInfo,
      'error',
      `Note on MKV: "${file.name}" is an MKV container. Browsers rarely decode MKV natively — convert to MP4 (H.264 + AAC) or WebM if playback fails.`
    );
  } else {
    setBanner(fileInfo, 'ok', `Ready: ${file.name} (${sizeMb})`);
  }

  if (video.src && video.src.startsWith('blob:')) {
    URL.revokeObjectURL(video.src);
  }

  const url = URL.createObjectURL(file);
  video.removeAttribute('crossorigin');
  video.src = url;
  video.load();
}

// Setup Three.js Scene & Renderer
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x050508);

const renderer = new THREE.WebGLRenderer({
  canvas: canvas,
  antialias: true,
  powerPreference: 'high-performance'
});
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 3));
renderer.setSize(window.innerWidth, window.innerHeight);

// Initialize VR Modules
const rig = new StereoRig(renderer, scene);
const cinemaScreen = new CinemaScreen(scene, video);
const environments = new Environments(scene);
const ambient = new AmbientLighting(scene, video, environments);
const gazeUI = new GazeUI(rig, video, cinemaScreen, environments);

let wakeLock = null;

// Video Lifecycle & Error Handling
video.addEventListener('loadedmetadata', () => {
  if (!currentSourceLabel || hasMkvWarning) return;
  const dims = video.videoWidth && video.videoHeight ? `${video.videoWidth}×${video.videoHeight}` : 'Video ready';
  setHeroMediaInfo(currentSourceLabel, dims);
  setBanner(fileInfo, 'ok', `Ready: ${currentSourceLabel} · ${dims}`);
});

video.addEventListener('canplay', () => {
  if (hasMkvWarning && video.videoWidth > 0) {
    hasMkvWarning = false;
    const dims = `${video.videoWidth}×${video.videoHeight}`;
    setHeroMediaInfo(currentSourceLabel, dims);
    setBanner(fileInfo, 'ok', `Ready: ${currentSourceLabel} · ${dims}`);
  }
});

video.addEventListener('error', () => {
  const err = video.error;
  let detail = 'The browser cannot decode or load this video file.';
  if (err) {
    if (err.code === MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED) {
      detail =
        'Format/codec not supported by browser. Chrome does not support MKV containers, AC3/DTS audio, or some HEVC profiles. For remote URLs, ensure the server allows CORS.';
    } else if (err.code === MediaError.MEDIA_ERR_DECODE) {
      detail = 'Video decoding failed midway. The file may be corrupt or use an unsupported codec.';
    } else if (err.code === MediaError.MEDIA_ERR_NETWORK) {
      detail = 'Network error while streaming the video. Check your connection or stream URL.';
    }
  }
  console.error('Video element error:', err, detail);
  setHeroMediaInfo(currentSourceLabel || 'Playback error', 'Could not load video');
  setBanner(fileInfo, 'error', `Playback Error: ${detail}`);
});

// Local File Picker & Drag-and-Drop
if (fileInput) {
  fileInput.addEventListener('change', (e) => {
    const file = e.target.files?.[0];
    if (file) loadLocalVideoFile(file);
  });
}

if (fileDropzone) {
  fileDropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    fileDropzone.classList.add('is-dragover');
  });
  fileDropzone.addEventListener('dragleave', () => {
    fileDropzone.classList.remove('is-dragover');
  });
  fileDropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    fileDropzone.classList.remove('is-dragover');
    const file = e.dataTransfer?.files?.[0];
    if (file) loadLocalVideoFile(file);
  });
}

// Demo Video
if (btnLoadDemo) {
  btnLoadDemo.addEventListener('click', () => {
    loadDemoVideo(true);
  });
}

// Online URL Streaming
function triggerUrlLoad() {
  const url = streamUrlInput ? streamUrlInput.value.trim() : '';
  if (!url) {
    setBanner(fileInfo, 'error', 'Please enter a valid HTTPS video stream URL.');
    streamUrlInput?.focus();
    return;
  }

  hasMkvWarning = false;
  currentSourceLabel = url;
  video.crossOrigin = 'anonymous';
  video.src = url;
  video.load();
  setHeroMediaInfo('Online Stream', url);
  setBanner(fileInfo, 'loading', `Connecting to stream: ${url}`);
}

if (btnLoadUrl && streamUrlInput) {
  btnLoadUrl.addEventListener('click', triggerUrlLoad);
  streamUrlInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.isComposing) {
      e.preventDefault();
      triggerUrlLoad();
    }
  });
}

// Subtitle File Loader (.srt / .vtt)
if (subtitleFileInput && subtitleInfo) {
  subtitleFileInput.addEventListener('change', async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      const text = await file.text();
      const cues = parseSubtitles(text);
      if (cues.length === 0) {
        cinemaScreen.setSubtitles([]);
        setBanner(subtitleInfo, 'error', `No valid cues found in "${file.name}". Ensure it is a valid .srt or .vtt file.`);
        btnClearSubtitles?.classList.add('hidden');
        subtitleChipLabel?.classList.remove('is-active');
        if (subtitleBtnText) subtitleBtnText.textContent = 'CC';
        return;
      }

      cinemaScreen.setSubtitles(cues);
      setBanner(subtitleInfo, 'ok', `Subtitles: ${file.name} (${cues.length} lines)`);
      btnClearSubtitles?.classList.remove('hidden');
      subtitleChipLabel?.classList.add('is-active');
      if (subtitleBtnText) subtitleBtnText.textContent = `${cues.length}`;
    } catch (err) {
      setBanner(subtitleInfo, 'error', `Could not read subtitle file: ${err.message}`);
    }
  });
}

if (btnClearSubtitles) {
  btnClearSubtitles.addEventListener('click', () => {
    cinemaScreen.setSubtitles([]);
    if (subtitleFileInput) subtitleFileInput.value = '';
    setBanner(subtitleInfo, 'ok', '');
    btnClearSubtitles.classList.add('hidden');
    subtitleChipLabel?.classList.remove('is-active');
    if (subtitleBtnText) subtitleBtnText.textContent = 'CC';
  });
}

// Persistent Headset & Viewing Preferences
const PREFS_KEY = 'jiodive_prefs';

function loadSavedPreferences() {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    if (!raw) {
      updateOpticsSummary();
      updateHeroPreview();
      return;
    }
    const prefs = JSON.parse(raw);

    if (prefs.ipd && setRadioValue('ipd', prefs.ipd)) {
      rig.setIPD(parseFloat(prefs.ipd));
    }
    if (prefs.fov && setRadioValue('fov', prefs.fov)) {
      rig.setFOV(parseFloat(prefs.fov));
    }
    if (prefs.screenSize && setRadioValue('screenSize', prefs.screenSize)) {
      cinemaScreen.setScreenPreset(prefs.screenSize);
    }
    if (prefs.distortion && distortionToggle) {
      const enabled = prefs.distortion === 'jiodive';
      distortionToggle.checked = enabled;
      rig.setDistortion(enabled);
    }
    if (prefs.brightness && setRadioValue('brightness', prefs.brightness)) {
      gazeUI.setBrightness(parseFloat(prefs.brightness));
    }
    if (prefs.dwell && dwellToggle) {
      const enabled = prefs.dwell !== 'off';
      dwellToggle.checked = enabled;
      gazeUI.setGazeDwell(enabled);
    }
  } catch {
    // Ignore storage access errors in restricted modes
  }
  updateOpticsSummary();
  updateHeroPreview();
}

function savePreferences() {
  try {
    localStorage.setItem(
      PREFS_KEY,
      JSON.stringify({
        ipd: getRadioValue('ipd', '0.062'),
        fov: getRadioValue('fov', '75'),
        screenSize: getRadioValue('screenSize', 'standard'),
        distortion: distortionToggle && distortionToggle.checked ? 'jiodive' : 'off',
        brightness: getRadioValue('brightness', '1'),
        dwell: dwellToggle && dwellToggle.checked ? '1200' : 'off'
      })
    );
  } catch {
    // Ignore storage errors
  }
  updateOpticsSummary();
  updateHeroPreview();
}

loadSavedPreferences();

document.querySelectorAll('input[name="env"], input[name="mode"]').forEach((el) => {
  el.addEventListener('change', updateHeroPreview);
});

document.querySelectorAll('input[name="ipd"]').forEach((el) => {
  el.addEventListener('change', () => {
    rig.setIPD(parseFloat(el.value));
    savePreferences();
  });
});

document.querySelectorAll('input[name="fov"]').forEach((el) => {
  el.addEventListener('change', () => {
    rig.setFOV(parseFloat(el.value));
    savePreferences();
  });
});

document.querySelectorAll('input[name="screenSize"]').forEach((el) => {
  el.addEventListener('change', () => {
    cinemaScreen.setScreenPreset(el.value);
    savePreferences();
  });
});

document.querySelectorAll('input[name="brightness"]').forEach((el) => {
  el.addEventListener('change', () => {
    gazeUI.setBrightness(parseFloat(el.value));
    savePreferences();
  });
});

if (distortionToggle) {
  distortionToggle.addEventListener('change', () => {
    rig.setDistortion(distortionToggle.checked);
    savePreferences();
  });
}

if (dwellToggle) {
  dwellToggle.addEventListener('change', () => {
    gazeUI.setGazeDwell(dwellToggle.checked);
    savePreferences();
  });
}

// Keep 2D launcher controls & saved prefs synced when settings change inside VR
window.addEventListener('jiodive:sync', (e) => {
  const { key, value } = e.detail || {};
  if (!key) return;

  if (key === 'mode' || key === 'env' || key === 'screenSize' || key === 'ipd' || key === 'fov' || key === 'brightness') {
    setRadioValue(key, value);
  } else if (key === 'distortion' && distortionToggle) {
    distortionToggle.checked = value === 'jiodive';
  } else if (key === 'swapEyes' && swapEyesToggle) {
    swapEyesToggle.checked = value === 'swapped';
  } else if (key === 'dwell' && dwellToggle) {
    dwellToggle.checked = value !== 'off';
  }

  savePreferences();
});

// Rotate-to-landscape guidance inside VR when holding phone vertically
function syncRotateHint() {
  if (!rotateHint) return;
  const needsLandscape = rig.isVrActive && !rotateHintDismissed && window.innerHeight > window.innerWidth;
  rotateHint.classList.toggle('hidden', !needsLandscape);
}

window.addEventListener('resize', syncRotateHint);
window.addEventListener('orientationchange', syncRotateHint);

if (btnDismissRotate) {
  btnDismissRotate.addEventListener('click', (e) => {
    e.stopPropagation();
    rotateHintDismissed = true;
    rotateHint?.classList.add('hidden');
  });
}

// Screen Wake Lock
async function requestWakeLock() {
  if ('wakeLock' in navigator) {
    try {
      wakeLock = await navigator.wakeLock.request('screen');
      console.log('Screen Wake Lock active');
      wakeLock.addEventListener('release', () => {
        console.log('Screen Wake Lock was released');
        wakeLock = null;
      });
    } catch (err) {
      console.warn('Wake Lock request failed:', err);
    }
  }
}

async function releaseWakeLock() {
  if (wakeLock !== null) {
    try {
      await wakeLock.release();
      wakeLock = null;
      console.log('Screen Wake Lock released');
    } catch (err) {
      console.warn('Error releasing wake lock:', err);
    }
  }
}

document.addEventListener('visibilitychange', async () => {
  if (document.visibilityState === 'visible' && rig.isVrActive) {
    await requestWakeLock();
  } else if (document.visibilityState === 'hidden') {
    await releaseWakeLock();
  }
});

window.addEventListener('pagehide', releaseWakeLock);

// Handle Fullscreen exit cleanly
let isFullscreenActive = false;
document.addEventListener('fullscreenchange', () => {
  if (document.fullscreenElement) {
    isFullscreenActive = true;
  } else if (isFullscreenActive && rig.isVrActive) {
    isFullscreenActive = false;
    gazeUI.exitVR();
  }
});

// Enter VR
btnEnterVr.addEventListener('click', () => {
  rotateHintDismissed = false;

  if (!video.src) {
    loadDemoVideo(false);
  }

  // CRITICAL: Trigger playback SYNCHRONOUSLY inside user click event
  let playPromise = null;
  try {
    playPromise = video.play();
  } catch (err) {
    console.warn('Direct play error:', err);
  }

  if (playPromise !== undefined && playPromise !== null) {
    playPromise.catch(async (err) => {
      console.warn('Autoplay with sound blocked by browser policy, trying muted fallback:', err);
      try {
        video.muted = true;
        await video.play();
        console.log('Playing muted - user can unmute in VR menu');
      } catch (muteErr) {
        console.error('Even muted playback failed:', muteErr);
      }
    });
  }

  // Trigger iOS Gyroscope permission synchronously inside user gesture without blocking VR entry
  rig.requestGyroPermission().then((gyroGranted) => {
    if (!gyroGranted) {
      alert('Motion & Orientation access was denied. If on iPhone, please allow Motion & Orientation access in iOS Settings > Safari so VR head-tracking works.');
    }
  });

  // Trigger Fullscreen, orientation lock (only during VR!), and Wake Lock non-blocking
  if (document.documentElement.requestFullscreen) {
    try {
      document.documentElement.requestFullscreen()
        .then(() => {
          if (window.screen?.orientation?.lock) {
            return window.screen.orientation.lock('landscape').catch(() => {});
          }
        })
        .catch(() => {
          console.log('Fullscreen skipped or denied');
        });
    } catch (err) {
      console.log('Fullscreen call failed:', err);
    }
  } else if (window.screen?.orientation?.lock) {
    window.screen.orientation.lock('landscape').catch(() => {});
  }

  requestWakeLock().catch(() => {});

  // Apply selected settings
  cinemaScreen.setSwapEyes(Boolean(swapEyesToggle && swapEyesToggle.checked));
  cinemaScreen.setFormat(getRadioValue('mode', '2d'));
  cinemaScreen.setScreenPreset(getRadioValue('screenSize', 'standard'));
  environments.setEnvironment(getRadioValue('env', 'cinema'), cinemaScreen, gazeUI);

  rig.setIPD(parseFloat(getRadioValue('ipd', '0.062')));
  rig.setFOV(parseFloat(getRadioValue('fov', '75')));
  rig.setDistortion(Boolean(distortionToggle ? distortionToggle.checked : true));
  gazeUI.setGazeDwell(Boolean(dwellToggle && dwellToggle.checked));

  if (gazeUI.setSettingsOpen) {
    gazeUI.setSettingsOpen(false);
  }

  // Hide 2D launcher & enter VR immediately
  launcher.inert = true;
  launcher.classList.add('hidden');
  rig.isVrActive = true;
  gazeUI.setBrightness(parseFloat(getRadioValue('brightness', '1')));
  rig.recenter();
  syncRotateHint();
});

// Main Animation Loop
function animate(time) {
  requestAnimationFrame(animate);

  if (!rig.isVrActive) {
    return;
  }

  rig.update();
  ambient.update(time);
  gazeUI.update();
  rig.render();
}

animate(0);
