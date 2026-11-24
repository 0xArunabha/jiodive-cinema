import * as THREE from 'three';

/**
 * Verified hardware optical profile extracted from official JioImmerse (JIOCardboardHeadset)
 */
export const JIODIVE_OPTICS = {
  vendor: 'JIO',
  model: 'JIOCardboardHeadset',
  ipdMeters: 0.062,          // 62 mm inter-lens distance
  screenToLensMeters: 0.04,  // 40 mm lens-to-screen distance
  trayToLensMeters: 0.035,   // 35 mm bottom tray offset
  k1: 0.101,                 // Brown-Conrady quadratic coefficient
  k2: 0.098                  // Brown-Conrady quartic coefficient
};

/**
 * JioDive Dual-Eye Stereo Camera Rig
 * Handles stereo separation (IPD), optional JioDive barrel lens distortion pass,
 * device orientation (gyroscope), and split-screen viewport rendering.
 */
export class StereoRig {
  constructor(renderer, scene) {
    this.renderer = renderer;
    this.scene = scene;

    // Root container for position/orientation
    this.container = new THREE.Object3D();
    this.container.position.set(0, 1.4, 0); // Average seated head height (1.4m)
    this.scene.add(this.container);

    // Head orientation node (receives gyro rotation)
    this.head = new THREE.Object3D();
    this.container.add(this.head);

    // Optical state (calibrated to JIOCardboardHeadset spec)
    this.ipd = JIODIVE_OPTICS.ipdMeters;
    this.fov = 75;
    this.distortionEnabled = true;
    this.k1 = JIODIVE_OPTICS.k1;
    this.k2 = JIODIVE_OPTICS.k2;

    // Dual Cameras
    const aspect = (window.innerWidth / 2) / Math.max(1, window.innerHeight);
    const near = 0.1;
    const far = 50;

    this.cameraL = new THREE.PerspectiveCamera(this.fov, aspect, near, far);
    this.cameraR = new THREE.PerspectiveCamera(this.fov, aspect, near, far);

    // Layer 0 is default (shared objects)
    // Layer 1 is Left-Eye only
    // Layer 2 is Right-Eye only
    this.cameraL.layers.enable(0);
    this.cameraL.layers.enable(1);
    this.cameraL.layers.disable(2);

    this.cameraR.layers.enable(0);
    this.cameraR.layers.disable(1);
    this.cameraR.layers.enable(2);

    // Distortion pipeline resources (lazy-initialized when distortion is active)
    this.rtL = null;
    this.rtR = null;
    this.postScene = null;
    this.postCamera = null;
    this.postUniforms = null;

    this.updateCameras();
    this.updateProjectionAndUniforms();
    this.head.add(this.cameraL);
    this.head.add(this.cameraR);

    // Orientation tracking state
    this.deviceOrientation = { alpha: 0, beta: 0, gamma: 0 };
    // Bug 3 Fix: Read screen orientation immediately at startup
    this.screenOrientation = this.getScreenOrientationAngle();
    this.hasGyro = false;
    this.isVrActive = false;

    // Bug 5 Fix: Ensure recenter works on iOS once first gyro event arrives
    this.needsRecenterOnFirstGyro = true;

    // Pre-allocated temporaries for update() — avoids per-frame GC pressure
    this._zee = new THREE.Vector3(0, 0, 1);
    this._euler = new THREE.Euler();
    this._q0 = new THREE.Quaternion();
    this._q1 = new THREE.Quaternion(-Math.sqrt(0.5), 0, 0, Math.sqrt(0.5));

    // Recenter offset (yaw offset in radians)
    this.yawOffset = 0;

    // Desktop mouse drag fallback
    this.isDragging = false;
    this.previousMousePosition = { x: 0, y: 0 };
    this.desktopEuler = new THREE.Euler(0, 0, 0, 'YXZ');

    this.initListeners();
  }

  getScreenOrientationAngle() {
    if (window.screen && window.screen.orientation && typeof window.screen.orientation.angle === 'number') {
      return window.screen.orientation.angle;
    }
    if (typeof window.orientation === 'number') {
      return window.orientation;
    }
    return 0;
  }

  updateCameras() {
    this.cameraL.position.x = -this.ipd / 2;
    this.cameraR.position.x = this.ipd / 2;
  }

  setIPD(ipdMeters) {
    this.ipd = ipdMeters;
    this.updateCameras();
  }

