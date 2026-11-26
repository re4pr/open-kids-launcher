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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import io.github.re4pr.kidslauncher.model.AvatarConfig
import io.github.re4pr.kidslauncher.model.LauncherMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time translucent OpenGL ES 2.0 3D Avatar stage rendered inside a Compose-friendly
 * [TextureView] (`isOpaque = false`). Supports touch drag to rotate 360° and tap to jump & wave,
 * plus mode-reactive 3D poses (glasses in School Time, starry nightcap & sleeping eyes in Bedtime,
 * energetic bounce in Tidy-Up).
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
                    userYawDeg = (userYawDeg + dragAmount * 0.45f) % 360f
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
        renderThread?.let { runCatching { it.join(400) } }
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

        val scene = Avatar3DSceneRenderer()
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

private class Avatar3DSceneRenderer {
    private var program = 0
    private var aPositionLoc = 0
    private var aNormalLoc = 0
    private var uMvpMatrixLoc = 0
    private var uModelMatrixLoc = 0
    private var uBaseColorLoc = 0
    private var uRimColorLoc = 0
    private var uSpecParamsLoc = 0 // x = shininess, y = specStrength, z = alpha

    private lateinit var sphereMesh: GlIndexedMesh
    private lateinit var coneSkirtMesh: GlIndexedMesh
    private lateinit var cylinderMesh: GlIndexedMesh
    private lateinit var hornMesh: GlIndexedMesh
    private lateinit var torusMesh: GlIndexedMesh

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
        uSpecParamsLoc = GLES20.glGetUniformLocation(program, "uSpecParams")

        sphereMesh = MeshBuilder.createSphere(latBands = 28, lonBands = 32)
        coneSkirtMesh = MeshBuilder.createFrustum(slices = 32, topRadius = 0.42f, bottomRadius = 0.92f)
        cylinderMesh = MeshBuilder.createFrustum(slices = 24, topRadius = 0.78f, bottomRadius = 0.85f)
        hornMesh = MeshBuilder.createFrustum(slices = 24, topRadius = 0.04f, bottomRadius = 0.35f)
        torusMesh = MeshBuilder.createTorus(majorSegs = 28, minorSegs = 14, majorRadius = 0.7f, minorRadius = 0.14f)
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
        Matrix.perspectiveM(projMatrix, 0, 34f, aspect, 1.0f, 25.0f)
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0.32f, 5.45f,
            0f, 0.15f, 0f,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val skinRgb = colorToFloat(AvatarCatalog.skinTones.getOrElse(avatar.skinToneIndex) { AvatarCatalog.skinTones[1] })
        val hairRgb = colorToFloat(AvatarCatalog.hairColors.getOrElse(avatar.hairColorIndex) { AvatarCatalog.hairColors[0] }.second)
        val outfitRgb = if (mode == LauncherMode.BEDTIME) {
            floatArrayOf(0.39f, 0.38f, 0.82f)
        } else {
            colorToFloat(AvatarCatalog.outfits.getOrElse(avatar.outfitIndex) { AvatarCatalog.outfits[0] }.second)
        }

        // Cheer jump & spin animation
        val isCheering = cheerElapsedSec in 0f..1.1f
        val jumpY = if (isCheering) {
            sin((cheerElapsedSec / 1.1f) * PI.toFloat()).coerceAtLeast(0f) * 0.42f
        } else {
            0f
        }
        val spinDeg = if (isCheering) {
            (cheerElapsedSec / 1.1f) * 360f
        } else {
            0f
        }

        val idleBreath = sin(timeSec * 2.3f) * 0.032f
        val idleSwayYaw = sin(timeSec * 1.1f) * 8.5f
        val totalYaw = userYawDeg + idleSwayYaw + spinDeg

        // 1. Soft 3D Stage Pedestal Disc under character
        Matrix.setIdentityM(partMatrix, 0)
        Matrix.translateM(partMatrix, 0, 0f, -1.32f, 0f)
        Matrix.scaleM(partMatrix, 0, 1.18f, 0.10f, 1.18f)
        drawMesh(cylinderMesh, partMatrix, floatArrayOf(1f, 1f, 1f), shininess = 24f, spec = 0.25f, alpha = 0.42f)

