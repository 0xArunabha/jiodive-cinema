package com.jiodive.cinema.vr

import com.jiodive.cinema.model.ScreenPreset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates an architectural 3D Dolby/IMAX-inspired cinema auditorium mesh.
 *
 * Vertex layout (`FLOATS_PER_VERTEX = 10`):
 * `[px, py, pz, nx, ny, nz, r, g, b, emissive]`
 *
 * Design highlights:
 * - Curved proscenium masking frame & subtle rear bias cove hugging the selected [ScreenPreset].
 * - Sculpted pleated velvet proscenium curtains framing the left and right periphery of the screen.
 * - Splayed acoustic auditorium side walls tapering smoothly from rear rows toward the stage,
 *   built with protruding charcoal pilasters, warm walnut-charcoal acoustic bays, and slim
 *   recessed sconce diffusers (hooded top/bottom so they illuminate wall bays without neon glare).
 * - Multi-stepped proscenium stage apron with subtle under-lip footlight wash.
 * - 5 sloped stadium seating tiers, center aisle runner with subtle step markers, and 48 plush
 *   contoured seats with headrests and armrests.
 * - Overhead acoustic ceiling ribs with indirect soffit light coves and rear projection booth.
 */
object CinemaHallBuilder {
    const val FLOATS_PER_VERTEX = 10

    fun screenCenterYFor(preset: ScreenPreset, screenAspect: Float): Float {
        val aspect = screenAspect.coerceIn(1.2f, 2.4f)
        val screenHeight = preset.widthMeters / aspect
        return max(0.35f, screenHeight * 0.5f - 2.15f)
    }

