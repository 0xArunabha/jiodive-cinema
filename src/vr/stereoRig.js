import * as THREE from 'three';

/**
 * JioDive Dual-Eye Stereo Camera Rig
 * Handles stereo separation (IPD), device orientation (gyroscope),
 * physical click events, and viewport rendering.
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

    // Interpupillary Distance (IPD) - default 64mm (0.064 meters)
    this.ipd = 0.064;

    // Dual Cameras
    const aspect = (window.innerWidth / 2) / window.innerHeight;
    const fov = 75; // Standard Cardboard / JioDive lens FOV
    const near = 0.1;
    const far = 1000;

    this.cameraL = new THREE.PerspectiveCamera(fov, aspect, near, far);
    this.cameraR = new THREE.PerspectiveCamera(fov, aspect, near, far);

    // Layer 0 is default (shared objects)
    // Layer 1 is Left-Eye only
    // Layer 2 is Right-Eye only
    this.cameraL.layers.enable(0);
    this.cameraL.layers.enable(1);
    this.cameraL.layers.disable(2);

    this.cameraR.layers.enable(0);
    this.cameraR.layers.disable(1);
    this.cameraR.layers.enable(2);

    this.updateCameras();
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

    const aspect = (width / 2) / height;
    this.cameraL.aspect = aspect;
    this.cameraL.updateProjectionMatrix();

    this.cameraR.aspect = aspect;
    this.cameraR.updateProjectionMatrix();
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

      // Cardboard gyroscope orientation calculation
      const zee = new THREE.Vector3(0, 0, 1);
      const euler = new THREE.Euler();
      const q0 = new THREE.Quaternion();
      const q1 = new THREE.Quaternion(-Math.sqrt(0.5), 0, 0, Math.sqrt(0.5)); // - PI/2 around X

      euler.set(beta, alpha, -gamma, 'YXZ');
      this.head.quaternion.setFromEuler(euler);
      this.head.quaternion.multiply(q1);
      this.head.quaternion.multiply(q0.setFromAxisAngle(zee, -orient));
    } else {
      this.head.quaternion.setFromEuler(this.desktopEuler);
    }
  }

  /**
   * Render side-by-side split screen for JioDive
   */
  render() {
    const width = window.innerWidth;
    const height = window.innerHeight;
    const halfWidth = Math.floor(width / 2);

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