        // Shadow disc shrinks slightly when jumping
        val shadowScale = (0.72f - jumpY * 0.35f).coerceAtLeast(0.35f)
        Matrix.setIdentityM(partMatrix, 0)
        Matrix.translateM(partMatrix, 0, 0f, -1.21f, 0f)
        Matrix.scaleM(partMatrix, 0, shadowScale, 0.02f, shadowScale)
        drawMesh(sphereMesh, partMatrix, floatArrayOf(0.06f, 0.08f, 0.18f), shininess = 4f, spec = 0f, alpha = 0.34f)

        // Root character matrix
        Matrix.setIdentityM(rootMatrix, 0)
        Matrix.translateM(rootMatrix, 0, 0f, -0.15f + idleBreath + jumpY, 0f)
        Matrix.rotateM(rootMatrix, 0, totalYaw, 0f, 1f, 0f)

        // 2. Legs & Cute Rounded 3D Shoes
        val bootColor = floatArrayOf(outfitRgb[0] * 0.65f, outfitRgb[1] * 0.65f, outfitRgb[2] * 0.75f)
        drawChildPart(rootMatrix, tx = -0.23f, ty = -0.82f, tz = 0f, sx = 0.14f, sy = 0.28f, sz = 0.14f, mesh = cylinderMesh, color = floatArrayOf(0.95f, 0.96f, 1f))
        drawChildPart(rootMatrix, tx = 0.23f, ty = -0.82f, tz = 0f, sx = 0.14f, sy = 0.28f, sz = 0.14f, mesh = cylinderMesh, color = floatArrayOf(0.95f, 0.96f, 1f))
        drawChildPart(rootMatrix, tx = -0.23f, ty = -1.03f, tz = 0.06f, sx = 0.18f, sy = 0.13f, sz = 0.24f, mesh = sphereMesh, color = bootColor, spec = 0.45f)
        drawChildPart(rootMatrix, tx = 0.23f, ty = -1.03f, tz = 0.06f, sx = 0.18f, sy = 0.13f, sz = 0.24f, mesh = sphereMesh, color = bootColor, spec = 0.45f)

        // 3. Flared 3D Dress / Tunic / Hoodie Body + Collar
        drawChildPart(rootMatrix, tx = 0f, ty = -0.36f, tz = 0f, sx = 0.66f, sy = 0.48f, sz = 0.56f, mesh = coneSkirtMesh, color = outfitRgb, spec = 0.32f)
        // White collar ring
        drawChildPart(rootMatrix, tx = 0f, ty = -0.02f, tz = 0.02f, rx = 90f, sx = 0.36f, sy = 0.36f, sz = 0.36f, mesh = torusMesh, color = floatArrayOf(1f, 0.98f, 1f), spec = 0.4f)

        // 4. Articulated 3D Arms & Waving Hand!
        // Left resting arm
        val leftSwing = sin(timeSec * 2.3f) * 8f
        drawArm(rootMatrix, isRight = false, shoulderX = -0.42f, angleZ = -26f + leftSwing, skinRgb = skinRgb, sleeveRgb = outfitRgb)

        // Right cheerful waving arm!
        val waveFast = if (isCheering || mode == LauncherMode.TIDY_UP) 9.5f else 4.2f
        val rightAngle = if (mode == LauncherMode.BEDTIME) {
            24f
        } else {
            132f + sin(timeSec * waveFast) * 18f
        }
        drawArm(rootMatrix, isRight = true, shoulderX = 0.42f, angleZ = rightAngle, skinRgb = skinRgb, sleeveRgb = outfitRgb)

        // 5. Expressive 3D Chibi Head
        val headMatrix = FloatArray(16)
        System.arraycopy(rootMatrix, 0, headMatrix, 0, 16)
        Matrix.translateM(headMatrix, 0, 0f, 0.52f, 0f)
        val headTiltZ = if (mode == LauncherMode.BEDTIME) 10f else sin(timeSec * 1.5f) * 4.5f
        val headLookY = sin(timeSec * 0.85f) * 9f
        Matrix.rotateM(headMatrix, 0, headLookY, 0f, 1f, 0f)
        Matrix.rotateM(headMatrix, 0, headTiltZ, 0f, 0f, 1f)