    fun buildHallVertices(preset: ScreenPreset, screenAspect: Float): FloatArray {
        val out = ArrayList<Float>(72000)

        val aspect = screenAspect.coerceIn(1.2f, 2.4f)
        val screenWidth = preset.widthMeters
        val screenHeight = screenWidth / aspect
        val screenCenterY = screenCenterYFor(preset, aspect)
        val screenBottomY = screenCenterY - screenHeight * 0.5f
        val screenTopY = screenCenterY + screenHeight * 0.5f

        val curveRad = Math.toRadians(preset.curveDegrees.toDouble()).toFloat().coerceAtLeast(0.05f)
        val curveRadius = screenWidth / curveRad
        val halfAngle = curveRad * 0.5f
        val screenCenterZ = -preset.distanceMeters
        val arcOriginZ = screenCenterZ + curveRadius

        val edgeX = curveRadius * sin(halfAngle)
        val edgeZ = arcOriginZ - curveRadius * cos(halfAngle)

        val stageTopY = (screenBottomY - 0.22f).coerceAtMost(-1.95f)
        val floorBaseY = stageTopY - 0.42f
        val prosceniumBackZ = screenCenterZ - 0.92f
        val backZ = 4.6f
        val ceilingY = max(4.35f, screenTopY + 0.92f)

        // Tapered auditorium side walls: hug the outer curtain bays up front and widen toward seats.
        val frontWallHalfW = (edgeX + 1.25f).coerceIn(5.85f, 7.65f)
        val rearWallHalfW = (frontWallHalfW + 0.85f).coerceAtMost(8.25f)
        val sideWallStartZ = edgeZ - 0.45f

        fun wallHalfWAt(z: Float): Float {
            val t = ((z - sideWallStartZ) / max(backZ - sideWallStartZ, 0.5f)).coerceIn(0f, 1f)
            return frontWallHalfW + (rearWallHalfW - frontWallHalfW) * t
        }

        fun emitVertex(
            x: Float,
            y: Float,
            z: Float,
            nx: Float,
            ny: Float,
            nz: Float,
            r: Float,
            g: Float,
            b: Float,
            emissive: Float,
        ) {
            out.add(x)
            out.add(y)
            out.add(z)
            out.add(nx)
            out.add(ny)
            out.add(nz)
            out.add(r)
            out.add(g)
            out.add(b)
            out.add(emissive)
        }

        fun addQuad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            r: Float, g: Float, b: Float,
            emissive: Float = 0f,
        ) {
            val ax = x1 - x0
            val ay = y1 - y0
            val az = z1 - z0
            val bx = x2 - x0
            val by = y2 - y0
            val bz = z2 - z0
            var nx = ay * bz - az * by
            var ny = az * bx - ax * bz
            var nz = ax * by - ay * bx
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-6f) {
                nx /= len
                ny /= len
                nz /= len
            } else {
                nx = 0f
                ny = 1f
                nz = 0f
            }
            emitVertex(x0, y0, z0, nx, ny, nz, r, g, b, emissive)
            emitVertex(x1, y1, z1, nx, ny, nz, r, g, b, emissive)
            emitVertex(x2, y2, z2, nx, ny, nz, r, g, b, emissive)
            emitVertex(x0, y0, z0, nx, ny, nz, r, g, b, emissive)
            emitVertex(x2, y2, z2, nx, ny, nz, r, g, b, emissive)
            emitVertex(x3, y3, z3, nx, ny, nz, r, g, b, emissive)
        }

        fun addBox(
            minX: Float, minY: Float, minZ: Float,
            maxX: Float, maxY: Float, maxZ: Float,
            r: Float, g: Float, b: Float,
            emissive: Float = 0f,
            skipBottom: Boolean = false,
        ) {
            // Front (+Z)
            addQuad(
                minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                r, g, b, emissive,
            )
            // Back (-Z)
            addQuad(
                maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ,
                r, g, b, emissive,
            )
            // Top (+Y)
            addQuad(
                minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
                r * 1.08f, g * 1.08f, b * 1.08f, emissive,
            )
            if (!skipBottom) {
                addQuad(
                    minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                    r * 0.8f, g * 0.8f, b * 0.8f, emissive * 0.7f,
                )
            }
            // Left (-X)
            addQuad(
                minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
                r * 0.92f, g * 0.92f, b * 0.92f, emissive,
            )
            // Right (+X)
            addQuad(
                maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ,
                r * 0.92f, g * 0.92f, b * 0.92f, emissive,
            )
        }

        // 1. Curved Matte Screen Masking Frame & Subtle Rear Bias Halo
        val bezelSegments = 24
        val bezelPad = 0.24f
        val frameAngle = halfAngle * 1.035f
        for (i in 0 until bezelSegments) {
            val a0 = -frameAngle + (2f * frameAngle * i / bezelSegments)
            val a1 = -frameAngle + (2f * frameAngle * (i + 1) / bezelSegments)
            val x0 = curveRadius * sin(a0)
            val z0 = arcOriginZ - curveRadius * cos(a0) - 0.045f
            val x1 = curveRadius * sin(a1)
            val z1 = arcOriginZ - curveRadius * cos(a1) - 0.045f

            // Matte obsidian masking surround behind curved screen edges
            addQuad(
                x0, screenBottomY - bezelPad, z0,
                x1, screenBottomY - bezelPad, z1,
                x1, screenTopY + bezelPad, z1,
                x0, screenTopY + bezelPad, z0,
                0.04f, 0.05f, 0.06f, 0.01f,
            )
            // Subtle cool slate bias glow tucked right behind top/bottom bezel rim
            addQuad(
                x0, screenTopY + bezelPad, z0 - 0.02f,
                x1, screenTopY + bezelPad, z1 - 0.02f,
                x1, screenTopY + bezelPad + 0.06f, z1 - 0.08f,
                x0, screenTopY + bezelPad + 0.06f, z0 - 0.08f,
                0.14f, 0.34f, 0.36f, 0.08f,
            )
            addQuad(
                x0, screenBottomY - bezelPad - 0.05f, z0 - 0.08f,
                x1, screenBottomY - bezelPad - 0.05f, z1 - 0.08f,
                x1, screenBottomY - bezelPad, z1 - 0.02f,
                x0, screenBottomY - bezelPad, z0 - 0.02f,
                0.14f, 0.34f, 0.36f, 0.07f,
            )
        }

        // Deep charcoal proscenium back wall & subtle acoustic slats beneath screen
        addQuad(
            -frontWallHalfW, floorBaseY, prosceniumBackZ,
            frontWallHalfW, floorBaseY, prosceniumBackZ,
            frontWallHalfW, ceilingY, prosceniumBackZ,
            -frontWallHalfW, ceilingY, prosceniumBackZ,
            0.07f, 0.08f, 0.10f, 0f,
        )

        // Vertical timber acoustic ribs flanking the lower stage wall (catch screen bounce light)
        val stageRibCount = 9
        for (i in 0 until stageRibCount) {
            val rx = -edgeX * 0.85f + (2f * edgeX * 0.85f * i / (stageRibCount - 1))
            addBox(
                rx - 0.08f, floorBaseY, prosceniumBackZ + 0.02f,
                rx + 0.08f, stageTopY - 0.04f, prosceniumBackZ + 0.18f,
                0.18f, 0.14f, 0.12f,
                skipBottom = true,
            )
        }

        // 2. Sculpted Pleated Proscenium Velvet Curtains (flanking left & right of screen)
        val curtainHeightTop = ceilingY - 0.08f
        val curtainHeightBottom = stageTopY + 0.02f
        val curtainFolds = 10
        val curtainSpanX = (frontWallHalfW - 0.18f) - (edgeX - 0.04f)
        if (curtainSpanX > 0.25f) {
            for (side in intArrayOf(-1, 1)) {
                val innerEdgeX = side * (edgeX - 0.04f)
                val outerEdgeX = side * (frontWallHalfW - 0.18f)
                val innerZ = edgeZ - 0.10f
                val outerZ = edgeZ - 0.34f
                for (f in 0 until curtainFolds) {
                    val t0 = f.toFloat() / curtainFolds
                    val tMid = (f + 0.5f) / curtainFolds
                    val t1 = (f + 1f) / curtainFolds

                    val x0 = innerEdgeX + (outerEdgeX - innerEdgeX) * t0
                    val xMid = innerEdgeX + (outerEdgeX - innerEdgeX) * tMid
                    val x1 = innerEdgeX + (outerEdgeX - innerEdgeX) * t1

                    val baseZ0 = innerZ + (outerZ - innerZ) * t0
                    val baseZMid = innerZ + (outerZ - innerZ) * tMid
                    val baseZ1 = innerZ + (outerZ - innerZ) * t1

                    val pleatDepth = 0.20f
                    val z0 = baseZ0
                    val zMid = baseZMid + pleatDepth
                    val z1 = baseZ1

                    // Alternate two faceted faces per pleat so screen bounce highlights each drape fold
                    if (side < 0) {
                        addQuad(
                            x1, curtainHeightBottom, z1,
                            xMid, curtainHeightBottom, zMid,
                            xMid, curtainHeightTop, zMid,
                            x1, curtainHeightTop, z1,
                            0.24f, 0.08f, 0.11f, 0f,
                        )
                        addQuad(
                            xMid, curtainHeightBottom, zMid,
                            x0, curtainHeightBottom, z0,
                            x0, curtainHeightTop, z0,
                            xMid, curtainHeightTop, zMid,
                            0.20f, 0.07f, 0.09f, 0f,
                        )
                    } else {
                        addQuad(
                            x0, curtainHeightBottom, z0,
                            xMid, curtainHeightBottom, zMid,
                            xMid, curtainHeightTop, zMid,
                            x0, curtainHeightTop, z0,
                            0.24f, 0.08f, 0.11f, 0f,
                        )
                        addQuad(
                            xMid, curtainHeightBottom, zMid,
                            x1, curtainHeightBottom, z1,
                            x1, curtainHeightTop, z1,
                            xMid, curtainHeightTop, zMid,
                            0.20f, 0.07f, 0.09f, 0f,
                        )
                    }
                }
            }
        }

        // Upper proscenium valance fascia & muted trim above the screen
        val fasciaBottom = screenTopY + bezelPad + 0.12f
        if (ceilingY - fasciaBottom > 0.20f) {
            addBox(
                -frontWallHalfW, fasciaBottom, edgeZ - 0.18f,
                frontWallHalfW, ceilingY, edgeZ + 0.16f,
                0.08f, 0.08f, 0.10f,
            )
        }

        // 3. Curved Stepped Proscenium Stage Apron & Footlight Trim
        val stageDepth = 1.65f
        val stageFrontZ = screenCenterZ + stageDepth * 0.5f
        addBox(
            -frontWallHalfW + 0.35f, floorBaseY, prosceniumBackZ,
            frontWallHalfW - 0.35f, stageTopY, stageFrontZ,
            0.13f, 0.11f, 0.10f, skipBottom = true,
        )
        // Lower stadium step landing extending forward from stage
        addBox(
            -frontWallHalfW + 0.75f, floorBaseY, stageFrontZ,
            frontWallHalfW - 0.75f, floorBaseY + 0.20f, stageFrontZ + 0.55f,
            0.12f, 0.12f, 0.14f, skipBottom = true,
        )
        // Quiet recessed stage overhang edge glow
        addBox(
            -edgeX * 0.88f, stageTopY - 0.04f, stageFrontZ - 0.01f,
            edgeX * 0.88f, stageTopY - 0.01f, stageFrontZ + 0.025f,
            0.34f, 0.52f, 0.54f, emissive = 0.14f, skipBottom = true,
        )

        // 4. Auditorium Carpeted Floor & Central Runner with Gold/Teal Trim
        addQuad(
            -rearWallHalfW, floorBaseY, backZ,
            rearWallHalfW, floorBaseY, backZ,
            frontWallHalfW, floorBaseY, prosceniumBackZ,
            -frontWallHalfW, floorBaseY, prosceniumBackZ,
            0.10f, 0.12f, 0.15f, 0f,
        )
        // Center aisle carpet runner
        addQuad(
            -0.88f, floorBaseY + 0.008f, backZ,
            0.88f, floorBaseY + 0.008f, backZ,
            0.88f, floorBaseY + 0.008f, stageFrontZ + 0.55f,
            -0.88f, floorBaseY + 0.008f, stageFrontZ + 0.55f,
            0.16f, 0.18f, 0.22f, 0f,
        )

        // 5. Tapered Side Acoustic Walls, Architectural Pilasters & Hooded Wall Sconces
        val sideSlices = 14
        for (s in 0 until sideSlices) {
            val z0 = sideWallStartZ + (backZ - sideWallStartZ) * (s.toFloat() / sideSlices)
            val z1 = sideWallStartZ + (backZ - sideWallStartZ) * ((s + 1f) / sideSlices)
            val w0 = wallHalfWAt(z0)
            val w1 = wallHalfWAt(z1)

            // Left tapered outer wall (inward normal +X/+Z)
            addQuad(
                -w0, floorBaseY, z0,
                -w1, floorBaseY, z1,
                -w1, ceilingY, z1,
                -w0, ceilingY, z0,
                0.14f, 0.14f, 0.17f, 0f,
            )
            // Right tapered outer wall (inward normal -X/+Z)
            addQuad(
                w1, floorBaseY, z1,
                w0, floorBaseY, z0,
                w0, ceilingY, z0,
                w1, ceilingY, z1,
                0.14f, 0.14f, 0.17f, 0f,
            )

            // Lower dark wainscoting dado rail along both side walls
            addQuad(
                -w0 + 0.04f, floorBaseY, z0,
                -w1 + 0.04f, floorBaseY, z1,
                -w1 + 0.04f, floorBaseY + 1.05f, z1,
                -w0 + 0.04f, floorBaseY + 1.05f, z0,
                0.10f, 0.11f, 0.13f, 0f,
            )
            addQuad(
                w1 - 0.04f, floorBaseY, z1,
                w0 - 0.04f, floorBaseY, z0,
                w0 - 0.04f, floorBaseY + 1.05f, z0,
                w1 - 0.04f, floorBaseY + 1.05f, z1,
                0.10f, 0.11f, 0.13f, 0f,
            )
        }

        // 5 Sculpted Acoustic Pilaster Columns + Recessed Hooded Sconces per side
        val bayCount = 5
        for (i in 0 until bayCount) {
            val bayZ = sideWallStartZ + 0.95f + (backZ - sideWallStartZ - 1.5f) * (i.toFloat() / max(bayCount - 1, 1))
            val wallX = wallHalfWAt(bayZ)
            val pilasterW = 0.28f
            val pilasterDepth = 0.42f

            // Left pilaster + recessed acoustic wood baffle + slim concealed sconce slit
            addBox(
                -wallX, floorBaseY, bayZ - pilasterDepth * 0.5f,
                -wallX + pilasterW, ceilingY, bayZ + pilasterDepth * 0.5f,
                0.12f, 0.13f, 0.16f, skipBottom = true,
            )
            // Warm acoustic walnut bay panel right beside pilaster
            addBox(
                -wallX, floorBaseY + 1.08f, bayZ + pilasterDepth * 0.5f + 0.06f,
                -wallX + 0.09f, ceilingY - 0.35f, bayZ + pilasterDepth * 0.5f + 1.02f,
                0.22f, 0.17f, 0.14f, skipBottom = true,
            )
            // Right pilaster + warm acoustic panel
            addBox(
                wallX - pilasterW, floorBaseY, bayZ - pilasterDepth * 0.5f,
                wallX, ceilingY, bayZ + pilasterDepth * 0.5f,
                0.12f, 0.13f, 0.16f, skipBottom = true,
            )
            addBox(
                wallX - 0.09f, floorBaseY + 1.08f, bayZ + pilasterDepth * 0.5f + 0.06f,
                wallX, ceilingY - 0.35f, bayZ + pilasterDepth * 0.5f + 1.02f,
                0.22f, 0.17f, 0.14f, skipBottom = true,
            )

            // Mid-wall architectural sconce fixture (warm tungsten gold & muted slate)
            val isTeal = (i % 2 == 0)
            val lr = if (isTeal) 0.28f else 0.88f
            val lg = if (isTeal) 0.68f else 0.70f
            val lb = if (isTeal) 0.66f else 0.38f
            val sconceMinY = floorBaseY + 2.05f
            val sconceMaxY = (ceilingY - 1.55f).coerceAtLeast(sconceMinY + 0.9f)
            addBox(
                -wallX + pilasterW - 0.03f, sconceMinY, bayZ + pilasterDepth * 0.5f + 0.005f,
                -wallX + pilasterW + 0.004f, sconceMaxY, bayZ + pilasterDepth * 0.5f + 0.030f,
                lr, lg, lb, emissive = 0.28f,
            )
            addBox(
                wallX - pilasterW - 0.004f, sconceMinY, bayZ + pilasterDepth * 0.5f + 0.005f,
                wallX - pilasterW + 0.03f, sconceMaxY, bayZ + pilasterDepth * 0.5f + 0.030f,
                lr, lg, lb, emissive = 0.28f,
            )

            // Overhead ceiling beam spanning across auditorium + muted indirect soffit cove
            addBox(
                -wallX, ceilingY - 0.30f, bayZ - 0.24f,
                wallX, ceilingY, bayZ + 0.24f,
                0.10f, 0.11f, 0.13f,
            )
            addBox(
                -wallX * 0.78f, ceilingY - 0.31f, bayZ + 0.23f,
                wallX * 0.78f, ceilingY - 0.27f, bayZ + 0.26f,
                0.46f, 0.38f, 0.24f, emissive = 0.10f,
            )
        }

        // Ceiling Canopy Panels (tapered to match walls)
        for (s in 0 until sideSlices) {
            val z0 = prosceniumBackZ + (backZ - prosceniumBackZ) * (s.toFloat() / sideSlices)
            val z1 = prosceniumBackZ + (backZ - prosceniumBackZ) * ((s + 1f) / sideSlices)
            val w0 = wallHalfWAt(z0)
            val w1 = wallHalfWAt(z1)
            addQuad(
                -w0, ceilingY, z0,
                w0, ceilingY, z0,
                w1, ceilingY, z1,
                -w1, ceilingY, z1,
                0.08f, 0.09f, 0.12f, 0f,
            )
        }

        // 6. Sloped Stadium Seating Tiers & Detailed Plush Seats
        val rows = listOf(
            -4.35f to 0.02f,
            -2.85f to 0.20f,
            -1.35f to 0.40f,
            0.25f to 0.62f,
            1.85f to 0.86f,
        )
        val seatXOffsets = floatArrayOf(
            -4.75f, -3.85f, -2.95f, -2.05f, -1.25f,
            1.25f, 2.05f, 2.95f, 3.85f, 4.75f,
        )

        for ((rowIdx, pair) in rows.withIndex()) {
            val rowZ = pair.first
            val rowRise = pair.second
            val tierTopY = floorBaseY + rowRise
            val tierW = (wallHalfWAt(rowZ) - 0.42f).coerceAtLeast(4.9f)

            // Left & right stadium concrete seating decks (leaving center aisle sunken cleanly)
            addBox(
                -tierW, floorBaseY, rowZ - 0.72f,
                -0.88f, tierTopY, rowZ + 0.72f,
                0.11f, 0.12f, 0.15f, skipBottom = true,
            )
            addBox(
                0.88f, floorBaseY, rowZ - 0.72f,
                tierW, tierTopY, rowZ + 0.72f,
                0.11f, 0.12f, 0.15f, skipBottom = true,
            )
            // Stepped central aisle deck for this row
            addBox(
                -0.88f, floorBaseY, rowZ - 0.72f,
                0.88f, tierTopY, rowZ + 0.72f,
                0.14f, 0.15f, 0.19f, skipBottom = true,
            )

            // Warm subtle recessed aisle step indicators along central stair border
            addBox(
                -0.91f, tierTopY - 0.02f, rowZ - 0.69f,
                -0.81f, tierTopY + 0.015f, rowZ - 0.55f,
                0.96f, 0.76f, 0.20f, emissive = 0.36f, skipBottom = true,
            )
            addBox(
                0.81f, tierTopY - 0.02f, rowZ - 0.69f,
                0.91f, tierTopY + 0.015f, rowZ - 0.55f,
                0.96f, 0.76f, 0.20f, emissive = 0.36f, skipBottom = true,
            )

            for (sx in seatXOffsets) {
                // Skip seats that collide with the side walls on tighter front rows
                if (kotlin.math.abs(sx) > tierW - 0.45f) continue
                // Keep direct center sightlines free behind viewer (rows >= 2)
                if (rowIdx == 2 && kotlin.math.abs(sx) < 1.35f) continue

                val baseH = 0.22f
                // Matte titanium pedestal base
                addBox(
                    sx - 0.14f, tierTopY, rowZ - 0.14f,
                    sx + 0.14f, tierTopY + baseH, rowZ + 0.14f,
                    0.10f, 0.11f, 0.13f, skipBottom = true,
                )
                // Contoured plush slate-indigo velvet seat cushion
                addBox(
                    sx - 0.30f, tierTopY + baseH, rowZ - 0.28f,
                    sx + 0.30f, tierTopY + baseH + 0.16f, rowZ + 0.24f,
                    0.18f, 0.20f, 0.26f,
                )
                // Sculpted backrest + tapered shoulder wing
                addBox(
                    sx - 0.28f, tierTopY + baseH + 0.12f, rowZ + 0.10f,
                    sx + 0.28f, tierTopY + baseH + 0.72f, rowZ + 0.27f,
                    0.20f, 0.22f, 0.29f,
                )
                // Tapered ergonomic headrest cushion + soft top crown
                addBox(
                    sx - 0.21f, tierTopY + baseH + 0.70f, rowZ + 0.07f,
                    sx + 0.21f, tierTopY + baseH + 0.88f, rowZ + 0.25f,
                    0.23f, 0.26f, 0.33f,
                )
                addBox(
                    sx - 0.16f, tierTopY + baseH + 0.87f, rowZ + 0.09f,
                    sx + 0.16f, tierTopY + baseH + 0.94f, rowZ + 0.23f,
                    0.25f, 0.28f, 0.36f,
                )
                // Twin padded armrests + metallic trim
                addBox(
                    sx - 0.35f, tierTopY + baseH + 0.08f, rowZ - 0.24f,
                    sx - 0.27f, tierTopY + baseH + 0.26f, rowZ + 0.22f,
                    0.16f, 0.18f, 0.21f,
                )
                addBox(
                    sx + 0.27f, tierTopY + baseH + 0.08f, rowZ - 0.24f,
                    sx + 0.35f, tierTopY + baseH + 0.26f, rowZ + 0.22f,
                    0.16f, 0.18f, 0.21f,
                )
            }
        }

        // 7. Rear Wall, Architectural Columns, Projector Booth Window & Subtle Exit Alcoves
        addQuad(
            rearWallHalfW, floorBaseY, backZ,
            -rearWallHalfW, floorBaseY, backZ,
            -rearWallHalfW, ceilingY, backZ,
            rearWallHalfW, ceilingY, backZ,
            0.10f, 0.11f, 0.14f, 0f,
        )
        // Rear acoustic pilasters & recessed timber bays behind back row
        val rearColXs = floatArrayOf(-5.6f, -3.4f, -1.8f, 1.8f, 3.4f, 5.6f)
        for (rx in rearColXs) {
            if (kotlin.math.abs(rx) > rearWallHalfW - 0.5f) continue
            addBox(
                rx - 0.20f, floorBaseY, backZ - 0.26f,
                rx + 0.20f, ceilingY, backZ,
                0.13f, 0.14f, 0.17f, skipBottom = true,
            )
        }
        // Subtle rear walnut acoustic panel bays left/right of projector booth
        addBox(
            -5.2f, floorBaseY + 1.05f, backZ - 0.08f,
            -2.1f, ceilingY - 0.45f, backZ,
            0.20f, 0.16f, 0.14f, skipBottom = true,
        )
        addBox(
            2.1f, floorBaseY + 1.05f, backZ - 0.08f,
            5.2f, ceilingY - 0.45f, backZ,
            0.20f, 0.16f, 0.14f, skipBottom = true,
        )
        // Rear projection booth dark tinted observation slit
        addBox(
            -1.45f, ceilingY - 1.35f, backZ - 0.08f,
            1.45f, ceilingY - 0.72f, backZ - 0.01f,
            0.24f, 0.46f, 0.56f, emissive = 0.18f,
        )
        // Subtle twin emerald EXIT signs near back corners (cinema atmosphere)
        for (side in intArrayOf(-1, 1)) {
            val ex = side * (rearWallHalfW - 1.35f)
            addBox(
                ex - 0.22f, floorBaseY + 2.45f, backZ - 0.07f,
                ex + 0.22f, floorBaseY + 2.68f, backZ - 0.01f,
                0.20f, 0.78f, 0.54f, emissive = 0.22f,
            )
        }

        // Convert ArrayList -> FloatArray efficiently
        val result = FloatArray(out.size)
        for (i in out.indices) {
            result[i] = out[i]
        }
        return result
    }
}
