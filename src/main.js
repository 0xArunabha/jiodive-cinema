import * as THREE from 'three';
import { StereoRig } from './vr/stereoRig.js';
import { CinemaScreen } from './vr/screenMesh.js';
import { AmbientLighting } from './vr/ambientLighting.js';
import { Environments } from './vr/environments.js';
import { GazeUI } from './vr/gazeUI.js';

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
const modeSelect = document.getElementById('mode-select');
const envSelect = document.getElementById('env-select');
const ipdSelect = document.getElementById('ipd-select');
const fovSelect = document.getElementById('fov-select');

// Verified high-reliability sample video with full CORS support (VideoJS CDN)
const DEMO_VIDEO_URL = 'https://vjs.zencdn.net/v/oceans.mp4';

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

// Demo Video (Item 6 Fix: verified CORS video)
btnLoadDemo.addEventListener('click', () => {
  video.crossOrigin = 'anonymous';
  video.src = DEMO_VIDEO_URL;
  video.load();
  fileInfo.className = 'file-info';
  fileInfo.textContent = `Loaded: Oceans HD (Online Sample)`;
  fileInfo.classList.remove('hidden');
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

// Handle Fullscreen exit (Item 3 Fix: auto exit VR if user presses Esc or swipes down)
document.addEventListener('fullscreenchange', () => {
  if (!document.fullscreenElement && rig.isVrActive) {
    gazeUI.exitVR();
  }
});

// Enter VR
btnEnterVr.addEventListener('click', async () => {
  // If no video loaded, load demo automatically
  if (!video.src) {
    video.crossOrigin = 'anonymous';
    video.src = DEMO_VIDEO_URL;
    video.load();
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

  // Request Gyroscope permissions with iOS alert if denied
  const gyroGranted = await rig.requestGyroPermission();
  if (!gyroGranted) {
    alert('Motion & Orientation access was denied. If on iPhone, please allow Motion & Orientation access in iOS Settings > Safari so VR head-tracking works.');
  }

  // Fullscreen and orientation lock
  try {
    if (document.documentElement.requestFullscreen) {
      await document.documentElement.requestFullscreen();
    }
    if (window.screen && window.screen.orientation && window.screen.orientation.lock) {
      await window.screen.orientation.lock('landscape').catch(() => {});
    }
  } catch (err) {
    console.log('Fullscreen/orientation lock skipped or denied');
  }

  await requestWakeLock();

  // Apply selected settings (Format, Environment, IPD, FOV)
  cinemaScreen.setFormat(modeSelect.value);
  environments.setEnvironment(envSelect.value, cinemaScreen, gazeUI);

  if (ipdSelect) {
    rig.setIPD(parseFloat(ipdSelect.value));
  }
  if (fovSelect) {
    rig.setFOV(parseFloat(fovSelect.value));
  }

  // If video is still paused, attempt play once more
  if (video.paused) {
    video.play().catch((e) => console.log('Deferred play:', e));
  }

  // Hide 2D launcher
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
