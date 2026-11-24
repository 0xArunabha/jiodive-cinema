import * as THREE from 'three';
import { SCREEN_PRESETS } from './screenMesh.js';

/**
 * In-VR Floating Gaze & Physical Click Theater Remote
 * Two clean modes to prevent clutter while watching:
 * 1. Main Theater Bar: Seek bar, time, CC, prominent Play/Pause, -10s/+10s, Mute, Room, Seat, Recenter, Settings, Exit
 * 2. Settings Sub-Panel: Format, Eye Swap, Bed Mode, Comfort Dimmer, IPD, FOV, Lens Optics, Gaze Dwell
 * All icons are drawn as crisp 2x HiDPI vector paths (zero OS emojis).
 */
export class GazeUI {
  constructor(stereoRig, videoElement, cinemaScreen, environments) {
    this.rig = stereoRig;
    this.video = videoElement;
    this.screen = cinemaScreen;
    this.environments = environments;

    this.mainInteractiveObjects = [];
    this.settingsInteractiveObjects = [];
    this.isSettingsOpen = false;

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

    this.createReticle();
    this.createControlPanel();
    this.initClickListeners();
  }

  createReticle() {
    const geom = new THREE.RingGeometry(0.008, 0.016, 24);
    const mat = new THREE.MeshBasicMaterial({
      color: 0xffffff,
      opacity: 0.88,
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

  roundedRectPath(ctx, x, y, w, h, r) {
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(x, y, w, h, r);
    } else {
      ctx.rect(x, y, w, h);
    }
  }

  createBackdropMesh(isSettingsPanel = false) {
    const bgUi = this.createUiTexture(776, 224);
    const { ctx, logicalW: w, logicalH: h } = bgUi;

    // Deep solid cinema glass prevents stage LED bleed-through (alphaTest clips rounded corners cleanly)
    this.roundedRectPath(ctx, 3, 3, w - 6, h - 6, 22);
    ctx.fillStyle = '#090b16';
    ctx.fill();

    ctx.strokeStyle = 'rgba(148, 163, 184, 0.28)';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Subtle top specular hairline highlight
    const topGlow = ctx.createLinearGradient(80, 0, w - 80, 0);
    topGlow.addColorStop(0, 'rgba(56, 189, 248, 0)');
    topGlow.addColorStop(0.5, 'rgba(147, 197, 253, 0.45)');
    topGlow.addColorStop(1, 'rgba(99, 102, 241, 0)');
    ctx.strokeStyle = topGlow;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(60, 4);
    ctx.lineTo(w - 60, 4);
    ctx.stroke();

    if (isSettingsPanel) {
      // Header bar inside Settings sub-panel
      ctx.fillStyle = '#141930';
      this.roundedRectPath(ctx, 16, 14, w - 32, 46, 12);
      ctx.fill();

      this.drawIcon(ctx, 'settings', 42, 37, 20, '#38bdf8');
      ctx.fillStyle = '#f8fafc';
      ctx.font = 'bold 21px -apple-system, BlinkMacSystemFont, sans-serif';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'middle';
      const titleText = 'Headset & Cinema Settings';
      ctx.fillText(titleText, 64, 37);

      const titleEnd = 64 + ctx.measureText(titleText).width;
      ctx.fillStyle = '#94a3b8';
      ctx.font = '500 14px -apple-system, BlinkMacSystemFont, sans-serif';
      ctx.fillText('Live VR controls', titleEnd + 16, 38);
    }

    const bgGeom = new THREE.PlaneGeometry(3.88, 1.12);
    const bgMat = new THREE.MeshBasicMaterial({
      map: bgUi.texture,
      transparent: false,
      alphaTest: 0.5,
      depthTest: false,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    const mesh = new THREE.Mesh(bgGeom, bgMat);
    mesh.renderOrder = 500;
    return mesh;
  }

  createControlPanel() {
    this.menuGroup = new THREE.Group();
    this.menuGroup.position.set(0, 0.56, -2.95);
    this.menuGroup.rotation.x = -0.22;
    this.menuGroup.scale.set(0.72, 0.72, 0.72);
    this.rig.scene.add(this.menuGroup);

    // 1. Main Theater Remote Group
    this.mainGroup = new THREE.Group();
    this.menuGroup.add(this.mainGroup);
    this.mainGroup.add(this.createBackdropMesh(false));

    // Top Strip (y = 0.34): Interactive Seek Bar + Time + CC Toggle
    this.createSeekBarRow(0.34, this.mainGroup, this.mainInteractiveObjects);

    // Row 1 (y = 0.04): Primary Playback Transport + Audio
    const playbackButtons = [
      {
        icon: 'rewind',
        label: '-10s',
        x: -1.46,
        y: 0.04,
        w: 0.68,
        h: 0.23,
        canvasW: 204,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        action: () => this.seek(-10)
      },
      {
        icon: 'play',
        label: 'Play',
        x: -0.39,
        y: 0.04,
        w: 1.30,
        h: 0.24,
        canvasW: 360,
        canvasH: 80,
        fontSize: 28,
        variant: 'primary',
        ref: 'playBtnMesh',
        action: () => this.togglePlay()
      },
      {
        icon: 'forward',
        label: '+10s',
        x: 0.68,
        y: 0.04,
        w: 0.68,
        h: 0.23,
        canvasW: 204,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        action: () => this.seek(10)
      },
      {
        icon: 'sound',
        label: 'Audio',
        x: 1.46,
        y: 0.04,
        w: 0.68,
        h: 0.23,
        canvasW: 204,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        ref: 'audioBtnMesh',
        action: () => this.toggleAudio()
      }
    ];

    playbackButtons.forEach((b) => this.addButtonToGroup(b, this.mainGroup, this.mainInteractiveObjects));

    // Row 2 (y = -0.32): Theater, Screen Size, Recenter, Settings, Exit
    const theaterButtons = [
      {
        icon: 'room',
        label: 'Cinema',
        x: -1.472,
        y: -0.32,
        w: 0.66,
        h: 0.20,
        canvasW: 200,
        canvasH: 68,
        fontSize: 23,
        variant: 'subtle',
        ref: 'roomBtnMesh',
        action: () => this.cycleEnvironment()
      },
      {
        icon: 'seat',
        label: SCREEN_PRESETS[this.screen?.screenPreset]?.label || 'Standard',
        x: -0.736,
        y: -0.32,
        w: 0.66,
        h: 0.20,
        canvasW: 200,
        canvasH: 68,
        fontSize: 23,
        variant: 'subtle',
        ref: 'sizeBtnMesh',
        action: () => this.cycleScreenPreset()
      },
      {
        icon: 'recenter',
        label: 'Recenter',
        x: 0,
        y: -0.32,
        w: 0.66,
        h: 0.20,
        canvasW: 200,
        canvasH: 68,
        fontSize: 23,
        variant: 'subtle',
        action: () => this.rig.recenter()
      },
      {
        icon: 'settings',
        label: 'Settings',
        x: 0.736,
        y: -0.32,
        w: 0.66,
        h: 0.20,
        canvasW: 200,
        canvasH: 68,
        fontSize: 23,
        variant: 'accent',
        action: () => this.setSettingsOpen(true)
      },
      {
        icon: 'exit',
        label: 'Exit',
        x: 1.472,
        y: -0.32,
        w: 0.66,
        h: 0.20,
        canvasW: 200,
        canvasH: 68,
        fontSize: 23,
        variant: 'danger',
        action: () => this.exitVR()
      }
    ];

    theaterButtons.forEach((b) => this.addButtonToGroup(b, this.mainGroup, this.mainInteractiveObjects));

    // 2. Settings Sub-Panel Group (hidden until Gear is clicked)
    this.settingsGroup = new THREE.Group();
    this.settingsGroup.visible = false;
    this.menuGroup.add(this.settingsGroup);
    this.settingsGroup.add(this.createBackdropMesh(true));

    // Settings Header Right Action: < Back
    this.addButtonToGroup(
      {
        icon: 'back',
        label: 'Back',
        x: 1.46,
        y: 0.35,
        w: 0.68,
        h: 0.16,
        canvasW: 204,
        canvasH: 56,
        fontSize: 23,
        variant: 'accent',
        action: () => this.setSettingsOpen(false)
      },
      this.settingsGroup,
      this.settingsInteractiveObjects
    );

    // Settings Row 1 (y = 0.03): 3D Format, Eye Swap, Bed Mode, Dimmer
    const settingsRow1 = [
      {
        icon: 'format3d',
        label: '2D Mode',
        x: -1.38,
        y: 0.03,
        w: 0.84,
        h: 0.22,
        canvasW: 252,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        ref: 'formatBtnMesh',
        action: () => this.cycleFormat()
      },
      {
        icon: 'swap',
        label: 'Eyes L/R',
        x: -0.46,
        y: 0.03,
        w: 0.84,
        h: 0.22,
        canvasW: 252,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        ref: 'swapBtnMesh',
        action: () => this.toggleSwapEyes()
      },
      {
        icon: 'bed',
        label: 'Bed Off',
        x: 0.46,
        y: 0.03,
        w: 0.84,
        h: 0.22,
        canvasW: 252,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        ref: 'bedBtnMesh',
        action: () => this.toggleBedMode()
      },
      {
        icon: 'brightness',
        label: '100% Bright',
        x: 1.38,
        y: 0.03,
        w: 0.84,
        h: 0.22,
        canvasW: 252,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        ref: 'brightBtnMesh',
        action: () => this.cycleBrightness()
      }
    ];

    settingsRow1.forEach((b) => this.addButtonToGroup(b, this.settingsGroup, this.settingsInteractiveObjects));

    // Settings Row 2 (y = -0.31): IPD, FOV, Lens Correction, Gaze Dwell
    const ipdMm = Math.round(this.rig.ipd * 1000);
    const settingsRow2 = [
      {
        icon: 'ipd',
        label: `IPD ${ipdMm}mm`,
        x: -1.38,
        y: -0.31,
        w: 0.84,
        h: 0.22,
        canvasW: 252,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        ref: 'ipdBtnMesh',
        action: () => this.cycleIPD()
      },
      {
        icon: 'fov',
        label: `FOV ${Math.round(this.rig.fov)}°`,
        x: -0.46,
        y: -0.31,
        w: 0.84,
        h: 0.22,
        canvasW: 252,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        ref: 'fovBtnMesh',
        action: () => this.cycleFOV()
      },
      {
        icon: 'lens',
        label: 'Lens On',
        x: 0.46,
        y: -0.31,
        w: 0.84,
        h: 0.22,
        canvasW: 252,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        active: true,
        ref: 'lensBtnMesh',
        action: () => this.toggleDistortion()
      },
      {
        icon: 'dwell',
        label: 'Dwell Off',
        x: 1.38,
        y: -0.31,
        w: 0.84,
        h: 0.22,
        canvasW: 252,
        canvasH: 74,
        fontSize: 24,
        variant: 'glass',
        ref: 'dwellBtnMesh',
        action: () => this.toggleGazeDwell()
      }
    ];

    settingsRow2.forEach((b) => this.addButtonToGroup(b, this.settingsGroup, this.settingsInteractiveObjects));
  }

  addButtonToGroup(cfg, targetGroup, rayList) {
    const btnMesh = this.createButtonMesh(cfg);
    targetGroup.add(btnMesh);
    rayList.push(btnMesh);
    if (cfg.ref) {
      this[cfg.ref] = btnMesh;
    }
    return btnMesh;
  }

  createSeekBarRow(rowY, targetGroup, rayList) {
    const seekUi = this.createUiTexture(540, 52);
    const seekGeom = new THREE.PlaneGeometry(2.38, 0.14);
    const seekMat = new THREE.MeshBasicMaterial({
      map: seekUi.texture,
      transparent: true,
      depthTest: false,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    this.seekBarMesh = new THREE.Mesh(seekGeom, seekMat);
    this.seekBarMesh.position.set(-0.61, rowY, 0.02);
    this.seekBarMesh.renderOrder = 501;
    this.seekBarMesh.userData = {
      isSeekBar: true,
      noHoverScale: true,
      ...seekUi
    };
    targetGroup.add(this.seekBarMesh);
    rayList.push(this.seekBarMesh);

    const timeUi = this.createUiTexture(240, 52);
    const timeGeom = new THREE.PlaneGeometry(0.72, 0.14);
    const timeMat = new THREE.MeshBasicMaterial({
      map: timeUi.texture,
      transparent: true,
      depthTest: false,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    this.timeMesh = new THREE.Mesh(timeGeom, timeMat);
    this.timeMesh.position.set(1.01, rowY, 0.02);
    this.timeMesh.renderOrder = 501;
    this.timeMesh.userData = { ...timeUi };
    targetGroup.add(this.timeMesh);

    this.ccBtnMesh = this.addButtonToGroup(
      {
        icon: 'cc',
        label: 'CC',
        x: 1.61,
        y: rowY,
        w: 0.38,
        h: 0.14,
        canvasW: 152,
        canvasH: 52,
        fontSize: 21,
        variant: 'subtle',
        action: () => this.toggleSubtitles()
      },
      targetGroup,
      rayList
    );

    this.drawSeekBar(0, null);
    this.drawTimeReadout(0, 0, null);
  }

  createButtonMesh(cfg) {
    const width = cfg.w ?? 0.66;
    const height = cfg.h ?? 0.21;
    const canvasW = cfg.canvasW ?? 220;
    const canvasH = cfg.canvasH ?? 80;
    const fontSize = cfg.fontSize ?? 22;

    const ui = this.createUiTexture(canvasW, canvasH);
    const geom = new THREE.PlaneGeometry(width, height);
    const mat = new THREE.MeshBasicMaterial({
      map: ui.texture,
      transparent: true,
      depthTest: false,
      depthWrite: false,
      side: THREE.DoubleSide
    });

    const mesh = new THREE.Mesh(geom, mat);
    mesh.position.set(cfg.x, cfg.y, 0.02);
    mesh.renderOrder = 501;
    mesh.userData = {
      onClick: cfg.action,
      originalScale: new THREE.Vector3(1, 1, 1),
      ...ui,
      canvasW,
      canvasH,
      fontSize,
      icon: cfg.icon || null,
      label: cfg.label || '',
      variant: cfg.variant || 'glass',
      active: Boolean(cfg.active),
      hovered: false,
      renderKey: ''
    };

    this.drawButton(mesh);
    return mesh;
  }

  drawIcon(ctx, icon, cx, cy, s, color) {
    if (!icon) return;
    const r = s * 0.48;
    ctx.save();
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = Math.max(2.0, s * 0.135);
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    switch (icon) {
      case 'play': {
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.55, cy - r * 0.78);
        ctx.lineTo(cx + r * 0.85, cy);
        ctx.lineTo(cx - r * 0.55, cy + r * 0.78);
        ctx.closePath();
        ctx.fill();
        break;
      }
      case 'pause': {
        const bw = r * 0.34;
        const bh = r * 1.4;
        this.roundedRectPath(ctx, cx - r * 0.52, cy - bh / 2, bw, bh, 2);
        ctx.fill();
        this.roundedRectPath(ctx, cx + r * 0.18, cy - bh / 2, bw, bh, 2);
        ctx.fill();
        break;
      }
      case 'rewind': {
        for (const offset of [-r * 0.32, r * 0.42]) {
          ctx.beginPath();
          ctx.moveTo(cx + offset + r * 0.18, cy - r * 0.6);
          ctx.lineTo(cx + offset - r * 0.48, cy);
          ctx.lineTo(cx + offset + r * 0.18, cy + r * 0.6);
          ctx.closePath();
          ctx.fill();
        }
        break;
      }
      case 'forward': {
        for (const offset of [-r * 0.42, r * 0.32]) {
          ctx.beginPath();
          ctx.moveTo(cx + offset - r * 0.18, cy - r * 0.6);
          ctx.lineTo(cx + offset + r * 0.48, cy);
          ctx.lineTo(cx + offset - r * 0.18, cy + r * 0.6);
          ctx.closePath();
          ctx.fill();
        }
        break;
      }
      case 'sound':
      case 'muted': {
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.75, cy - r * 0.32);
        ctx.lineTo(cx - r * 0.35, cy - r * 0.32);
        ctx.lineTo(cx + r * 0.15, cy - r * 0.72);
        ctx.lineTo(cx + r * 0.15, cy + r * 0.72);
        ctx.lineTo(cx - r * 0.35, cy + r * 0.32);
        ctx.lineTo(cx - r * 0.75, cy + r * 0.32);
        ctx.closePath();
        ctx.fill();

        if (icon === 'sound') {
          ctx.beginPath();
          ctx.arc(cx + r * 0.25, cy, r * 0.36, -0.75, 0.75);
          ctx.stroke();
          ctx.beginPath();
          ctx.arc(cx + r * 0.25, cy, r * 0.66, -0.75, 0.75);
          ctx.stroke();
        } else {
          ctx.beginPath();
          ctx.moveTo(cx + r * 0.42, cy - r * 0.34);
          ctx.lineTo(cx + r * 0.92, cy + r * 0.34);
          ctx.moveTo(cx + r * 0.92, cy - r * 0.34);
          ctx.lineTo(cx + r * 0.42, cy + r * 0.34);
          ctx.stroke();
        }
        break;
      }
      case 'room': {
        this.roundedRectPath(ctx, cx - r * 0.88, cy - r * 0.62, r * 1.76, r * 1.1, 3);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.45, cy + r * 0.8);
        ctx.lineTo(cx + r * 0.45, cy + r * 0.8);
        ctx.stroke();
        break;
      }
      case 'seat': {
        // Curved theater screen arc
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.88, cy - r * 0.45);
        ctx.quadraticCurveTo(cx, cy - r * 0.18, cx + r * 0.88, cy - r * 0.45);
        ctx.lineTo(cx + r * 0.88, cy + r * 0.45);
        ctx.quadraticCurveTo(cx, cy + r * 0.18, cx - r * 0.88, cy + r * 0.45);
        ctx.closePath();
        ctx.stroke();
        break;
      }
      case 'recenter': {
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.62, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.18, 0, Math.PI * 2);
        ctx.fill();
        for (const [dx, dy] of [[0, -1], [0, 1], [-1, 0], [1, 0]]) {
          ctx.beginPath();
          ctx.moveTo(cx + dx * r * 0.48, cy + dy * r * 0.48);
          ctx.lineTo(cx + dx * r * 0.9, cy + dy * r * 0.9);
          ctx.stroke();
        }
        break;
      }
      case 'settings': {
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.3, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.64, 0, Math.PI * 2);
        ctx.stroke();
        for (let i = 0; i < 6; i++) {
          const a = (i * Math.PI) / 3;
          ctx.beginPath();
          ctx.moveTo(cx + Math.cos(a) * r * 0.58, cy + Math.sin(a) * r * 0.58);
          ctx.lineTo(cx + Math.cos(a) * r * 0.88, cy + Math.sin(a) * r * 0.88);
          ctx.stroke();
        }
        break;
      }
      case 'exit': {
        ctx.beginPath();
        ctx.moveTo(cx + r * 0.1, cy - r * 0.72);
        ctx.lineTo(cx - r * 0.72, cy - r * 0.72);
        ctx.lineTo(cx - r * 0.72, cy + r * 0.72);
        ctx.lineTo(cx + r * 0.1, cy + r * 0.72);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.18, cy);
        ctx.lineTo(cx + r * 0.82, cy);
        ctx.moveTo(cx + r * 0.46, cy - r * 0.36);
        ctx.lineTo(cx + r * 0.84, cy);
        ctx.lineTo(cx + r * 0.46, cy + r * 0.36);
        ctx.stroke();
        break;
      }
      case 'cc': {
        this.roundedRectPath(ctx, cx - r * 0.88, cy - r * 0.62, r * 1.76, r * 1.24, 3);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.45, cy + r * 0.18);
        ctx.lineTo(cx + r * 0.08, cy + r * 0.18);
        ctx.moveTo(cx + r * 0.24, cy + r * 0.18);
        ctx.lineTo(cx + r * 0.5, cy + r * 0.18);
        ctx.stroke();
        break;
      }
      case 'back': {
        ctx.beginPath();
        ctx.moveTo(cx + r * 0.25, cy - r * 0.6);
        ctx.lineTo(cx - r * 0.45, cy);
        ctx.lineTo(cx + r * 0.25, cy + r * 0.6);
        ctx.stroke();
        break;
      }
      case 'format3d': {
        this.roundedRectPath(ctx, cx - r * 0.92, cy - r * 0.55, r * 1.84, r * 1.1, 4);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx - r * 0.36, cy, r * 0.22, 0, Math.PI * 2);
        ctx.arc(cx + r * 0.36, cy, r * 0.22, 0, Math.PI * 2);
        ctx.stroke();
        break;
      }
      case 'swap': {
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.72, cy - r * 0.32);
        ctx.lineTo(cx + r * 0.72, cy - r * 0.32);
        ctx.lineTo(cx + r * 0.38, cy - r * 0.62);
        ctx.moveTo(cx + r * 0.72, cy + r * 0.32);
        ctx.lineTo(cx - r * 0.72, cy + r * 0.32);
        ctx.lineTo(cx - r * 0.38, cy + r * 0.62);
        ctx.stroke();
        break;
      }
      case 'bed': {
        // Reclined screen angle icon
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.8, cy + r * 0.52);
        ctx.lineTo(cx + r * 0.8, cy + r * 0.52);
        ctx.moveTo(cx - r * 0.65, cy - r * 0.15);
        ctx.lineTo(cx + r * 0.62, cy - r * 0.62);
        ctx.stroke();
        break;
      }
      case 'brightness': {
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.38, 0, Math.PI * 2);
        ctx.stroke();
        for (let i = 0; i < 8; i++) {
          const a = (i * Math.PI) / 4;
          ctx.beginPath();
          ctx.moveTo(cx + Math.cos(a) * r * 0.6, cy + Math.sin(a) * r * 0.6);
          ctx.lineTo(cx + Math.cos(a) * r * 0.88, cy + Math.sin(a) * r * 0.88);
          ctx.stroke();
        }
        break;
      }
      case 'ipd': {
        ctx.beginPath();
        ctx.arc(cx - r * 0.52, cy - r * 0.15, r * 0.28, 0, Math.PI * 2);
        ctx.arc(cx + r * 0.52, cy - r * 0.15, r * 0.28, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.52, cy + r * 0.56);
        ctx.lineTo(cx + r * 0.52, cy + r * 0.56);
        ctx.stroke();
        break;
      }
      case 'fov': {
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.65, cy);
        ctx.lineTo(cx + r * 0.65, cy - r * 0.68);
        ctx.moveTo(cx - r * 0.65, cy);
        ctx.lineTo(cx + r * 0.65, cy + r * 0.68);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx - r * 0.65, cy, r * 0.82, -0.7, 0.7);
        ctx.stroke();
        break;
      }
      case 'lens': {
        ctx.beginPath();
        ctx.ellipse(cx, cy, r * 0.45, r * 0.82, 0, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(cx - r * 0.85, cy);
        ctx.lineTo(cx + r * 0.85, cy);
        ctx.stroke();
        break;
      }
      case 'dwell': {
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.72, -Math.PI / 2, Math.PI * 0.9);
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.2, 0, Math.PI * 2);
        ctx.fill();
        break;
      }
    }

    ctx.restore();
  }

  updateButtonState(mesh, { icon, label, active, hovered, variant } = {}) {
    if (!mesh || !mesh.userData) return;
    const ud = mesh.userData;
    if (icon !== undefined) ud.icon = icon;
    if (label !== undefined) ud.label = label;
    if (active !== undefined) ud.active = Boolean(active);
    if (hovered !== undefined) ud.hovered = Boolean(hovered);
    if (variant !== undefined) ud.variant = variant;
    this.drawButton(mesh);
  }

  drawButton(mesh) {
    if (!mesh || !mesh.userData) return;
    const ud = mesh.userData;
    const {
      ctx,
      canvasW = 220,
      canvasH = 80,
      fontSize = 22,
      icon,
      label,
      variant = 'glass',
      active = false,
      hovered = false
    } = ud;

    const nextKey = `${icon}|${label}|${variant}|${active}|${hovered}`;
    if (ud.renderKey === nextKey) return;
    ud.renderKey = nextKey;

    ctx.clearRect(0, 0, canvasW, canvasH);

    const pad = 4;
    const rw = canvasW - pad * 2;
    const rh = canvasH - pad * 2;
    const radius = Math.min(16, rh * 0.26);

    // Choose palette based on semantic hierarchy (CTA > Accent/Active > Glass > Subtle > Danger)
    let fill = '#161a30';
    let stroke = 'rgba(148, 163, 184, 0.28)';
    let strokeWidth = 2;
    let textColor = '#f1f5f9';
    let iconColor = '#cbd5e1';

    if (variant === 'primary') {
      const grad = ctx.createLinearGradient(pad, pad, canvasW - pad, canvasH - pad);
      grad.addColorStop(0, hovered ? '#3b82f6' : '#2563eb');
      grad.addColorStop(1, hovered ? '#6366f1' : '#4f46e5');
      fill = grad;
      stroke = hovered ? '#bae6fd' : 'rgba(147, 197, 253, 0.55)';
      strokeWidth = 2.5;
      textColor = '#ffffff';
      iconColor = '#ffffff';
    } else if (variant === 'danger') {
      fill = hovered ? '#2d1824' : '#1b111a';
      stroke = hovered ? '#f87171' : 'rgba(248, 113, 113, 0.36)';
      textColor = '#fecaca';
      iconColor = '#fca5a5';
    } else if (active || variant === 'accent') {
      fill = hovered ? '#183754' : '#10273e';
      stroke = hovered ? '#7dd3fc' : 'rgba(56, 189, 248, 0.68)';
      strokeWidth = 2.2;
      textColor = '#f0f9ff';
      iconColor = '#38bdf8';
    } else if (variant === 'subtle') {
      fill = hovered ? '#1c223f' : '#111528';
      stroke = hovered ? '#38bdf8' : 'rgba(148, 163, 184, 0.22)';
      textColor = hovered ? '#ffffff' : '#cbd5e1';
      iconColor = hovered ? '#38bdf8' : '#94a3b8';
    } else {
      // 'glass'
      fill = hovered ? '#202646' : '#161b33';
      stroke = hovered ? '#38bdf8' : 'rgba(148, 163, 184, 0.3)';
      textColor = '#f8fafc';
      iconColor = hovered ? '#7dd3fc' : '#cbd5e1';
    }

    this.roundedRectPath(ctx, pad, pad, rw, rh, radius);
    ctx.fillStyle = fill;
    ctx.fill();
    ctx.strokeStyle = stroke;
    ctx.lineWidth = strokeWidth;
    ctx.stroke();

    // Active status dot for toggled settings
    if (active && variant !== 'primary') {
      ctx.fillStyle = '#38bdf8';
      ctx.beginPath();
      ctx.arc(canvasW - 16, 16, 4, 0, Math.PI * 2);
      ctx.fill();
    }

    // Center [vector icon + gap + text] horizontally with bold legibility
    ctx.font = `700 ${fontSize}px -apple-system, BlinkMacSystemFont, sans-serif`;
    ctx.textBaseline = 'middle';

    const textW = label ? ctx.measureText(label).width : 0;
    const iconSize = icon ? Math.round(fontSize * 0.94) : 0;
    const gap = icon && label ? 8 : 0;
    const totalW = iconSize + gap + textW;
    const startX = (canvasW - totalW) / 2;
    const cy = canvasH / 2;

    if (icon) {
      this.drawIcon(ctx, icon, startX + iconSize / 2, cy, iconSize, iconColor);
    }

    if (label) {
      ctx.fillStyle = textColor;
      ctx.textAlign = 'left';
      ctx.fillText(label, startX + iconSize + gap, cy + 1);
    }

    if (mesh.material.map) {
      mesh.material.map.needsUpdate = true;
    }
  }

  setHoveredObject(hitObj) {
    if (this.hoveredObject === hitObj) return;

    if (this.hoveredObject) {
      this.hoveredObject.scale.set(1, 1, 1);
      if (!this.hoveredObject.userData?.isSeekBar) {
        this.updateButtonState(this.hoveredObject, { hovered: false });
      }
    }

    this.hoveredObject = hitObj;

    if (this.hoveredObject) {
      if (!this.hoveredObject.userData?.noHoverScale) {
        this.hoveredObject.scale.set(1.06, 1.06, 1.06);
      }
      if (!this.hoveredObject.userData?.isSeekBar) {
        this.updateButtonState(this.hoveredObject, { hovered: true });
      }
    }
  }

  setSettingsOpen(open) {
    this.isSettingsOpen = Boolean(open);
    this.setHoveredObject(null);
    this.dwellTarget = null;
    this.dwellConsumed = false;
    this.hoverSeekRatio = null;
    if (this.dwellRing) this.dwellRing.visible = false;

    if (this.mainGroup) this.mainGroup.visible = !this.isSettingsOpen;
    if (this.settingsGroup) this.settingsGroup.visible = this.isSettingsOpen;
    this.updateDynamicLabels(performance.now());
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
    const handleTrigger = (e) => {
      if (!this.rig.isVrActive) return;
      if (e?.target?.closest?.('#rotate-hint, dialog, #launcher')) return;

      const now = performance.now();
      // Double click detection (<350ms) -> Instant Recenter!
      if (now - this.lastClickTime < 350) {
        if (this.clickTimeout) {
          clearTimeout(this.clickTimeout);
          this.clickTimeout = null;
        }
        this.lastClickTime = 0;
        this.rig.recenter();
        return;
      }
      this.lastClickTime = now;

      const targetObj = this.hoveredObject;
      const targetSeekRatio = this.hoverSeekRatio;

      this.clickTimeout = setTimeout(() => {
        this.clickTimeout = null;
        if (!this.rig.isVrActive) return;
        if (!this.triggerInteractiveObject(targetObj, targetSeekRatio)) {
          this.triggerEmptySpaceAction();
        }
      }, 280);
    };

    window.addEventListener('pointerdown', handleTrigger);

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
    mesh.scale.set(1.13, 1.13, 1.13);
    setTimeout(() => {
      if (this.hoveredObject === mesh && !mesh.userData?.noHoverScale) {
        mesh.scale.set(1.06, 1.06, 1.06);
      } else {
        mesh.scale.set(1, 1, 1);
      }
    }, 140);
  }

  emitPrefSync(key, value) {
    window.dispatchEvent(new CustomEvent('jiodive:sync', { detail: { key, value: String(value) } }));
  }

  toggleMenu() {
    this.isMenuVisible = !this.isMenuVisible;
    this.menuGroup.visible = this.isMenuVisible;
    if (!this.isMenuVisible) {
      this.setHoveredObject(null);
    }
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
    const next = this.environments.currentEnv === 'cinema' ? 'void' : 'cinema';
    this.environments.setEnvironment(next, this.screen, this);
    this.emitPrefSync('env', next);
  }

  toggleBedMode() {
    const target = this.environments.currentEnv === 'bed' ? 'cinema' : 'bed';
    this.environments.setEnvironment(target, this.screen, this);
    this.emitPrefSync('env', target);
  }

  cycleFormat() {
    const formats = ['2d', '3d-hsbs', '3d-fsbs', '3d-hou', '3d-fou'];
    const next = formats[(formats.indexOf(this.screen.format) + 1) % formats.length];
    this.screen.setFormat(next);
    this.emitPrefSync('mode', next);
  }

  cycleScreenPreset() {
    const presets = ['imax', 'standard', 'balcony'];
    const next = presets[(presets.indexOf(this.screen.screenPreset) + 1) % presets.length];
    this.screen.setScreenPreset(next);
    this.emitPrefSync('screenSize', next);
  }

  toggleSwapEyes() {
    if (!this.screen) return;
    const swapped = this.screen.toggleSwapEyes();
    this.emitPrefSync('swapEyes', swapped ? 'swapped' : 'normal');
  }

  cycleIPD() {
    const ipdValues = [0.058, 0.062, 0.064, 0.068, 0.072];
    const currentIndex = ipdValues.findIndex((v) => Math.abs(v - this.rig.ipd) < 0.001);
    const newIPD = ipdValues[(currentIndex + 1) % ipdValues.length];
    this.rig.setIPD(newIPD);
    this.emitPrefSync('ipd', newIPD);
  }

  toggleDistortion() {
    const next = !this.rig.distortionEnabled;
    this.rig.setDistortion(next);
    this.emitPrefSync('distortion', next ? 'jiodive' : 'off');
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
    const currentIndex = levels.findIndex((v) => Math.abs(v - this.brightness) < 0.05);
    const next = levels[(currentIndex + 1) % levels.length];
    this.setBrightness(next);
    this.emitPrefSync('brightness', next);
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
    this.emitPrefSync('dwell', next ? '1200' : 'off');
  }

  cycleFOV() {
    const fovs = [70, 75, 80, 85];
    const currentIndex = fovs.findIndex((v) => Math.abs(v - this.rig.fov) < 1);
    const next = fovs[(currentIndex + 1) % fovs.length];
    this.rig.setFOV(next);
    this.emitPrefSync('fov', next);
  }

  exitVR() {
    this.rig.isVrActive = false;
    this.setSettingsOpen(false);
    this.setBrightness(this.brightness); // Resets dimmer opacity to 0 on launcher
    if (this.video) {
      this.video.pause();
    }
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    }
    if (window.screen?.orientation?.unlock) {
      try {
        window.screen.orientation.unlock();
      } catch {
        // Ignore on browsers where orientation unlock is unsupported
      }
    }
    const rotateHint = document.getElementById('rotate-hint');
    if (rotateHint) {
      rotateHint.classList.add('hidden');
    }
    const launcher = document.getElementById('launcher');
    if (launcher) {
      launcher.inert = false;
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
    const padX = 16;
    const trackW = w - padX * 2;
    const trackH = 10;
    const trackY = (h - trackH) / 2;

    ctx.clearRect(0, 0, w, h);

    this.roundedRectPath(ctx, 2, 2, w - 4, h - 4, 14);
    ctx.fillStyle = hoverRatio !== null ? '#1e2542' : '#121528';
    ctx.fill();
    ctx.strokeStyle = hoverRatio !== null ? '#38bdf8' : 'rgba(148, 163, 184, 0.22)';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Base track
    this.roundedRectPath(ctx, padX, trackY, trackW, trackH, 5);
    ctx.fillStyle = '#27324b';
    ctx.fill();

    // Filled progress
    const fillW = Math.max(0, Math.min(trackW, trackW * progressRatio));
    if (fillW > 0) {
      const grad = ctx.createLinearGradient(padX, 0, padX + trackW, 0);
      grad.addColorStop(0, '#38bdf8');
      grad.addColorStop(1, '#6366f1');
      this.roundedRectPath(ctx, padX, trackY, fillW, trackH, 5);
      ctx.fillStyle = grad;
      ctx.fill();
    }

    // Gaze hover indicator line
    if (hoverRatio !== null) {
      const hx = padX + trackW * THREE.MathUtils.clamp(hoverRatio, 0, 1);
      ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
      ctx.fillRect(hx - 1.5, trackY - 6, 3, trackH + 12);
    }

    // Current playback thumb knob
    const kx = padX + fillW;
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.arc(kx, h / 2, 8.5, 0, Math.PI * 2);
    ctx.fill();

    if (this.seekBarMesh.material.map) {
      this.seekBarMesh.material.map.needsUpdate = true;
    }
  }

  drawTimeReadout(currentSec, durationSec, hoverRatio) {
    if (!this.timeMesh || !this.timeMesh.userData) return;
    const { ctx, logicalW: w, logicalH: h } = this.timeMesh.userData;

    ctx.clearRect(0, 0, w, h);

    this.roundedRectPath(ctx, 2, 2, w - 4, h - 4, 12);
    ctx.fillStyle = '#121528';
    ctx.fill();
    ctx.strokeStyle = 'rgba(148, 163, 184, 0.2)';
    ctx.lineWidth = 2;
    ctx.stroke();

    const hasDur = Number.isFinite(durationSec) && durationSec > 0;
    let label = `${this.formatTime(currentSec)} / ${hasDur ? this.formatTime(durationSec) : '--:--'}`;
    ctx.fillStyle = '#e2e8f0';

    if (hoverRatio !== null && hasDur) {
      label = `Seek ${this.formatTime(hoverRatio * durationSec)}`;
      ctx.fillStyle = '#38bdf8';
    }

    ctx.font = '700 22px -apple-system, BlinkMacSystemFont, monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, w / 2, h / 2 + 1);

    if (this.timeMesh.material.map) {
      this.timeMesh.material.map.needsUpdate = true;
    }
  }

  /**
   * Reorient menu in Bed Mode so it tilts onto the ceiling alongside the screen
   */
  setTilt(pitchDegrees) {
    const pitchRad = THREE.MathUtils.degToRad(pitchDegrees);
    const relY = -0.84;
    const relZ = -2.95;

    const newY = 1.4 + (relY * Math.cos(pitchRad) - relZ * Math.sin(pitchRad));
    const newZ = relY * Math.sin(pitchRad) + relZ * Math.cos(pitchRad);

    this.menuGroup.position.set(0, newY, newZ);
    this.menuGroup.rotation.x = -0.22 + pitchRad;
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
    if (!this.isSettingsOpen) {
      if (this.video) {
        if (this.playBtnMesh) {
          this.updateButtonState(this.playBtnMesh, {
            icon: this.video.paused ? 'play' : 'pause',
            label: this.video.paused ? 'Play' : 'Pause'
          });
        }
        if (this.audioBtnMesh) {
          this.updateButtonState(this.audioBtnMesh, {
            icon: this.video.muted ? 'muted' : 'sound',
            label: this.video.muted ? 'Muted' : 'Audio',
            active: this.video.muted
          });
        }
      }

      if (this.roomBtnMesh && this.environments) {
        const env = this.environments.currentEnv;
        const roomLabel = env === 'void' ? 'Void' : (env === 'bed' ? 'Bed' : 'Cinema');
        this.updateButtonState(this.roomBtnMesh, {
          label: roomLabel,
          active: env !== 'cinema'
        });
      }

      if (this.sizeBtnMesh && this.screen) {
        const sizeLabel = SCREEN_PRESETS[this.screen.screenPreset]?.label || 'Standard';
        this.updateButtonState(this.sizeBtnMesh, { label: sizeLabel });
      }

      if (this.ccBtnMesh && this.screen) {
        let ccLabel = 'CC';
        let ccActive = false;
        if (now < this.ccHintUntil) {
          ccLabel = 'No SRT';
        } else if (this.screen.subtitleCues && this.screen.subtitleCues.length > 0) {
          ccActive = Boolean(this.screen.subtitlesEnabled);
          ccLabel = ccActive ? 'CC On' : 'CC Off';
        }
        this.updateButtonState(this.ccBtnMesh, { label: ccLabel, active: ccActive });
      }
      return;
    }

    // Settings sub-panel labels
    if (this.formatBtnMesh && this.screen) {
      let fmtLabel = '2D Mode';
      if (this.screen.format === '3d-hsbs' || this.screen.format === '3d-sbs') fmtLabel = 'Half-SBS';
      else if (this.screen.format === '3d-fsbs') fmtLabel = 'Full-SBS';
      else if (this.screen.format === '3d-hou' || this.screen.format === '3d-ou') fmtLabel = 'Half-OU';
      else if (this.screen.format === '3d-fou') fmtLabel = 'Full-OU';
      this.updateButtonState(this.formatBtnMesh, {
        label: fmtLabel,
        active: this.screen.format !== '2d'
      });
    }

    if (this.swapBtnMesh && this.screen) {
      this.updateButtonState(this.swapBtnMesh, {
        label: this.screen.swapEyes ? 'Eyes R/L' : 'Eyes L/R',
        active: Boolean(this.screen.swapEyes)
      });
    }

    if (this.bedBtnMesh && this.environments) {
      const isBed = this.environments.currentEnv === 'bed';
      this.updateButtonState(this.bedBtnMesh, {
        label: isBed ? 'Bed On' : 'Bed Off',
        active: isBed
      });
    }

    if (this.brightBtnMesh) {
      const pct = Math.round(this.brightness * 100);
      this.updateButtonState(this.brightBtnMesh, {
        label: pct === 100 ? '100% Bright' : `${pct}% Dim`,
        active: pct < 100
      });
    }

    if (this.ipdBtnMesh) {
      this.updateButtonState(this.ipdBtnMesh, {
        label: `IPD ${Math.round(this.rig.ipd * 1000)}mm`,
        active: Math.abs(this.rig.ipd - 0.062) > 0.001
      });
    }

    if (this.fovBtnMesh) {
      this.updateButtonState(this.fovBtnMesh, {
        label: `FOV ${Math.round(this.rig.fov)}°`,
        active: Math.round(this.rig.fov) !== 75
      });
    }

    if (this.lensBtnMesh) {
      this.updateButtonState(this.lensBtnMesh, {
        label: this.rig.distortionEnabled ? 'Lens On' : 'Lens Off',
        active: Boolean(this.rig.distortionEnabled)
      });
    }

    if (this.dwellBtnMesh) {
      this.updateButtonState(this.dwellBtnMesh, {
        label: this.gazeDwellEnabled ? 'Dwell 1.2s' : 'Dwell Off',
        active: Boolean(this.gazeDwellEnabled)
      });
    }
  }

  update(now = performance.now()) {
    const dtSec = Math.min(0.1, Math.max(0, (now - this.lastUpdateTime) / 1000));
    this.lastUpdateTime = now;

    this.pollGamepad(dtSec);

    if (this.screen && this.screen.updateSubtitles) {
      this.screen.updateSubtitles();
    }

    this.updateDynamicLabels(now);

    this.rig.head.updateWorldMatrix(true, false);
    this._rayOrigin.setFromMatrixPosition(this.rig.head.matrixWorld);
    this._rayDir.set(0, 0, -1).transformDirection(this.rig.head.matrixWorld);
    this.raycaster.set(this._rayOrigin, this._rayDir);

    let targetDistance = 6.0;
    this.hoverSeekRatio = null;

    if (this.isMenuVisible) {
      const activeObjects = this.isSettingsOpen ? this.settingsInteractiveObjects : this.mainInteractiveObjects;
      const menuIntersects = this.raycaster.intersectObjects(activeObjects);
      if (menuIntersects.length > 0) {
        const firstHit = menuIntersects[0];
        const hitObj = firstHit.object;
        targetDistance = Math.max(0.5, firstHit.distance);

        if (hitObj === this.seekBarMesh && firstHit.uv) {
          this.hoverSeekRatio = THREE.MathUtils.clamp(firstHit.uv.x, 0, 1);
        }

        this.setHoveredObject(hitObj);
      } else {
        this.setHoveredObject(null);
      }
    } else {
      this.setHoveredObject(null);
    }

    if (!this.hoveredObject && this.screen && this.screen.meshL) {
      const screenIntersects = this.raycaster.intersectObject(this.screen.meshL);
      if (screenIntersects.length > 0) {
        targetDistance = Math.max(0.5, screenIntersects[0].distance);
      }
    }

    this.updateGazeDwell(now);

    if (this.isMenuVisible && !this.isSettingsOpen && this.video) {
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

    const depthZ = -targetDistance + 0.04;
    this.reticle.position.z = depthZ;
    this.dwellRing.position.z = depthZ + 0.001;

    const baseScale = targetDistance / 1.8;
    const hoverScale = this.hoveredObject && !this.hoveredObject.userData?.noHoverScale ? 1.35 : 1.0;
    const finalScale = baseScale * hoverScale;
    this.reticle.scale.set(finalScale, finalScale, finalScale);
    this.dwellRing.scale.set(finalScale, finalScale, finalScale);
  }
}