        // Main smooth 3D head sphere + cute ears + button nose
        drawChildPart(headMatrix, 0f, 0f, 0f, sx = 0.56f, sy = 0.53f, sz = 0.54f, mesh = sphereMesh, color = skinRgb, spec = 0.18f)
        drawChildPart(headMatrix, -0.53f, -0.03f, 0f, sx = 0.12f, sy = 0.14f, sz = 0.10f, mesh = sphereMesh, color = skinRgb, spec = 0.15f)
        drawChildPart(headMatrix, 0.53f, -0.03f, 0f, sx = 0.12f, sy = 0.14f, sz = 0.10f, mesh = sphereMesh, color = skinRgb, spec = 0.15f)
        drawChildPart(headMatrix, 0f, -0.04f, 0.53f, sx = 0.075f, sy = 0.065f, sz = 0.075f, mesh = sphereMesh, color = skinRgb, spec = 0.25f)

        // Rosy 3D cheek blushes
        val blushRgb = floatArrayOf(1.0f, 0.52f, 0.66f)
        drawChildPart(headMatrix, -0.31f, -0.11f, 0.44f, sx = 0.11f, sy = 0.07f, sz = 0.06f, mesh = sphereMesh, color = blushRgb, spec = 0.1f, alpha = 0.72f)
        drawChildPart(headMatrix, 0.31f, -0.11f, 0.44f, sx = 0.11f, sy = 0.07f, sz = 0.06f, mesh = sphereMesh, color = blushRgb, spec = 0.1f, alpha = 0.72f)

        // Cheerful 3D smile
        drawChildPart(headMatrix, 0f, -0.19f, 0.49f, rx = 18f, sx = 0.13f, sy = 0.045f, sz = 0.05f, mesh = sphereMesh, color = floatArrayOf(0.82f, 0.24f, 0.38f), spec = 0.2f)

        // 6. Glossy 3D Eyes with natural periodic blink!
        val blinkCycle = timeSec % 3.8f
        val isBlinking = mode == LauncherMode.BEDTIME || mode == LauncherMode.LOCKED || (blinkCycle in 3.62f..3.76f)
        drawEyes3D(headMatrix, avatar.eyeStyleIndex, isBlinking)

        // School-time 3D Glasses
        if (mode == LauncherMode.SCHOOL_TIME) {
            val frameColor = floatArrayOf(0.12f, 0.22f, 0.38f)
            drawChildPart(headMatrix, -0.21f, 0.03f, 0.52f, sx = 0.20f, sy = 0.20f, sz = 0.12f, mesh = torusMesh, color = frameColor, spec = 0.6f)
            drawChildPart(headMatrix, 0.21f, 0.03f, 0.52f, sx = 0.20f, sy = 0.20f, sz = 0.12f, mesh = torusMesh, color = frameColor, spec = 0.6f)
        }

        // 7. Volumetric 3D Hairstyle
        drawHair3D(headMatrix, avatar.hairStyleIndex, hairRgb)

        // 8. 3D Crown / Unicorn Horn / Tiara / Hat Accessory
        if (mode == LauncherMode.BEDTIME) {
            drawChildPart(headMatrix, 0.08f, 0.62f, 0f, rz = -22f, sx = 0.85f, sy = 0.45f, sz = 0.85f, mesh = hornMesh, color = floatArrayOf(0.48f, 0.38f, 0.98f), spec = 0.3f)
            drawChildPart(headMatrix, 0.28f, 0.98f, 0f, sx = 0.13f, sy = 0.13f, sz = 0.13f, mesh = sphereMesh, color = floatArrayOf(1f, 0.88f, 0.4f), spec = 0.7f)
        } else {
            drawAccessory3D(headMatrix, avatar.accessoryIndex)
        }

