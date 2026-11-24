import * as THREE from 'three';
import { SCREEN_PRESETS } from './screenMesh.js';

/**
 * In-VR Floating Gaze & Physical Click UI
 * Optimized for JioDive conductive physical button:
 * - Single Click: Selects aimed button OR starts video / toggles menu visibility (debounced)
 * - Double Click: Instantly recenters VR view without side-effects
 * - Bed Mode: Automatically tilts menu upward with screen
 */
export class GazeUI {
  constructor(stereoRig, videoElement, cinemaScreen, environments) {
    this.rig = stereoRig;
    this.video = videoElement;
    this.screen = cinemaScreen;
    this.environments = environments;

    this.interactiveObjects = [];
    this.hoveredObject = null;
    this.hoverSeekRatio = null;
    this.raycaster = new THREE.Raycaster();
    this.raycaster.layers.enable(1); // Match cinema screen meshL (layer 1) in addition to layer 0 UI
    this._rayOrigin = new THREE.Vector3();
    this._rayDir = new THREE.Vector3();
    this.isMenuVisible = true;

    this.lastSeekRenderKey = '';
    this.lastTimeRenderKey = '';
    this.ccHintUntil = 0;

    // Gaze Dwell & Comfort Dimmer state
    this.gazeDwellEnabled = false;
    this.dwellDelayMs = 1200;
    this.dwellGraceMs = 200;
    this.dwellTarget = null;
    this.dwellStart = 0;
    this.dwellConsumed = false;
    this.brightness = 1.0;

    // Gamepad state
    this.prevPadButtons = {};
    this.lastUpdateTime = performance.now();

    // Physical button double-click detection & single-click debounce timer
    this.lastClickTime = 0;
    this.clickTimeout = null;

    // Reticle + Dwell Progress Ring
    this.createReticle();

    // Floating 3D Menu Panel
    this.createControlPanel();

    // Physical Button, Keyboard & Pointer Listeners
    this.initClickListeners();
  }

