import * as THREE from 'three';

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
    this.raycaster = new THREE.Raycaster();
    this.isMenuVisible = true;

    // Physical button double-click detection & single-click debounce timer
    this.lastClickTime = 0;
    this.clickTimeout = null;

    // Reticle
    this.createReticle();

    // Floating 3D Menu Panel
    this.createControlPanel();

    // Physical Button & Pointer Listeners
    this.initClickListeners();
  }

  createReticle() {
    // Reticle dot fixed at center of view (1.8m in front of eyes)
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
  }

  createControlPanel() {
    this.menuGroup = new THREE.Group();
    // Position floating panel comfortably in lower gaze view, tilted toward viewer
    this.menuGroup.position.set(0, 0.2, -4.5);
    this.menuGroup.rotation.x = -0.22;
    this.rig.scene.add(this.menuGroup);

    // Background Plate
    const bgGeom = new THREE.PlaneGeometry(3.8, 0.76);
    const bgMat = new THREE.MeshBasicMaterial({
      color: 0x0c0e17,
      transparent: true,
      opacity: 0.8,
      side: THREE.DoubleSide
    });
    const bg = new THREE.Mesh(bgGeom, bgMat);
    this.menuGroup.add(bg);

    // Row 1 Buttons
    const row1 = [
      { id: 'rw', label: '⏪ -10s', x: -1.4, y: 0.12, action: () => this.seek(-10) },
      { id: 'play', label: '▶ Play', x: -0.7, y: 0.12, action: () => this.togglePlay() },
      { id: 'ff', label: '⏩ +10s', x: 0.0, y: 0.12, action: () => this.seek(10) },
      { id: 'env', label: '🏛 Room', x: 0.7, y: 0.12, action: () => this.cycleEnvironment() },
      { id: 'recenter', label: '🎯 Recenter', x: 1.4, y: 0.12, action: () => this.rig.recenter() }
    ];

    row1.forEach((b) => {
      const btnMesh = this.createButtonMesh(b.label, b.x, b.y, b.action);
      this.menuGroup.add(btnMesh);
      this.interactiveObjects.push(btnMesh);
      if (b.id === 'play') {
        this.playBtnMesh = btnMesh;
      }
    });

    // Row 2 Buttons
    const audioBtn = this.createButtonMesh('🔊 Sound', -1.2, -0.16, () => this.toggleAudio());
    this.menuGroup.add(audioBtn);
    this.interactiveObjects.push(audioBtn);
    this.audioBtnMesh = audioBtn;

    const formatBtn = this.createButtonMesh('🕶 2D', 0.0, -0.16, () => this.cycleFormat());
    this.menuGroup.add(formatBtn);
    this.interactiveObjects.push(formatBtn);
    this.formatBtnMesh = formatBtn;

    const bedBtn = this.createButtonMesh('🛏 Bed Mode', 1.2, -0.16, () => this.toggleBedMode());
    this.menuGroup.add(bedBtn);
    this.interactiveObjects.push(bedBtn);
  }

  createButtonMesh(text, x, y, onClick) {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 80;
    const ctx = canvas.getContext('2d');

    // Draw button background
    ctx.fillStyle = '#1e2235';
    if (ctx.roundRect) {
      ctx.roundRect(4, 4, 248, 72, 16);
    } else {
      ctx.rect(4, 4, 248, 72);
    }
    ctx.fill();

    // Border
    ctx.strokeStyle = '#4f46e5';
    ctx.lineWidth = 4;
    if (ctx.roundRect) {
      ctx.roundRect(4, 4, 248, 72, 16);
    } else {
      ctx.rect(4, 4, 248, 72);
    }
    ctx.stroke();

    // Text
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 26px -apple-system, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, 128, 40);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    const geom = new THREE.PlaneGeometry(0.62, 0.2);
    const mat = new THREE.MeshBasicMaterial({
      map: texture,
      transparent: true,
      side: THREE.DoubleSide
    });

    const mesh = new THREE.Mesh(geom, mat);
    mesh.position.set(x, y, 0.02);
    mesh.userData = {
      onClick: onClick,
      originalScale: new THREE.Vector3(1, 1, 1),
      canvas: canvas,
      ctx: ctx,
      currentText: text
    };

    return mesh;
  }

  updateButtonText(mesh, text) {
    if (!mesh || !mesh.userData) return;
    const ctx = mesh.userData.ctx;
    const canvas = mesh.userData.canvas;

    ctx.clearRect(0, 0, 256, 80);

    ctx.fillStyle = '#1e2235';
    if (ctx.roundRect) {
      ctx.roundRect(4, 4, 248, 72, 16);
    } else {
      ctx.rect(4, 4, 248, 72);
    }
    ctx.fill();

    ctx.strokeStyle = '#4f46e5';
    ctx.lineWidth = 4;
    if (ctx.roundRect) {
      ctx.roundRect(4, 4, 248, 72, 16);
    } else {
      ctx.rect(4, 4, 248, 72);
    }
    ctx.stroke();

    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 26px -apple-system, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, 128, 40);

    mesh.userData.currentText = text;
    if (mesh.material.map) {
      mesh.material.map.needsUpdate = true;
    }
  }

  initClickListeners() {
    const handleTrigger = (e) => {
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

      // Capture target object at time of tap
      const targetObj = this.hoveredObject;

      // Bug 4: Debounce single click action (~280ms) so double-click cancels cleanly
      this.clickTimeout = setTimeout(() => {
        this.clickTimeout = null;
        if (!this.rig.isVrActive) return;

        if (targetObj && targetObj.userData && targetObj.userData.onClick) {
          targetObj.userData.onClick();
          this.pulseButton(targetObj);
        } else {
          // Clicked empty space
          if (this.video && this.video.paused) {
            this.video.play().catch((err) => console.log('Tap play failed:', err));
          } else {
            this.toggleMenu();
          }
        }
      }, 280);
    };

    // JioDive physical button triggers pointerdown / touchstart on the screen
    window.addEventListener('pointerdown', handleTrigger);
  }

  pulseButton(mesh) {
    mesh.scale.set(1.15, 1.15, 1.15);
    setTimeout(() => {
      mesh.scale.set(1, 1, 1);
    }, 150);
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

  cycleEnvironment() {
    const envs = ['cinema', 'void'];
    const next = envs[(envs.indexOf(this.environments.currentEnv) + 1) % envs.length];
    this.environments.setEnvironment(next, this.screen, this);
  }

  toggleBedMode() {
    const target = this.environments.currentEnv === 'bed' ? 'cinema' : 'bed';
    this.environments.setEnvironment(target, this.screen, this);
  }

  cycleFormat() {
    const formats = ['2d', '3d-hsbs', '3d-fsbs', '3d-hou', '3d-fou'];
    const next = formats[(formats.indexOf(this.screen.format) + 1) % formats.length];
    this.screen.setFormat(next);
  }

  /**
   * Bug 6 Fix: Reorient menu in Bed Mode so it tilts with the screen onto the ceiling
   */
  setTilt(pitchDegrees) {
    const pitchRad = THREE.MathUtils.degToRad(pitchDegrees);
    // Relative to eye position (0, 1.4, 0), normal menu pos is (0, -1.2, -4.5)
    const relY = -1.2;
    const relZ = -4.5;

    const newY = 1.4 + (relY * Math.cos(pitchRad) - relZ * Math.sin(pitchRad));
    const newZ = relY * Math.sin(pitchRad) + relZ * Math.cos(pitchRad);

    this.menuGroup.position.set(0, newY, newZ);
    this.menuGroup.rotation.x = -0.22 + pitchRad;
  }

  update() {
    // Dynamic Button Labels
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

      if (this.formatBtnMesh) {
        let fmtLabel = '🕶 2D';
        if (this.screen.format === '3d-hsbs' || this.screen.format === '3d-sbs') fmtLabel = '👓 Half-SBS';
        else if (this.screen.format === '3d-fsbs') fmtLabel = '👓 Full-SBS';
        else if (this.screen.format === '3d-hou' || this.screen.format === '3d-ou') fmtLabel = '👓 Half-OU';
        else if (this.screen.format === '3d-fou') fmtLabel = '👓 Full-OU';
        if (this.formatBtnMesh.userData.currentText !== fmtLabel) {
          this.updateButtonText(this.formatBtnMesh, fmtLabel);
        }
      }
    }

    if (!this.isMenuVisible) {
      this.hoveredObject = null;
      this.reticle.scale.set(1, 1, 1);
      return;
    }

    // Cast ray from center of head view
    this.raycaster.setFromCamera({ x: 0, y: 0 }, this.rig.cameraL);
    const intersects = this.raycaster.intersectObjects(this.interactiveObjects);

    if (intersects.length > 0) {
      const hit = intersects[0].object;
      if (this.hoveredObject !== hit) {
        if (this.hoveredObject) this.hoveredObject.scale.set(1, 1, 1);
        this.hoveredObject = hit;
        this.hoveredObject.scale.set(1.08, 1.08, 1.08); // Hover highlight
        this.reticle.scale.set(1.4, 1.4, 1.4);
      }
    } else {
      if (this.hoveredObject) {
        this.hoveredObject.scale.set(1, 1, 1);
        this.hoveredObject = null;
        this.reticle.scale.set(1, 1, 1);
      }
    }
  }
}
