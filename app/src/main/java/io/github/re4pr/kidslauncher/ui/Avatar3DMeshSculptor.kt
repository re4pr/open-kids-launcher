package io.github.re4pr.kidslauncher.ui

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class GlMesh(
    val vertexBuffer: FloatBuffer,
    val indexBuffer: ShortBuffer,
    val indexCount: Int,
)

/**
 * Procedural spline-based 3D organic mesh sculptor for OpenGL ES 2.0.
 *
 * Instead of sticking primitive spheres and cones together, [Avatar3DMeshSculptor] generates:
 * - Continuous Catmull-Rom / Bezier surfaces of revolution with cheek/chin/jawline modulation
 *   for organic stylized human heads & necks.
 * - Pleated / scalloped cloth dresses and billowing back capes with real 3D harmonic folds.
 * - Cubic 3D Bezier swept tubes with variable radius profiles for flowing hair locks, braids,
 *   ponytails, tails, bent limbs, and curved animal horns/ears.
 * - Sculpted fairy/butterfly/dragon/angel wing surfaces.
 */
internal object Avatar3DMeshSculptor {

    data class Vec3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
        operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
        fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
        fun normalized(): Vec3 {
            val l = sqrt(x * x + y * y + z * z)
            return if (l > 1e-5f) Vec3(x / l, y / l, z / l) else Vec3(0f, 1f, 0f)
        }
    }

    /**
     * Sculpted stylized child head with integrated neck, soft fuller cheeks, tapered chin,
     * gentle eye-socket flattening, and smooth vertex normals.
     * Center of head is around y = 0; integrated neck extends down to y = -0.68.
     */
    fun createSculptedHeadWithNeck(rings: Int = 34, slices: Int = 40): GlMesh {
        // Profile (y from -0.68 neck base up to +0.58 crown)
        val profile = listOf(
            -0.68f to 0.16f, // neck base
            -0.54f to 0.15f, // mid neck
            -0.44f to 0.22f, // under chin transition
            -0.36f to 0.38f, // jawline / chin
            -0.20f to 0.52f, // plush lower cheeks
            -0.04f to 0.54f, // mid face / cheekbones
             0.12f to 0.53f, // eye / temple level
             0.30f to 0.50f, // forehead
             0.45f to 0.40f, // upper cranium
             0.55f to 0.22f, // near crown
             0.58f to 0.001f // crown tip
        )

        return buildGridMesh(rings, slices) { u, v ->
            // u in 0..1 (bottom to top), v in 0..1 (around Y axis, phi = 0 is +Z front!)
            val (y, baseR) = sampleProfile(profile, u)
            val phi = (v * 2.0 * PI).toFloat()
            val sinPhi = sin(phi)
            val cosPhi = cos(phi) // +1 at front (+Z), -1 at back (-Z)

            // Cranium depth & cheek fullness sculpting
            var rx = baseR * 1.03f
            var rz = baseR * 0.98f

            if (y > -0.38f) {
                // Soft plump Pixar cheeks on front-sides (phi around ±38 deg)
                val cheekBand = expFalloff((y + 0.14f) / 0.16f)
                val cheekAngle = expFalloff((cosPhi - 0.72f) / 0.25f) * abs(sinPhi)
                val cheekBoost = 0.055f * cheekBand * cheekAngle
                rx += cheekBoost
                rz += cheekBoost * 0.8f

                // Tapered cute chin at front bottom
                val chinBand = expFalloff((y + 0.33f) / 0.10f) * cosPhi.coerceAtLeast(0f)
                rz += 0.032f * chinBand

                // Subtle eye-socket recess so 3D eyes sit IN the face rather than floating on a ball!
                val eyeSocketBand = expFalloff((y - 0.05f) / 0.10f)
                val eyeSocketSide = expFalloff((abs(sinPhi) - 0.36f) / 0.16f) * cosPhi.coerceAtLeast(0f)
                rz -= 0.042f * eyeSocketBand * eyeSocketSide
            }

            Vec3(sinPhi * rx, y, cosPhi * rz)
        }
    }

    /**
     * Sculpted human torso/bodice with natural shoulders, waist taper, and hip flare.
     */
    fun createSculptedTorso(rings: Int = 22, slices: Int = 32): GlMesh {
        val profile = listOf(
            -0.44f to 0.31f, // hips
            -0.22f to 0.26f, // natural waist taper
             0.05f to 0.30f, // chest
             0.28f to 0.33f, // shoulders
             0.38f to 0.18f, // collar base
             0.40f to 0.02f, // neck socket cap
        )
        return buildGridMesh(rings, slices) { u, v ->
            val (y, r) = sampleProfile(profile, u)
            val phi = (v * 2.0 * PI).toFloat()
            // Slightly wider across shoulders (X) than chest depth (Z)
            Vec3(sin(phi) * r * 1.14f, y, cos(phi) * r * 0.88f)
        }
    }

    /**
     * Pleated & scalloped 3D dress skirt with genuine harmonic fabric folds!
     */
    fun createPleatedSkirt(
        rings: Int = 22,
        slices: Int = 48,
        waistRadius: Float = 0.27f,
        hemRadius: Float = 0.72f,
        height: Float = 0.62f,
        pleatCount: Int = 10,
        pleatDepth: Float = 0.055f,
    ): GlMesh {
        return buildGridMesh(rings, slices) { u, v ->
            // u = 0 at hem, u = 1 at waist
            val t = 1f - u // 0 at waist, 1 at hem
            val bellCurve = t.pow(0.82f)
            val phi = (v * 2.0 * PI).toFloat()
            val pleatWave = sin(phi * pleatCount) * pleatDepth * t * t
            val r = waistRadius + (hemRadius - waistRadius) * bellCurve + pleatWave
            val scallopY = sin(phi * pleatCount) * 0.014f * t
            val y = (0.5f - t) * height + scallopY
            Vec3(sin(phi) * r * 1.05f, y, cos(phi) * r * 0.96f)
        }
    }

    /**
     * Billowing 3D royal/frost/superhero back cape with gentle wind ripple!
     */
    fun createFlowingCape(rings: Int = 20, cols: Int = 24): GlMesh {
        return buildGridMesh(rings, cols) { u, v ->
            // u = 0 top collar, u = 1 bottom hem; v = 0..1 left-to-right arc behind back
            val t = u
            val angle = ((-0.68f + v * 1.36f) * PI * 0.52).toFloat() // spans back ~125 degrees
            val widthR = 0.28f + t * 0.48f
            val ripple = sin((v * 5.0 * PI).toFloat()) * 0.035f * t
            val x = sin(angle) * widthR
            val z = -cos(angle) * (0.24f + t * 0.34f) + ripple
            val y = 0.30f - t * 0.96f
            Vec3(x, y, z)
        }
    }

    /**
     * Sweeps a circular/elliptical cross-section along a 3D cubic Bezier curve with
     * customizable radius profile — ideal for realistic flowing hair locks, braids, ponytails,
     * animal tails, horns, curved ears, and bent arms/legs!
     */
    fun createBezierTube(
        p0: Vec3,
        p1: Vec3,
        p2: Vec3,
        p3: Vec3,
        lengthSegs: Int = 22,
        radialSegs: Int = 16,
        radiusAt: (Float) -> Float,
        twistTurns: Float = 0f,
        lobeCount: Int = 0,
        lobeDepth: Float = 0f,
    ): GlMesh {
        return buildGridMesh(lengthSegs, radialSegs) { u, v ->
            val center = evalBezier(p0, p1, p2, p3, u)
            val tangent = evalBezierDeriv(p0, p1, p2, p3, u).normalized()
            val refUp = if (abs(tangent.y) > 0.92f) Vec3(0f, 0f, 1f) else Vec3(0f, 1f, 0f)
            val normal = tangent.cross(refUp).normalized()
            val binormal = tangent.cross(normal).normalized()

            val phi = (v * 2.0 * PI + u * twistTurns * 2.0 * PI).toFloat()
            val baseR = radiusAt(u)
            val lobe = if (lobeCount > 0) 1f + sin(phi * lobeCount) * lobeDepth else 1f
            val r = (baseR * lobe).coerceAtLeast(0.002f)

            val offset = (normal * cos(phi) + binormal * sin(phi)) * r
            center + offset
        }
    }

    /**
     * Sculpted curved fairy / butterfly / angel / dragon wing panel (right-side wing from x=0 outward).
     */
    fun createSculptedWing(isRoundFairy: Boolean, uSegs: Int = 18, vSegs: Int = 18): GlMesh {
        return buildGridMesh(uSegs, vSegs) { u, v ->
            // u = 0..1 span outward from spine; v = 0..1 bottom lobe to top tip
            val span = u * 0.78f
            val silhouette = sin(u * PI).toFloat().pow(0.65f)
            val heightSpan = if (isRoundFairy) {
                // Dual-lobe butterfly/fairy wing contour!
                val upperLobe = v * (0.48f + 0.36f * sin(u * PI).toFloat())
                val lowerDip = (1f - v) * (-0.34f * silhouette)
                upperLobe + lowerDip
            } else {
                // Swept feathered/dragon wing arch
                val arch = sin(u * PI * 0.85).toFloat() * 0.32f
                (v - 0.45f) * (0.18f + 0.46f * silhouette) + arch
            }
            val camberZ = -sin(u * PI).toFloat() * 0.08f - (1f - v) * 0.03f
            val thickness = sin(v * PI).toFloat() * 0.018f * (1f - u * 0.7f)
            Vec3(span, heightSpan, camberZ + thickness)
        }
    }

    /**
     * High-detail smooth UV sphere for eyeballs, pearls, joints, and curved accents.
     */
    fun createSmoothSphere(latBands: Int = 26, lonBands: Int = 30): GlMesh {
        return buildGridMesh(latBands, lonBands) { u, v ->
            val theta = (u * PI).toFloat()
            val phi = (v * 2.0 * PI).toFloat()
            Vec3(
                sin(theta) * sin(phi),
                cos(theta),
                sin(theta) * cos(phi),
            )
        }
    }

    /**
     * Sculpted quadruped/companion pet body (chest, tummy, hindquarters) with organic curves.
     */
    fun createSculptedPetBody(rings: Int = 20, slices: Int = 26): GlMesh {
        val profile = listOf(
            -0.34f to 0.02f, // rump tip
            -0.28f to 0.16f, // hindquarters
            -0.08f to 0.19f, // cozy tummy
             0.14f to 0.21f, // proud chest
             0.28f to 0.14f, // neck base
             0.33f to 0.01f, // front cap
        )
        return buildGridMesh(rings, slices) { u, v ->
            val (z, r) = sampleProfile(profile, u)
            val phi = (v * 2.0 * PI).toFloat()
            val archY = sin(u * PI).toFloat() * 0.03f
            Vec3(sin(phi) * r * 0.92f, cos(phi) * r * 1.02f + archY, z)
        }
    }

    /**
     * Smooth torus for glasses frames, collars, crowns, and halos.
     */
    fun createTorus(majorSegs: Int = 28, minorSegs: Int = 14, majorRadius: Float = 0.7f, minorRadius: Float = 0.12f): GlMesh {
        return buildGridMesh(majorSegs, minorSegs) { u, v ->
            val a = (u * 2.0 * PI).toFloat()
            val b = (v * 2.0 * PI).toFloat()
            Vec3(
                (majorRadius + minorRadius * cos(b)) * cos(a),
                (majorRadius + minorRadius * cos(b)) * sin(a),
                minorRadius * sin(b),
            )
        }
    }

    /**
     * Builds an indexed triangle mesh from any parametric surface `pos(u, v)` and computes
     * smooth analytical finite-difference normals across the surface so lighting is seamless!
     */
    private fun buildGridMesh(
        uSegs: Int,
        vSegs: Int,
        surface: (Float, Float) -> Vec3,
    ): GlMesh {
        val rows = uSegs + 1
        val cols = vSegs + 1
        val positions = Array(rows * cols) { Vec3(0f, 0f, 0f) }

        for (r in 0 until rows) {
            val u = r.toFloat() / uSegs.toFloat()
            for (c in 0 until cols) {
                val v = c.toFloat() / vSegs.toFloat()
                positions[r * cols + c] = surface(u, v)
            }
        }

        val verts = FloatArray(rows * cols * 6)
        val eps = 0.004f
        var vp = 0
        for (r in 0 until rows) {
            val u = r.toFloat() / uSegs.toFloat()
            for (c in 0 until cols) {
                val v = c.toFloat() / vSegs.toFloat()
                val p = positions[r * cols + c]
                val pu1 = surface((u + eps).coerceAtMost(1f), v)
                val pu0 = surface((u - eps).coerceAtLeast(0f), v)
                val pv1 = surface(u, (v + eps).coerceAtMost(1f))
                val pv0 = surface(u, (v - eps).coerceAtLeast(0f))
                val du = pu1 - pu0
                val dv = pv1 - pv0
                var n = dv.cross(du).normalized()
                // Ensure outward normal relative to origin when cross product flips near poles
                if (n.x * p.x + n.z * p.z < -0.15f && abs(p.x) + abs(p.z) > 0.08f) {
                    n = n * -1f
                }
                verts[vp++] = p.x
                verts[vp++] = p.y
                verts[vp++] = p.z
                verts[vp++] = n.x
                verts[vp++] = n.y
                verts[vp++] = n.z
            }
        }

        val indices = ShortArray(uSegs * vSegs * 6)
        var ip = 0
        for (r in 0 until uSegs) {
            for (c in 0 until vSegs) {
                val i0 = (r * cols + c).toShort()
                val i1 = ((r + 1) * cols + c).toShort()
                val i2 = (r * cols + c + 1).toShort()
                val i3 = ((r + 1) * cols + c + 1).toShort()
                indices[ip++] = i0
                indices[ip++] = i1
                indices[ip++] = i2
                indices[ip++] = i1
                indices[ip++] = i3
                indices[ip++] = i2
            }
        }

        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(verts).position(0)
        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(indices).position(0)
        return GlMesh(vb, ib, indices.size)
    }

    private fun sampleProfile(points: List<Pair<Float, Float>>, u: Float): Pair<Float, Float> {
        val scaled = (u.coerceIn(0f, 1f) * (points.size - 1))
        val idx = scaled.toInt().coerceIn(0, points.size - 2)
        val frac = scaled - idx
        // Smoothstep interpolation between control rings for organic curvature
        val s = frac * frac * (3f - 2f * frac)
        val (y0, r0) = points[idx]
        val (y1, r1) = points[idx + 1]
        return (y0 + (y1 - y0) * frac) to (r0 + (r1 - r0) * s)
    }

    private fun evalBezier(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Float): Vec3 {
        val inv = 1f - t
        return p0 * (inv * inv * inv) +
            p1 * (3f * inv * inv * t) +
            p2 * (3f * inv * t * t) +
            p3 * (t * t * t)
    }

    private fun evalBezierDeriv(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Float): Vec3 {
        val inv = 1f - t
        return (p1 - p0) * (3f * inv * inv) +
            (p2 - p1) * (6f * inv * t) +
            (p3 - p2) * (3f * t * t)
    }

    private fun expFalloff(x: Float): Float = kotlin.math.exp((-x * x).toDouble()).toFloat()
}
