import * as THREE from 'three';
import { StereoRig } from './vr/stereoRig.js';
import { CinemaScreen } from './vr/screenMesh.js';
import { AmbientLighting } from './vr/ambientLighting.js';
import { Environments } from './vr/environments.js';
import { GazeUI } from './vr/gazeUI.js';
import { parseSubtitles } from './vr/subtitleParser.js';

// Elements
const canvas = document.getElementById('vr-canvas');
const video = document.getElementById('video-player');
const launcher = document.getElementById('launcher');
const btnEnterVr = document.getElementById('btn-enter-vr');
const fileInput = document.getElementById('video-file-input');
const btnLoadDemo = document.getElementById('btn-load-demo');
const streamUrlInput = document.getElementById('stream-url-input');
const btnLoadUrl = document.getElementById('btn-load-url');
const fileInfo = document.getElementById('file-info');
const subtitleFileInput = document.getElementById('subtitle-file-input');
const btnClearSubtitles = document.getElementById('btn-clear-subtitles');
const subtitleInfo = document.getElementById('subtitle-info');
const modeSelect = document.getElementById('mode-select');
const envSelect = document.getElementById('env-select');
const ipdSelect = document.getElementById('ipd-select');
const fovSelect = document.getElementById('fov-select');
const sizeSelect = document.getElementById('size-select');
const swapEyesSelect = document.getElementById('swap-eyes-select');

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
  video.crossOrigin = 'anonymous';
  video.src = pickSupportedDemoUrl();
  video.load();
  if (showBanner) {
    fileInfo.className = 'file-info';
    fileInfo.textContent = 'Loaded: Oceans HD (Online Sample)';
    fileInfo.classList.remove('hidden');
  }
}

// Setup Three.js Scene & Renderer
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x050508);

const renderer = new THREE.WebGLRenderer({
  canvas: canvas,
  antialias: true,
  powerPreference: 'high-performance'
});
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);

// Initialize VR Modules
const rig = new StereoRig(renderer, scene);
const cinemaScreen = new CinemaScreen(scene, video);
const environments = new Environments(scene);
const ambient = new AmbientLighting(scene, video, environments);
const gazeUI = new GazeUI(rig, video, cinemaScreen, environments);

let wakeLock = null;

// Video Error Handling
video.addEventListener('error', () => {
  const err = video.error;
  let detail = 'The browser cannot decode or load this video file.';
  if (err) {
    if (err.code === MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED) {
      detail = 'Format/codec not supported by browser. Note: Google Chrome does NOT support MKV containers, AC3/DTS audio, or certain HEVC profiles natively. If streaming an online URL, ensure the server permits CORS headers (Access-Control-Allow-Origin).';
    } else if (err.code === MediaError.MEDIA_ERR_DECODE) {
      detail = 'Video decoding failed midway. The file might be corrupt or encoded with an unsupported profile.';
    } else if (err.code === MediaError.MEDIA_ERR_NETWORK) {
      detail = 'Network error while attempting to stream the video file. Check your internet connection or verify the stream URL.';
    }
  }
  console.error('Video element error:', err, detail);
  fileInfo.className = 'file-info error';
  fileInfo.textContent = `⚠️ Playback Error: ${detail}`;
  fileInfo.classList.remove('hidden');
});

// File Picker (Item 7 Fix: safe DOM construction with textContent to prevent filename injection)
fileInput.addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (file) {
    const isMkv = file.name.toLowerCase().endsWith('.mkv');
    fileInfo.textContent = '';

    if (isMkv) {
      fileInfo.className = 'file-info error';
      fileInfo.textContent = `⚠️ Note on MKV: "${file.name}" is an MKV container. Browsers (Chrome/Safari) cannot natively decode MKV files. If the screen is black or silent, please convert it to MP4 (H.264 + AAC) or WebM.`;
      fileInfo.classList.remove('hidden');
    } else {
      fileInfo.className = 'file-info';
      fileInfo.textContent = `Loaded: ${file.name} (${(file.size / (1024 * 1024)).toFixed(1)} MB)`;
      fileInfo.classList.remove('hidden');
    }

    // Revoke previous blob URL if exists
    if (video.src && video.src.startsWith('blob:')) {
      URL.revokeObjectURL(video.src);
    }

    const url = URL.createObjectURL(file);
    // Remove crossOrigin for local blobs to prevent origin issues
    video.removeAttribute('crossorigin');
    video.src = url;
    video.load();
  }
});

// Demo Video (Item 6 Fix: verified CORS video with MP4/WebM codec detection)
btnLoadDemo.addEventListener('click', () => {
  loadDemoVideo(true);
});

// Online URL Streaming (Item 5 Fix)
if (btnLoadUrl && streamUrlInput) {
  btnLoadUrl.addEventListener('click', () => {
    const url = streamUrlInput.value.trim();
    if (!url) {
      fileInfo.className = 'file-info error';
      fileInfo.textContent = 'Please enter a valid video stream URL.';
      fileInfo.classList.remove('hidden');
      return;
    }

    video.crossOrigin = 'anonymous';
    video.src = url;
    video.load();
    fileInfo.className = 'file-info';
    fileInfo.textContent = `Streaming URL: ${url}`;
    fileInfo.classList.remove('hidden');
  });
}

