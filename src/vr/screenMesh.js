import * as THREE from 'three';
import { getActiveCueText } from './subtitleParser.js';

export const SCREEN_PRESETS = {
  imax: { distance: 5.2, curveDeg: 82, stageClearanceY: 0.68, label: 'IMAX' },
  standard: { distance: 6.5, curveDeg: 64, stageClearanceY: 0.55, label: 'Standard' },
  balcony: { distance: 7.45, curveDeg: 44, stageClearanceY: 0.80, label: 'Balcony' }
};

/**
 * Curved Virtual Cinema Screen Mesh
 * Supports 2D, 3D Half/Full SBS & OU, L/R eye swapping, and screen distance presets.
 * Highly optimized: shares a single VideoTexture in 2D mode and avoids redundant per-frame GPU re-uploads.
 */
export class CinemaScreen {
  constructor(scene, videoElement) {
    this.scene = scene;
    this.video = videoElement;

    // Screen dimensions & preset state
    this.screenPreset = 'standard';
    const initialCfg = SCREEN_PRESETS.standard;
    this.distance = initialCfg.distance;
    this.curveAngle = THREE.MathUtils.degToRad(initialCfg.curveDeg);
    this.chordWidth = 2 * this.distance * Math.sin(this.curveAngle / 2);
    this.height = this.chordWidth / (16 / 9);
    this.stageClearanceY = initialCfg.stageClearanceY;

    this.format = '2d'; // '2d', '3d-hsbs', '3d-fsbs', '3d-hou', '3d-fou'
    this.swapEyes = false;

    // Standby canvas texture (shown before video starts or while connecting)
    this.standbyTexture = this.createCanvasTexture(
      'JioDive VR Cinema',
      'Loading stream or tap anywhere to begin playback'
    );

    // Primary Video Texture (shared by both eyes in 2D mode to cut GPU texture uploads in half!)
    this.videoTextureL = new THREE.VideoTexture(this.video);
    this.videoTextureL.minFilter = THREE.LinearFilter;
    this.videoTextureL.magFilter = THREE.LinearFilter;
    this.videoTextureL.colorSpace = THREE.SRGBColorSpace;

    // Secondary texture for Right Eye in 3D modes only (instantiated on demand)
    this.videoTextureR = null;

    // Root screen container placed at eye height Y = 1.4 (rotation pivot for Bed Mode)
    this.container = new THREE.Group();
    this.container.position.set(0, 1.4, 0);
    this.scene.add(this.container);

    // Screen Anchor group: elevated so screen bottom edge clears stage
    this.screenAnchor = new THREE.Group();
    this.screenAnchor.position.y = (this.stageClearanceY + this.height / 2) - 1.4;
    this.container.add(this.screenAnchor);

    // Subtitle state
    this.subtitleCues = [];
    this.subtitlesEnabled = false;
    this.currentSubText = '';

    this.createScreenMeshes();
    this.createSubtitleOverlay();
    this.setFormat('2d');
    this.initVideoListeners();
  }

  createCanvasTexture(title, subtitle) {
    const canvas = document.createElement('canvas');
    canvas.width = 1280;
    canvas.height = 720;
    const ctx = canvas.getContext('2d');

    const grad = ctx.createRadialGradient(640, 260, 40, 640, 360, 760);
    grad.addColorStop(0, '#171d3d');
    grad.addColorStop(0.55, '#0b0e1e');
    grad.addColorStop(1, '#070812');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, 1280, 720);

    // Crisp outer cinema hairline border
    ctx.strokeStyle = 'rgba(148, 163, 184, 0.22)';
    ctx.lineWidth = 4;
    ctx.strokeRect(24, 24, 1232, 672);

    // Subtle horizon glow arc matching JioDive crest
    const arcGrad = ctx.createLinearGradient(440, 180, 840, 180);
    arcGrad.addColorStop(0, 'rgba(56, 189, 248, 0)');
    arcGrad.addColorStop(0.5, 'rgba(199, 210, 254, 0.9)');
    arcGrad.addColorStop(1, 'rgba(99, 102, 241, 0)');
    ctx.strokeStyle = arcGrad;
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(460, 204);
    ctx.quadraticCurveTo(640, 168, 820, 204);
    ctx.stroke();

