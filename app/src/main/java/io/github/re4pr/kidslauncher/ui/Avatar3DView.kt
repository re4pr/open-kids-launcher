package io.github.re4pr.kidslauncher.ui

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.TextureView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import io.github.re4pr.kidslauncher.model.AvatarConfig
import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.ui.Avatar3DMeshSculptor.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-detail sculpted OpenGL ES 2.0 3D Avatar + Companion Pet renderer.
 * Uses spline-sculpted head/neck anatomy, subsurface-scattering skin shading, anisotropic
 * hair sheen, pleated cloth dresses, articulated bent-elbow limbs, flapping translucent 3D wings,
 * held magic wands, and 12 multi-part animated 3D pets.
 */
@Composable
fun Avatar3DStage(
    avatar: AvatarConfig,
    mode: LauncherMode,
    rimHighlightColor: Color = Color(0xFF9EE8FF),
    modifier: Modifier = Modifier,
    onTapAvatar: (() -> Unit)? = null,
) {
    val controller = remember { Avatar3DController() }
    var userYawDeg by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(avatar, mode, rimHighlightColor) {
        controller.updateState(avatar, mode, rimHighlightColor)
    }

    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    AndroidView(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    userYawDeg = (userYawDeg + dragAmount * 0.48f) % 360f
                    controller.setUserYaw(userYawDeg)
                }
            }
            .pointerInput(onTapAvatar) {
                detectTapGestures(
                    onTap = {
                        controller.triggerCheerJump()
                        onTapAvatar?.invoke()
                    }
                )
            },
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        controller.start(surface, width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        controller.resize(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        controller.stop()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
    )
}

private class Avatar3DController {
    @Volatile private var avatarState: AvatarConfig = AvatarConfig()
    @Volatile private var modeState: LauncherMode = LauncherMode.FREE_PLAY
    @Volatile private var rimRgb: FloatArray = floatArrayOf(0.62f, 0.91f, 1.0f)
    @Volatile private var userYawDeg: Float = 0f
    @Volatile private var cheerStartSec: Float = -10f
    @Volatile private var viewportWidth: Int = 1
    @Volatile private var viewportHeight: Int = 1

    private var renderThread: Thread? = null
    @Volatile private var running = false

    fun updateState(avatar: AvatarConfig, mode: LauncherMode, rimColor: Color) {
        avatarState = avatar
        modeState = mode
        rimRgb = floatArrayOf(rimColor.red, rimColor.green, rimColor.blue)
    }

    fun setUserYaw(deg: Float) {
        userYawDeg = deg
    }

    fun triggerCheerJump() {
        cheerStartSec = (System.nanoTime() / 1_000_000_000.0).toFloat()
    }

    fun resize(w: Int, h: Int) {
        viewportWidth = w.coerceAtLeast(1)
        viewportHeight = h.coerceAtLeast(1)
    }

    fun start(surfaceTexture: SurfaceTexture, w: Int, h: Int) {
        stop()
        viewportWidth = w.coerceAtLeast(1)
        viewportHeight = h.coerceAtLeast(1)
        running = true
        renderThread = Thread({ runGlLoop(surfaceTexture) }, "Avatar3DGlThread").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        renderThread?.let { runCatching { it.join(450) } }
        renderThread = null
    }

    private fun runGlLoop(surfaceTexture: SurfaceTexture) {
        val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
            EGL14.eglTerminate(display)
            return
        }
        val config = configs[0]!!

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val context: EGLContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface: EGLSurface = EGL14.eglCreateWindowSurface(display, config, surfaceTexture, surfaceAttribs, 0)

        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            return
        }

        val scene = SculptedAvatarScene()
        scene.initGl()

        val startTimeNs = System.nanoTime()
        while (running) {
            val nowSec = ((System.nanoTime() - startTimeNs) / 1_000_000_000.0).toFloat()
            val absSec = (System.nanoTime() / 1_000_000_000.0).toFloat()
            val cheerElapsed = absSec - cheerStartSec

            scene.drawFrame(
                width = viewportWidth,
                height = viewportHeight,
                timeSec = nowSec,
                cheerElapsedSec = cheerElapsed,
                userYawDeg = userYawDeg,
                avatar = avatarState,
                mode = modeState,
                rimColor = rimRgb,
            )
            EGL14.eglSwapBuffers(display, eglSurface)

            try {
                Thread.sleep(16)
            } catch (_: InterruptedException) {
                break
            }
        }

        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}

private class SculptedAvatarScene {
    private var program = 0
    private var aPositionLoc = 0
    private var aNormalLoc = 0
    private var uMvpMatrixLoc = 0
    private var uModelMatrixLoc = 0
    private var uBaseColorLoc = 0
    private var uRimColorLoc = 0
    private var uMaterialParamsLoc = 0 // x = shininess, y = specStrength, z = alpha, w = materialType

    // Sculpted Organic Meshes
    private lateinit var headNeckMesh: GlMesh
    private lateinit var torsoMesh: GlMesh
    private lateinit var pleatedSkirtMesh: GlMesh
    private lateinit var tutuSkirtMesh: GlMesh
    private lateinit var mermaidSkirtMesh: GlMesh
    private lateinit var flowingCapeMesh: GlMesh
    private lateinit var fairyWingMesh: GlMesh
    private lateinit var featherWingMesh: GlMesh
    private lateinit var sphereMesh: GlMesh
    private lateinit var torusMesh: GlMesh
    private lateinit var limbSegmentMesh: GlMesh

    // Bezier Hair & Facial Meshes
    private lateinit var wavyLockMesh: GlMesh
    private lateinit var twistedBraidMesh: GlMesh
    private lateinit var ponytailMesh: GlMesh
    private lateinit var bangSweepMesh: GlMesh
    private lateinit var hornSpiralMesh: GlMesh
    private lateinit var smileArcMesh: GlMesh
    private lateinit var browArcMesh: GlMesh
    private lateinit var bunnyEarMesh: GlMesh

    // Companion Pet Meshes
    private lateinit var petBodyMesh: GlMesh
    private lateinit var wagTailMesh: GlMesh
    private lateinit var seahorseBodyMesh: GlMesh

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val rootMatrix = FloatArray(16)
    private val partMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun initGl() {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(program, "aNormal")
        uMvpMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(program, "uModelMatrix")
        uBaseColorLoc = GLES20.glGetUniformLocation(program, "uBaseColor")
        uRimColorLoc = GLES20.glGetUniformLocation(program, "uRimColor")
        uMaterialParamsLoc = GLES20.glGetUniformLocation(program, "uMatParams")

        headNeckMesh = Avatar3DMeshSculptor.createSculptedHeadWithNeck()
        torsoMesh = Avatar3DMeshSculptor.createSculptedTorso()
        pleatedSkirtMesh = Avatar3DMeshSculptor.createPleatedSkirt(
            waistRadius = 0.25f, hemRadius = 0.66f, height = 0.58f, pleatCount = 12, pleatDepth = 0.048f
        )
        tutuSkirtMesh = Avatar3DMeshSculptor.createPleatedSkirt(
            waistRadius = 0.25f, hemRadius = 0.78f, height = 0.28f, pleatCount = 16, pleatDepth = 0.055f
        )
        mermaidSkirtMesh = Avatar3DMeshSculptor.createPleatedSkirt(
            waistRadius = 0.26f, hemRadius = 0.34f, height = 0.60f, pleatCount = 8, pleatDepth = 0.022f
        )
        flowingCapeMesh = Avatar3DMeshSculptor.createFlowingCape()
        fairyWingMesh = Avatar3DMeshSculptor.createSculptedWing(isRoundFairy = true)
        featherWingMesh = Avatar3DMeshSculptor.createSculptedWing(isRoundFairy = false)
        sphereMesh = Avatar3DMeshSculptor.createSmoothSphere()
        torusMesh = Avatar3DMeshSculptor.createTorus()

        limbSegmentMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(0f, 0f, 0f),
            p1 = Vec3(0f, -0.10f, 0.01f),
            p2 = Vec3(0f, -0.20f, 0.01f),
            p3 = Vec3(0f, -0.30f, 0f),
            radiusAt = { u -> 0.095f - u * 0.020f },
        )

        wavyLockMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(0f, 0.24f, -0.05f),
            p1 = Vec3(0.08f, -0.05f, -0.14f),
            p2 = Vec3(-0.06f, -0.38f, -0.08f),
            p3 = Vec3(0.04f, -0.72f, -0.02f),
            radiusAt = { u -> (0.18f * (1f - u * 0.68f)).coerceAtLeast(0.03f) },
        )

        twistedBraidMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(0f, 0.10f, 0f),
            p1 = Vec3(0.06f, -0.16f, 0.08f),
            p2 = Vec3(0.04f, -0.44f, 0.14f),
            p3 = Vec3(0.02f, -0.70f, 0.12f),
            twistTurns = 4.0f,
            lobeCount = 3,
            lobeDepth = 0.26f,
            radiusAt = { u -> (0.125f * (1f - u * 0.45f)).coerceAtLeast(0.04f) },
        )

        ponytailMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(0f, 0.36f, -0.32f),
            p1 = Vec3(0f, 0.56f, -0.62f),
            p2 = Vec3(0f, 0.12f, -0.78f),
            p3 = Vec3(0f, -0.32f, -0.64f),
            radiusAt = { u -> 0.08f + sin(u * PI).toFloat() * 0.12f * (1f - u * 0.4f) },
        )

        bangSweepMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(-0.06f, 0.45f, 0.34f),
            p1 = Vec3(0.14f, 0.42f, 0.48f),
            p2 = Vec3(0.34f, 0.28f, 0.45f),
            p3 = Vec3(0.46f, 0.08f, 0.30f),
            radiusAt = { u -> (0.145f * (1f - u * 0.62f)).coerceAtLeast(0.025f) },
        )

        hornSpiralMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(0f, 0f, 0f),
            p1 = Vec3(0f, 0.18f, 0.02f),
            p2 = Vec3(0f, 0.36f, 0.04f),
            p3 = Vec3(0f, 0.54f, 0.06f),
            twistTurns = 5.0f,
            lobeCount = 2,
            lobeDepth = 0.14f,
            radiusAt = { u -> (0.095f * (1f - u * 0.90f)).coerceAtLeast(0.008f) },
        )

        smileArcMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(-0.11f, -0.17f, 0.49f),
            p1 = Vec3(-0.04f, -0.22f, 0.525f),
            p2 = Vec3(0.04f, -0.22f, 0.525f),
            p3 = Vec3(0.11f, -0.17f, 0.49f),
            lengthSegs = 14,
            radialSegs = 10,
            radiusAt = { u -> 0.014f + sin(u * PI).toFloat() * 0.008f },
        )

        browArcMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(-0.10f, 0f, 0f),
            p1 = Vec3(-0.03f, 0.035f, 0.015f),
            p2 = Vec3(0.04f, 0.035f, 0.015f),
            p3 = Vec3(0.10f, 0.005f, 0f),
            lengthSegs = 12,
            radialSegs = 10,
            radiusAt = { u -> 0.018f * (1f - u * 0.35f) },
        )

        bunnyEarMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(0f, 0f, 0f),
            p1 = Vec3(0.03f, 0.22f, 0f),
            p2 = Vec3(0.06f, 0.42f, 0.04f),
            p3 = Vec3(0.08f, 0.54f, 0.12f),
            radiusAt = { u -> 0.055f + sin(u * PI).toFloat() * 0.055f },
        )

        petBodyMesh = Avatar3DMeshSculptor.createSculptedPetBody()
        wagTailMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(0f, 0f, 0f),
            p1 = Vec3(0f, 0.10f, -0.16f),
            p2 = Vec3(0f, 0.28f, -0.24f),
            p3 = Vec3(0f, 0.38f, -0.14f),
            radiusAt = { u -> 0.045f + sin(u * PI).toFloat() * 0.065f * (1f - u * 0.3f) },
        )
        seahorseBodyMesh = Avatar3DMeshSculptor.createBezierTube(
            p0 = Vec3(0f, 0.24f, 0.05f),
            p1 = Vec3(0f, 0.08f, -0.10f),
            p2 = Vec3(0f, -0.14f, 0.14f),
            p3 = Vec3(0f, -0.32f, -0.06f),
            radiusAt = { u -> 0.05f + sin(u * PI).toFloat() * 0.095f },
        )
    }

    fun drawFrame(
        width: Int,
        height: Int,
        timeSec: Float,
        cheerElapsedSec: Float,
        userYawDeg: Float,
        avatar: AvatarConfig,
        mode: LauncherMode,
        rimColor: FloatArray,
    ) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniform3fv(uRimColorLoc, 1, rimColor, 0)

        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projMatrix, 0, 32f, aspect, 1.0f, 25.0f)
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0.18f, 5.55f,
            0f, 0.06f, 0f,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val skinRgb = colorToFloat(AvatarCatalog.skinTones.getOrElse(avatar.skinToneIndex) { AvatarCatalog.skinTones[1] })
        val hairRgb = colorToFloat(AvatarCatalog.hairColors.getOrElse(avatar.hairColorIndex) { AvatarCatalog.hairColors[0] }.second)
        val irisRgb = colorToFloat(AvatarCatalog.eyeColors.getOrElse(avatar.eyeColorIndex) { AvatarCatalog.eyeColors[0] }.second)

        val baseOutfitColor = AvatarCatalog.outfits.getOrElse(avatar.outfitIndex) { AvatarCatalog.outfits[0] }.second
        val dyeOverride = AvatarCatalog.outfitColorOverrides.getOrElse(avatar.outfitColorIndex) { AvatarCatalog.outfitColorOverrides[0] }.second
        val chosenOutfitColor = if (dyeOverride.isSpecified) dyeOverride else baseOutfitColor
        val outfitRgb = if (mode == LauncherMode.BEDTIME) {
            floatArrayOf(0.40f, 0.38f, 0.86f)
        } else {
            colorToFloat(chosenOutfitColor)
        }

        val isCheering = cheerElapsedSec in 0f..1.15f
        val jumpY = if (isCheering) sin((cheerElapsedSec / 1.15f) * PI.toFloat()).coerceAtLeast(0f) * 0.42f else 0f
        val spinDeg = if (isCheering) (cheerElapsedSec / 1.15f) * 360f else 0f

        val breathY = sin(timeSec * 2.2f) * 0.025f
        val gentleTurn = sin(timeSec * 0.95f) * 8f

        // Ground shadow disc
        val shadowScale = (0.64f - jumpY * 0.30f).coerceAtLeast(0.30f)
        Matrix.setIdentityM(partMatrix, 0)
        Matrix.translateM(partMatrix, 0, -0.10f, -1.25f, 0f)
        Matrix.scaleM(partMatrix, 0, shadowScale, 0.02f, shadowScale * 0.85f)
        drawMesh(sphereMesh, partMatrix, floatArrayOf(0.05f, 0.07f, 0.16f), shininess = 4f, spec = 0f, alpha = 0.32f, mat = MAT_CLOTH)

        // Character Root (shifted slightly left when a pet is present so both frame beautifully!)
        val rootX = if (avatar.petIndex > 0) -0.16f else 0f
        Matrix.setIdentityM(rootMatrix, 0)
        Matrix.translateM(rootMatrix, 0, rootX, -0.10f + breathY + jumpY, 0f)
        Matrix.rotateM(rootMatrix, 0, userYawDeg + gentleTurn + spinDeg, 0f, 1f, 0f)

        // 1. Back Attachments (Flapping 3D Wings, Capes, Backpacks)
        drawBackAttachment(rootMatrix, avatar.wingsBackIndex, avatar.outfitIndex, outfitRgb, timeSec)

        // 2. Sculpted Outfit, Legs & Footwear!
        val effectiveOutfitIdx = if (mode == LauncherMode.BEDTIME) 11 else avatar.outfitIndex
        drawOutfitAndLegs(rootMatrix, effectiveOutfitIdx, outfitRgb, skinRgb)

        // 3. Articulated Bent-Elbow Arms, Sculpted Hands & Held Magic Wand/Scepter!
        drawArticulatedArmsAndProps(
            rootMatrix = rootMatrix,
            skinRgb = skinRgb,
            sleeveRgb = outfitRgb,
            handPropIndex = if (mode == LauncherMode.BEDTIME) 0 else avatar.handFacePropIndex,
            timeSec = timeSec,
            isCheering = isCheering,
            mode = mode,
        )

        // 4. Sculpted Head, Neck, Recessed 3D Iris Eyes, Lashes, Brows, Nose & Smile!
        val headMatrix = FloatArray(16)
        System.arraycopy(rootMatrix, 0, headMatrix, 0, 16)
        Matrix.translateM(headMatrix, 0, 0f, 0.58f, 0f)
        val headLookY = sin(timeSec * 0.8f) * 7f
        val headTiltZ = if (mode == LauncherMode.BEDTIME) 9f else sin(timeSec * 1.4f) * 3.5f
        Matrix.rotateM(headMatrix, 0, headLookY, 0f, 1f, 0f)
        Matrix.rotateM(headMatrix, 0, headTiltZ, 0f, 0f, 1f)

        // Organic Head + Integrated Neck (Subsurface Scattering Skin!)
        drawPart(headMatrix, 0f, 0f, 0f, sx = 0.90f, sy = 0.90f, sz = 0.90f, mesh = headNeckMesh, color = skinRgb, spec = 0.16f, mat = MAT_SKIN)
        // Sculpted 3D Ears
        drawPart(headMatrix, -0.47f, 0.01f, -0.02f, ry = -15f, sx = 0.09f, sy = 0.13f, sz = 0.06f, mesh = sphereMesh, color = skinRgb, mat = MAT_SKIN)
        drawPart(headMatrix, 0.47f, 0.01f, -0.02f, ry = 15f, sx = 0.09f, sy = 0.13f, sz = 0.06f, mesh = sphereMesh, color = skinRgb, mat = MAT_SKIN)
        // Upturned Pixar Nose Bridge
        drawPart(headMatrix, 0f, -0.03f, 0.475f, rx = -18f, sx = 0.048f, sy = 0.058f, sz = 0.062f, mesh = sphereMesh, color = skinRgb, spec = 0.22f, mat = MAT_SKIN)

        // Cheeks & Freckles
        drawCheekDetails(headMatrix, avatar.cheekStyleIndex)

        // Curved 3D Smile
        drawPart(headMatrix, 0f, 0.01f, -0.025f, mesh = smileArcMesh, color = floatArrayOf(0.84f, 0.26f, 0.38f), spec = 0.25f, mat = MAT_SKIN)

        // Layered 3D Eyes (Sclera + Colored Iris + Pupil + Dual Catchlights + Eyelashes + Brows)
        val blinkPhase = timeSec % 3.9f
        val closedEyes = mode == LauncherMode.BEDTIME || mode == LauncherMode.LOCKED || (blinkPhase in 3.72f..3.86f)
        drawDetailedEyesAndBrows(headMatrix, avatar.eyeStyleIndex, irisRgb, hairRgb, closedEyes)

        // Face Glasses / Shades (either School Time or chosen in Studio)
        drawFaceGlasses(headMatrix, mode, avatar.handFacePropIndex)

        // 5. Sculpted Strand-Swept 3D Hairstyles (12 styles!)
        drawSculptedHairstyle(headMatrix, avatar.hairStyleIndex, hairRgb, timeSec)

        // 6. Sculpted 3D Crowns, Horns, Ears & Hats (12 items!)
        if (mode == LauncherMode.BEDTIME) {
            drawPart(headMatrix, 0.06f, 0.46f, -0.04f, rz = -26f, sx = 1.9f, sy = 1.0f, sz = 1.9f, mesh = hornSpiralMesh, color = floatArrayOf(0.46f, 0.36f, 0.96f), mat = MAT_CLOTH)
            drawPart(headMatrix, 0.32f, 0.88f, 0.02f, sx = 0.10f, sy = 0.10f, sz = 0.10f, mesh = sphereMesh, color = floatArrayOf(1f, 0.88f, 0.36f), spec = 0.85f, mat = MAT_GEM)
        } else {
            drawHeadwear3D(headMatrix, avatar.accessoryIndex)
        }

        // 7. Multi-Part Sculpted Animated 3D Companion Pet (12 pets!)
        if (avatar.petIndex > 0) {
            drawSculptedCompanionPet(rootMatrix, avatar.petIndex, timeSec)
        }
    }

    private fun drawOutfitAndLegs(
        rootMatrix: FloatArray,
        outfitIdx: Int,
        outfitRgb: FloatArray,
        skinRgb: FloatArray,
    ) {
        val trimWhite = floatArrayOf(0.98f, 0.98f, 1.0f)
        val accentLight = floatArrayOf(
            (outfitRgb[0] * 0.55f + 0.45f).coerceAtMost(1f),
            (outfitRgb[1] * 0.55f + 0.45f).coerceAtMost(1f),
            (outfitRgb[2] * 0.55f + 0.45f).coerceAtMost(1f),
        )
        val denimBlue = floatArrayOf(0.24f, 0.42f, 0.68f)

        // Sculpted upper torso bodice
        drawPart(rootMatrix, 0f, -0.08f, 0f, sx = 0.86f, sy = 0.78f, sz = 0.84f, mesh = torsoMesh, color = outfitRgb, spec = 0.25f, mat = MAT_CLOTH)
        // Neckline collar trim
        drawPart(rootMatrix, 0f, 0.19f, 0.02f, rx = 86f, sx = 0.25f, sy = 0.25f, sz = 0.24f, mesh = torusMesh, color = trimWhite, spec = 0.35f, mat = MAT_CLOTH)

        val isPantsOutfit = outfitIdx in listOf(2, 6, 7, 8, 10, 11)
        when {
            outfitIdx == 3 -> { // Mermaid Fin Gown!
                drawPart(rootMatrix, 0f, -0.56f, 0f, sx = 0.92f, sy = 1.05f, sz = 0.88f, mesh = mermaidSkirtMesh, color = outfitRgb, shininess = 56f, spec = 0.65f, mat = MAT_GEM)
                // Shimmering dual tail flukes at ankles
                drawPart(rootMatrix, -0.04f, -0.96f, 0.04f, rz = 135f, sx = 0.42f, sy = 0.36f, sz = 0.42f, mesh = fairyWingMesh, color = accentLight, shininess = 64f, spec = 0.8f, alpha = 0.92f, mat = MAT_GEM)
                drawPart(rootMatrix, 0.04f, -0.96f, 0.04f, ry = 180f, rz = 135f, sx = 0.42f, sy = 0.36f, sz = 0.42f, mesh = fairyWingMesh, color = accentLight, shininess = 64f, spec = 0.8f, alpha = 0.92f, mat = MAT_GEM)
            }
            outfitIdx == 5 -> { // Ballerina Tutu + Tights + Slippers
                drawPart(rootMatrix, 0f, -0.34f, 0f, sx = 1.02f, sy = 0.95f, sz = 1.02f, mesh = tutuSkirtMesh, color = accentLight, spec = 0.3f, mat = MAT_CLOTH)
                drawLegsAndShoes(rootMatrix, legRgb = trimWhite, shoeRgb = outfitRgb, sneakers = false)
            }
            outfitIdx == 9 -> { // School Blazer & Pleated Skirt
                drawPart(rootMatrix, 0f, -0.44f, 0f, sx = 0.78f, sy = 0.62f, sz = 0.78f, mesh = pleatedSkirtMesh, color = floatArrayOf(0.22f, 0.28f, 0.44f), mat = MAT_CLOTH)
                drawLegsAndShoes(rootMatrix, legRgb = skinRgb, shoeRgb = floatArrayOf(0.16f, 0.16f, 0.20f), sneakers = false)
            }
            isPantsOutfit -> { // Hoodie & Jeans / Explorer / Superhero / Overalls / Astronaut / Pajamas
                val pantsRgb = when (outfitIdx) {
                    2, 8 -> denimBlue
                    6 -> floatArrayOf(0.36f, 0.32f, 0.26f)
                    else -> floatArrayOf(outfitRgb[0] * 0.82f, outfitRgb[1] * 0.82f, outfitRgb[2] * 0.88f)
                }
                // Waistband
                drawPart(rootMatrix, 0f, -0.34f, 0f, rx = 90f, sx = 0.34f, sy = 0.30f, sz = 0.32f, mesh = torusMesh, color = accentLight, mat = MAT_CLOTH)
                drawLegsAndShoes(rootMatrix, legRgb = pantsRgb, shoeRgb = outfitRgb, sneakers = true)
            }
            else -> { // Full Pleated Royal / Frost / Fairy Gown!
                if (outfitIdx == 0) {
                    // Built-in frost shimmer back cape on Frost Queen gown
                    drawPart(rootMatrix, 0f, -0.05f, -0.02f, sx = 0.95f, sy = 0.92f, sz = 0.95f, mesh = flowingCapeMesh, color = accentLight, spec = 0.6f, alpha = 0.78f, mat = MAT_GEM)
                }
                // Waist ribbon sash
                drawPart(rootMatrix, 0f, -0.28f, 0f, rx = 90f, sx = 0.33f, sy = 0.29f, sz = 0.34f, mesh = torusMesh, color = accentLight, spec = 0.45f, mat = MAT_CLOTH)
                drawPart(rootMatrix, 0f, -0.58f, 0f, sx = 0.96f, sy = 1.02f, sz = 0.94f, mesh = pleatedSkirtMesh, color = outfitRgb, spec = 0.32f, mat = MAT_CLOTH)
                // Cute dress shoes peeking beneath skirt hem
                val shoeColor = floatArrayOf(outfitRgb[0] * 0.75f, outfitRgb[1] * 0.75f, outfitRgb[2] * 0.85f)
                drawPart(rootMatrix, -0.18f, -1.06f, 0.08f, sx = 0.12f, sy = 0.085f, sz = 0.18f, mesh = sphereMesh, color = shoeColor, spec = 0.55f, mat = MAT_GEM)
                drawPart(rootMatrix, 0.18f, -1.06f, 0.08f, sx = 0.12f, sy = 0.085f, sz = 0.18f, mesh = sphereMesh, color = shoeColor, spec = 0.55f, mat = MAT_GEM)
            }
        }
    }

    private fun drawLegsAndShoes(
        rootMatrix: FloatArray,
        legRgb: FloatArray,
        shoeRgb: FloatArray,
        sneakers: Boolean,
    ) {
        for (side in listOf(-1f, 1f)) {
            val lx = side * 0.17f
            // Sculpted thigh + calf tube
            drawPart(rootMatrix, lx, -0.40f, 0f, sx = 1.15f, sy = 2.05f, sz = 1.15f, mesh = limbSegmentMesh, color = legRgb, mat = MAT_CLOTH)
            // Footwear
            drawPart(rootMatrix, lx, -1.03f, 0.06f, sx = 0.135f, sy = 0.095f, sz = 0.20f, mesh = sphereMesh, color = shoeRgb, spec = 0.4f, mat = MAT_CLOTH)
            if (sneakers) {
                // Crisp white sneaker sole & toe cap
                drawPart(rootMatrix, lx, -1.09f, 0.07f, sx = 0.145f, sy = 0.038f, sz = 0.215f, mesh = sphereMesh, color = floatArrayOf(0.98f, 0.98f, 1f), mat = MAT_CLOTH)
            }
        }
    }

    private fun drawArticulatedArmsAndProps(
        rootMatrix: FloatArray,
        skinRgb: FloatArray,
        sleeveRgb: FloatArray,
        handPropIndex: Int,
        timeSec: Float,
        isCheering: Boolean,
        mode: LauncherMode,
    ) {
        // Left Arm (relaxed natural bend at elbow)
        val leftShoulder = FloatArray(16)
        System.arraycopy(rootMatrix, 0, leftShoulder, 0, 16)
        Matrix.translateM(leftShoulder, 0, -0.32f, 0.12f, 0f)
        Matrix.rotateM(leftShoulder, 0, -22f + sin(timeSec * 2.2f) * 5f, 0f, 0f, 1f)
        // Puffed sleeve cap
        drawPart(leftShoulder, 0f, -0.02f, 0f, sx = 0.135f, sy = 0.135f, sz = 0.135f, mesh = sphereMesh, color = sleeveRgb, mat = MAT_CLOTH)
        drawPart(leftShoulder, 0f, -0.03f, 0f, sx = 0.92f, sy = 0.85f, sz = 0.92f, mesh = limbSegmentMesh, color = skinRgb, mat = MAT_SKIN)

        val leftElbow = FloatArray(16)
        System.arraycopy(leftShoulder, 0, leftElbow, 0, 16)
        Matrix.translateM(leftElbow, 0, 0f, -0.27f, 0f)
        Matrix.rotateM(leftElbow, 0, -18f, 1f, 0f, 0f)
        Matrix.rotateM(leftElbow, 0, 12f, 0f, 0f, 1f)
        drawPart(leftElbow, 0f, 0f, 0f, sx = 0.82f, sy = 0.78f, sz = 0.82f, mesh = limbSegmentMesh, color = skinRgb, mat = MAT_SKIN)
        // Sculpted hand + thumb
        drawPart(leftElbow, 0f, -0.25f, 0.01f, sx = 0.085f, sy = 0.095f, sz = 0.065f, mesh = sphereMesh, color = skinRgb, mat = MAT_SKIN)
        drawPart(leftElbow, 0.06f, -0.22f, 0.03f, rz = -32f, sx = 0.035f, sy = 0.055f, sz = 0.035f, mesh = sphereMesh, color = skinRgb, mat = MAT_SKIN)

        // Right Arm (Articulated Elbow Wave + Optional Held Magic Wand/Scepter/Mic!)
        val rightShoulder = FloatArray(16)
        System.arraycopy(rootMatrix, 0, rightShoulder, 0, 16)
        Matrix.translateM(rightShoulder, 0, 0.32f, 0.12f, 0.02f)

        val shoulderAngleZ = if (mode == LauncherMode.BEDTIME) 22f else 58f
        Matrix.rotateM(rightShoulder, 0, shoulderAngleZ, 0f, 0f, 1f)
        Matrix.rotateM(rightShoulder, 0, -15f, 1f, 0f, 0f)

        drawPart(rightShoulder, 0f, -0.02f, 0f, sx = 0.135f, sy = 0.135f, sz = 0.135f, mesh = sphereMesh, color = sleeveRgb, mat = MAT_CLOTH)
        drawPart(rightShoulder, 0f, -0.03f, 0f, sx = 0.92f, sy = 0.85f, sz = 0.92f, mesh = limbSegmentMesh, color = skinRgb, mat = MAT_SKIN)

        val rightElbow = FloatArray(16)
        System.arraycopy(rightShoulder, 0, rightElbow, 0, 16)
        Matrix.translateM(rightElbow, 0, 0f, -0.27f, 0f)
        val waveSpeed = if (isCheering || mode == LauncherMode.TIDY_UP) 8.5f else 3.8f
        val elbowBend = if (mode == LauncherMode.BEDTIME) {
            15f
        } else {
            92f + sin(timeSec * waveSpeed) * 20f
        }
        Matrix.rotateM(rightElbow, 0, elbowBend, 0f, 0f, 1f)
        drawPart(rightElbow, 0f, 0f, 0f, sx = 0.82f, sy = 0.78f, sz = 0.82f, mesh = limbSegmentMesh, color = skinRgb, mat = MAT_SKIN)
        drawPart(rightElbow, 0f, -0.25f, 0.01f, sx = 0.085f, sy = 0.095f, sz = 0.065f, mesh = sphereMesh, color = skinRgb, mat = MAT_SKIN)
        drawPart(rightElbow, -0.06f, -0.22f, 0.03f, rz = 32f, sx = 0.035f, sy = 0.055f, sz = 0.035f, mesh = sphereMesh, color = skinRgb, mat = MAT_SKIN)

        // Held 3D Wands / Scepter / Microphone!
        if (handPropIndex in 1..4) {
            val propM = FloatArray(16)
            System.arraycopy(rightElbow, 0, propM, 0, 16)
            Matrix.translateM(propM, 0, 0f, -0.26f, 0.05f)
            Matrix.rotateM(propM, 0, 95f, 1f, 0f, 0f)
            when (handPropIndex) {
                1 -> { // Magic Star Wand
                    drawPart(propM, 0f, 0f, 0f, sx = 0.32f, sy = 1.35f, sz = 0.32f, mesh = limbSegmentMesh, color = floatArrayOf(1f, 0.72f, 0.90f), mat = MAT_GEM)
                    drawPart(propM, 0f, -0.44f, 0f, sx = 0.11f, sy = 0.11f, sz = 0.05f, mesh = sphereMesh, color = floatArrayOf(1f, 0.88f, 0.25f), shininess = 90f, spec = 1f, mat = MAT_GEM)
                }
                2 -> { // Crystal Ice Wand
                    drawPart(propM, 0f, 0f, 0f, sx = 0.32f, sy = 1.35f, sz = 0.32f, mesh = limbSegmentMesh, color = floatArrayOf(0.75f, 0.94f, 1f), mat = MAT_GEM)
                    drawPart(propM, 0f, -0.45f, 0f, sx = 0.08f, sy = 0.14f, sz = 0.08f, mesh = sphereMesh, color = floatArrayOf(0.45f, 0.90f, 1f), shininess = 96f, spec = 1f, mat = MAT_GEM)
                }
                3 -> { // Royal Gem Scepter
                    drawPart(propM, 0f, 0f, 0f, sx = 0.36f, sy = 1.45f, sz = 0.36f, mesh = limbSegmentMesh, color = floatArrayOf(1f, 0.80f, 0.22f), shininess = 85f, spec = 0.95f, mat = MAT_GEM)
                    drawPart(propM, 0f, -0.46f, 0f, sx = 0.095f, sy = 0.095f, sz = 0.095f, mesh = sphereMesh, color = floatArrayOf(0.96f, 0.18f, 0.48f), shininess = 96f, spec = 1f, mat = MAT_GEM)
                }
                4 -> { // Pop Star Microphone
                    drawPart(propM, 0f, 0f, 0f, sx = 0.38f, sy = 0.75f, sz = 0.38f, mesh = limbSegmentMesh, color = floatArrayOf(0.20f, 0.22f, 0.28f), mat = MAT_GEM)
                    drawPart(propM, 0f, -0.26f, 0f, sx = 0.085f, sy = 0.085f, sz = 0.085f, mesh = sphereMesh, color = floatArrayOf(0.82f, 0.86f, 0.94f), shininess = 85f, spec = 0.9f, mat = MAT_GEM)
                }
            }
        }
    }

    private fun drawBackAttachment(
        rootMatrix: FloatArray,
        wingsBackIndex: Int,
        outfitIndex: Int,
        outfitRgb: FloatArray,
        timeSec: Float,
    ) {
        val flapDeg = sin(timeSec * 4.2f) * 16f
        when (wingsBackIndex) {
            1, 2, 3 -> { // Animated Flapping 3D Wings!
                val mesh = if (wingsBackIndex == 2) featherWingMesh else fairyWingMesh
                val wingColor = when (wingsBackIndex) {
                    1 -> floatArrayOf(1.0f, 0.68f, 0.94f) // Iridescent Fairy Butterfly
                    2 -> floatArrayOf(0.98f, 0.98f, 1.0f) // Feathered Pegasus/Angel
                    else -> floatArrayOf(0.62f, 0.93f, 1.0f) // Crystal Ice Wings
                }
                val alpha = if (wingsBackIndex == 2) 0.96f else 0.76f

                // Right wing
                val rw = FloatArray(16)
                System.arraycopy(rootMatrix, 0, rw, 0, 16)
                Matrix.translateM(rw, 0, 0.08f, 0.08f, -0.22f)
                Matrix.rotateM(rw, 0, -18f + flapDeg, 0f, 1f, 0f)
                Matrix.scaleM(rw, 0, 1.15f, 1.15f, 1.0f)
                drawMesh(mesh, rw, wingColor, shininess = 72f, spec = 0.85f, alpha = alpha, mat = MAT_GEM)

                // Left wing (mirrored across X)
                val lw = FloatArray(16)
                System.arraycopy(rootMatrix, 0, lw, 0, 16)
                Matrix.translateM(lw, 0, -0.08f, 0.08f, -0.22f)
                Matrix.rotateM(lw, 0, 18f - flapDeg, 0f, 1f, 0f)
                Matrix.scaleM(lw, 0, -1.15f, 1.15f, 1.0f)
                drawMesh(mesh, lw, wingColor, shininess = 72f, spec = 0.85f, alpha = alpha, mat = MAT_GEM)
            }
            4 -> { // Royal Velvet Cape
                drawPart(rootMatrix, 0f, -0.04f, -0.03f, sx = 1.02f, sy = 0.98f, sz = 1.0f, mesh = flowingCapeMesh, color = floatArrayOf(0.76f, 0.12f, 0.34f), spec = 0.3f, mat = MAT_CLOTH)
            }
            5 -> { // Shimmer Frost Cape
                drawPart(rootMatrix, 0f, -0.04f, -0.03f, sx = 1.04f, sy = 1.0f, sz = 1.0f, mesh = flowingCapeMesh, color = floatArrayOf(0.56f, 0.90f, 1.0f), shininess = 68f, spec = 0.75f, alpha = 0.82f, mat = MAT_GEM)
            }
            6 -> { // Cute Backpack
                drawPart(rootMatrix, 0f, -0.04f, -0.26f, sx = 0.24f, sy = 0.27f, sz = 0.14f, mesh = sphereMesh, color = outfitRgb, mat = MAT_CLOTH)
                drawPart(rootMatrix, 0f, -0.10f, -0.35f, sx = 0.16f, sy = 0.13f, sz = 0.07f, mesh = sphereMesh, color = floatArrayOf(1f, 0.84f, 0.35f), mat = MAT_CLOTH)
            }
            7 -> { // Star Jetpack
                val silver = floatArrayOf(0.84f, 0.88f, 0.95f)
                drawPart(rootMatrix, -0.12f, 0.06f, -0.26f, sx = 1.0f, sy = 1.1f, sz = 1.0f, mesh = limbSegmentMesh, color = silver, spec = 0.8f, mat = MAT_GEM)
                drawPart(rootMatrix, 0.12f, 0.06f, -0.26f, sx = 1.0f, sy = 1.1f, sz = 1.0f, mesh = limbSegmentMesh, color = silver, spec = 0.8f, mat = MAT_GEM)
                drawPart(rootMatrix, -0.12f, -0.30f, -0.26f, sx = 0.07f, sy = 0.11f, sz = 0.07f, mesh = sphereMesh, color = floatArrayOf(0.2f, 0.9f, 1f), spec = 1f, mat = MAT_GEM)
                drawPart(rootMatrix, 0.12f, -0.30f, -0.26f, sx = 0.07f, sy = 0.11f, sz = 0.07f, mesh = sphereMesh, color = floatArrayOf(0.2f, 0.9f, 1f), spec = 1f, mat = MAT_GEM)
            }
            else -> {
                if (outfitIndex == 7) { // Superhero default red cape
                    drawPart(rootMatrix, 0f, -0.04f, -0.03f, sx = 1.0f, sy = 0.92f, sz = 1.0f, mesh = flowingCapeMesh, color = floatArrayOf(0.88f, 0.16f, 0.22f), mat = MAT_CLOTH)
                }
            }
        }
    }

    private fun drawCheekDetails(headMatrix: FloatArray, cheekStyleIndex: Int) {
        when (cheekStyleIndex) {
            1, 4 -> {
                val c = if (cheekStyleIndex == 4) floatArrayOf(0.95f, 0.38f, 0.58f) else floatArrayOf(1.0f, 0.55f, 0.68f)
                drawPart(headMatrix, -0.28f, -0.08f, 0.41f, ry = -22f, sx = 0.105f, sy = 0.065f, sz = 0.035f, mesh = sphereMesh, color = c, alpha = 0.62f, mat = MAT_SKIN)
                drawPart(headMatrix, 0.28f, -0.08f, 0.41f, ry = 22f, sx = 0.105f, sy = 0.065f, sz = 0.035f, mesh = sphereMesh, color = c, alpha = 0.62f, mat = MAT_SKIN)
            }
            2 -> { // Cute Freckles + soft blush
                val freckle = floatArrayOf(0.58f, 0.34f, 0.22f)
                for (side in listOf(-1f, 1f)) {
                    drawPart(headMatrix, side * 0.23f, -0.06f, 0.445f, sx = 0.016f, sy = 0.016f, sz = 0.016f, mesh = sphereMesh, color = freckle, mat = MAT_SKIN)
                    drawPart(headMatrix, side * 0.28f, -0.08f, 0.430f, sx = 0.016f, sy = 0.016f, sz = 0.016f, mesh = sphereMesh, color = freckle, mat = MAT_SKIN)
                    drawPart(headMatrix, side * 0.25f, -0.10f, 0.438f, sx = 0.015f, sy = 0.015f, sz = 0.015f, mesh = sphereMesh, color = freckle, mat = MAT_SKIN)
                }
            }
            3 -> { // Golden Star Cheek Stickers
                val gold = floatArrayOf(1f, 0.85f, 0.30f)
                drawPart(headMatrix, -0.29f, -0.07f, 0.43f, ry = -24f, sx = 0.042f, sy = 0.042f, sz = 0.02f, mesh = sphereMesh, color = gold, spec = 0.9f, mat = MAT_GEM)
                drawPart(headMatrix, 0.29f, -0.07f, 0.43f, ry = 24f, sx = 0.042f, sy = 0.042f, sz = 0.02f, mesh = sphereMesh, color = gold, spec = 0.9f, mat = MAT_GEM)
            }
        }
    }

    private fun drawDetailedEyesAndBrows(
        headMatrix: FloatArray,
        eyeStyleIndex: Int,
        irisRgb: FloatArray,
        browRgb: FloatArray,
        closed: Boolean,
    ) {
        val lashDark = floatArrayOf(0.10f, 0.09f, 0.15f)
        val scleraWhite = floatArrayOf(0.99f, 0.99f, 1.0f)

        // Expressive 3D Arched Eyebrows
        drawPart(headMatrix, -0.19f, 0.17f, 0.435f, rz = 6f, sx = 0.88f, sy = 0.85f, sz = 0.85f, mesh = browArcMesh, color = browRgb, mat = MAT_HAIR)
        drawPart(headMatrix, 0.19f, 0.17f, 0.435f, ry = 180f, rz = 6f, sx = 0.88f, sy = 0.85f, sz = 0.85f, mesh = browArcMesh, color = browRgb, mat = MAT_HAIR)

        if (closed || eyeStyleIndex == 2) {
            drawPart(headMatrix, -0.19f, 0.04f, 0.45f, sx = 0.82f, sy = 0.7f, sz = 0.8f, mesh = browArcMesh, color = lashDark, mat = MAT_CLOTH)
            drawPart(headMatrix, 0.19f, 0.04f, 0.45f, sx = 0.82f, sy = 0.7f, sz = 0.8f, mesh = browArcMesh, color = lashDark, mat = MAT_CLOTH)
            return
        }

        val hScale = when (eyeStyleIndex) {
            4 -> 0.088f // Dreamy Almond
            5 -> 0.122f // Starry Wide
            else -> 0.106f
        }

        for (side in listOf(-1f, 1f)) {
            if (side > 0f && eyeStyleIndex == 3) {
                // Playful wink on right eye!
                drawPart(headMatrix, 0.19f, 0.04f, 0.45f, sx = 0.82f, sy = 0.7f, sz = 0.8f, mesh = browArcMesh, color = lashDark, mat = MAT_CLOTH)
                continue
            }
            val ex = side * 0.185f
            // 1. Almond/Round White Sclera nestled into sculpted eye socket
            drawPart(headMatrix, ex, 0.045f, 0.415f, ry = side * 10f, sx = 0.108f, sy = hScale, sz = 0.068f, mesh = sphereMesh, color = scleraWhite, shininess = 50f, spec = 0.5f, mat = MAT_GEM)
            // 2. Colored 3D Iris
            drawPart(headMatrix, ex * 0.96f, 0.045f, 0.452f, ry = side * 8f, sx = 0.074f, sy = hScale * 0.82f, sz = 0.045f, mesh = sphereMesh, color = irisRgb, shininess = 78f, spec = 0.85f, mat = MAT_GEM)
            // 3. Glossy Dark Pupil
            drawPart(headMatrix, ex * 0.95f, 0.045f, 0.476f, sx = 0.042f, sy = hScale * 0.48f, sz = 0.028f, mesh = sphereMesh, color = lashDark, shininess = 96f, spec = 1f, mat = MAT_GEM)
            // 4. Dual Specular Catchlights
            drawPart(headMatrix, ex - 0.024f, 0.072f, 0.495f, sx = 0.022f, sy = 0.022f, sz = 0.018f, mesh = sphereMesh, color = scleraWhite, shininess = 99f, spec = 1f, mat = MAT_GEM)
            drawPart(headMatrix, ex + 0.018f, 0.024f, 0.492f, sx = 0.011f, sy = 0.011f, sz = 0.012f, mesh = sphereMesh, color = scleraWhite, shininess = 99f, spec = 1f, mat = MAT_GEM)
            // 5. Upper Eyelash Line
            drawPart(headMatrix, ex, 0.105f, 0.448f, rz = -side * 8f, sx = 0.92f, sy = 0.82f, sz = 0.85f, mesh = browArcMesh, color = lashDark, mat = MAT_HAIR)
        }
    }

    private fun drawFaceGlasses(headMatrix: FloatArray, mode: LauncherMode, propIndex: Int) {
        val showRound = mode == LauncherMode.SCHOOL_TIME || propIndex == 5
        val showHeart = propIndex == 6
        val showStar = propIndex == 7
        if (!showRound && !showHeart && !showStar) return

        val rimColor = when {
            showHeart -> floatArrayOf(1f, 0.36f, 0.68f)
            showStar -> floatArrayOf(1f, 0.82f, 0.20f)
            else -> floatArrayOf(0.15f, 0.24f, 0.42f)
        }
        drawPart(headMatrix, -0.185f, 0.05f, 0.485f, sx = 0.175f, sy = 0.165f, sz = 0.10f, mesh = torusMesh, color = rimColor, shininess = 75f, spec = 0.85f, mat = MAT_GEM)
        drawPart(headMatrix, 0.185f, 0.05f, 0.485f, sx = 0.175f, sy = 0.165f, sz = 0.10f, mesh = torusMesh, color = rimColor, shininess = 75f, spec = 0.85f, mat = MAT_GEM)
        drawPart(headMatrix, 0f, 0.06f, 0.49f, sx = 0.055f, sy = 0.016f, sz = 0.02f, mesh = sphereMesh, color = rimColor, mat = MAT_GEM)
    }

    private fun drawSculptedHairstyle(
        headMatrix: FloatArray,
        styleIndex: Int,
        hairRgb: FloatArray,
        timeSec: Float,
    ) {
        val hairSway = sin(timeSec * 1.9f) * 3.5f

        // Sculpted voluminous cranial hair cap + side-swept bangs
        drawPart(headMatrix, 0f, 0.15f, -0.04f, sx = 0.51f, sy = 0.45f, sz = 0.50f, mesh = sphereMesh, color = hairRgb, mat = MAT_HAIR)
        drawPart(headMatrix, 0f, 0.0f, 0f, sx = 0.92f, sy = 0.92f, sz = 0.92f, mesh = bangSweepMesh, color = hairRgb, mat = MAT_HAIR)
        drawPart(headMatrix, 0f, 0.0f, 0f, sx = -0.78f, sy = 0.88f, sz = 0.90f, mesh = bangSweepMesh, color = hairRgb, mat = MAT_HAIR)

        when (styleIndex) {
            0, 4, 11 -> { // Long Wavy Cascading Locks / Princess Half-Up
                for (i in -3..3) {
                    val angleY = i * 24f
                    val hm = FloatArray(16)
                    System.arraycopy(headMatrix, 0, hm, 0, 16)
                    Matrix.rotateM(hm, 0, 180f + angleY, 0f, 1f, 0f)
                    Matrix.translateM(hm, 0, 0f, 0.05f, 0.34f)
                    Matrix.rotateM(hm, 0, hairSway * (if (i % 2 == 0) 1f else -1f), 0f, 0f, 1f)
                    drawMesh(wavyLockMesh, hm, hairRgb, shininess = 42f, spec = 0.45f, alpha = 1f, mat = MAT_HAIR)
                }
                if (styleIndex == 4) {
                    // Half-up crown bun!
                    drawPart(headMatrix, 0f, 0.48f, -0.14f, sx = 0.22f, sy = 0.18f, sz = 0.22f, mesh = sphereMesh, color = hairRgb, mat = MAT_HAIR)
                }
            }
            1 -> { // Twin Twisted 3D Braids over Shoulders!
                drawPart(headMatrix, -0.36f, -0.02f, 0.04f, rz = -8f + hairSway, sx = 0.95f, sy = 0.98f, sz = 0.95f, mesh = twistedBraidMesh, color = hairRgb, mat = MAT_HAIR)
                drawPart(headMatrix, 0.36f, -0.02f, 0.04f, rz = 8f + hairSway, sx = -0.95f, sy = 0.98f, sz = 0.95f, mesh = twistedBraidMesh, color = hairRgb, mat = MAT_HAIR)
                // Cute hair-tie ribbons at braid tips
                val ribbon = floatArrayOf(1f, 0.55f, 0.82f)
                drawPart(headMatrix, -0.40f, -0.66f, 0.15f, sx = 0.065f, sy = 0.05f, sz = 0.065f, mesh = sphereMesh, color = ribbon, mat = MAT_CLOTH)
                drawPart(headMatrix, 0.40f, -0.66f, 0.15f, sx = 0.065f, sy = 0.05f, sz = 0.065f, mesh = sphereMesh, color = ribbon, mat = MAT_CLOTH)
            }
            2 -> { // High Bouncy Ponytail
                drawPart(headMatrix, 0f, 0.02f, 0f, rx = hairSway, sx = 1.15f, sy = 1.05f, sz = 1.05f, mesh = ponytailMesh, color = hairRgb, mat = MAT_HAIR)
                drawPart(headMatrix, 0f, 0.36f, -0.32f, rx = 45f, sx = 0.14f, sy = 0.14f, sz = 0.14f, mesh = torusMesh, color = floatArrayOf(1f, 0.45f, 0.78f), mat = MAT_CLOTH)
            }
            3 -> { // Twin Pigtail Sweeps + Ribbons
                drawPart(headMatrix, -0.34f, 0.05f, 0.12f, ry = -62f, rz = hairSway, sx = 0.92f, sy = 0.95f, sz = 0.92f, mesh = ponytailMesh, color = hairRgb, mat = MAT_HAIR)
                drawPart(headMatrix, 0.34f, 0.05f, 0.12f, ry = 62f, rz = hairSway, sx = 0.92f, sy = 0.95f, sz = 0.92f, mesh = ponytailMesh, color = hairRgb, mat = MAT_HAIR)
            }
            5 -> { // Fluffy Cloud Curls Halo
                for (a in 0 until 9) {
                    val ang = (a * 2.0 * PI / 9.0).toFloat()
                    if (cos(ang) < 0.72f) { // keep face clear in front
                        drawPart(headMatrix, sin(ang) * 0.44f, 0.18f + cos(ang * 2) * 0.10f, cos(ang) * 0.42f, sx = 0.22f, sy = 0.22f, sz = 0.22f, mesh = sphereMesh, color = hairRgb, mat = MAT_HAIR)
                    }
                }
            }
            6 -> { // Sleek Side-Swept Bob
                drawPart(headMatrix, -0.38f, -0.02f, 0.05f, rz = 12f, sx = 0.82f, sy = 0.62f, sz = 0.85f, mesh = wavyLockMesh, color = hairRgb, mat = MAT_HAIR)
                drawPart(headMatrix, 0.38f, -0.02f, 0.05f, rz = -12f, sx = -0.82f, sy = 0.62f, sz = 0.85f, mesh = wavyLockMesh, color = hairRgb, mat = MAT_HAIR)
                drawPart(headMatrix, 0f, 0.02f, -0.26f, sx = 0.48f, sy = 0.38f, sz = 0.30f, mesh = sphereMesh, color = hairRgb, mat = MAT_HAIR)
            }
            7 -> { // Space Buns & Wispy Locks
                drawPart(headMatrix, -0.40f, 0.42f, -0.05f, sx = 0.21f, sy = 0.21f, sz = 0.21f, mesh = sphereMesh, color = hairRgb, mat = MAT_HAIR)
                drawPart(headMatrix, 0.40f, 0.42f, -0.05f, sx = 0.21f, sy = 0.21f, sz = 0.21f, mesh = sphereMesh, color = hairRgb, mat = MAT_HAIR)
            }
            8 -> { // Royal Crown Braid Halo
                drawPart(headMatrix, 0f, 0.34f, 0.02f, rx = 82f, sx = 0.62f, sy = 0.62f, sz = 0.55f, mesh = torusMesh, color = hairRgb, mat = MAT_HAIR)
            }
            9 -> { // Short Pixie Crop (clean sculpted cap + bangs)
                drawPart(headMatrix, 0f, 0.12f, -0.14f, sx = 0.48f, sy = 0.38f, sz = 0.42f, mesh = sphereMesh, color = hairRgb, mat = MAT_HAIR)
            }
            10 -> { // Swept Hero Spikes
                for (s in -2..2) {
                    drawPart(headMatrix, s * 0.13f, 0.44f, 0.06f, rx = 20f, rz = -s * 18f, sx = 1.4f, sy = 0.65f, sz = 1.4f, mesh = hornSpiralMesh, color = hairRgb, mat = MAT_HAIR)
                }
            }
        }
    }

    private fun drawHeadwear3D(headMatrix: FloatArray, accessoryIndex: Int) {
        val gold = floatArrayOf(1.0f, 0.82f, 0.22f)
        when (accessoryIndex) {
            1 -> { // Crystal Ice Tiara
                val ice = floatArrayOf(0.55f, 0.92f, 1.0f)
                drawPart(headMatrix, 0f, 0.45f, 0.10f, rx = 78f, sx = 0.42f, sy = 0.42f, sz = 0.40f, mesh = torusMesh, color = ice, shininess = 90f, spec = 1f, mat = MAT_GEM)
                for (s in -1..1) {
                    drawPart(headMatrix, s * 0.14f, 0.48f, 0.30f - kotlin.math.abs(s) * 0.04f, rz = -s * 14f, sx = 0.75f, sy = 0.46f - kotlin.math.abs(s) * 0.12f, sz = 0.75f, mesh = hornSpiralMesh, color = floatArrayOf(0.86f, 0.98f, 1f), shininess = 96f, spec = 1f, mat = MAT_GEM)
                }
            }
            2 -> { // Spiral Golden Unicorn Horn + Cute Pastel Ears & Rosebuds!
                drawPart(headMatrix, 0f, 0.44f, 0.22f, rx = -18f, sx = 1.0f, sy = 1.05f, sz = 1.0f, mesh = hornSpiralMesh, color = gold, shininess = 92f, spec = 1f, mat = MAT_GEM)
                drawPart(headMatrix, -0.26f, 0.42f, 0.10f, rz = 28f, sx = 0.72f, sy = 0.48f, sz = 0.55f, mesh = bunnyEarMesh, color = floatArrayOf(1f, 0.72f, 0.90f), mat = MAT_CLOTH)
                drawPart(headMatrix, 0.26f, 0.42f, 0.10f, rz = -28f, sx = -0.72f, sy = 0.48f, sz = 0.55f, mesh = bunnyEarMesh, color = floatArrayOf(1f, 0.72f, 0.90f), mat = MAT_CLOTH)
                drawPart(headMatrix, -0.12f, 0.45f, 0.25f, sx = 0.065f, sy = 0.055f, sz = 0.065f, mesh = sphereMesh, color = floatArrayOf(1f, 0.45f, 0.75f), mat = MAT_CLOTH)
                drawPart(headMatrix, 0.12f, 0.45f, 0.25f, sx = 0.065f, sy = 0.055f, sz = 0.065f, mesh = sphereMesh, color = floatArrayOf(0.55f, 0.92f, 0.82f), mat = MAT_CLOTH)
            }
            3 -> { // Royal Jewelled Crown
                drawPart(headMatrix, 0f, 0.47f, 0.02f, rx = 90f, sx = 0.42f, sy = 0.42f, sz = 0.46f, mesh = torusMesh, color = gold, shininess = 90f, spec = 1f, mat = MAT_GEM)
                for (p in 0 until 6) {
                    val ang = (p * 2.0 * PI / 6.0).toFloat()
                    drawPart(headMatrix, sin(ang) * 0.28f, 0.48f, cos(ang) * 0.28f + 0.02f, sx = 0.75f, sy = 0.42f, sz = 0.75f, mesh = hornSpiralMesh, color = gold, shininess = 90f, spec = 1f, mat = MAT_GEM)
                }
                drawPart(headMatrix, 0f, 0.50f, 0.33f, sx = 0.055f, sy = 0.065f, sz = 0.045f, mesh = sphereMesh, color = floatArrayOf(0.98f, 0.16f, 0.48f), shininess = 96f, spec = 1f, mat = MAT_GEM)
            }
            4 -> { // Flower Wreath Garland
                drawPart(headMatrix, 0f, 0.38f, 0.04f, rx = 80f, sx = 0.62f, sy = 0.62f, sz = 0.48f, mesh = torusMesh, color = floatArrayOf(0.32f, 0.78f, 0.45f), mat = MAT_CLOTH)
                val flowerColors = listOf(floatArrayOf(1f, 0.52f, 0.78f), floatArrayOf(1f, 0.88f, 0.36f), floatArrayOf(0.68f, 0.58f, 1f))
                for (f in -2..2) {
                    val ang = f * 0.45f
                    drawPart(headMatrix, sin(ang) * 0.44f, 0.38f - ang * ang * 0.04f, cos(ang) * 0.44f, sx = 0.075f, sy = 0.075f, sz = 0.065f, mesh = sphereMesh, color = flowerColors[(f + 2) % 3], mat = MAT_CLOTH)
                }
            }
            5 -> { // Coral Pearl Hairpin
                drawPart(headMatrix, 0.38f, 0.28f, 0.26f, sx = 0.13f, sy = 0.11f, sz = 0.06f, mesh = sphereMesh, color = floatArrayOf(1f, 0.50f, 0.46f), mat = MAT_GEM)
                drawPart(headMatrix, 0.38f, 0.27f, 0.32f, sx = 0.055f, sy = 0.055f, sz = 0.055f, mesh = sphereMesh, color = floatArrayOf(1f, 0.98f, 0.94f), shininess = 96f, spec = 1f, mat = MAT_GEM)
            }
            6 -> { // Big Pink Ribbon Bow
                val pink = floatArrayOf(1f, 0.38f, 0.72f)
                drawPart(headMatrix, -0.14f, 0.52f, 0.16f, rz = 22f, sx = 0.16f, sy = 0.11f, sz = 0.07f, mesh = sphereMesh, color = pink, mat = MAT_CLOTH)
                drawPart(headMatrix, 0.14f, 0.52f, 0.16f, rz = -22f, sx = 0.16f, sy = 0.11f, sz = 0.07f, mesh = sphereMesh, color = pink, mat = MAT_CLOTH)
                drawPart(headMatrix, 0f, 0.51f, 0.18f, sx = 0.065f, sy = 0.065f, sz = 0.065f, mesh = sphereMesh, color = pink, mat = MAT_CLOTH)
            }
            7 -> { // Cute Cat Ears Headband
                drawPart(headMatrix, -0.28f, 0.42f, 0.06f, rz = 22f, sx = 1.45f, sy = 0.42f, sz = 0.9f, mesh = hornSpiralMesh, color = floatArrayOf(0.22f, 0.22f, 0.28f), mat = MAT_CLOTH)
                drawPart(headMatrix, 0.28f, 0.42f, 0.06f, rz = -22f, sx = 1.45f, sy = 0.42f, sz = 0.9f, mesh = hornSpiralMesh, color = floatArrayOf(0.22f, 0.22f, 0.28f), mat = MAT_CLOTH)
            }
            8 -> { // Fluffy Bunny Ears!
                drawPart(headMatrix, -0.20f, 0.44f, 0.04f, rz = 12f, sx = 1.05f, sy = 1.0f, sz = 0.75f, mesh = bunnyEarMesh, color = floatArrayOf(0.98f, 0.96f, 1f), mat = MAT_CLOTH)
                drawPart(headMatrix, 0.20f, 0.44f, 0.04f, rz = -12f, sx = -1.05f, sy = 1.0f, sz = 0.75f, mesh = bunnyEarMesh, color = floatArrayOf(0.98f, 0.96f, 1f), mat = MAT_CLOTH)
            }
            9 -> { // Wizard Star Hat
                val wiz = floatArrayOf(0.40f, 0.22f, 0.78f)
                drawPart(headMatrix, 0f, 0.46f, 0f, rx = 90f, sx = 0.78f, sy = 0.78f, sz = 0.24f, mesh = torusMesh, color = wiz, mat = MAT_CLOTH)
                drawPart(headMatrix, 0f, 0.46f, -0.02f, rx = -12f, sx = 2.6f, sy = 1.35f, sz = 2.6f, mesh = hornSpiralMesh, color = wiz, mat = MAT_CLOTH)
                drawPart(headMatrix, 0f, 1.18f, 0.06f, sx = 0.085f, sy = 0.085f, sz = 0.085f, mesh = sphereMesh, color = gold, shininess = 90f, spec = 1f, mat = MAT_GEM)
            }
            10 -> { // Glow Headphones
                val cyan = floatArrayOf(0.08f, 0.84f, 0.98f)
                drawPart(headMatrix, 0f, 0.06f, 0f, sx = 0.78f, sy = 0.78f, sz = 0.78f, mesh = torusMesh, color = cyan, shininess = 70f, spec = 0.8f, mat = MAT_GEM)
                drawPart(headMatrix, -0.52f, 0.01f, 0f, sx = 0.11f, sy = 0.17f, sz = 0.15f, mesh = sphereMesh, color = cyan, spec = 0.8f, mat = MAT_GEM)
                drawPart(headMatrix, 0.52f, 0.01f, 0f, sx = 0.11f, sy = 0.17f, sz = 0.15f, mesh = sphereMesh, color = cyan, spec = 0.8f, mat = MAT_GEM)
            }
            11 -> { // Space Visor Halo
                drawPart(headMatrix, 0f, 0.08f, 0.05f, sx = 0.62f, sy = 0.58f, sz = 0.58f, mesh = sphereMesh, color = floatArrayOf(0.55f, 0.90f, 1.0f), shininess = 96f, spec = 1f, alpha = 0.30f, mat = MAT_GEM)
            }
        }
    }

    /**
     * Renders 12 distinct multi-part sculpted animated 3D companion pets!
     */
    private fun drawSculptedCompanionPet(rootMatrix: FloatArray, petIndex: Int, timeSec: Float) {
        val isFlyingOrFloating = petIndex in listOf(3, 6, 7, 10, 11, 12)
        val bobY = if (isFlyingOrFloating) sin(timeSec * 3.2f) * 0.11f + 0.26f else kotlin.math.abs(sin(timeSec * 3.0f)) * 0.05f
        val tailWag = sin(timeSec * 6.5f) * 24f
        val wingFlap = sin(timeSec * 7.5f) * 28f

        val pm = FloatArray(16)
        System.arraycopy(rootMatrix, 0, pm, 0, 16)
        Matrix.translateM(pm, 0, 0.82f, -0.78f + bobY, 0.24f)
        Matrix.rotateM(pm, 0, -24f + sin(timeSec * 1.6f) * 7f, 0f, 1f, 0f)

        when (petIndex) {
            6 -> { // Coral Seahorse (S-curved hovering body + tubular snout + dorsal fin!)
                val coral = floatArrayOf(1.0f, 0.56f, 0.48f)
                drawPart(pm, 0f, 0.12f, 0f, sx = 1.15f, sy = 1.15f, sz = 1.15f, mesh = seahorseBodyMesh, color = coral, spec = 0.45f, mat = MAT_SKIN)
                drawPart(pm, 0f, 0.42f, 0.08f, sx = 0.16f, sy = 0.15f, sz = 0.17f, mesh = sphereMesh, color = coral, mat = MAT_SKIN)
                drawPart(pm, 0f, 0.40f, 0.24f, rx = 85f, sx = 0.45f, sy = 0.55f, sz = 0.45f, mesh = limbSegmentMesh, color = coral, mat = MAT_SKIN)
                drawPart(pm, 0f, 0.12f, -0.06f, ry = 90f + wingFlap * 0.5f, sx = 0.28f, sy = 0.28f, sz = 0.28f, mesh = fairyWingMesh, color = floatArrayOf(0.45f, 0.95f, 0.92f), alpha = 0.82f, mat = MAT_GEM)
                drawPetEyes(pm, headY = 0.44f, headZ = 0.18f, spreadX = 0.085f)
                return
            }
            7 -> { // Playful Baby Dolphin (arched streamlined body, beak, dorsal fin & tail flukes!)
                val aqua = floatArrayOf(0.32f, 0.78f, 0.96f)
                drawPart(pm, 0f, 0.12f, 0f, rx = -12f, sx = 1.05f, sy = 0.95f, sz = 1.35f, mesh = petBodyMesh, color = aqua, shininess = 68f, spec = 0.75f, mat = MAT_GEM)
                drawPart(pm, 0f, 0.14f, 0.42f, rx = 90f, sx = 0.55f, sy = 0.45f, sz = 0.45f, mesh = limbSegmentMesh, color = aqua, mat = MAT_GEM)
                drawPart(pm, 0f, 0.30f, -0.02f, rz = 0f, sx = 0.55f, sy = 0.32f, sz = 0.85f, mesh = hornSpiralMesh, color = aqua, mat = MAT_GEM)
                drawPart(pm, 0.18f, 0.04f, 0.08f, rz = -35f, sx = 0.25f, sy = 0.18f, sz = 0.25f, mesh = featherWingMesh, color = aqua, mat = MAT_GEM)
                drawPart(pm, -0.18f, 0.04f, 0.08f, rz = 35f, sx = -0.25f, sy = 0.18f, sz = 0.25f, mesh = featherWingMesh, color = aqua, mat = MAT_GEM)
                drawPetEyes(pm, headY = 0.18f, headZ = 0.30f, spreadX = 0.10f)
                return
            }
            10 -> { // Snowy Owl (plump feathered body, golden eyes, orange beak, flapping white wings!)
                val snow = floatArrayOf(0.96f, 0.97f, 1.0f)
                drawPart(pm, 0f, 0.05f, 0f, sx = 0.22f, sy = 0.28f, sz = 0.21f, mesh = sphereMesh, color = snow, mat = MAT_CLOTH)
                drawPart(pm, 0f, 0.30f, 0.03f, sx = 0.20f, sy = 0.18f, sz = 0.19f, mesh = sphereMesh, color = snow, mat = MAT_CLOTH)
                drawPart(pm, 0f, 0.28f, 0.22f, rx = 90f, sx = 0.42f, sy = 0.22f, sz = 0.42f, mesh = hornSpiralMesh, color = floatArrayOf(1f, 0.65f, 0.18f), mat = MAT_GEM)
                drawPart(pm, 0.18f, 0.10f, 0f, ry = wingFlap, sx = 0.38f, sy = 0.35f, sz = 0.38f, mesh = featherWingMesh, color = snow, mat = MAT_CLOTH)
                drawPart(pm, -0.18f, 0.10f, 0f, ry = -wingFlap, sx = -0.38f, sy = 0.35f, sz = 0.38f, mesh = featherWingMesh, color = snow, mat = MAT_CLOTH)
                drawPetEyes(pm, headY = 0.32f, headZ = 0.18f, spreadX = 0.08f, eyeColor = floatArrayOf(1f, 0.78f, 0.12f))
                return
            }
            11 -> { // Rainbow Fairy Butterfly!
                val body = floatArrayOf(0.42f, 0.26f, 0.68f)
                drawPart(pm, 0f, 0.15f, 0f, rx = 25f, sx = 0.42f, sy = 0.85f, sz = 0.42f, mesh = limbSegmentMesh, color = body, mat = MAT_GEM)
                drawPart(pm, 0.03f, 0.06f, 0f, ry = wingFlap * 1.2f, sx = 0.52f, sy = 0.52f, sz = 0.52f, mesh = fairyWingMesh, color = floatArrayOf(1f, 0.52f, 0.88f), alpha = 0.86f, mat = MAT_GEM)
                drawPart(pm, -0.03f, 0.06f, 0f, ry = -wingFlap * 1.2f, sx = -0.52f, sy = 0.52f, sz = 0.52f, mesh = fairyWingMesh, color = floatArrayOf(0.42f, 0.90f, 1f), alpha = 0.86f, mat = MAT_GEM)
                return
            }
            12 -> { // Friendly Mini Robot!
                val chassis = floatArrayOf(0.88f, 0.93f, 0.98f)
                drawPart(pm, 0f, 0.0f, 0f, sx = 0.20f, sy = 0.22f, sz = 0.18f, mesh = sphereMesh, color = chassis, shininess = 80f, spec = 0.9f, mat = MAT_GEM)
                drawPart(pm, 0f, 0.28f, 0f, sx = 0.22f, sy = 0.17f, sz = 0.19f, mesh = sphereMesh, color = chassis, shininess = 80f, spec = 0.9f, mat = MAT_GEM)
                drawPart(pm, 0f, 0.28f, 0.15f, sx = 0.16f, sy = 0.08f, sz = 0.06f, mesh = sphereMesh, color = floatArrayOf(0.08f, 0.12f, 0.22f), spec = 0.95f, mat = MAT_GEM)
                val cyanLed = floatArrayOf(0.15f, 0.95f, 1f)
                drawPart(pm, -0.065f, 0.28f, 0.205f, sx = 0.032f, sy = 0.032f, sz = 0.02f, mesh = sphereMesh, color = cyanLed, spec = 1f, mat = MAT_GEM)
                drawPart(pm, 0.065f, 0.28f, 0.205f, sx = 0.032f, sy = 0.032f, sz = 0.02f, mesh = sphereMesh, color = cyanLed, spec = 1f, mat = MAT_GEM)
                drawPart(pm, 0f, 0.50f, 0f, sx = 0.045f, sy = 0.045f, sz = 0.045f, mesh = sphereMesh, color = floatArrayOf(1f, 0.38f, 0.68f), spec = 1f, mat = MAT_GEM)
                return
            }
        }

        // Quadruped Sculpted Pets: 1=Snow Fox, 2=Winged Unicorn, 3=Baby Dragon, 4=Puppy, 5=Kitten, 8=Bunny, 9=Panda!
        val primaryRgb = when (petIndex) {
            1 -> floatArrayOf(0.90f, 0.96f, 1.0f) // Arctic Snow Fox
            2 -> floatArrayOf(1.0f, 0.86f, 0.96f) // Winged Alicorn Unicorn
            3 -> floatArrayOf(0.42f, 0.86f, 0.68f) // Baby Crystal Dragon
            4 -> floatArrayOf(0.92f, 0.70f, 0.36f) // Golden Puppy
            5 -> floatArrayOf(0.98f, 0.82f, 0.74f) // Fluffy Kitten
            8 -> floatArrayOf(0.98f, 0.95f, 0.98f) // Cuddly Bunny
            else -> floatArrayOf(0.98f, 0.98f, 1.0f) // Baby Panda
        }
        val legRgb = if (petIndex == 9) floatArrayOf(0.14f, 0.15f, 0.20f) else primaryRgb

        // Sculpted Quadruped Body + 4 Paws/Hooves!
        drawPart(pm, 0f, 0.0f, 0f, sx = 1.08f, sy = 1.05f, sz = 1.08f, mesh = petBodyMesh, color = primaryRgb, mat = MAT_SKIN)
        for (lx in listOf(-0.12f, 0.12f)) {
            for (lz in listOf(-0.16f, 0.16f)) {
                drawPart(pm, lx, -0.10f, lz, sx = 0.55f, sy = 0.58f, sz = 0.55f, mesh = limbSegmentMesh, color = legRgb, mat = MAT_SKIN)
            }
        }

        // Sculpted Pet Head + Snout/Muzzle + Nose!
        drawPart(pm, 0f, 0.24f, 0.22f, sx = 0.20f, sy = 0.185f, sz = 0.20f, mesh = sphereMesh, color = primaryRgb, mat = MAT_SKIN)
        val muzzleLen = if (petIndex in listOf(1, 2, 3, 4)) 0.11f else 0.07f
        drawPart(pm, 0f, 0.19f, 0.36f, sx = 0.095f, sy = 0.075f, sz = muzzleLen, mesh = sphereMesh, color = floatArrayOf(1f, 0.98f, 1f), mat = MAT_SKIN)
        val noseRgb = if (petIndex in listOf(5, 8)) floatArrayOf(1f, 0.55f, 0.72f) else floatArrayOf(0.14f, 0.15f, 0.20f)
        drawPart(pm, 0f, 0.21f, 0.36f + muzzleLen * 0.85f, sx = 0.032f, sy = 0.026f, sz = 0.026f, mesh = sphereMesh, color = noseRgb, spec = 0.8f, mat = MAT_GEM)

        // Glossy Pet Eyes (+ Panda Eye Patches if Panda!)
        if (petIndex == 9) {
            val patch = floatArrayOf(0.14f, 0.15f, 0.20f)
            drawPart(pm, -0.085f, 0.25f, 0.38f, sx = 0.058f, sy = 0.068f, sz = 0.03f, mesh = sphereMesh, color = patch, mat = MAT_CLOTH)
            drawPart(pm, 0.085f, 0.25f, 0.38f, sx = 0.058f, sy = 0.068f, sz = 0.03f, mesh = sphereMesh, color = patch, mat = MAT_CLOTH)
        }
        drawPetEyes(pm, headY = 0.255f, headZ = 0.395f, spreadX = 0.085f)

        // Wagging S-Curved 3D Tail!
        if (petIndex == 8 || petIndex == 9) {
            // Fluffy round bunny/panda pom-pom tail
            drawPart(pm, 0f, 0.04f, -0.32f, sx = 0.085f, sy = 0.085f, sz = 0.085f, mesh = sphereMesh, color = if (petIndex == 9) legRgb else primaryRgb, mat = MAT_CLOTH)
        } else {
            val tailRgb = if (petIndex == 2) floatArrayOf(1f, 0.58f, 0.86f) else primaryRgb
            drawPart(pm, 0f, 0.02f, -0.26f, ry = tailWag, sx = if (petIndex == 1) 1.35f else 0.95f, sy = 1.0f, sz = 1.0f, mesh = wagTailMesh, color = tailRgb, mat = MAT_HAIR)
        }

        // Species Ears, Horns & Wings!
        when (petIndex) {
            1 -> { // Snow Fox tall ears + chest ruff
                drawPart(pm, 0f, 0.08f, 0.25f, sx = 0.14f, sy = 0.13f, sz = 0.10f, mesh = sphereMesh, color = floatArrayOf(1f, 1f, 1f), mat = MAT_CLOTH)
                drawPart(pm, -0.11f, 0.38f, 0.20f, rz = 16f, sx = 0.68f, sy = 0.42f, sz = 0.55f, mesh = bunnyEarMesh, color = primaryRgb, mat = MAT_SKIN)
                drawPart(pm, 0.11f, 0.38f, 0.20f, rz = -16f, sx = -0.68f, sy = 0.42f, sz = 0.55f, mesh = bunnyEarMesh, color = primaryRgb, mat = MAT_SKIN)
            }
            2 -> { // Winged Unicorn: Golden spiral horn + pink mane + flapping wings!
                drawPart(pm, 0f, 0.40f, 0.28f, rx = -20f, sx = 0.65f, sy = 0.58f, sz = 0.65f, mesh = hornSpiralMesh, color = floatArrayOf(1f, 0.84f, 0.25f), spec = 1f, mat = MAT_GEM)
                drawPart(pm, 0f, 0.32f, 0.08f, sx = 0.07f, sy = 0.15f, sz = 0.16f, mesh = sphereMesh, color = floatArrayOf(0.75f, 0.52f, 1f), mat = MAT_HAIR)
                drawPart(pm, 0.14f, 0.10f, 0.02f, ry = wingFlap, sx = 0.38f, sy = 0.36f, sz = 0.38f, mesh = featherWingMesh, color = floatArrayOf(1f, 0.98f, 1f), mat = MAT_GEM)
                drawPart(pm, -0.14f, 0.10f, 0.02f, ry = -wingFlap, sx = -0.38f, sy = 0.36f, sz = 0.38f, mesh = featherWingMesh, color = floatArrayOf(1f, 0.98f, 1f), mat = MAT_GEM)
            }
            3 -> { // Baby Dragon: Twin horns + flapping crystal dragon wings!
                val hornCol = floatArrayOf(1f, 0.86f, 0.42f)
                drawPart(pm, -0.09f, 0.38f, 0.18f, rx = -25f, rz = 18f, sx = 0.65f, sy = 0.42f, sz = 0.65f, mesh = hornSpiralMesh, color = hornCol, mat = MAT_GEM)
                drawPart(pm, 0.09f, 0.38f, 0.18f, rx = -25f, rz = -18f, sx = 0.65f, sy = 0.42f, sz = 0.65f, mesh = hornSpiralMesh, color = hornCol, mat = MAT_GEM)
                drawPart(pm, 0.14f, 0.12f, 0f, ry = wingFlap, sx = 0.42f, sy = 0.40f, sz = 0.42f, mesh = featherWingMesh, color = floatArrayOf(0.62f, 0.96f, 0.88f), mat = MAT_GEM)
                drawPart(pm, -0.14f, 0.12f, 0f, ry = -wingFlap, sx = -0.42f, sy = 0.40f, sz = 0.42f, mesh = featherWingMesh, color = floatArrayOf(0.62f, 0.96f, 0.88f), mat = MAT_GEM)
            }
            4 -> { // Golden Puppy floppy ears + red collar
                drawPart(pm, 0f, 0.12f, 0.18f, rx = 70f, sx = 0.22f, sy = 0.22f, sz = 0.22f, mesh = torusMesh, color = floatArrayOf(0.92f, 0.22f, 0.28f), mat = MAT_CLOTH)
                drawPart(pm, -0.18f, 0.24f, 0.20f, rz = 25f, sx = 0.055f, sy = 0.13f, sz = 0.09f, mesh = sphereMesh, color = floatArrayOf(0.72f, 0.48f, 0.22f), mat = MAT_SKIN)
                drawPart(pm, 0.18f, 0.24f, 0.20f, rz = -25f, sx = 0.055f, sy = 0.13f, sz = 0.09f, mesh = sphereMesh, color = floatArrayOf(0.72f, 0.48f, 0.22f), mat = MAT_SKIN)
            }
            5 -> { // Kitten perky ears
                drawPart(pm, -0.11f, 0.38f, 0.22f, rz = 18f, sx = 0.95f, sy = 0.28f, sz = 0.7f, mesh = hornSpiralMesh, color = primaryRgb, mat = MAT_SKIN)
                drawPart(pm, 0.11f, 0.38f, 0.22f, rz = -18f, sx = 0.95f, sy = 0.28f, sz = 0.7f, mesh = hornSpiralMesh, color = primaryRgb, mat = MAT_SKIN)
            }
            8 -> { // Long sculpted Bunny ears!
                drawPart(pm, -0.09f, 0.38f, 0.20f, rz = 10f, sx = 0.75f, sy = 0.68f, sz = 0.65f, mesh = bunnyEarMesh, color = primaryRgb, mat = MAT_SKIN)
                drawPart(pm, 0.09f, 0.38f, 0.20f, rz = -10f, sx = -0.75f, sy = 0.68f, sz = 0.65f, mesh = bunnyEarMesh, color = primaryRgb, mat = MAT_SKIN)
            }
            9 -> { // Panda round black ears
                drawPart(pm, -0.14f, 0.39f, 0.20f, sx = 0.068f, sy = 0.068f, sz = 0.05f, mesh = sphereMesh, color = legRgb, mat = MAT_CLOTH)
                drawPart(pm, 0.14f, 0.39f, 0.20f, sx = 0.068f, sy = 0.068f, sz = 0.05f, mesh = sphereMesh, color = legRgb, mat = MAT_CLOTH)
            }
        }
    }

    private fun drawPetEyes(
        pm: FloatArray,
        headY: Float,
        headZ: Float,
        spreadX: Float,
        eyeColor: FloatArray = floatArrayOf(0.10f, 0.12f, 0.22f),
    ) {
        for (side in listOf(-1f, 1f)) {
            drawPart(pm, side * spreadX, headY, headZ, sx = 0.036f, sy = 0.040f, sz = 0.028f, mesh = sphereMesh, color = eyeColor, shininess = 90f, spec = 1f, mat = MAT_GEM)
            drawPart(pm, side * spreadX - 0.010f, headY + 0.012f, headZ + 0.022f, sx = 0.012f, sy = 0.012f, sz = 0.010f, mesh = sphereMesh, color = floatArrayOf(1f, 1f, 1f), spec = 1f, mat = MAT_GEM)
        }
    }

    private fun drawPart(
        parent: FloatArray,
        tx: Float,
        ty: Float,
        tz: Float,
        rx: Float = 0f,
        ry: Float = 0f,
        rz: Float = 0f,
        sx: Float = 1f,
        sy: Float = 1f,
        sz: Float = 1f,
        mesh: GlMesh,
        color: FloatArray,
        shininess: Float = 28f,
        spec: Float = 0.24f,
        alpha: Float = 1f,
        mat: Float = MAT_CLOTH,
    ) {
        System.arraycopy(parent, 0, partMatrix, 0, 16)
        Matrix.translateM(partMatrix, 0, tx, ty, tz)
        if (rx != 0f) Matrix.rotateM(partMatrix, 0, rx, 1f, 0f, 0f)
        if (ry != 0f) Matrix.rotateM(partMatrix, 0, ry, 0f, 1f, 0f)
        if (rz != 0f) Matrix.rotateM(partMatrix, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(partMatrix, 0, sx, sy, sz)
        drawMesh(mesh, partMatrix, color, shininess, spec, alpha, mat)
    }

    private fun drawMesh(
        mesh: GlMesh,
        model: FloatArray,
        color: FloatArray,
        shininess: Float,
        spec: Float,
        alpha: Float,
        mat: Float,
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, model, 0)
        GLES20.glUniform3fv(uBaseColorLoc, 1, color, 0)
        GLES20.glUniform4f(uMaterialParamsLoc, shininess, spec, alpha, mat)

        mesh.vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 24, mesh.vertexBuffer)

        mesh.vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(aNormalLoc)
        GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, 24, mesh.vertexBuffer)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)
    }

    private fun colorToFloat(c: Color): FloatArray = floatArrayOf(c.red, c.green, c.blue)

    private fun linkProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val MAT_SKIN = 0f
        private const val MAT_HAIR = 1f
        private const val MAT_CLOTH = 2f
        private const val MAT_GEM = 3f

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vWorldNormal;
            varying vec3 vWorldPos;
            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vWorldNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uBaseColor;
            uniform vec3 uRimColor;
            uniform vec4 uMatParams; // x=shininess, y=specStrength, z=alpha, w=materialType
            varying vec3 vWorldNormal;
            varying vec3 vWorldPos;
            void main() {
                vec3 N = normalize(vWorldNormal);
                vec3 V = normalize(vec3(0.0, 0.25, 5.55) - vWorldPos);
                vec3 L = normalize(vec3(0.52, 0.76, 0.88));
                vec3 H = normalize(L + V);

                float ndl = dot(N, L);
                float halfLambert = ndl * 0.5 + 0.5;
                float wrapDiffuse = smoothstep(0.04, 0.98, halfLambert);
                vec3 hemiFill = mix(vec3(0.48, 0.42, 0.62), vec3(1.0, 0.98, 0.96), N.y * 0.5 + 0.5);

                vec3 sss = vec3(0.0);
                if (uMatParams.w < 0.5) {
                    // Warm Pixar skin subsurface scattering glow along light terminator
                    float terminator = exp(-ndl * ndl * 3.8);
                    sss = vec3(0.22, 0.065, 0.045) * terminator;
                }

                float hairSheen = 0.0;
                if (uMatParams.w > 0.5 && uMatParams.w < 1.5) {
                    // Anisotropic silky hair ribbon highlight
                    hairSheen = pow(clamp(1.0 - abs(N.y - 0.32), 0.0, 1.0), 7.0) * 0.26;
                }

                float spec = pow(max(dot(N, H), 0.0), uMatParams.x) * uMatParams.y;
                float rim = pow(1.0 - max(dot(N, V), 0.0), 2.6) * 0.38;

                vec3 lit = uBaseColor * (0.44 * hemiFill + 0.68 * wrapDiffuse)
                    + sss
                    + vec3(spec + hairSheen)
                    + uRimColor * rim;
                gl_FragColor = vec4(clamp(lit, 0.0, 1.0), uMatParams.z);
            }
        """
    }
}