        // 9. 3D Companion Pet bobbing happily beside the character!
        if (avatar.petIndex > 0) {
            drawCompanionPet3D(rootMatrix, avatar.petIndex, timeSec)
        }
    }

    private fun drawArm(
        parent: FloatArray,
        isRight: Boolean,
        shoulderX: Float,
        angleZ: Float,
        skinRgb: FloatArray,
        sleeveRgb: FloatArray,
    ) {
        val armPivot = FloatArray(16)
        System.arraycopy(parent, 0, armPivot, 0, 16)
        Matrix.translateM(armPivot, 0, shoulderX, -0.06f, 0.02f)
        Matrix.rotateM(armPivot, 0, if (isRight) angleZ else angleZ, 0f, 0f, 1f)

        // Puffy shoulder sleeve
        drawChildPart(armPivot, 0f, -0.06f, 0f, sx = 0.16f, sy = 0.16f, sz = 0.16f, mesh = sphereMesh, color = sleeveRgb, spec = 0.3f)
        // Forearm
        drawChildPart(armPivot, 0f, -0.22f, 0f, sx = 0.11f, sy = 0.18f, sz = 0.11f, mesh = cylinderMesh, color = skinRgb, spec = 0.18f)
        // Cute round hand
        drawChildPart(armPivot, 0f, -0.38f, 0f, sx = 0.12f, sy = 0.12f, sz = 0.12f, mesh = sphereMesh, color = skinRgb, spec = 0.2f)
    }

    private fun drawEyes3D(headMatrix: FloatArray, eyeStyleIndex: Int, closed: Boolean) {
        val darkIris = floatArrayOf(0.10f, 0.12f, 0.20f)
        val white = floatArrayOf(1f, 1f, 1f)

        if (closed) {
            drawChildPart(headMatrix, -0.20f, 0.03f, 0.49f, sx = 0.11f, sy = 0.025f, sz = 0.04f, mesh = sphereMesh, color = darkIris)
            drawChildPart(headMatrix, 0.20f, 0.03f, 0.49f, sx = 0.11f, sy = 0.025f, sz = 0.04f, mesh = sphereMesh, color = darkIris)
            return
        }

        val eyeScaleY = when (eyeStyleIndex) {
            1 -> 0.07f
            3 -> 0.135f
            else -> 0.115f
        }

        // Left eye + 3D glossy catchlight
        drawChildPart(headMatrix, -0.20f, 0.04f, 0.46f, sx = 0.105f, sy = eyeScaleY, sz = 0.08f, mesh = sphereMesh, color = darkIris, shininess = 64f, spec = 0.9f)
        drawChildPart(headMatrix, -0.23f, 0.08f, 0.525f, sx = 0.036f, sy = 0.036f, sz = 0.03f, mesh = sphereMesh, color = white, shininess = 90f, spec = 1f)

        if (eyeStyleIndex == 2) {
            // Cheerful wink on right eye
            drawChildPart(headMatrix, 0.20f, 0.04f, 0.49f, sx = 0.11f, sy = 0.028f, sz = 0.04f, mesh = sphereMesh, color = darkIris)
        } else {
            drawChildPart(headMatrix, 0.20f, 0.04f, 0.46f, sx = 0.105f, sy = eyeScaleY, sz = 0.08f, mesh = sphereMesh, color = darkIris, shininess = 64f, spec = 0.9f)
            drawChildPart(headMatrix, 0.17f, 0.08f, 0.525f, sx = 0.036f, sy = 0.036f, sz = 0.03f, mesh = sphereMesh, color = white, shininess = 90f, spec = 1f)
        }
    }

    private fun drawHair3D(headMatrix: FloatArray, styleIndex: Int, hairRgb: FloatArray) {
        // Base smooth 3D crown cap
        drawChildPart(headMatrix, 0f, 0.14f, -0.05f, sx = 0.59f, sy = 0.48f, sz = 0.58f, mesh = sphereMesh, color = hairRgb, shininess = 32f, spec = 0.38f)
        // Soft 3D front bangs
        drawChildPart(headMatrix, -0.14f, 0.34f, 0.36f, rz = 16f, sx = 0.28f, sy = 0.14f, sz = 0.20f, mesh = sphereMesh, color = hairRgb, spec = 0.35f)
        drawChildPart(headMatrix, 0.18f, 0.33f, 0.35f, rz = -18f, sx = 0.26f, sy = 0.13f, sz = 0.20f, mesh = sphereMesh, color = hairRgb, spec = 0.35f)

        when (styleIndex) {
            0 -> { // Sunny Curls
                for (i in -2..2) {
                    drawChildPart(headMatrix, i * 0.18f, 0.46f - (i * i) * 0.02f, 0.18f, sx = 0.19f, sy = 0.18f, sz = 0.19f, mesh = sphereMesh, color = hairRgb, spec = 0.35f)
                }
            }
            1 -> { // Twin Puffs
                drawChildPart(headMatrix, -0.54f, 0.38f, -0.04f, sx = 0.27f, sy = 0.27f, sz = 0.27f, mesh = sphereMesh, color = hairRgb, spec = 0.4f)
                drawChildPart(headMatrix, 0.54f, 0.38f, -0.04f, sx = 0.27f, sy = 0.27f, sz = 0.27f, mesh = sphereMesh, color = hairRgb, spec = 0.4f)
            }
            2 -> { // Royal Side Braid
                drawChildPart(headMatrix, 0f, -0.08f, -0.22f, sx = 0.56f, sy = 0.52f, sz = 0.42f, mesh = sphereMesh, color = hairRgb, spec = 0.35f)
                for (b in 0..3) {
                    drawChildPart(headMatrix, 0.44f, -0.14f - b * 0.16f, 0.16f, sx = 0.15f - b * 0.015f, sy = 0.13f, sz = 0.15f, mesh = sphereMesh, color = hairRgb, spec = 0.4f)
                }
            }
            3 -> { // Wavy Bob
                drawChildPart(headMatrix, -0.46f, -0.05f, 0.04f, sx = 0.20f, sy = 0.38f, sz = 0.34f, mesh = sphereMesh, color = hairRgb, spec = 0.35f)
                drawChildPart(headMatrix, 0.46f, -0.05f, 0.04f, sx = 0.20f, sy = 0.38f, sz = 0.34f, mesh = sphereMesh, color = hairRgb, spec = 0.35f)
            }
            4 -> { // Star Spikes
                for (s in -1..1) {
                    drawChildPart(headMatrix, s * 0.22f, 0.64f, 0.05f, rz = -s * 20f, sx = 0.48f, sy = 0.28f, sz = 0.48f, mesh = hornMesh, color = hairRgb, spec = 0.4f)
                }
            }
            5 -> { // Long Flowing Hair
                drawChildPart(headMatrix, 0f, -0.26f, -0.20f, sx = 0.62f, sy = 0.68f, sz = 0.42f, mesh = sphereMesh, color = hairRgb, spec = 0.38f)
            }
        }
    }

    private fun drawAccessory3D(headMatrix: FloatArray, accessoryIndex: Int) {
        when (accessoryIndex) {
            1 -> { // Crystal Snowflake Tiara
                val iceBlue = floatArrayOf(0.55f, 0.92f, 1.0f)
                drawChildPart(headMatrix, 0f, 0.50f, 0.12f, rx = 78f, sx = 0.46f, sy = 0.46f, sz = 0.46f, mesh = torusMesh, color = iceBlue, shininess = 80f, spec = 0.95f)
                drawChildPart(headMatrix, 0f, 0.68f, 0.32f, sx = 0.36f, sy = 0.26f, sz = 0.36f, mesh = hornMesh, color = floatArrayOf(0.88f, 0.98f, 1f), shininess = 90f, spec = 1f)
            }
            2 -> { // Golden 3D Unicorn Horn + Pastel Ears
                val gold = floatArrayOf(1.0f, 0.86f, 0.32f)
                drawChildPart(headMatrix, 0f, 0.76f, 0.22f, rx = -14f, sx = 0.42f, sy = 0.42f, sz = 0.42f, mesh = hornMesh, color = gold, shininess = 88f, spec = 0.95f)
                drawChildPart(headMatrix, -0.28f, 0.56f, 0.12f, rz = 24f, sx = 0.35f, sy = 0.20f, sz = 0.25f, mesh = hornMesh, color = floatArrayOf(1f, 0.65f, 0.88f), spec = 0.4f)
                drawChildPart(headMatrix, 0.28f, 0.56f, 0.12f, rz = -24f, sx = 0.35f, sy = 0.20f, sz = 0.25f, mesh = hornMesh, color = floatArrayOf(1f, 0.65f, 0.88f), spec = 0.4f)
            }
            3 -> { // Golden Royal Crown
                val gold = floatArrayOf(1.0f, 0.80f, 0.18f)
                drawChildPart(headMatrix, 0f, 0.54f, 0.02f, sx = 0.38f, sy = 0.11f, sz = 0.38f, mesh = cylinderMesh, color = gold, shininess = 85f, spec = 0.95f)
                for (p in 0 until 5) {
                    val ang = (p * 2.0 * PI / 5.0).toFloat()
                    drawChildPart(headMatrix, cos(ang) * 0.28f, 0.70f, sin(ang) * 0.28f + 0.02f, sx = 0.24f, sy = 0.18f, sz = 0.24f, mesh = hornMesh, color = gold, shininess = 85f, spec = 0.95f)
                }
                drawChildPart(headMatrix, 0f, 0.55f, 0.33f, sx = 0.07f, sy = 0.07f, sz = 0.07f, mesh = sphereMesh, color = floatArrayOf(1.0f, 0.22f, 0.52f), shininess = 90f, spec = 1f)
            }
            4 -> { // Coral Seashell Hairpin + Pearl
                drawChildPart(headMatrix, 0.42f, 0.34f, 0.28f, sx = 0.16f, sy = 0.14f, sz = 0.08f, mesh = sphereMesh, color = floatArrayOf(1.0f, 0.52f, 0.48f), spec = 0.5f)
                drawChildPart(headMatrix, 0.42f, 0.32f, 0.35f, sx = 0.065f, sy = 0.065f, sz = 0.065f, mesh = sphereMesh, color = floatArrayOf(1f, 0.98f, 0.92f), shininess = 90f, spec = 1f)
            }
            5 -> { // Wizard Star Hat
                val purple = floatArrayOf(0.42f, 0.24f, 0.78f)
                drawChildPart(headMatrix, 0f, 0.50f, 0f, sx = 0.72f, sy = 0.05f, sz = 0.72f, mesh = cylinderMesh, color = purple, spec = 0.35f)
                drawChildPart(headMatrix, 0f, 0.82f, 0f, sx = 1.05f, sy = 0.42f, sz = 1.05f, mesh = hornMesh, color = purple, spec = 0.35f)
                drawChildPart(headMatrix, 0f, 1.22f, 0f, sx = 0.11f, sy = 0.11f, sz = 0.11f, mesh = sphereMesh, color = floatArrayOf(1f, 0.90f, 0.32f), shininess = 80f, spec = 0.9f)
            }
            6 -> { // 3D Headphones
                val cyan = floatArrayOf(0.05f, 0.82f, 0.95f)
                drawChildPart(headMatrix, 0f, 0.08f, 0f, sx = 0.86f, sy = 0.86f, sz = 0.86f, mesh = torusMesh, color = cyan, shininess = 60f, spec = 0.75f)
                drawChildPart(headMatrix, -0.58f, 0.0f, 0f, sx = 0.14f, sy = 0.20f, sz = 0.18f, mesh = sphereMesh, color = cyan, shininess = 60f, spec = 0.75f)
                drawChildPart(headMatrix, 0.58f, 0.0f, 0f, sx = 0.14f, sy = 0.20f, sz = 0.18f, mesh = sphereMesh, color = cyan, shininess = 60f, spec = 0.75f)
            }
        }
    }

    private fun drawCompanionPet3D(rootMatrix: FloatArray, petIndex: Int, timeSec: Float) {
        val petBob = sin(timeSec * 3.4f) * 0.09f
        val petMatrix = FloatArray(16)
        System.arraycopy(rootMatrix, 0, petMatrix, 0, 16)
        Matrix.translateM(petMatrix, 0, 0.88f, -0.52f + petBob, 0.25f)
        Matrix.rotateM(petMatrix, 0, -18f + sin(timeSec * 2f) * 8f, 0f, 1f, 0f)

        val petColor = when (petIndex) {
            1 -> floatArrayOf(0.86f, 0.96f, 1.0f) // Snow Fox
            2 -> floatArrayOf(1.0f, 0.76f, 0.92f) // Mini Unicorn
            3 -> floatArrayOf(0.52f, 0.88f, 0.62f) // Baby Dragon
            else -> floatArrayOf(1.0f, 0.64f, 0.52f) // Coral Seahorse
        }

        // Cute 3D pet body + head + glossy eyes
        drawChildPart(petMatrix, 0f, -0.14f, 0f, sx = 0.22f, sy = 0.20f, sz = 0.24f, mesh = sphereMesh, color = petColor, spec = 0.35f)
        drawChildPart(petMatrix, 0f, 0.12f, 0.05f, sx = 0.24f, sy = 0.22f, sz = 0.23f, mesh = sphereMesh, color = petColor, spec = 0.35f)
        drawChildPart(petMatrix, -0.09f, 0.15f, 0.24f, sx = 0.045f, sy = 0.05f, sz = 0.04f, mesh = sphereMesh, color = floatArrayOf(0.1f, 0.12f, 0.2f), spec = 0.9f)
        drawChildPart(petMatrix, 0.09f, 0.15f, 0.24f, sx = 0.045f, sy = 0.05f, sz = 0.04f, mesh = sphereMesh, color = floatArrayOf(0.1f, 0.12f, 0.2f), spec = 0.9f)

        if (petIndex == 2) {
            // Mini golden unicorn horn
            drawChildPart(petMatrix, 0f, 0.36f, 0.12f, sx = 0.18f, sy = 0.18f, sz = 0.18f, mesh = hornMesh, color = floatArrayOf(1f, 0.85f, 0.3f), spec = 0.9f)
        } else {
            // Cute perky ears / fins
            drawChildPart(petMatrix, -0.14f, 0.32f, 0.04f, rz = 18f, sx = 0.22f, sy = 0.14f, sz = 0.18f, mesh = hornMesh, color = petColor, spec = 0.3f)
            drawChildPart(petMatrix, 0.14f, 0.32f, 0.04f, rz = -18f, sx = 0.22f, sy = 0.14f, sz = 0.18f, mesh = hornMesh, color = petColor, spec = 0.3f)
        }
    }

    private fun drawChildPart(
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
        mesh: GlIndexedMesh,
        color: FloatArray,
        shininess: Float = 28f,
        spec: Float = 0.25f,
        alpha: Float = 1f,
    ) {
        System.arraycopy(parent, 0, partMatrix, 0, 16)
        Matrix.translateM(partMatrix, 0, tx, ty, tz)
        if (rx != 0f) Matrix.rotateM(partMatrix, 0, rx, 1f, 0f, 0f)
        if (ry != 0f) Matrix.rotateM(partMatrix, 0, ry, 0f, 1f, 0f)
        if (rz != 0f) Matrix.rotateM(partMatrix, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(partMatrix, 0, sx, sy, sz)
        drawMesh(mesh, partMatrix, color, shininess, spec, alpha)
    }

    private fun drawMesh(
        mesh: GlIndexedMesh,
        model: FloatArray,
        color: FloatArray,
        shininess: Float,
        spec: Float,
        alpha: Float,
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, model, 0)
        GLES20.glUniform3fv(uBaseColorLoc, 1, color, 0)
        GLES20.glUniform3f(uSpecParamsLoc, shininess, spec, alpha)

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
            uniform vec3 uSpecParams;
            varying vec3 vWorldNormal;
            varying vec3 vWorldPos;
            void main() {
                vec3 N = normalize(vWorldNormal);
                vec3 V = normalize(vec3(0.0, 0.35, 5.45) - vWorldPos);
                vec3 L = normalize(vec3(0.55, 0.78, 0.85));
                vec3 H = normalize(L + V);

                float halfLambert = dot(N, L) * 0.5 + 0.5;
                float wrapDiffuse = smoothstep(0.05, 0.98, halfLambert);
                vec3 hemiFill = mix(vec3(0.46, 0.42, 0.58), vec3(1.0, 0.98, 0.96), N.y * 0.5 + 0.5);

                float spec = pow(max(dot(N, H), 0.0), uSpecParams.x) * uSpecParams.y;
                float rim = pow(1.0 - max(dot(N, V), 0.0), 2.5) * 0.42;

                vec3 lit = uBaseColor * (0.42 * hemiFill + 0.72 * wrapDiffuse) + vec3(spec) + uRimColor * rim;
                gl_FragColor = vec4(clamp(lit, 0.0, 1.0), uSpecParams.z);
            }
        """
    }
}

private class GlIndexedMesh(
    val vertexBuffer: FloatBuffer,
    val indexBuffer: ShortBuffer,
    val indexCount: Int,
)

private object MeshBuilder {
    fun createSphere(latBands: Int, lonBands: Int): GlIndexedMesh {
        val verts = FloatArray((latBands + 1) * (lonBands + 1) * 6)
        var vPtr = 0
        for (lat in 0..latBands) {
            val theta = lat * PI / latBands
            val sinTheta = sin(theta).toFloat()
            val cosTheta = cos(theta).toFloat()
            for (lon in 0..lonBands) {
                val phi = lon * 2.0 * PI / lonBands
                val x = (cos(phi) * sinTheta).toFloat()
                val y = cosTheta
                val z = (sin(phi) * sinTheta).toFloat()
                verts[vPtr++] = x
                verts[vPtr++] = y
                verts[vPtr++] = z
                verts[vPtr++] = x
                verts[vPtr++] = y
                verts[vPtr++] = z
            }
        }
        val indices = ShortArray(latBands * lonBands * 6)
        var iPtr = 0
        for (lat in 0 until latBands) {
            for (lon in 0 until lonBands) {
                val first = (lat * (lonBands + 1) + lon).toShort()
                val second = (first + lonBands + 1).toShort()
                indices[iPtr++] = first
                indices[iPtr++] = second
                indices[iPtr++] = (first + 1).toShort()
                indices[iPtr++] = second
                indices[iPtr++] = (second + 1).toShort()
                indices[iPtr++] = (first + 1).toShort()
            }
        }
        return toGlMesh(verts, indices)
    }

    fun createFrustum(slices: Int, topRadius: Float, bottomRadius: Float): GlIndexedMesh {
        val verts = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        val slopeY = (bottomRadius - topRadius) * 0.5f

        for (ring in 0..1) {
            val y = if (ring == 0) 1f else -1f
            val r = if (ring == 0) topRadius else bottomRadius
            for (s in 0..slices) {
                val a = (s * 2.0 * PI / slices).toFloat()
                val cx = cos(a)
                val sz = sin(a)
                val len = sqrt(cx * cx + slopeY * slopeY + sz * sz).coerceAtLeast(0.001f)
                verts.addAll(listOf(cx * r, y, sz * r, cx / len, slopeY / len, sz / len))
            }
        }
        for (s in 0 until slices) {
            val top0 = s.toShort()
            val top1 = (s + 1).toShort()
            val bot0 = (s + slices + 1).toShort()
            val bot1 = (s + slices + 2).toShort()
            indices.addAll(listOf(top0, bot0, top1, bot0, bot1, top1))
        }
        return toGlMesh(verts.toFloatArray(), indices.toShortArray())
    }

    fun createTorus(majorSegs: Int, minorSegs: Int, majorRadius: Float, minorRadius: Float): GlIndexedMesh {
        val verts = FloatArray((majorSegs + 1) * (minorSegs + 1) * 6)
        var vp = 0
        for (i in 0..majorSegs) {
            val u = (i * 2.0 * PI / majorSegs).toFloat()
            val cu = cos(u)
            val su = sin(u)
            for (j in 0..minorSegs) {
                val v = (j * 2.0 * PI / minorSegs).toFloat()
                val cv = cos(v)
                val sv = sin(v)
                val nx = cu * cv
                val ny = su * cv
                val nz = sv
                verts[vp++] = (majorRadius + minorRadius * cv) * cu
                verts[vp++] = (majorRadius + minorRadius * cv) * su
                verts[vp++] = minorRadius * sv
                verts[vp++] = nx
                verts[vp++] = ny
                verts[vp++] = nz
            }
        }
        val indices = ShortArray(majorSegs * minorSegs * 6)
        var ip = 0
        for (i in 0 until majorSegs) {
            for (j in 0 until minorSegs) {
                val a = (i * (minorSegs + 1) + j).toShort()
                val b = ((i + 1) * (minorSegs + 1) + j).toShort()
                indices[ip++] = a
                indices[ip++] = b
                indices[ip++] = (a + 1).toShort()
                indices[ip++] = b
                indices[ip++] = (b + 1).toShort()
                indices[ip++] = (a + 1).toShort()
            }
        }
        return toGlMesh(verts, indices)
    }

    private fun toGlMesh(verts: FloatArray, indices: ShortArray): GlIndexedMesh {
        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(verts).position(0)
        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(indices).position(0)
        return GlIndexedMesh(vb, ib, indices.size)
    }
}