  setFOV(fovDegrees) {
    this.fov = fovDegrees;
    this.updateProjectionAndUniforms();
  }

  setDistortion(enabled, k1 = JIODIVE_OPTICS.k1, k2 = JIODIVE_OPTICS.k2) {
    this.distortionEnabled = Boolean(enabled);
    this.k1 = k1;
    this.k2 = k2;
    if (!this.distortionEnabled) {
      this.disposeDistortionTargets();
    }
    this.updateProjectionAndUniforms();
  }

  ensureDistortionPass() {
    if (!this.postScene) {
      this.postCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
      this.postScene = new THREE.Scene();

      this.postUniforms = {
        tDiffuse: { value: null },
        uK1: { value: this.k1 },
        uK2: { value: this.k2 },
        uTanHalf: { value: new THREE.Vector2(1, 1) },
        uRenderTanHalf: { value: new THREE.Vector2(1, 1) },
        uTexelSize: { value: new THREE.Vector2(1 / 1280, 1 / 1440) },
        uSharpness: { value: 0.55 }
      };

      const postMat = new THREE.ShaderMaterial({
        uniforms: this.postUniforms,
        depthTest: false,
        depthWrite: false,
        toneMapped: false,
        vertexShader: `
          varying vec2 vUv;
          void main() {
            vUv = uv;
            gl_Position = vec4(position.xy, 0.0, 1.0);
          }
        `,
        fragmentShader: `
          uniform sampler2D tDiffuse;
          uniform float uK1;
          uniform float uK2;
          uniform vec2 uTanHalf;
          uniform vec2 uRenderTanHalf;
          uniform vec2 uTexelSize;
          uniform float uSharpness;
          varying vec2 vUv;

          void main() {
            vec2 p = vUv * 2.0 - 1.0;
            vec2 t = p * uTanHalf;
            float r2 = dot(t, t);
            float factor = 1.0 + uK1 * r2 + uK2 * r2 * r2;
            vec2 srcUv = (t * factor / uRenderTanHalf) * 0.5 + 0.5;

            vec2 edgeDist = min(srcUv, 1.0 - srcUv);
            float insideMask = smoothstep(-0.004, 0.004, min(edgeDist.x, edgeDist.y));
            if (insideMask <= 0.0) {
              gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
              return;
            }

            vec2 uv0 = clamp(srcUv, vec2(0.0), vec2(1.0));
            vec3 c = texture2D(tDiffuse, uv0).rgb;

            // Perceptual 5-tap Contrast-Adaptive Sharpening (CAS) to offset bilinear distortion blur
            if (uSharpness > 0.0) {
              vec3 cp = sqrt(max(c, vec3(0.0)));
              vec3 n = sqrt(max(texture2D(tDiffuse, clamp(uv0 + vec2(0.0, uTexelSize.y), vec2(0.0), vec2(1.0))).rgb, vec3(0.0)));
              vec3 s = sqrt(max(texture2D(tDiffuse, clamp(uv0 - vec2(0.0, uTexelSize.y), vec2(0.0), vec2(1.0))).rgb, vec3(0.0)));
              vec3 e = sqrt(max(texture2D(tDiffuse, clamp(uv0 + vec2(uTexelSize.x, 0.0), vec2(0.0), vec2(1.0))).rgb, vec3(0.0)));
              vec3 w = sqrt(max(texture2D(tDiffuse, clamp(uv0 - vec2(uTexelSize.x, 0.0), vec2(0.0), vec2(1.0))).rgb, vec3(0.0)));

              vec3 mn = min(cp, min(min(n, s), min(e, w)));
              vec3 mx = max(cp, max(max(n, s), max(e, w)));
              vec3 amp = clamp(min(mn, vec3(1.0) - mx) / max(mx, vec3(1e-4)), vec3(0.0), vec3(1.0));
              float peak = -1.0 / mix(8.0, 5.0, uSharpness);
              vec3 weight = sqrt(amp) * peak;
              vec3 sharpened = clamp((cp + (n + s + e + w) * weight) / (vec3(1.0) + 4.0 * weight), vec3(0.0), vec3(1.0));
              c = sharpened * sharpened;
            }

            gl_FragColor = vec4(c * insideMask, 1.0);
            #include <colorspace_fragment>
          }
        `
      });

      const quad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), postMat);
      this.postScene.add(quad);
      this.updateProjectionAndUniforms();
    }

    if (!this.rtL || !this.rtR) {
      this.resizeDistortionTargets();
    }
  }

  disposeDistortionTargets() {
    if (this.rtL) {
      this.rtL.dispose();
      this.rtL = null;
    }
    if (this.rtR) {
      this.rtR.dispose();
      this.rtR = null;
    }
  }

  resizeDistortionTargets() {
    this.disposeDistortionTargets();
    const dpr = this.renderer.getPixelRatio();
    // Oversample each eye target (minimum 2.0x on 1x displays, up to 3.5x on high-DPR OLED phones, capped for VRAM safety)
    const ssScale = Math.min(2.0, Math.max(1.5, dpr * 1.2));
    const maxDim = Math.min(2400, this.renderer.capabilities.maxTextureSize || 2048);
    const rtW = Math.min(maxDim, Math.max(1, Math.floor((window.innerWidth * ssScale) / 2)));
    const rtH = Math.min(maxDim, Math.max(1, Math.floor(window.innerHeight * ssScale)));
    const opts = {
      minFilter: THREE.LinearFilter,
      magFilter: THREE.LinearFilter,
      generateMipmaps: false,
      format: THREE.RGBAFormat,
      type: THREE.UnsignedByteType,
      samples: 0
    };

    this.rtL = new THREE.WebGLRenderTarget(rtW, rtH, opts);
    this.rtR = new THREE.WebGLRenderTarget(rtW, rtH, opts);

    if (this.postUniforms?.uTexelSize) {
      this.postUniforms.uTexelSize.value.set(1.0 / rtW, 1.0 / rtH);
    }
  }

  updateProjectionAndUniforms() {
    const aspect = (window.innerWidth / 2) / Math.max(1, window.innerHeight);
    const tanHalfY = Math.tan(THREE.MathUtils.degToRad(this.fov) * 0.5);
    const tanHalfX = tanHalfY * aspect;

    let renderFov = this.fov;
    let renderTanHalfX = tanHalfX;
    let renderTanHalfY = tanHalfY;

    if (this.distortionEnabled) {
      const edgeTan = Math.max(tanHalfX, tanHalfY);
      const edgeR2 = edgeTan * edgeTan;
      const edgeFactor = 1.0 + this.k1 * edgeR2 + this.k2 * edgeR2 * edgeR2;
      renderTanHalfY = tanHalfY * edgeFactor;
      renderTanHalfX = tanHalfX * edgeFactor;
      renderFov = THREE.MathUtils.radToDeg(2.0 * Math.atan(renderTanHalfY));
    }

    this.cameraL.aspect = aspect;
    this.cameraL.fov = renderFov;
    this.cameraL.updateProjectionMatrix();

    this.cameraR.aspect = aspect;
    this.cameraR.fov = renderFov;
    this.cameraR.updateProjectionMatrix();

    if (this.postUniforms) {
      this.postUniforms.uK1.value = this.k1;
      this.postUniforms.uK2.value = this.k2;
      this.postUniforms.uTanHalf.value.set(tanHalfX, tanHalfY);
      this.postUniforms.uRenderTanHalf.value.set(renderTanHalfX, renderTanHalfY);
    }
  }

  initListeners() {
    // Window resize
    window.addEventListener('resize', () => this.onResize());

    // Screen orientation changes (modern screen.orientation.onchange + fallback)
    const updateOrientation = () => {
      this.screenOrientation = this.getScreenOrientationAngle();
    };

    if (window.screen && window.screen.orientation) {
      window.screen.orientation.addEventListener('change', updateOrientation);
    }
    window.addEventListener('orientationchange', updateOrientation);

    // Device orientation (gyroscope)
    window.addEventListener('deviceorientation', (e) => {
      if (e.alpha !== null) {
        const wasGyroUnavailable = !this.hasGyro;
        this.hasGyro = true;
        this.deviceOrientation = {
          alpha: THREE.MathUtils.degToRad(e.alpha || 0),
          beta: THREE.MathUtils.degToRad(e.beta || 0),
          gamma: THREE.MathUtils.degToRad(e.gamma || 0)
        };

        // Bug 5 Fix: If VR was entered before gyro data arrived, execute deferred recenter now
        if (wasGyroUnavailable && this.needsRecenterOnFirstGyro) {
          this.recenter();
        }
      }
    });

    // Desktop mouse fallback
    window.addEventListener('mousedown', (e) => {
      this.isDragging = true;
      this.previousMousePosition = { x: e.clientX, y: e.clientY };
    });

    window.addEventListener('mousemove', (e) => {
      if (!this.isDragging || this.hasGyro) return;
      const deltaX = e.clientX - this.previousMousePosition.x;
      const deltaY = e.clientY - this.previousMousePosition.y;

      this.desktopEuler.y -= deltaX * 0.003;
      this.desktopEuler.x -= deltaY * 0.003;
      this.desktopEuler.x = Math.max(-Math.PI / 2.2, Math.min(Math.PI / 2.2, this.desktopEuler.x));

      this.previousMousePosition = { x: e.clientX, y: e.clientY };
    });

    window.addEventListener('mouseup', () => {
      this.isDragging = false;
    });
  }

  onResize() {
    const width = window.innerWidth;
    const height = window.innerHeight;
    this.renderer.setSize(width, height);
    this.updateProjectionAndUniforms();

    if (this.distortionEnabled) {
      this.resizeDistortionTargets();
    }
  }

  /**
   * Request Gyroscope permissions (Mandatory for iOS 13+ and some Android versions)
   */
  async requestGyroPermission() {
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
      try {
        const response = await DeviceOrientationEvent.requestPermission();
        return response === 'granted';
      } catch (err) {
        console.warn('Gyro permission error:', err);
        return false;
      }
    }
    return true; // Granted by default on Android
  }

  /**
   * Recenter forward view to current facing direction
   */
  recenter() {
    if (this.hasGyro) {
      // Offset yaw to current alpha
      this.yawOffset = this.deviceOrientation.alpha;
      this.needsRecenterOnFirstGyro = false;
    } else {
      this.desktopEuler.set(0, 0, 0);
      this.needsRecenterOnFirstGyro = true;
    }
    console.log('VR View Recentered');
  }

  /**
   * Update head orientation every frame
   */
  update() {
    if (this.hasGyro) {
      const alpha = this.deviceOrientation.alpha - this.yawOffset;
      const beta = this.deviceOrientation.beta;
      const gamma = this.deviceOrientation.gamma;
      const orient = THREE.MathUtils.degToRad(this.screenOrientation);

      // Cardboard gyroscope orientation calculation (reuses pre-allocated objects)
      this._euler.set(beta, alpha, -gamma, 'YXZ');
      this.head.quaternion.setFromEuler(this._euler);
      this.head.quaternion.multiply(this._q1);
      this.head.quaternion.multiply(this._q0.setFromAxisAngle(this._zee, -orient));
    } else {
      this.head.quaternion.setFromEuler(this.desktopEuler);
    }
  }

  /**
   * Render side-by-side split screen for JioDive (with optional two-pass barrel distortion)
   */
  render() {
    const width = window.innerWidth;
    const height = window.innerHeight;
    const halfWidth = Math.floor(width / 2);

    if (this.distortionEnabled) {
      this.ensureDistortionPass();
      this.renderer.setScissorTest(false);

      // Pass 1: Render Left & Right eye views into isolated eye textures
      this.renderer.setRenderTarget(this.rtL);
      this.renderer.clear();
      this.renderer.render(this.scene, this.cameraL);

      this.renderer.setRenderTarget(this.rtR);
      this.renderer.clear();
      this.renderer.render(this.scene, this.cameraR);

      // Pass 2: Output barrel-distorted views side-by-side onto screen
      this.renderer.setRenderTarget(null);
      this.renderer.setScissorTest(true);

      this.renderer.setScissor(0, 0, halfWidth, height);
      this.renderer.setViewport(0, 0, halfWidth, height);
      this.postUniforms.tDiffuse.value = this.rtL.texture;
      this.renderer.render(this.postScene, this.postCamera);

      this.renderer.setScissor(halfWidth, 0, width - halfWidth, height);
      this.renderer.setViewport(halfWidth, 0, width - halfWidth, height);
      this.postUniforms.tDiffuse.value = this.rtR.texture;
      this.renderer.render(this.postScene, this.postCamera);

      this.renderer.setScissorTest(false);
      return;
    }

    this.renderer.setScissorTest(true);

    // Left Eye
    this.renderer.setScissor(0, 0, halfWidth, height);
    this.renderer.setViewport(0, 0, halfWidth, height);
    this.renderer.render(this.scene, this.cameraL);

    // Right Eye
    this.renderer.setScissor(halfWidth, 0, width - halfWidth, height);
    this.renderer.setViewport(halfWidth, 0, width - halfWidth, height);
    this.renderer.render(this.scene, this.cameraR);

    this.renderer.setScissorTest(false);
  }
}
