import * as THREE from 'three';

/**
 * Curved Virtual Cinema Screen Mesh
 * Supports 2D, 3D Half-SBS, 3D Full-SBS, 3D Half-OU, and 3D Full-OU video playback.
 */
export class CinemaScreen {
  constructor(scene, videoElement) {
    this.scene = scene;
    this.video = videoElement;

    // Screen dimensions & placement
    this.distance = 6.5; // 6.5 meters directly in front of camera
    this.curveAngle = THREE.MathUtils.degToRad(64); // ~64 degree horizontal cinema arc
    this.chordWidth = 2 * this.distance * Math.sin(this.curveAngle / 2); // ~6.88m wide
    this.height = this.chordWidth / (16 / 9); // ~3.87m height for 16:9
    this.format = '2d'; // '2d', '3d-hsbs', '3d-fsbs', '3d-hou', '3d-fou'

    // Stage clearance: Stage tops out at Y = 0.41m.
    // Setting bottom edge to 0.55m gives a clean 14cm clearance above stage.
    this.stageClearanceY = 0.55;

    // Standby canvas texture (shown before video starts or while loading)
    this.standbyTexture = this.createCanvasTexture(
      '🥽 JioDive VR Cinema',
      'Select a movie or tap anywhere to start playback'
    );

    // Create Video Textures
    this.videoTextureL = new THREE.VideoTexture(this.video);
    this.videoTextureL.minFilter = THREE.LinearFilter;
    this.videoTextureL.magFilter = THREE.LinearFilter;
    this.videoTextureL.colorSpace = THREE.SRGBColorSpace;

    this.videoTextureR = new THREE.VideoTexture(this.video);
    this.videoTextureR.minFilter = THREE.LinearFilter;
    this.videoTextureR.magFilter = THREE.LinearFilter;
    this.videoTextureR.colorSpace = THREE.SRGBColorSpace;

    // Root screen container placed at eye height Y = 1.4 (rotation pivot for Bed Mode)
    this.container = new THREE.Group();
    this.container.position.set(0, 1.4, 0);
    this.scene.add(this.container);

    // Screen Anchor group: elevated so screen bottom edge clears stage
    this.screenAnchor = new THREE.Group();
    this.screenAnchor.position.y = (this.stageClearanceY + this.height / 2) - 1.4;
    this.container.add(this.screenAnchor);

    this.createScreenMeshes();
    this.setFormat('2d');
    this.initVideoListeners();
  }

  createCanvasTexture(title, subtitle) {
    const canvas = document.createElement('canvas');
    canvas.width = 1280;
    canvas.height = 720;
    const ctx = canvas.getContext('2d');

    const grad = ctx.createLinearGradient(0, 0, 1280, 720);
    grad.addColorStop(0, '#0c0f1d');
    grad.addColorStop(0.5, '#1e243d');
    grad.addColorStop(1, '#0c0f1d');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, 1280, 720);

    ctx.strokeStyle = '#6366f1';
    ctx.lineWidth = 8;
    ctx.strokeRect(20, 20, 1240, 680);

    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 54px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(title, 640, 320);

    ctx.fillStyle = '#a5b4fc';
    ctx.font = '28px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText(subtitle, 640, 410);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  showErrorTexture(errorMessage) {
    const canvas = document.createElement('canvas');
    canvas.width = 1280;
    canvas.height = 720;
    const ctx = canvas.getContext('2d');

    ctx.fillStyle = '#1a0d10';
    ctx.fillRect(0, 0, 1280, 720);

    ctx.strokeStyle = '#ef4444';
    ctx.lineWidth = 8;
    ctx.strokeRect(20, 20, 1240, 680);

    ctx.fillStyle = '#f87171';
    ctx.font = 'bold 50px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('⚠️ Playback Error', 640, 280);

    ctx.fillStyle = '#fca5a5';
    ctx.font = '26px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText(errorMessage, 640, 370);

    ctx.fillStyle = '#e2e8f0';
    ctx.font = '22px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText('Convert this file to MP4 (H.264 + AAC) or WebM for browser playback.', 640, 430);

    const errTex = new THREE.CanvasTexture(canvas);
    errTex.colorSpace = THREE.SRGBColorSpace;

