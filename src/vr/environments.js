import * as THREE from 'three';

/**
 * 3D Virtual Cinema Environments
 * Features an architecturally authentic luxury IMAX/Dolby-style theater:
 * - Tiered riser platforms with amber LED step lighting
 * - Luxury leather VIP recliner seating with armrests & cup holders
 * - Acoustic timber-slat wall paneling with procedural fabric & wood grain
 * - Architectural wall sconces with warm up/down illumination
 * - Subtle volumetric cinema projector beam from the back booth
 * - Front screen stage platform with perimeter edge lighting
 * - Authentic illuminated emergency EXIT sign in the rear
 */
export class Environments {
  constructor(scene) {
    this.scene = scene;
    this.currentEnv = 'cinema';

    this.cinemaGroup = new THREE.Group();
    this.scene.add(this.cinemaGroup);

    // Create procedural textures
    this.carpetTexture = this.createCarpetTexture();
    this.wallTexture = this.createWallTexture();
    this.frontCurtainTexture = this.createFrontCurtainTexture();
    this.ceilingTexture = this.createCeilingTexture();
    this.exitSignTexture = this.createExitSignTexture();

    this.buildCinemaHall();
  }

  // --- Procedural Texture Generators ---

  createCarpetTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 512;
    const ctx = canvas.getContext('2d');

    // Dark charcoal-navy base
    ctx.fillStyle = '#0f111a';
    ctx.fillRect(0, 0, 512, 512);

    // Subtle theater diamond pattern
    ctx.strokeStyle = '#1b1f2e';
    ctx.lineWidth = 3;

    const size = 64;
    for (let x = 0; x < 512; x += size) {
      for (let y = 0; y < 512; y += size) {
        ctx.beginPath();
        ctx.moveTo(x + size / 2, y);
        ctx.lineTo(x + size, y + size / 2);
        ctx.lineTo(x + size / 2, y + size);
        ctx.lineTo(x, y + size / 2);
        ctx.closePath();
        ctx.stroke();

        // Subtle burgundy & gold center flecks
        ctx.fillStyle = (x + y) % 128 === 0 ? '#38141e' : '#141826';
        ctx.fillRect(x + size / 2 - 4, y + size / 2 - 4, 8, 8);
      }
    }

    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  createWallTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 512;
    const ctx = canvas.getContext('2d');

    // Acoustic dark felt backdrop
    ctx.fillStyle = '#0a0b12';
    ctx.fillRect(0, 0, 512, 512);

    // Vertical acoustic timber slats
    const slatWidth = 16;
    const gap = 16;
    for (let x = 0; x < 512; x += slatWidth + gap) {
      // Wood gradient
      const grad = ctx.createLinearGradient(x, 0, x + slatWidth, 0);
      grad.addColorStop(0, '#1c1512');
      grad.addColorStop(0.3, '#2a1f18');
      grad.addColorStop(0.7, '#241a14');
      grad.addColorStop(1, '#150f0c');

      ctx.fillStyle = grad;
      ctx.fillRect(x, 0, slatWidth, 512);

      // Subtle warm highlight on the left edge
      ctx.fillStyle = 'rgba(255, 200, 150, 0.08)';
      ctx.fillRect(x, 0, 2, 512);

      // Shadow in the gap
      ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
      ctx.fillRect(x + slatWidth, 0, gap, 512);
    }

    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  createFrontCurtainTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 512;
    const ctx = canvas.getContext('2d');

    ctx.fillStyle = '#06070a';
    ctx.fillRect(0, 0, 512, 512);

    // Soft vertical velvet folds
    const foldWidth = 32;
    for (let x = 0; x < 512; x += foldWidth) {
      const grad = ctx.createLinearGradient(x, 0, x + foldWidth, 0);
      grad.addColorStop(0, '#06070a');
      grad.addColorStop(0.5, '#12141c');
      grad.addColorStop(1, '#06070a');
      ctx.fillStyle = grad;
      ctx.fillRect(x, 0, foldWidth, 512);
    }

    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  createCeilingTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 512;
    const ctx = canvas.getContext('2d');

    ctx.fillStyle = '#08090f';
    ctx.fillRect(0, 0, 512, 512);