    // Vector dual-lens emblem
    const emblemGrad = ctx.createLinearGradient(576, 224, 704, 304);
    emblemGrad.addColorStop(0, '#38bdf8');
    emblemGrad.addColorStop(1, '#6366f1');
    ctx.strokeStyle = emblemGrad;
    ctx.lineWidth = 5;
    ctx.fillStyle = '#11152c';
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(574, 224, 132, 72, 20);
    } else {
      ctx.rect(574, 224, 132, 72);
    }
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = '#38bdf8';
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(592, 240, 40, 38, 10);
      ctx.roundRect(648, 240, 40, 38, 10);
    } else {
      ctx.rect(592, 240, 40, 38);
      ctx.rect(648, 240, 40, 38);
    }
    ctx.fill();

    ctx.fillStyle = '#f8fafc';
    ctx.font = 'bold 52px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(title, 640, 368);

    ctx.fillStyle = '#94a3b8';
    ctx.font = '26px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText(subtitle, 640, 434);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  showErrorTexture(errorMessage) {
    const canvas = document.createElement('canvas');
    canvas.width = 1280;
    canvas.height = 720;
    const ctx = canvas.getContext('2d');

    ctx.fillStyle = '#13090e';
    ctx.fillRect(0, 0, 1280, 720);

    ctx.strokeStyle = 'rgba(248, 113, 113, 0.55)';
    ctx.lineWidth = 5;
    ctx.strokeRect(24, 24, 1232, 672);

    // Crisp vector warning triangle (no OS emoji)
    ctx.strokeStyle = '#f87171';
    ctx.lineWidth = 6;
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(640, 195);
    ctx.lineTo(686, 275);
    ctx.lineTo(594, 275);
    ctx.closePath();
    ctx.stroke();

    ctx.fillStyle = '#f87171';
    ctx.fillRect(637, 222, 6, 26);
    ctx.beginPath();
    ctx.arc(640, 261, 4, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = '#fca5a5';
    ctx.font = 'bold 46px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('Playback Error', 640, 336);

    ctx.fillStyle = '#fecaca';
    ctx.font = '26px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText(errorMessage, 640, 405);

    ctx.fillStyle = '#cbd5e1';
    ctx.font = '22px -apple-system, BlinkMacSystemFont, sans-serif';
    ctx.fillText('Convert this file to MP4 (H.264 + AAC) or WebM for browser playback.', 640, 464);

    const errTex = new THREE.CanvasTexture(canvas);
    errTex.colorSpace = THREE.SRGBColorSpace;

    this.materialL.map = errTex;
    this.materialL.needsUpdate = true;
    this.materialR.map = errTex;
    this.materialR.needsUpdate = true;
  }

  buildCurvedGeometry(radius, angleRad, height, segments = 48) {
    const thetaStart = Math.PI - angleRad / 2;
    const geom = new THREE.CylinderGeometry(
      radius, radius, height, segments, 1, true, thetaStart, angleRad
    );
    // Invert X so the inside of cylinder faces camera at (0, 0, 0) and UVs run Left->Right
    geom.scale(-1, 1, 1);
    return geom;
  }

  getSubtitleDims() {
    const scaleFactor = this.distance / 6.5;
    return {
      radius: this.distance - 0.14,
      angle: Math.min(THREE.MathUtils.degToRad(54), this.curveAngle * 0.68),
      height: 0.9 * scaleFactor,
      yOffset: -this.height / 2 + 0.56 * scaleFactor
    };
  }

  createScreenMeshes() {
    const geometry = this.buildCurvedGeometry(this.distance, this.curveAngle, this.height, 48);

    const initialMap = (this.video && this.video.readyState >= 2) ? this.videoTextureL : this.standbyTexture;

    // Left Eye Material
    this.materialL = new THREE.MeshBasicMaterial({
      map: initialMap,
      side: THREE.DoubleSide
    });

    // Right Eye Material (in 2D shares initialMap!)
    this.materialR = new THREE.MeshBasicMaterial({
      map: initialMap,
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
    const borderGeom = this.buildCurvedGeometry(
      this.distance + 0.05,
      this.curveAngle + THREE.MathUtils.degToRad(3),
      this.height + 0.28,
      48
    );
    const borderMat = new THREE.MeshBasicMaterial({ color: 0x07080d, side: THREE.DoubleSide });
    this.frameMesh = new THREE.Mesh(borderGeom, borderMat);
    this.frameMesh.layers.set(0); // Visible to both eyes
    this.screenAnchor.add(this.frameMesh);
  }

  createSubtitleOverlay() {
    this.subtitleCanvas = document.createElement('canvas');
    this.subtitleCanvas.width = 1024;
    this.subtitleCanvas.height = 256;
    this.subtitleCtx = this.subtitleCanvas.getContext('2d');

    this.subtitleTexture = new THREE.CanvasTexture(this.subtitleCanvas);
    this.subtitleTexture.minFilter = THREE.LinearFilter;
    this.subtitleTexture.magFilter = THREE.LinearFilter;
    this.subtitleTexture.anisotropy = 8;
    this.subtitleTexture.colorSpace = THREE.SRGBColorSpace;

    const sub = this.getSubtitleDims();
    const subGeom = this.buildCurvedGeometry(sub.radius, sub.angle, sub.height, 32);

    const subMat = new THREE.MeshBasicMaterial({
      map: this.subtitleTexture,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide
    });

    this.subtitleMesh = new THREE.Mesh(subGeom, subMat);
    this.subtitleMesh.layers.set(0); // Render crisp stereo depth for both eyes
    this.subtitleMesh.position.y = sub.yOffset;
    this.subtitleMesh.renderOrder = 10;
    this.subtitleMesh.visible = false;
    this.screenAnchor.add(this.subtitleMesh);
  }

  initVideoListeners() {
    const switchToVideo = () => {
      const is3D = (this.format !== '2d');
      if (this.materialL.map !== this.videoTextureL) {
        this.materialL.map = this.videoTextureL;
        this.materialL.needsUpdate = true;
      }
      const targetR = is3D ? (this.videoTextureR || this.videoTextureL) : this.videoTextureL;
      if (this.materialR.map !== targetR) {
        this.materialR.map = targetR;
        this.materialR.needsUpdate = true;
      }
      this.videoTextureL.needsUpdate = true;
      if (this.videoTextureR) this.videoTextureR.needsUpdate = true;
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

    this.video.addEventListener('error', () => {
      const err = this.video.error;
      let msg = 'Browser cannot decode or load this file.';
      if (err && err.code === MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED) {
        msg = 'Browser does not support MKV container or audio codec.';
      }
      this.showErrorTexture(msg);
    });
  }

  getEffectiveAspectRatio() {
    if (this.video && this.video.videoWidth && this.video.videoHeight) {
      let aspect = this.video.videoWidth / this.video.videoHeight;
      if (this.format === '3d-fsbs') {
        aspect = (this.video.videoWidth / 2) / this.video.videoHeight;
      } else if (this.format === '3d-fou') {
        aspect = this.video.videoWidth / (this.video.videoHeight / 2);
      }
      if (aspect > 0 && isFinite(aspect)) {
        return aspect;
      }
    }
    return 16 / 9;
  }

  updateAspectRatio() {
    if (this.video && this.video.videoWidth && this.video.videoHeight) {
      const aspect = this.getEffectiveAspectRatio();
      const targetHeight = this.chordWidth / aspect;
      const clampedHeight = Math.max(2.0, Math.min(5.5, targetHeight));
      if (Math.abs(this.height - clampedHeight) > 0.05) {
        this.height = clampedHeight;
        this.rebuildGeometry();
      }
    }
  }

  rebuildGeometry() {
    // Recompute screen elevation so bottom edge clears stage
    if (this.screenAnchor) {
      this.screenAnchor.position.y = (this.stageClearanceY + this.height / 2) - 1.4;
    }

    const newGeom = this.buildCurvedGeometry(this.distance, this.curveAngle, this.height, 48);

    if (this.meshL) {
      this.meshL.geometry.dispose();
      this.meshL.geometry = newGeom;
    }
    if (this.meshR) {
      this.meshR.geometry.dispose();
      this.meshR.geometry = newGeom.clone();
    }

    const borderGeom = this.buildCurvedGeometry(
      this.distance + 0.05,
      this.curveAngle + THREE.MathUtils.degToRad(3),
      this.height + 0.28,
      48
    );
    if (this.frameMesh) {
      this.frameMesh.geometry.dispose();
      this.frameMesh.geometry = borderGeom;
    }
    if (this.subtitleMesh) {
      const sub = this.getSubtitleDims();
      this.subtitleMesh.geometry.dispose();
      this.subtitleMesh.geometry = this.buildCurvedGeometry(sub.radius, sub.angle, sub.height, 32);
      this.subtitleMesh.position.y = sub.yOffset;
    }
  }

  setScreenPreset(presetName) {
    const cfg = SCREEN_PRESETS[presetName] || SCREEN_PRESETS.standard;
    this.screenPreset = SCREEN_PRESETS[presetName] ? presetName : 'standard';
    this.distance = cfg.distance;
    this.curveAngle = THREE.MathUtils.degToRad(cfg.curveDeg);
    this.stageClearanceY = cfg.stageClearanceY;
    this.chordWidth = 2 * this.distance * Math.sin(this.curveAngle / 2);

    const aspect = this.getEffectiveAspectRatio();
    this.height = Math.max(2.0, Math.min(5.5, this.chordWidth / aspect));
    this.rebuildGeometry();
  }

  setSwapEyes(swapped) {
    this.swapEyes = Boolean(swapped);
    this.setFormat(this.format);
  }

  toggleSwapEyes() {
    this.setSwapEyes(!this.swapEyes);
    return this.swapEyes;
  }

  /**
   * Set 2D, 3D Half-SBS, 3D Full-SBS, 3D Half-OU, or 3D Full-OU
   */
  setFormat(format) {
    this.format = format;
    const is3D = (format !== '2d');

    if (is3D) {
      // Create secondary right eye texture on-demand if needed
      if (!this.videoTextureR) {
        this.videoTextureR = new THREE.VideoTexture(this.video);
        this.videoTextureR.minFilter = THREE.LinearFilter;
        this.videoTextureR.magFilter = THREE.LinearFilter;
        this.videoTextureR.colorSpace = THREE.SRGBColorSpace;
      }

      if (format === '3d-hsbs' || format === '3d-fsbs' || format === '3d-sbs') {
        const leftX = this.swapEyes ? 0.5 : 0;
        const rightX = this.swapEyes ? 0 : 0.5;
        this.videoTextureL.repeat.set(0.5, 1);
        this.videoTextureL.offset.set(leftX, 0);

        this.videoTextureR.repeat.set(0.5, 1);
        this.videoTextureR.offset.set(rightX, 0);
      } else if (format === '3d-hou' || format === '3d-fou' || format === '3d-ou') {
        const leftY = this.swapEyes ? 0 : 0.5;
        const rightY = this.swapEyes ? 0.5 : 0;
        this.videoTextureL.repeat.set(1, 0.5);
        this.videoTextureL.offset.set(0, leftY);

        this.videoTextureR.repeat.set(1, 0.5);
        this.videoTextureR.offset.set(0, rightY);
      }

      if (this.materialL.map !== this.standbyTexture) {
        this.materialL.map = this.videoTextureL;
        this.materialR.map = this.videoTextureR;
        this.materialL.needsUpdate = true;
        this.materialR.needsUpdate = true;
      }
    } else {
      // 2D Mode: Shared single texture for both eyes (50% GPU upload saving!)
      this.videoTextureL.repeat.set(1, 1);
      this.videoTextureL.offset.set(0, 0);

      if (this.materialL.map !== this.standbyTexture) {
        this.materialL.map = this.videoTextureL;
        this.materialR.map = this.videoTextureL; // Shared!
        this.materialL.needsUpdate = true;
        this.materialR.needsUpdate = true;
      }
    }

    this.videoTextureL.needsUpdate = true;
    if (this.videoTextureR) this.videoTextureR.needsUpdate = true;
    this.updateAspectRatio();
  }

  /**
   * Load parsed SRT/VTT cue list and enable captions
   */
  setSubtitles(cues) {
    this.subtitleCues = Array.isArray(cues) ? cues : [];
    this.subtitlesEnabled = this.subtitleCues.length > 0;
    this.currentSubText = '';
    this.updateSubtitles(true);
  }

  toggleSubtitles() {
    this.subtitlesEnabled = !this.subtitlesEnabled;
    this.updateSubtitles(true);
    return this.subtitlesEnabled;
  }

  updateSubtitles(forceRedraw = false) {
    if (!this.subtitleMesh) return;

    const nextText = (this.subtitlesEnabled && this.subtitleCues.length > 0 && this.video)
      ? getActiveCueText(this.subtitleCues, this.video.currentTime)
      : '';

    if (nextText !== this.currentSubText || forceRedraw) {
      this.currentSubText = nextText;
      if (!nextText) {
        this.subtitleMesh.visible = false;
        return;
      }
      this.renderSubtitleCanvas(nextText);
      this.subtitleMesh.visible = true;
    }
  }

  renderSubtitleCanvas(text) {
    const ctx = this.subtitleCtx;
    const width = this.subtitleCanvas.width;
    const height = this.subtitleCanvas.height;

    ctx.clearRect(0, 0, width, height);
    ctx.font = 'bold 40px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    // Word-wrap cue paragraphs onto max ~3 readable lines
    const maxLineWidth = 920;
    const lines = [];
    for (const para of text.split('\n')) {
      const words = para.split(/\s+/);
      let current = '';
      for (const word of words) {
        const candidate = current ? `${current} ${word}` : word;
        if (ctx.measureText(candidate).width > maxLineWidth && current) {
          lines.push(current);
          current = word;
        } else {
          current = candidate;
        }
      }
      if (current) lines.push(current);
    }

    const visibleLines = lines.slice(-3);
    const lineHeight = 52;
    const blockHeight = visibleLines.length * lineHeight + 24;
    const maxTextWidth = visibleLines.reduce((max, l) => Math.max(max, ctx.measureText(l).width), 0);
    const pillWidth = Math.min(width - 40, maxTextWidth + 56);
    const startY = height - blockHeight - 16;

    // Translucent cinema caption backing pill for high contrast over bright frames
    ctx.fillStyle = 'rgba(8, 10, 18, 0.72)';
    if (ctx.roundRect) {
      ctx.beginPath();
      ctx.roundRect((width - pillWidth) / 2, startY, pillWidth, blockHeight, 14);
      ctx.fill();
    } else {
      ctx.fillRect((width - pillWidth) / 2, startY, pillWidth, blockHeight);
    }

    // Draw text lines with dark outline stroke + crisp white fill
    ctx.lineWidth = 5;
    ctx.strokeStyle = 'rgba(0, 0, 0, 0.9)';
    ctx.fillStyle = '#ffffff';

    visibleLines.forEach((line, idx) => {
      const y = startY + 12 + (idx + 0.5) * lineHeight;
      ctx.strokeText(line, width / 2, y);
      ctx.fillText(line, width / 2, y);
    });

    this.subtitleTexture.needsUpdate = true;
  }

  /**
   * Reorient for Bed / Ceiling Mode
   */
  setTilt(pitchDegrees) {
    this.container.rotation.x = THREE.MathUtils.degToRad(pitchDegrees);
  }
}