    this.materialL.map = errTex;
    this.materialL.needsUpdate = true;
    this.materialR.map = errTex;
    this.materialR.needsUpdate = true;
  }

  createScreenMeshes() {
    const thetaStart = Math.PI - this.curveAngle / 2;

    const geometry = new THREE.CylinderGeometry(
      this.distance, this.distance, this.height, 48, 1, true, thetaStart, this.curveAngle
    );
    // Invert X so the inside of cylinder faces camera at (0, 0, 0) and UVs run Left->Right
    geometry.scale(-1, 1, 1);

    const initialMap = (this.video && this.video.readyState >= 2) ? this.videoTextureL : this.standbyTexture;

    // Left Eye Material
    this.materialL = new THREE.MeshBasicMaterial({
      map: initialMap,
      side: THREE.DoubleSide
    });

    // Right Eye Material
    this.materialR = new THREE.MeshBasicMaterial({
      map: (this.video && this.video.readyState >= 2) ? this.videoTextureR : this.standbyTexture,
      side: THREE.DoubleSide
    });

    // Left Screen Mesh
    this.meshL = new THREE.Mesh(geometry, this.materialL);
    this.meshL.layers.set(1); // Left Eye Camera Only
    this.screenAnchor.add(this.meshL);

    // Right Screen Mesh
    this.meshR = new THREE.Mesh(geometry.clone(), this.materialR);
    this.meshR.layers.set(2); // Right Eye Camera Only
    this.screenAnchor.add(this.meshR);

    // Screen Border / Frame
    const borderDistance = this.distance + 0.05;
    const borderAngle = this.curveAngle + THREE.MathUtils.degToRad(3);
    const borderHeight = this.height + 0.28;
    const borderThetaStart = Math.PI - borderAngle / 2;

    const borderGeom = new THREE.CylinderGeometry(
      borderDistance, borderDistance, borderHeight, 48, 1, true, borderThetaStart, borderAngle
    );
    borderGeom.scale(-1, 1, 1);
    const borderMat = new THREE.MeshBasicMaterial({ color: 0x07080d, side: THREE.DoubleSide });
    this.frameMesh = new THREE.Mesh(borderGeom, borderMat);
    this.frameMesh.layers.set(0); // Visible to both eyes
    this.screenAnchor.add(this.frameMesh);
  }

  initVideoListeners() {
    const switchToVideo = () => {
      if (this.materialL.map !== this.videoTextureL) {
        this.materialL.map = this.videoTextureL;
        this.materialL.needsUpdate = true;
      }
      if (this.materialR.map !== this.videoTextureR) {
        this.materialR.map = this.videoTextureR;
        this.materialR.needsUpdate = true;
      }
      this.videoTextureL.needsUpdate = true;
      this.videoTextureR.needsUpdate = true;
    };

    this.video.addEventListener('loadedmetadata', () => {
      this.updateAspectRatio();
    });

    this.video.addEventListener('loadeddata', () => {
      this.updateAspectRatio();
      switchToVideo();
    });

    this.video.addEventListener('canplay', switchToVideo);
    this.video.addEventListener('playing', switchToVideo);
    this.video.addEventListener('seeked', switchToVideo);
    this.video.addEventListener('timeupdate', switchToVideo);

    this.video.addEventListener('error', () => {
      const err = this.video.error;
      let msg = 'Browser cannot decode or load this file.';
      if (err && err.code === MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED) {
        msg = 'Browser does not support MKV container or audio codec.';
      }
      this.showErrorTexture(msg);
    });
  }

  updateAspectRatio() {
    if (this.video && this.video.videoWidth && this.video.videoHeight) {
      let aspect = this.video.videoWidth / this.video.videoHeight;
      // Full SBS: file is 3840x1080 -> each eye is (width/2) / height
      if (this.format === '3d-fsbs') {
        aspect = (this.video.videoWidth / 2) / this.video.videoHeight;
      }
      // Half SBS (HSBS): file is 1920x1080 anamorphic (each eye squeezed 2:1 horizontally) -> display aspect is W/H (16:9)
      else if (this.format === '3d-hsbs' || this.format === '3d-sbs') {
        aspect = this.video.videoWidth / this.video.videoHeight;
      }
      // Full OU: file is 1920x2160 -> each eye is width / (height/2)
      else if (this.format === '3d-fou') {
        aspect = this.video.videoWidth / (this.video.videoHeight / 2);
      }
      // Half OU (HOU): file is 1920x1080 anamorphic (each eye squeezed 2:1 vertically) -> display aspect is W/H (16:9)
      else if (this.format === '3d-hou' || this.format === '3d-ou') {
        aspect = this.video.videoWidth / this.video.videoHeight;
      }

      if (aspect > 0 && isFinite(aspect)) {
        const targetHeight = this.chordWidth / aspect;
        const clampedHeight = Math.max(2.2, Math.min(5.5, targetHeight));
        if (Math.abs(this.height - clampedHeight) > 0.05) {
          this.height = clampedHeight;
          this.rebuildGeometry();
        }
      }
    }
  }

  rebuildGeometry() {
    // Recompute screen elevation so bottom edge clears stage
    if (this.screenAnchor) {
      this.screenAnchor.position.y = (this.stageClearanceY + this.height / 2) - 1.4;
    }

    const thetaStart = Math.PI - this.curveAngle / 2;

    const newGeom = new THREE.CylinderGeometry(
      this.distance, this.distance, this.height, 48, 1, true, thetaStart, this.curveAngle
    );
    newGeom.scale(-1, 1, 1);

    if (this.meshL) {
      this.meshL.geometry.dispose();
      this.meshL.geometry = newGeom;
    }
    if (this.meshR) {
      this.meshR.geometry.dispose();
      this.meshR.geometry = newGeom.clone();
    }

    const borderDistance = this.distance + 0.05;
    const borderAngle = this.curveAngle + THREE.MathUtils.degToRad(3);
    const borderHeight = this.height + 0.28;
    const borderThetaStart = Math.PI - borderAngle / 2;

    const borderGeom = new THREE.CylinderGeometry(
      borderDistance, borderDistance, borderHeight, 48, 1, true, borderThetaStart, borderAngle
    );
    borderGeom.scale(-1, 1, 1);
    if (this.frameMesh) {
      this.frameMesh.geometry.dispose();
      this.frameMesh.geometry = borderGeom;
    }
  }

  /**
   * Set 2D, 3D Half-SBS, 3D Full-SBS, 3D Half-OU, or 3D Full-OU
   */
  setFormat(format) {
    this.format = format;

    if (format === '3d-hsbs' || format === '3d-fsbs' || format === '3d-sbs') {
      // Left eye sees left half: UV [0.0, 0.5]
      this.videoTextureL.repeat.set(0.5, 1);
      this.videoTextureL.offset.set(0, 0);

      // Right eye sees right half: UV [0.5, 1.0]
      this.videoTextureR.repeat.set(0.5, 1);
      this.videoTextureR.offset.set(0.5, 0);
    } else if (format === '3d-hou' || format === '3d-fou' || format === '3d-ou') {
      // Left eye sees top half
      this.videoTextureL.repeat.set(1, 0.5);
      this.videoTextureL.offset.set(0, 0.5);

      // Right eye sees bottom half
      this.videoTextureR.repeat.set(1, 0.5);
      this.videoTextureR.offset.set(0, 0);
    } else {
      // Standard 2D
      this.videoTextureL.repeat.set(1, 1);
      this.videoTextureL.offset.set(0, 0);

      this.videoTextureR.repeat.set(1, 1);
      this.videoTextureR.offset.set(0, 0);
    }

    this.videoTextureL.needsUpdate = true;
    this.videoTextureR.needsUpdate = true;
    this.updateAspectRatio();
  }

  /**
   * Continuous per-frame check: Ensures texture updates even if
   * requestVideoFrameCallback was skipped or throttled by browser.
   */
  update() {
    if (this.video && !this.video.paused && this.video.readyState >= this.video.HAVE_CURRENT_DATA) {
      this.videoTextureL.needsUpdate = true;
      this.videoTextureR.needsUpdate = true;
    }
  }

  /**
   * Reorient for Bed / Ceiling Mode
   */
  setTilt(pitchDegrees) {
    this.container.rotation.x = THREE.MathUtils.degToRad(pitchDegrees);
  }
}