    // Grid coffered ceiling tiles
    ctx.strokeStyle = '#12141f';
    ctx.lineWidth = 8;
    ctx.strokeRect(4, 4, 504, 504);

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.02)';
    ctx.lineWidth = 2;
    ctx.strokeRect(16, 16, 480, 480);

    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  createExitSignTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 128;
    const ctx = canvas.getContext('2d');

    // Green emergency sign background
    ctx.fillStyle = '#065f46';
    ctx.fillRect(0, 0, 256, 128);

    // Glowing border
    ctx.strokeStyle = '#34d399';
    ctx.lineWidth = 6;
    ctx.strokeRect(6, 6, 244, 116);

    // White bold text
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 52px -apple-system, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('EXIT ➔', 128, 64);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  // --- Theater Construction ---

  buildCinemaHall() {
    // 1. Tiered Floor Risers & Steps
    this.buildTieredFloor();

    // 2. Cinema Walls (Side, Front & Back)
    this.buildWalls();

    // 3. Ceiling with Recessed Architectural Lighting
    this.buildCeiling();

    // 4. Front Screen Stage Platform
    this.buildFrontStage();

    // 5. Luxury VIP Recliner Seats
    this.buildTieredSeats();

    // 6. Wall Sconces & Architectural Lighting
    this.buildWallSconces();

    // 7. Projector Booth Window & Volumetric Beam
    this.buildProjectorBooth();

    // 8. Exit Sign
    this.buildExitSign();
  }

  buildTieredFloor() {
    const carpetMat = new THREE.MeshStandardMaterial({
      map: this.carpetTexture,
      roughness: 0.85,
      metalness: 0.05
    });
    this.carpetTexture.repeat.set(12, 18);

    // Base Main Floor (underneath everything)
    const baseFloorGeom = new THREE.PlaneGeometry(22, 26);
    const baseFloor = new THREE.Mesh(baseFloorGeom, carpetMat);
    baseFloor.rotation.x = -Math.PI / 2;
    baseFloor.position.set(0, 0, 2);
    this.cinemaGroup.add(baseFloor);

    // Tiered Platforms for Seating
    const stepLightMat = new THREE.MeshBasicMaterial({ color: 0xffa333 });
    const tierRows = 6;

    for (let r = 0; r < tierRows; r++) {
      const zPos = -1.2 + r * 1.8;
      const height = 0.35 + r * 0.35;
      const depth = 1.8;
      const width = 18;

      const riserGeom = new THREE.BoxGeometry(width, height, depth);
      const riserMesh = new THREE.Mesh(riserGeom, carpetMat);
      // Position riser so its top surface is at Y = height
      riserMesh.position.set(0, height / 2, zPos);
      this.cinemaGroup.add(riserMesh);

      // Amber LED Strip along front top edge of the step
      const stepLightGeom = new THREE.BoxGeometry(width, 0.025, 0.04);
      const stepLight = new THREE.Mesh(stepLightGeom, stepLightMat);
      stepLight.position.set(0, height + 0.012, zPos - depth / 2 + 0.02);
      this.cinemaGroup.add(stepLight);
    }
  }

  buildWalls() {
    const wallMat = new THREE.MeshStandardMaterial({
      map: this.wallTexture,
      roughness: 0.75,
      metalness: 0.1
    });
    this.wallTexture.repeat.set(18, 1);

    // Left Wall
    const leftWallGeom = new THREE.PlaneGeometry(26, 9.5);
    const leftWall = new THREE.Mesh(leftWallGeom, wallMat);
    leftWall.rotation.y = Math.PI / 2;
    leftWall.position.set(-10, 4.75, 2);
    this.cinemaGroup.add(leftWall);

    // Right Wall
    const rightWallGeom = new THREE.PlaneGeometry(26, 9.5);
    const rightWall = new THREE.Mesh(rightWallGeom, wallMat);
    rightWall.rotation.y = -Math.PI / 2;
    rightWall.position.set(10, 4.75, 2);
    this.cinemaGroup.add(rightWall);

    // Back Wall
    const backWallMat = new THREE.MeshStandardMaterial({
      color: 0x0c0e18,
      roughness: 0.85
    });
    const backWallGeom = new THREE.PlaneGeometry(20, 9.5);
    const backWall = new THREE.Mesh(backWallGeom, backWallMat);
    backWall.position.set(0, 4.75, 14);
    backWall.rotation.y = Math.PI;
    this.cinemaGroup.add(backWall);

    // Front Wall (Behind Screen with Velvet Acoustic Drapes)
    const frontWallMat = new THREE.MeshStandardMaterial({
      map: this.frontCurtainTexture,
      roughness: 0.95,
      metalness: 0.0
    });
    this.frontCurtainTexture.repeat.set(24, 1);
    const frontWallGeom = new THREE.PlaneGeometry(20, 9.5);
    const frontWall = new THREE.Mesh(frontWallGeom, frontWallMat);
    frontWall.position.set(0, 4.75, -7.8);
    this.cinemaGroup.add(frontWall);
  }

  buildCeiling() {
    const ceilingMat = new THREE.MeshStandardMaterial({
      map: this.ceilingTexture,
      roughness: 0.8,
      metalness: 0.15
    });
    this.ceilingTexture.repeat.set(8, 12);

    const ceilingGeom = new THREE.PlaneGeometry(20, 26);
    const ceiling = new THREE.Mesh(ceilingGeom, ceilingMat);
    ceiling.rotation.x = Math.PI / 2;
    ceiling.position.set(0, 9.5, 2);
    this.cinemaGroup.add(ceiling);

    // Recessed Dark Architectural Beams
    const beamMat = new THREE.MeshStandardMaterial({ color: 0x06070a, roughness: 0.5 });
    for (let z = -6; z <= 12; z += 4.5) {
      const beamGeom = new THREE.BoxGeometry(20, 0.35, 0.4);
      const beam = new THREE.Mesh(beamGeom, beamMat);
      beam.position.set(0, 9.35, z);
      this.cinemaGroup.add(beam);
    }
  }

  buildFrontStage() {
    // Curved/Beveled Low Stage below screen
    const stageMat = new THREE.MeshStandardMaterial({
      color: 0x090a10,
      roughness: 0.9
    });

    const stageGeom = new THREE.BoxGeometry(14, 0.4, 3.0);
    const stage = new THREE.Mesh(stageGeom, stageMat);
    stage.position.set(0, 0.2, -6.6);
    this.cinemaGroup.add(stage);

    // Front Stage Edge Amber Glow Strip
    const stageEdgeMat = new THREE.MeshBasicMaterial({ color: 0xffaa44 });
    const stageEdgeGeom = new THREE.BoxGeometry(14, 0.03, 0.06);
    const stageEdge = new THREE.Mesh(stageEdgeGeom, stageEdgeMat);
    stageEdge.position.set(0, 0.41, -5.1);
    this.cinemaGroup.add(stageEdge);
  }

  buildTieredSeats() {
    // Luxury VIP Recliner Chair Components
    const seatBottomGeom = new THREE.BoxGeometry(0.64, 0.16, 0.6);
    const seatBackGeom = new THREE.BoxGeometry(0.64, 0.72, 0.16);
    const headrestGeom = new THREE.BoxGeometry(0.48, 0.24, 0.14);
    const armrestGeom = new THREE.BoxGeometry(0.12, 0.45, 0.55);
    const cupHolderGeom = new THREE.CylinderGeometry(0.045, 0.04, 0.08, 16);

    // VIP Burgundy Leather Material with subtle specular sheen
    const seatMat = new THREE.MeshStandardMaterial({
      color: 0x5a141e,
      roughness: 0.55,
      metalness: 0.1
    });

    // Dark Matte Charcoal Armrests
    const armrestMat = new THREE.MeshStandardMaterial({
      color: 0x14161e,
      roughness: 0.6,
      metalness: 0.2
    });

    // Silver Brushed Chrome for Cup Holders
    const metalMat = new THREE.MeshStandardMaterial({
      color: 0x94a3b8,
      roughness: 0.3,
      metalness: 0.8
    });

    const rows = 6;
    const seatsPerRow = 8; // 4 Left Block, Center Aisle, 4 Right Block
    const totalSeats = rows * seatsPerRow;

    const bottomMesh = new THREE.InstancedMesh(seatBottomGeom, seatMat, totalSeats);
    const backMesh = new THREE.InstancedMesh(seatBackGeom, seatMat, totalSeats);
    const headrestMesh = new THREE.InstancedMesh(headrestGeom, seatMat, totalSeats);
    const armrestLMesh = new THREE.InstancedMesh(armrestGeom, armrestMat, totalSeats);
    const armrestRMesh = new THREE.InstancedMesh(armrestGeom, armrestMat, totalSeats);
    const cupHolderMesh = new THREE.InstancedMesh(cupHolderGeom, metalMat, totalSeats * 2);

    const dummy = new THREE.Object3D();
    let seatIndex = 0;
    let cupIndex = 0;

    for (let r = 0; r < rows; r++) {
      const zPos = -1.2 + r * 1.8;
      const tierHeight = 0.35 + r * 0.35;

      for (let s = 0; s < seatsPerRow; s++) {
        // Skip user's immediate seat in Row 1 (center left seat s=3) so view is completely open
        if (r === 1 && (s === 3 || s === 4)) {
          continue;
        }

        // Center aisle gap (1.6m wide) between seats 3 and 4
        const isLeftBlock = s < 4;
        const xOffset = isLeftBlock ? (s - 3.5) * 0.82 - 0.45 : (s - 4) * 0.82 + 0.85;

        // 1. Seat Bottom Cushion
        dummy.position.set(xOffset, tierHeight + 0.32, zPos + 0.05);
        dummy.rotation.set(0, 0, 0);
        dummy.updateMatrix();
        bottomMesh.setMatrixAt(seatIndex, dummy.matrix);

        // 2. Reclined Backrest
        dummy.position.set(xOffset, tierHeight + 0.68, zPos + 0.32);
        dummy.rotation.set(-0.16, 0, 0);
        dummy.updateMatrix();
        backMesh.setMatrixAt(seatIndex, dummy.matrix);

        // 3. Ergonomic Headrest
        dummy.position.set(xOffset, tierHeight + 1.05, zPos + 0.37);
        dummy.rotation.set(-0.12, 0, 0);
        dummy.updateMatrix();
        headrestMesh.setMatrixAt(seatIndex, dummy.matrix);

        // 4. Left Armrest
        dummy.position.set(xOffset - 0.34, tierHeight + 0.42, zPos + 0.08);
        dummy.rotation.set(0, 0, 0);
        dummy.updateMatrix();
        armrestLMesh.setMatrixAt(seatIndex, dummy.matrix);

        // 5. Right Armrest
        dummy.position.set(xOffset + 0.34, tierHeight + 0.42, zPos + 0.08);
        dummy.rotation.set(0, 0, 0);
        dummy.updateMatrix();
        armrestRMesh.setMatrixAt(seatIndex, dummy.matrix);

        // 6. Silver Cup Holders (on front of each armrest)
        dummy.position.set(xOffset - 0.34, tierHeight + 0.64, zPos - 0.14);
        dummy.rotation.set(0, 0, 0);
        dummy.updateMatrix();
        cupHolderMesh.setMatrixAt(cupIndex++, dummy.matrix);

        dummy.position.set(xOffset + 0.34, tierHeight + 0.64, zPos - 0.14);
        dummy.rotation.set(0, 0, 0);
        dummy.updateMatrix();
        cupHolderMesh.setMatrixAt(cupIndex++, dummy.matrix);

        seatIndex++;
      }
    }

    bottomMesh.count = seatIndex;
    backMesh.count = seatIndex;
    headrestMesh.count = seatIndex;
    armrestLMesh.count = seatIndex;
    armrestRMesh.count = seatIndex;
    cupHolderMesh.count = cupIndex;

    bottomMesh.instanceMatrix.needsUpdate = true;
    backMesh.instanceMatrix.needsUpdate = true;
    headrestMesh.instanceMatrix.needsUpdate = true;
    armrestLMesh.instanceMatrix.needsUpdate = true;
    armrestRMesh.instanceMatrix.needsUpdate = true;
    cupHolderMesh.instanceMatrix.needsUpdate = true;

    this.cinemaGroup.add(bottomMesh);
    this.cinemaGroup.add(backMesh);
    this.cinemaGroup.add(headrestMesh);
    this.cinemaGroup.add(armrestLMesh);
    this.cinemaGroup.add(armrestRMesh);
    this.cinemaGroup.add(cupHolderMesh);
  }

  buildWallSconces() {
    const sconcePositions = [
      { x: -9.85, z: -3 },
      { x: -9.85, z: 2 },
      { x: -9.85, z: 7 },
      { x: 9.85, z: -3 },
      { x: 9.85, z: 2 },
      { x: 9.85, z: 7 }
    ];

    const fixtureMat = new THREE.MeshStandardMaterial({ color: 0x181a24, roughness: 0.4, metalness: 0.8 });
    const lensMat = new THREE.MeshBasicMaterial({ color: 0xffb355 });

    sconcePositions.forEach((pos) => {
      // Modern vertical sconce fixture
      const fixtureGeom = new THREE.BoxGeometry(0.12, 0.7, 0.18);
      const fixture = new THREE.Mesh(fixtureGeom, fixtureMat);
      fixture.position.set(pos.x, 4.2, pos.z);
      this.cinemaGroup.add(fixture);

      // Top and bottom glowing light lenses
      const lensTop = new THREE.Mesh(new THREE.BoxGeometry(0.13, 0.08, 0.16), lensMat);
      lensTop.position.set(pos.x, 4.56, pos.z);
      this.cinemaGroup.add(lensTop);

      const lensBottom = new THREE.Mesh(new THREE.BoxGeometry(0.13, 0.08, 0.16), lensMat);
      lensBottom.position.set(pos.x, 3.84, pos.z);
      this.cinemaGroup.add(lensBottom);

      // Subtle warm local point light
      const sconceLight = new THREE.PointLight(0xff9944, 0.65, 7.5, 2);
      sconceLight.position.set(pos.x * 0.95, 4.2, pos.z);
      this.cinemaGroup.add(sconceLight);
    });
  }

  buildProjectorBooth() {
    // Projector window on rear wall
    const windowFrameMat = new THREE.MeshStandardMaterial({ color: 0x11131c, roughness: 0.5 });
    const frame = new THREE.Mesh(new THREE.BoxGeometry(2.4, 1.2, 0.2), windowFrameMat);
    frame.position.set(0, 6.2, 13.9);
    this.cinemaGroup.add(frame);

    // Tinted glowing projector glass aperture
    const glassMat = new THREE.MeshBasicMaterial({ color: 0x7dd3fc, transparent: true, opacity: 0.85 });
    const glass = new THREE.Mesh(new THREE.PlaneGeometry(1.8, 0.8), glassMat);
    glass.position.set(0, 6.2, 13.88);
    glass.rotation.y = Math.PI;
    this.cinemaGroup.add(glass);

    // Subtle atmospheric projector light beam cutting across theater
    const beamGeom = new THREE.ConeGeometry(3.6, 20, 32, 1, true);
    const beamMat = new THREE.MeshBasicMaterial({
      color: 0x93c5fd,
      transparent: true,
      opacity: 0.022,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      side: THREE.DoubleSide
    });
    const beam = new THREE.Mesh(beamGeom, beamMat);
    beam.position.set(0, 4.0, 3.8);
    beam.rotation.x = Math.PI / 2 + 0.18;
    this.cinemaGroup.add(beam);
  }

  buildExitSign() {
    const exitSignMat = new THREE.MeshBasicMaterial({
      map: this.exitSignTexture,
      transparent: true
    });
    const signGeom = new THREE.PlaneGeometry(0.9, 0.45);
    const signMesh = new THREE.Mesh(signGeom, exitSignMat);
    // Position above back-right exit door
    signMesh.position.set(8.2, 3.2, 13.9);
    signMesh.rotation.y = Math.PI;
    this.cinemaGroup.add(signMesh);

    // Subtle green emergency glow
    const exitLight = new THREE.PointLight(0x10b981, 0.4, 4);
    exitLight.position.set(8.2, 3.2, 13.7);
    this.cinemaGroup.add(exitLight);
  }

  setEnvironment(envName, cinemaScreen, gazeUI) {
    this.currentEnv = envName;

    if (envName === 'void') {
      this.cinemaGroup.visible = false;
      cinemaScreen.setTilt(0);
      if (gazeUI && gazeUI.setTilt) gazeUI.setTilt(0);
    } else if (envName === 'bed') {
      this.cinemaGroup.visible = false;
      // Reorient screen and gaze menu 75 degrees up towards ceiling for comfortable flat lying down
      cinemaScreen.setTilt(75);
      if (gazeUI && gazeUI.setTilt) gazeUI.setTilt(75);
    } else {
      // Cinema Hall
      this.cinemaGroup.visible = true;
      cinemaScreen.setTilt(0);
      if (gazeUI && gazeUI.setTilt) gazeUI.setTilt(0);
    }
  }
}