  createReticle() {
    // Reticle dot fixed at center of view
    const geom = new THREE.RingGeometry(0.008, 0.016, 24);
    const mat = new THREE.MeshBasicMaterial({
      color: 0xffffff,
      opacity: 0.85,
      transparent: true,
      depthTest: false
    });

    this.reticle = new THREE.Mesh(geom, mat);
    this.reticle.position.set(0, 0, -1.8);
    this.reticle.renderOrder = 999;
    this.rig.head.add(this.reticle);

    // GPU-driven radial dwell ring wrapped around the reticle
    this.dwellUniforms = { uProgress: { value: 0.0 } };
    const dwellMat = new THREE.ShaderMaterial({
      uniforms: this.dwellUniforms,
      transparent: true,
      depthTest: false,
      depthWrite: false,
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform float uProgress;
        varying vec2 vUv;
        void main() {
          vec2 p = vUv - 0.5;
          float r = length(p);
          float ringMask = smoothstep(0.32, 0.35, r) * (1.0 - smoothstep(0.46, 0.49, r));
          float angle = atan(p.x, p.y);
          float norm = (angle < 0.0 ? angle + 6.2831853 : angle) / 6.2831853;
          float arcMask = step(norm, uProgress);
          float alpha = ringMask * arcMask * 0.95;
          if (alpha <= 0.01) discard;
          gl_FragColor = vec4(0.22, 0.74, 0.97, alpha);
        }
      `
    });
    this.dwellRing = new THREE.Mesh(new THREE.PlaneGeometry(0.056, 0.056), dwellMat);
    this.dwellRing.position.set(0, 0, -1.8);
    this.dwellRing.renderOrder = 1000;
    this.dwellRing.visible = false;
    this.rig.head.add(this.dwellRing);
  }

  createUiTexture(logicalW, logicalH) {
    const canvas = document.createElement('canvas');
    canvas.width = logicalW * 2;
    canvas.height = logicalH * 2;
    const ctx = canvas.getContext('2d');
    ctx.scale(2, 2); // 2x supersampled HiDPI drawing
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    const maxAniso = this.rig?.renderer?.capabilities?.getMaxAnisotropy?.() || 1;
    texture.anisotropy = Math.min(8, maxAniso);
    return { canvas, ctx, texture, logicalW, logicalH };
  }

  createControlPanel() {
    this.menuGroup = new THREE.Group();
    // Position floating panel cleanly above floor and below IMAX screen edge, closer for sharp legibility
    this.menuGroup.position.set(0, 0.49, -3.6);
    this.menuGroup.rotation.x = -0.2;
    this.menuGroup.scale.set(0.58, 0.58, 0.58);
    this.rig.scene.add(this.menuGroup);

    // Rounded high-opacity glass backdrop so distant stage LED lines never bleed through the menu
    const bgUi = this.createUiTexture(792, 256);
    const { ctx: bgCtx, logicalW: bw, logicalH: bh } = bgUi;
    bgCtx.fillStyle = 'rgba(11, 13, 24, 0.98)';
    bgCtx.beginPath();
    if (bgCtx.roundRect) {
      bgCtx.roundRect(3, 3, bw - 6, bh - 6, 22);
    } else {
      bgCtx.rect(3, 3, bw - 6, bh - 6);
    }
    bgCtx.fill();
    bgCtx.strokeStyle = 'rgba(79, 70, 229, 0.45)';
    bgCtx.lineWidth = 3;
    bgCtx.stroke();

    const bgGeom = new THREE.PlaneGeometry(3.96, 1.28);
    const bgMat = new THREE.MeshBasicMaterial({
      map: bgUi.texture,
      transparent: true,
      alphaTest: 0.05,
      depthWrite: true,
      side: THREE.DoubleSide
    });
    const bg = new THREE.Mesh(bgGeom, bgMat);
    this.menuGroup.add(bg);

    // Top Row (y = 0.46): Interactive Seek Bar + Time Readout + CC Toggle
    this.createSeekBarRow(0.46);

    const ipdMm = Math.round(this.rig.ipd * 1000);
    const buttons = [
      // Row 1 (y = 0.17) - Playback, Scene & Screen Size
      { label: '⏪ -10s', x: -1.55, y: 0.17, action: () => this.seek(-10) },
      { label: '▶ Play', x: -0.93, y: 0.17, action: () => this.togglePlay(), ref: 'playBtnMesh' },
      { label: '⏩ +10s', x: -0.31, y: 0.17, action: () => this.seek(10) },
      { label: '🏛 Room', x: 0.31, y: 0.17, action: () => this.cycleEnvironment() },
      { label: '📐 Standard', x: 0.93, y: 0.17, action: () => this.cycleScreenPreset(), ref: 'sizeBtnMesh' },
      { label: '🎯 Recenter', x: 1.55, y: 0.17, action: () => this.rig.recenter() },
      // Row 2 (y = -0.13) - Audio, 3D Format, Eye Swap, IPD, Bed & Exit
      { label: '🔊 Sound', x: -1.55, y: -0.13, action: () => this.toggleAudio(), ref: 'audioBtnMesh' },
      { label: '🕶 2D', x: -0.93, y: -0.13, action: () => this.cycleFormat(), ref: 'formatBtnMesh' },
      { label: '🔀 L/R', x: -0.31, y: -0.13, action: () => this.toggleSwapEyes(), ref: 'swapBtnMesh' },
      { label: `👁 ${ipdMm}mm`, x: 0.31, y: -0.13, action: () => this.cycleIPD(), ref: 'ipdBtnMesh' },
      { label: '🛏 Bed', x: 0.93, y: -0.13, action: () => this.toggleBedMode() },
      { label: '🚪 Exit', x: 1.55, y: -0.13, action: () => this.exitVR() }
    ];

    buttons.forEach((b) => {
      const btnMesh = this.createButtonMesh(b.label, b.x, b.y, b.action, {
        width: 0.56,
        height: 0.2,
        fontSize: 23
      });
      this.menuGroup.add(btnMesh);
      this.interactiveObjects.push(btnMesh);
      if (b.ref) {
        this[b.ref] = btnMesh;
      }
    });

    // Row 3 Utility Strip (y = -0.44) - Optics Distortion, Brightness Dimmer, Gaze Dwell, FOV
    const utilityButtons = [
      { label: '🔍 Lens On', x: -1.41, y: -0.44, action: () => this.toggleDistortion(), ref: 'lensBtnMesh' },
      { label: '☀ 100%', x: -0.47, y: -0.44, action: () => this.cycleBrightness(), ref: 'brightBtnMesh' },
      { label: '⌛ Dwell Off', x: 0.47, y: -0.44, action: () => this.toggleGazeDwell(), ref: 'dwellBtnMesh' },
      { label: `🔭 FOV ${Math.round(this.rig.fov)}°`, x: 1.41, y: -0.44, action: () => this.cycleFOV(), ref: 'fovBtnMesh' }
    ];

    utilityButtons.forEach((b) => {
      const btnMesh = this.createButtonMesh(b.label, b.x, b.y, b.action, {
        width: 0.86,
        height: 0.16,
        canvasW: 320,
        canvasH: 64,
        fontSize: 24
      });
      this.menuGroup.add(btnMesh);
      this.interactiveObjects.push(btnMesh);
      this[b.ref] = btnMesh;
    });
  }

  createSeekBarRow(rowY = 0.46) {
    // 1. Seek Track (interactive UV horizontal scrub, 2x HiDPI)
    const seekUi = this.createUiTexture(512, 48);
    const seekGeom = new THREE.PlaneGeometry(2.34, 0.14);
    const seekMat = new THREE.MeshBasicMaterial({ map: seekUi.texture, transparent: true, side: THREE.DoubleSide });
    this.seekBarMesh = new THREE.Mesh(seekGeom, seekMat);
    this.seekBarMesh.position.set(-0.66, rowY, 0.02);
    this.seekBarMesh.userData = {
      isSeekBar: true,
      noHoverScale: true,
      ...seekUi
    };
    this.menuGroup.add(this.seekBarMesh);
    this.interactiveObjects.push(this.seekBarMesh);

    // 2. Time Display Readout (2x HiDPI)
    const timeUi = this.createUiTexture(256, 48);
    const timeGeom = new THREE.PlaneGeometry(0.74, 0.14);
    const timeMat = new THREE.MeshBasicMaterial({ map: timeUi.texture, transparent: true, side: THREE.DoubleSide });
    this.timeMesh = new THREE.Mesh(timeGeom, timeMat);
    this.timeMesh.position.set(0.94, rowY, 0.02);
    this.timeMesh.userData = { ...timeUi };
    this.menuGroup.add(this.timeMesh);

    // 3. Subtitle Toggle Button
    const ccBtn = this.createButtonMesh('💬 CC', 1.60, rowY, () => this.toggleSubtitles(), {
      width: 0.46,
      height: 0.14,
      canvasW: 168,
      canvasH: 56,
      fontSize: 21
    });
    this.menuGroup.add(ccBtn);
    this.interactiveObjects.push(ccBtn);
    this.ccBtnMesh = ccBtn;

    this.drawSeekBar(0, null);
    this.drawTimeReadout(0, 0, null);
  }

  createButtonMesh(text, x, y, onClick, opts = {}) {
    const width = opts.width ?? 0.62;
    const height = opts.height ?? 0.2;
    const canvasW = opts.canvasW ?? 256;
    const canvasH = opts.canvasH ?? 80;
    const fontSize = opts.fontSize ?? 26;

    const ui = this.createUiTexture(canvasW, canvasH);
    const geom = new THREE.PlaneGeometry(width, height);
    const mat = new THREE.MeshBasicMaterial({
      map: ui.texture,
      transparent: true,
      side: THREE.DoubleSide
    });

    const mesh = new THREE.Mesh(geom, mat);
    mesh.position.set(x, y, 0.02);
    mesh.userData = {
      onClick,
      originalScale: new THREE.Vector3(1, 1, 1),
      ...ui,
      canvasW,
      canvasH,
      fontSize,
      currentText: ''
    };

    this.updateButtonText(mesh, text);
    return mesh;
  }

  updateButtonText(mesh, text) {
    if (!mesh || !mesh.userData) return;
    const { ctx, canvasW = 256, canvasH = 80, fontSize = 26 } = mesh.userData;

    ctx.clearRect(0, 0, canvasW, canvasH);

    ctx.fillStyle = '#1e2235';
    if (ctx.roundRect) {
      ctx.beginPath();
      ctx.roundRect(4, 4, canvasW - 8, canvasH - 8, 16);
      ctx.fill();
    } else {
      ctx.fillRect(4, 4, canvasW - 8, canvasH - 8);
    }

    ctx.strokeStyle = '#4f46e5';
    ctx.lineWidth = 4;
    if (ctx.roundRect) {
      ctx.beginPath();
      ctx.roundRect(4, 4, canvasW - 8, canvasH - 8, 16);
      ctx.stroke();
    } else {
      ctx.strokeRect(4, 4, canvasW - 8, canvasH - 8);
    }

    ctx.fillStyle = '#ffffff';
    ctx.font = `bold ${fontSize}px -apple-system, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, canvasW / 2, canvasH / 2);

    mesh.userData.currentText = text;
    if (mesh.material.map) {
      mesh.material.map.needsUpdate = true;
    }
  }

  triggerInteractiveObject(targetObj, targetSeekRatio) {
    if (targetObj === this.seekBarMesh && targetSeekRatio !== null) {
      this.seekToRatio(targetSeekRatio);
      return true;
    }
    if (targetObj && targetObj.userData && targetObj.userData.onClick) {
      targetObj.userData.onClick();
      if (!targetObj.userData.noHoverScale) {
        this.pulseButton(targetObj);
      }
      return true;
    }
    return false;
  }

  triggerEmptySpaceAction() {
    if (this.video && this.video.paused) {
      this.video.play().catch((err) => console.log('Tap play failed:', err));
    } else {
      this.toggleMenu();
    }
  }

  initClickListeners() {
    const handleTrigger = () => {
      // Bug 2: Ignore any pointer events if VR is not active (prevents launcher taps triggering VR UI)
      if (!this.rig.isVrActive) return;

      const now = performance.now();
      // Double click detection (<350ms) -> Instant Recenter!
      if (now - this.lastClickTime < 350) {
        // Bug 4: Cancel pending single click action so recenter has zero side-effects
        if (this.clickTimeout) {
          clearTimeout(this.clickTimeout);
          this.clickTimeout = null;
        }
        this.lastClickTime = 0;
        this.rig.recenter();
        return;
      }
      this.lastClickTime = now;

      // Capture target object & seek ratio at time of tap
      const targetObj = this.hoveredObject;
      const targetSeekRatio = this.hoverSeekRatio;

      // Bug 4: Debounce single click action (~280ms) so double-click cancels cleanly
      this.clickTimeout = setTimeout(() => {
        this.clickTimeout = null;
        if (!this.rig.isVrActive) return;
        if (!this.triggerInteractiveObject(targetObj, targetSeekRatio)) {
          this.triggerEmptySpaceAction();
        }
      }, 280);
    };

    // JioDive capacitive button triggers pointerdown / touchstart on the screen
    window.addEventListener('pointerdown', handleTrigger);

    // Keyboard shortcuts for desktop / Bluetooth HID clickers while VR is active
    window.addEventListener('keydown', (e) => {
      if (!this.rig.isVrActive) return;
      if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'TEXTAREA')) {
        return;
      }

      switch (e.code) {
        case 'Space':
        case 'Enter':
        case 'KeyK':
          e.preventDefault();
          if (!this.triggerInteractiveObject(this.hoveredObject, this.hoverSeekRatio)) {
            this.togglePlay();
          }
          break;
        case 'ArrowLeft':
        case 'KeyJ':
          e.preventDefault();
          this.seek(-10);
          break;
        case 'ArrowRight':
        case 'KeyL':
          e.preventDefault();
          this.seek(10);
          break;
        case 'KeyM':
          this.toggleAudio();
          break;
        case 'KeyH':
          this.toggleMenu();
          break;
        case 'KeyR':
          this.rig.recenter();
          break;
        case 'Escape':
          this.exitVR();
          break;
      }
    });
  }

  pulseButton(mesh) {
    mesh.scale.set(1.15, 1.15, 1.15);
    setTimeout(() => {
      mesh.scale.set(1, 1, 1);
    }, 150);
  }

  syncSelect(id, value) {
    const el = document.getElementById(id);
    if (el && el.value !== value) {
      el.value = value;
      el.dispatchEvent(new Event('change'));
    }
  }

  toggleMenu() {
    this.isMenuVisible = !this.isMenuVisible;
    this.menuGroup.visible = this.isMenuVisible;
  }

  togglePlay() {
    if (!this.video) return;
    if (this.video.paused) {
      this.video.play().catch((e) => console.log('Play error:', e));
    } else {
      this.video.pause();
    }
  }

  toggleAudio() {
    if (!this.video) return;
    this.video.muted = !this.video.muted;
  }

  seek(seconds) {
    if (!this.video || !this.video.duration) return;
    this.video.currentTime = Math.max(0, Math.min(this.video.duration, this.video.currentTime + seconds));
  }

  seekToRatio(ratio) {
    if (!this.video || !Number.isFinite(this.video.duration) || this.video.duration <= 0) return;
    const clamped = THREE.MathUtils.clamp(ratio, 0, 1);
    this.video.currentTime = clamped * this.video.duration;
  }

  toggleSubtitles() {
    if (!this.screen) return;
    if (!this.screen.subtitleCues || this.screen.subtitleCues.length === 0) {
      this.ccHintUntil = performance.now() + 1800;
      return;
    }
    this.screen.toggleSubtitles();
  }

  cycleEnvironment() {
    const envs = ['cinema', 'void'];
    const next = envs[(envs.indexOf(this.environments.currentEnv) + 1) % envs.length];
    this.environments.setEnvironment(next, this.screen, this);
    this.syncSelect('env-select', next);
  }

  toggleBedMode() {
    const target = this.environments.currentEnv === 'bed' ? 'cinema' : 'bed';
    this.environments.setEnvironment(target, this.screen, this);
    this.syncSelect('env-select', target);
  }

  cycleFormat() {
    const formats = ['2d', '3d-hsbs', '3d-fsbs', '3d-hou', '3d-fou'];
    const next = formats[(formats.indexOf(this.screen.format) + 1) % formats.length];
    this.screen.setFormat(next);
    this.syncSelect('mode-select', next);
  }

  cycleScreenPreset() {
    const presets = ['imax', 'standard', 'balcony'];
    const next = presets[(presets.indexOf(this.screen.screenPreset) + 1) % presets.length];
    this.screen.setScreenPreset(next);
    this.syncSelect('size-select', next);
  }

  toggleSwapEyes() {
    if (!this.screen) return;
    const swapped = this.screen.toggleSwapEyes();
    this.syncSelect('swap-eyes-select', swapped ? 'swapped' : 'normal');
  }

  cycleIPD() {
    const ipdValues = [0.058, 0.062, 0.064, 0.068, 0.072];
    const currentIndex = ipdValues.findIndex(v => Math.abs(v - this.rig.ipd) < 0.001);
    const nextIndex = (currentIndex + 1) % ipdValues.length;
    const newIPD = ipdValues[nextIndex];
    this.rig.setIPD(newIPD);
    const mm = Math.round(newIPD * 1000);
    if (this.ipdBtnMesh) {
      this.updateButtonText(this.ipdBtnMesh, `👁 ${mm}mm`);
    }
    this.syncSelect('ipd-select', newIPD.toString());
  }

  toggleDistortion() {
    const next = !this.rig.distortionEnabled;
    this.rig.setDistortion(next);
    this.syncSelect('distortion-select', next ? 'jiodive' : 'off');
  }

  setBrightness(level) {
    this.brightness = THREE.MathUtils.clamp(level, 0.4, 1.0);
    const dimmer = document.getElementById('vr-dimmer');
    if (dimmer) {
      const opacity = this.rig.isVrActive ? Math.max(0, 1.0 - this.brightness) : 0;
      dimmer.style.opacity = opacity.toFixed(2);
    }
  }

  cycleBrightness() {
    const levels = [1.0, 0.8, 0.6];
    const currentIndex = levels.findIndex(v => Math.abs(v - this.brightness) < 0.05);
    const next = levels[(currentIndex + 1) % levels.length];
    this.setBrightness(next);
    this.syncSelect('brightness-select', next.toString());
  }

  setGazeDwell(enabled) {
    this.gazeDwellEnabled = Boolean(enabled);
    if (!this.gazeDwellEnabled) {
      this.dwellTarget = null;
      this.dwellConsumed = false;
      if (this.dwellRing) this.dwellRing.visible = false;
    }
  }

  toggleGazeDwell() {
    const next = !this.gazeDwellEnabled;
    this.setGazeDwell(next);
    this.syncSelect('dwell-select', next ? '1200' : 'off');
  }

  cycleFOV() {
    const fovs = [70, 75, 80, 85];
    const currentIndex = fovs.findIndex(v => Math.abs(v - this.rig.fov) < 1);
    const next = fovs[(currentIndex + 1) % fovs.length];
    this.rig.setFOV(next);
    this.syncSelect('fov-select', next.toString());
  }

  exitVR() {
    this.rig.isVrActive = false;
    this.setBrightness(this.brightness); // Ensures dimmer opacity resets to 0 on launcher
    if (this.video) {
      this.video.pause();
    }
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    }
    const launcher = document.getElementById('launcher');
    if (launcher) {
      launcher.classList.remove('hidden');
    }
  }

  formatTime(seconds) {
    if (!Number.isFinite(seconds) || seconds < 0) return '0:00';
    const totalSec = Math.floor(seconds);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    const mm = h > 0 ? String(m).padStart(2, '0') : String(m);
    const ss = String(s).padStart(2, '0');
    return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
  }

  drawSeekBar(progressRatio, hoverRatio) {
    if (!this.seekBarMesh || !this.seekBarMesh.userData) return;
    const { ctx, logicalW: w, logicalH: h } = this.seekBarMesh.userData;
    const padX = 14;
    const trackW = w - padX * 2;
    const trackH = 12;
    const trackY = (h - trackH) / 2;

    ctx.clearRect(0, 0, w, h);

    // Subtle interactive container pill
    ctx.fillStyle = hoverRatio !== null ? '#252a42' : '#181b2c';
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(2, 2, w - 4, h - 4, 14);
    } else {
      ctx.rect(2, 2, w - 4, h - 4);
    }
    ctx.fill();

    ctx.strokeStyle = hoverRatio !== null ? '#818cf8' : '#313659';
    ctx.lineWidth = 3;
    ctx.stroke();

    // Base track
    ctx.fillStyle = '#334155';
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(padX, trackY, trackW, trackH, 6);
    } else {
      ctx.rect(padX, trackY, trackW, trackH);
    }
    ctx.fill();

    // Filled progress
    const fillW = Math.max(0, Math.min(trackW, trackW * progressRatio));
    if (fillW > 0) {
      const grad = ctx.createLinearGradient(padX, 0, padX + trackW, 0);
      grad.addColorStop(0, '#6366f1');
      grad.addColorStop(1, '#38bdf8');
      ctx.fillStyle = grad;
      ctx.beginPath();
      if (ctx.roundRect) {
        ctx.roundRect(padX, trackY, fillW, trackH, 6);
      } else {
        ctx.rect(padX, trackY, fillW, trackH);
      }
      ctx.fill();
    }

    // Gaze hover indicator line
    if (hoverRatio !== null) {
      const hx = padX + trackW * THREE.MathUtils.clamp(hoverRatio, 0, 1);
      ctx.fillStyle = 'rgba(255, 255, 255, 0.75)';
      ctx.fillRect(hx - 2, trackY - 6, 4, trackH + 12);
    }

    // Current playback thumb knob
    const kx = padX + fillW;
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.arc(kx, h / 2, 10, 0, Math.PI * 2);
    ctx.fill();

    if (this.seekBarMesh.material.map) {
      this.seekBarMesh.material.map.needsUpdate = true;
    }
  }

  drawTimeReadout(currentSec, durationSec, hoverRatio) {
    if (!this.timeMesh || !this.timeMesh.userData) return;
    const { ctx, logicalW: w, logicalH: h } = this.timeMesh.userData;

    ctx.clearRect(0, 0, w, h);

    ctx.fillStyle = '#181b2c';
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(2, 2, w - 4, h - 4, 12);
    } else {
      ctx.rect(2, 2, w - 4, h - 4);
    }
    ctx.fill();

    const hasDur = Number.isFinite(durationSec) && durationSec > 0;
    let label = `${this.formatTime(currentSec)} / ${hasDur ? this.formatTime(durationSec) : '--:--'}`;
    ctx.fillStyle = '#e2e8f0';

    if (hoverRatio !== null && hasDur) {
      label = `➔ ${this.formatTime(hoverRatio * durationSec)}`;
      ctx.fillStyle = '#7dd3fc';
    }

    ctx.font = 'bold 23px -apple-system, monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, w / 2, h / 2);

    if (this.timeMesh.material.map) {
      this.timeMesh.material.map.needsUpdate = true;
    }
  }

  /**
   * Bug 6 Fix: Reorient menu in Bed Mode so it tilts with the screen onto the ceiling
   */
  setTilt(pitchDegrees) {
    const pitchRad = THREE.MathUtils.degToRad(pitchDegrees);
    // Relative to eye position (0, 1.4, 0), normal menu pos is (0, -0.91, -3.6)
    const relY = -0.91;
    const relZ = -3.6;

    const newY = 1.4 + (relY * Math.cos(pitchRad) - relZ * Math.sin(pitchRad));
    const newZ = relY * Math.sin(pitchRad) + relZ * Math.cos(pitchRad);

    this.menuGroup.position.set(0, newY, newZ);
    this.menuGroup.rotation.x = -0.2 + pitchRad;
  }

  pollGamepad(dtSec) {
    if (!navigator.getGamepads) return;
    const pads = navigator.getGamepads();
    let pad = null;
    for (let i = 0; i < pads.length; i++) {
      if (pads[i] && pads[i].connected) {
        pad = pads[i];
        break;
      }
    }
    if (!pad) return;

    const pressed = (idx) => Boolean(pad.buttons[idx] && pad.buttons[idx].pressed);
    const justPressed = (idx) => pressed(idx) && !this.prevPadButtons[idx];

    if (justPressed(0)) {
      if (!this.triggerInteractiveObject(this.hoveredObject, this.hoverSeekRatio)) {
        this.triggerEmptySpaceAction();
      }
    }
    if (justPressed(1)) this.toggleMenu();
    if (justPressed(2) || justPressed(3) || justPressed(8) || justPressed(9)) this.rig.recenter();
    if (justPressed(14) || justPressed(4)) this.seek(-10);
    if (justPressed(15) || justPressed(5)) this.seek(10);

    for (let b = 0; b < pad.buttons.length; b++) {
      this.prevPadButtons[b] = pressed(b);
    }

    // Optional thumbstick look pan on non-gyro desktops
    if (!this.rig.hasGyro && pad.axes && pad.axes.length >= 2) {
      const ax = Math.abs(pad.axes[0]) > 0.22 ? pad.axes[0] : (pad.axes.length >= 3 && Math.abs(pad.axes[2]) > 0.22 ? pad.axes[2] : 0);
      const ay = Math.abs(pad.axes[1]) > 0.22 ? pad.axes[1] : (pad.axes.length >= 4 && Math.abs(pad.axes[3]) > 0.22 ? pad.axes[3] : 0);
      if (ax !== 0 || ay !== 0) {
        this.rig.desktopEuler.y -= ax * 1.4 * dtSec;
        this.rig.desktopEuler.x = Math.max(-Math.PI / 2.2, Math.min(Math.PI / 2.2, this.rig.desktopEuler.x - ay * 1.1 * dtSec));
      }
    }
  }

  updateGazeDwell(now) {
    if (!this.gazeDwellEnabled || !this.isMenuVisible || !this.hoveredObject || this.hoveredObject.userData?.isSeekBar) {
      this.dwellTarget = null;
      this.dwellConsumed = false;
      this.dwellUniforms.uProgress.value = 0.0;
      this.dwellRing.visible = false;
      return;
    }

    if (this.dwellTarget !== this.hoveredObject) {
      this.dwellTarget = this.hoveredObject;
      this.dwellStart = now;
      this.dwellConsumed = false;
      this.dwellUniforms.uProgress.value = 0.0;
      this.dwellRing.visible = false;
      return;
    }

    if (this.dwellConsumed) {
      this.dwellRing.visible = false;
      return;
    }

    const elapsed = now - this.dwellStart;
    if (elapsed <= this.dwellGraceMs) {
      this.dwellRing.visible = false;
      return;
    }

    const progress = Math.min(1.0, (elapsed - this.dwellGraceMs) / (this.dwellDelayMs - this.dwellGraceMs));
    this.dwellUniforms.uProgress.value = progress;
    this.dwellRing.visible = true;

    if (progress >= 1.0) {
      this.dwellConsumed = true;
      this.dwellRing.visible = false;
      this.triggerInteractiveObject(this.hoveredObject, null);
    }
  }

  updateDynamicLabels(now) {
    if (this.video) {
      if (this.playBtnMesh) {
        const expectedPlay = this.video.paused ? '▶ Play' : '⏸ Pause';
        if (this.playBtnMesh.userData.currentText !== expectedPlay) {
          this.updateButtonText(this.playBtnMesh, expectedPlay);
        }
      }

      if (this.audioBtnMesh) {
        const expectedAudio = this.video.muted ? '🔇 Muted' : '🔊 Sound';
        if (this.audioBtnMesh.userData.currentText !== expectedAudio) {
          this.updateButtonText(this.audioBtnMesh, expectedAudio);
        }
      }
    }

    if (this.formatBtnMesh && this.screen) {
      let fmtLabel = '🕶 2D';
      if (this.screen.format === '3d-hsbs' || this.screen.format === '3d-sbs') fmtLabel = '👓 Half-SBS';
      else if (this.screen.format === '3d-fsbs') fmtLabel = '👓 Full-SBS';
      else if (this.screen.format === '3d-hou' || this.screen.format === '3d-ou') fmtLabel = '👓 Half-OU';
      else if (this.screen.format === '3d-fou') fmtLabel = '👓 Full-OU';
      if (this.formatBtnMesh.userData.currentText !== fmtLabel) {
        this.updateButtonText(this.formatBtnMesh, fmtLabel);
      }
    }

    if (this.ipdBtnMesh) {
      const expectedIpd = `👁 ${Math.round(this.rig.ipd * 1000)}mm`;
      if (this.ipdBtnMesh.userData.currentText !== expectedIpd) {
        this.updateButtonText(this.ipdBtnMesh, expectedIpd);
      }
    }

    if (this.sizeBtnMesh && this.screen) {
      const sizeLabel = SCREEN_PRESETS[this.screen.screenPreset]?.label || '📐 Standard';
      if (this.sizeBtnMesh.userData.currentText !== sizeLabel) {
        this.updateButtonText(this.sizeBtnMesh, sizeLabel);
      }
    }

    if (this.swapBtnMesh && this.screen) {
      const swapLabel = this.screen.swapEyes ? '🔀 R/L' : '🔀 L/R';
      if (this.swapBtnMesh.userData.currentText !== swapLabel) {
        this.updateButtonText(this.swapBtnMesh, swapLabel);
      }
    }

    if (this.ccBtnMesh && this.screen) {
      let ccLabel = '💬 CC';
      if (now < this.ccHintUntil) {
        ccLabel = 'Load .SRT';
      } else if (this.screen.subtitleCues && this.screen.subtitleCues.length > 0) {
        ccLabel = this.screen.subtitlesEnabled ? '💬 CC On' : '💬 CC Off';
      }
      if (this.ccBtnMesh.userData.currentText !== ccLabel) {
        this.updateButtonText(this.ccBtnMesh, ccLabel);
      }
    }

    if (this.lensBtnMesh) {
      const lensLabel = this.rig.distortionEnabled ? '🔍 Lens On' : '🔍 Lens Off';
      if (this.lensBtnMesh.userData.currentText !== lensLabel) {
        this.updateButtonText(this.lensBtnMesh, lensLabel);
      }
    }

    if (this.brightBtnMesh) {
      const brightLabel = `☀ ${Math.round(this.brightness * 100)}%`;
      if (this.brightBtnMesh.userData.currentText !== brightLabel) {
        this.updateButtonText(this.brightBtnMesh, brightLabel);
      }
    }

    if (this.dwellBtnMesh) {
      const dwellLabel = this.gazeDwellEnabled ? '⌛ Dwell 1.2s' : '⌛ Dwell Off';
      if (this.dwellBtnMesh.userData.currentText !== dwellLabel) {
        this.updateButtonText(this.dwellBtnMesh, dwellLabel);
      }
    }

    if (this.fovBtnMesh) {
      const fovLabel = `🔭 FOV ${Math.round(this.rig.fov)}°`;
      if (this.fovBtnMesh.userData.currentText !== fovLabel) {
        this.updateButtonText(this.fovBtnMesh, fovLabel);
      }
    }
  }

  update(now = performance.now()) {
    const dtSec = Math.min(0.1, Math.max(0, (now - this.lastUpdateTime) / 1000));
    this.lastUpdateTime = now;

    // Poll Bluetooth / USB Gamepad inputs
    this.pollGamepad(dtSec);

    // Update active subtitles cue
    if (this.screen && this.screen.updateSubtitles) {
      this.screen.updateSubtitles();
    }

    // Update dynamic button canvas labels
    this.updateDynamicLabels(now);

    // Cast ray from cyclopean head center along forward (-Z) gaze axis
    this.rig.head.updateWorldMatrix(true, false);
    this._rayOrigin.setFromMatrixPosition(this.rig.head.matrixWorld);
    this._rayDir.set(0, 0, -1).transformDirection(this.rig.head.matrixWorld);
    this.raycaster.set(this._rayOrigin, this._rayDir);

    let targetDistance = 6.0;
    this.hoverSeekRatio = null;

    if (this.isMenuVisible) {
      const menuIntersects = this.raycaster.intersectObjects(this.interactiveObjects);
      if (menuIntersects.length > 0) {
        const firstHit = menuIntersects[0];
        const hitObj = firstHit.object;
        targetDistance = Math.max(0.5, firstHit.distance);

        if (hitObj === this.seekBarMesh && firstHit.uv) {
          this.hoverSeekRatio = THREE.MathUtils.clamp(firstHit.uv.x, 0, 1);
        }

        if (this.hoveredObject !== hitObj) {
          if (this.hoveredObject) this.hoveredObject.scale.set(1, 1, 1);
          this.hoveredObject = hitObj;
          if (!this.hoveredObject.userData?.noHoverScale) {
            this.hoveredObject.scale.set(1.08, 1.08, 1.08);
          }
        }
      } else {
        if (this.hoveredObject) {
          this.hoveredObject.scale.set(1, 1, 1);
          this.hoveredObject = null;
        }
      }
    } else {
      if (this.hoveredObject) {
        this.hoveredObject.scale.set(1, 1, 1);
        this.hoveredObject = null;
      }
    }

    // If not hovering over a UI item, test ray against cinema screen meshL
    if (!this.hoveredObject && this.screen && this.screen.meshL) {
      const screenIntersects = this.raycaster.intersectObject(this.screen.meshL);
      if (screenIntersects.length > 0) {
        targetDistance = Math.max(0.5, screenIntersects[0].distance);
      }
    }

    // Update hands-free Gaze Dwell ring & click trigger
    this.updateGazeDwell(now);

    // Redraw seek bar & time badge only when visible values change
    if (this.isMenuVisible && this.video) {
      const dur = Number.isFinite(this.video.duration) && this.video.duration > 0 ? this.video.duration : 0;
      const cur = Number.isFinite(this.video.currentTime) ? this.video.currentTime : 0;
      const ratio = dur > 0 ? cur / dur : 0;

      const progressBucket = Math.round(ratio * 240);
      const hoverBucket = this.hoverSeekRatio !== null ? Math.round(this.hoverSeekRatio * 100) : -1;
      const seekKey = `${progressBucket}:${hoverBucket}`;
      if (seekKey !== this.lastSeekRenderKey) {
        this.lastSeekRenderKey = seekKey;
        this.drawSeekBar(ratio, this.hoverSeekRatio);
      }

      const timeKey = `${Math.floor(cur)}:${Math.floor(dur)}:${hoverBucket}`;
      if (timeKey !== this.lastTimeRenderKey) {
        this.lastTimeRenderKey = timeKey;
        this.drawTimeReadout(cur, dur, this.hoverSeekRatio);
      }
    }

    // Place reticle & dwell ring at hit distance slightly forward to avoid z-fighting and diplopia
    const depthZ = -targetDistance + 0.04;
    this.reticle.position.z = depthZ;
    this.dwellRing.position.z = depthZ + 0.001;

    // Scale reticle proportionally with distance to maintain optimal gaze reticle size
    const baseScale = targetDistance / 1.8;
    const hoverScale = this.hoveredObject && !this.hoveredObject.userData?.noHoverScale ? 1.35 : 1.0;
    const finalScale = baseScale * hoverScale;
    this.reticle.scale.set(finalScale, finalScale, finalScale);
    this.dwellRing.scale.set(finalScale, finalScale, finalScale);
  }
}