// Subtitle File Loader (.srt / .vtt)
if (subtitleFileInput && subtitleInfo) {
  subtitleFileInput.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    try {
      const text = await file.text();
      const cues = parseSubtitles(text);
      if (cues.length === 0) {
        cinemaScreen.setSubtitles([]);
        subtitleInfo.className = 'file-info error';
        subtitleInfo.textContent = `⚠️ No valid cues found in "${file.name}". Ensure it is a valid .srt or .vtt file.`;
        subtitleInfo.classList.remove('hidden');
        if (btnClearSubtitles) btnClearSubtitles.classList.add('hidden');
        return;
      }

      cinemaScreen.setSubtitles(cues);
      subtitleInfo.className = 'file-info';
      subtitleInfo.textContent = `Loaded Subtitles: ${file.name} (${cues.length} lines)`;
      subtitleInfo.classList.remove('hidden');
      if (btnClearSubtitles) btnClearSubtitles.classList.remove('hidden');
    } catch (err) {
      subtitleInfo.className = 'file-info error';
      subtitleInfo.textContent = `⚠️ Could not read subtitle file: ${err.message}`;
      subtitleInfo.classList.remove('hidden');
    }
  });
}

if (btnClearSubtitles) {
  btnClearSubtitles.addEventListener('click', () => {
    cinemaScreen.setSubtitles([]);
    if (subtitleFileInput) subtitleFileInput.value = '';
    if (subtitleInfo) {
      subtitleInfo.textContent = '';
      subtitleInfo.classList.add('hidden');
    }
    btnClearSubtitles.classList.add('hidden');
  });
}

// Persistent Headset & Viewing Preferences (IPD, FOV, Screen Size)
const PREFS_KEY = 'jiodive_prefs';

function restoreSelectOption(selectEl, value, onApply) {
  if (selectEl && value && Array.from(selectEl.options).some((o) => o.value === String(value))) {
    selectEl.value = String(value);
    onApply(selectEl.value);
  }
}

function loadSavedPreferences() {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    if (!raw) return;
    const prefs = JSON.parse(raw);
    restoreSelectOption(ipdSelect, prefs.ipd, (v) => rig.setIPD(parseFloat(v)));
    restoreSelectOption(fovSelect, prefs.fov, (v) => rig.setFOV(parseFloat(v)));
    restoreSelectOption(sizeSelect, prefs.screenSize, (v) => cinemaScreen.setScreenPreset(v));
  } catch {
    // Ignore storage access errors in restricted modes
  }
}

function savePreferences() {
  try {
    localStorage.setItem(
      PREFS_KEY,
      JSON.stringify({
        ipd: ipdSelect ? ipdSelect.value : '0.064',
        fov: fovSelect ? fovSelect.value : '75',
        screenSize: sizeSelect ? sizeSelect.value : 'standard'
      })
    );
  } catch {
    // Ignore storage errors
  }
}

loadSavedPreferences();
[ipdSelect, fovSelect, sizeSelect].forEach((el) => {
  if (el) el.addEventListener('change', savePreferences);
});

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

// Re-request wake lock when returning from background
document.addEventListener('visibilitychange', async () => {
  if (document.visibilityState === 'visible' && rig.isVrActive) {
    await requestWakeLock();
  } else if (document.visibilityState === 'hidden') {
    await releaseWakeLock();
  }
});

window.addEventListener('pagehide', releaseWakeLock);

// Handle Fullscreen exit (Item 3 Fix: auto exit VR only when leaving an active fullscreen session)
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
  // If no video loaded, load demo automatically
  if (!video.src) {
    loadDemoVideo(false);
  }

  // CRITICAL: Trigger playback SYNCHRONOUSLY directly inside the user click event!
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

  // Trigger Fullscreen, orientation lock, and Wake Lock non-blocking
  // (embedded WebViews and iOS Safari may leave fullscreen/wakelock Promises pending or unsupported)
  if (document.documentElement.requestFullscreen) {
    try {
      document.documentElement.requestFullscreen()
        .then(() => {
          if (window.screen && window.screen.orientation && window.screen.orientation.lock) {
            return window.screen.orientation.lock('landscape').catch(() => {});
          }
        })
        .catch(() => {
          console.log('Fullscreen skipped or denied');
        });
    } catch (err) {
      console.log('Fullscreen call failed:', err);
    }
  } else if (window.screen && window.screen.orientation && window.screen.orientation.lock) {
    window.screen.orientation.lock('landscape').catch(() => {});
  }

  requestWakeLock().catch(() => {});

  // Apply selected settings (Eye Swap, Format, Screen Size, Environment, IPD, FOV)
  cinemaScreen.setSwapEyes(Boolean(swapEyesSelect && swapEyesSelect.value === 'swapped'));
  cinemaScreen.setFormat(modeSelect.value);
  if (sizeSelect) {
    cinemaScreen.setScreenPreset(sizeSelect.value);
  }
  environments.setEnvironment(envSelect.value, cinemaScreen, gazeUI);

  if (ipdSelect) {
    rig.setIPD(parseFloat(ipdSelect.value));
  }
  if (fovSelect) {
    rig.setFOV(parseFloat(fovSelect.value));
  }

  // Hide 2D launcher & enter VR immediately
  launcher.classList.add('hidden');
  rig.isVrActive = true;
  rig.recenter();
});

// Main Animation Loop
function animate(time) {
  requestAnimationFrame(animate);

  // Skip 100% of 3D rendering while 2D launcher is visible
  if (!rig.isVrActive) {
    return;
  }

  // Update VR modules
  rig.update();
  ambient.update(time);
  gazeUI.update();

  // Render Left & Right eye viewports
  rig.render();
}

animate(0);
