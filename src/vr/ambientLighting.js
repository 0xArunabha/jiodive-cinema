import * as THREE from 'three';

/**
 * Real-time Ambient Screen Lighting
 * Samples the video texture color and projects a realistic theater glow
 * onto surrounding seats, walls, and floor.
 */
export class AmbientLighting {
  constructor(scene, videoElement) {
    this.scene = scene;
    this.video = videoElement;

    // Offscreen canvas for fast video color sampling
    this.sampleCanvas = document.createElement('canvas');
    this.sampleCanvas.width = 16;
    this.sampleCanvas.height = 9;
    this.sampleCtx = this.sampleCanvas.getContext('2d', { willReadFrequently: true });

    // Theater bounce lights
    this.screenLight = new THREE.PointLight(0xffffff, 1.4, 20, 1.5);
    this.screenLight.position.set(0, 3.0, -5.5);
    this.scene.add(this.screenLight);

    // Rich ambient light so theater architecture and seats are visible
    this.ambientBase = new THREE.AmbientLight(0x22263a, 1.2);
    this.scene.add(this.ambientBase);

    this.currentColor = new THREE.Color(0x2a2d42);
    this.targetColor = new THREE.Color(0x2a2d42);
    this.lastSampleTime = 0;
  }

  update(time) {
    // Sample at ~15fps to conserve mobile battery while remaining smooth
    if (time - this.lastSampleTime > 66) {
      this.lastSampleTime = time;
      this.sampleVideoColor();
    }

    // Smoothly lerp light color
    this.currentColor.lerp(this.targetColor, 0.1);
    this.screenLight.color.copy(this.currentColor);
  }

  sampleVideoColor() {
    if (!this.video || this.video.paused || this.video.ended || !this.video.videoWidth) {
      return;
    }

    try {
      this.sampleCtx.drawImage(this.video, 0, 0, 16, 9);
      const imgData = this.sampleCtx.getImageData(0, 0, 16, 9).data;

      let r = 0, g = 0, b = 0;
      const count = imgData.length / 4;

      for (let i = 0; i < imgData.length; i += 4) {
        r += imgData[i];
        g += imgData[i + 1];
        b += imgData[i + 2];
      }

      r = Math.min(255, (r / count) * 1.3);
      g = Math.min(255, (g / count) * 1.3);
      b = Math.min(255, (b / count) * 1.3);

      this.targetColor.setRGB(r / 255, g / 255, b / 255);
      
      // Calculate scene intensity based on screen brightness
      const brightness = (r * 0.299 + g * 0.587 + b * 0.114) / 255;
      this.screenLight.intensity = 0.5 + brightness * 2.0;
    } catch (e) {
      // Cross-origin fallback
    }
  }

  setIntensity(multiplier) {
    this.screenLight.intensity = multiplier;
  }
}
