package com.jarvis.assistant.avatar
import android.annotation.SuppressLint
import android.content.Context
import android.view.Choreographer
import android.view.SurfaceView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
class AvatarRenderer(
    context: Context,
    private val scope: CoroutineScope,
    val expressionController: AvatarExpressionController = AvatarExpressionController(),
    val animationController: AvatarAnimationController = AvatarAnimationController()
) {
    private val appContext = context.applicationContext
    val modelLoader = AvatarModelLoader(appContext)
    val voiceSynchronizer = AvatarVoiceSynchronizer(expressionController, animationController)
    private val _configuration = MutableStateFlow(AvatarConfiguration())
    val configuration: StateFlow<AvatarConfiguration> = _configuration.asStateFlow()
    private val _modelLoadState = MutableStateFlow<AvatarModelLoadState>(AvatarModelLoadState.Initializing)
    val modelLoadState: StateFlow<AvatarModelLoadState> = _modelLoadState.asStateFlow()
    private val _evaluatedPose = MutableStateFlow(AvatarEvaluatedPose())
    val evaluatedPose: StateFlow<AvatarEvaluatedPose> = _evaluatedPose.asStateFlow()
    private var choreographer: Choreographer? = null
    private var uiHelper: UiHelper? = null
    private var modelViewer: ModelViewer? = null
    private var attachedLifecycle: Lifecycle? = null
    private var isRenderingActive = false
    private var lastFrameTimeNanos = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRenderingActive) return
            choreographer?.postFrameCallback(this)
            val config = _configuration.value
            val minFrameIntervalNanos = 1_000_000_000L / config.performanceTier.targetFps.coerceIn(15, 60)
            val elapsedNanos = if (lastFrameTimeNanos == 0L) {
                minFrameIntervalNanos
            } else {
                frameTimeNanos - lastFrameTimeNanos
            }
            if (elapsedNanos < minFrameIntervalNanos * 88L / 100L) {
                return
            }
            lastFrameTimeNanos = frameTimeNanos
            val deltaSeconds = (elapsedNanos / 1_000_000_000.0f).coerceIn(0.005f, 0.1f)
            val viewer = modelViewer
            val loadedAsset = viewer?.asset
            val pose = animationController.updateAnimationFrame(
                deltaSeconds = deltaSeconds,
                config = config,
                expressionController = expressionController,
                filamentAnimator = viewer?.animator
            )
            _evaluatedPose.value = pose
            if (viewer != null && loadedAsset != null) {
                val state = _modelLoadState.value
                if (state is AvatarModelLoadState.LoadedGlbModel && state.hasFacialMorphTargets) {
                    expressionController.applyMorphTargetsToFilamentAsset(
                        engine = viewer.engine,
                        asset = loadedAsset,
                        pose = pose
                    )
                }
                try {
                    viewer.render(frameTimeNanos)
                } catch (_: Exception) {
                }
            }
        }
    }
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            startRenderLoop()
        }
        override fun onPause(owner: LifecycleOwner) {
            stopRenderLoop()
        }
        override fun onDestroy(owner: LifecycleOwner) {
            releaseGpuResources()
        }
    }
    init {
        ensureFilamentInitialized()
        probeAndLoadConfiguredModel()
    }
    fun updateConfiguration(newConfig: AvatarConfiguration) {
        val assetChanged = _configuration.value.primaryGlbAssetPath != newConfig.primaryGlbAssetPath
        _configuration.value = newConfig
        applyQualitySettingsToViewer()
        if (assetChanged) {
            probeAndLoadConfiguredModel()
        }
    }
    fun probeAndLoadConfiguredModel() {
        val assetPath = _configuration.value.primaryGlbAssetPath
        _modelLoadState.value = AvatarModelLoadState.LoadingFromAssets(assetPath)
        scope.launch {
            val bufferResult = modelLoader.readValidatedGlbBuffer(assetPath)
            bufferResult.onSuccess { directBuffer ->
                val viewer = modelViewer
                if (viewer != null) {
                    _modelLoadState.value = modelLoader.applyBufferToModelViewer(
                        assetPath = assetPath,
                        buffer = directBuffer,
                        modelViewer = viewer
                    )
                } else {
                    _modelLoadState.value = AvatarModelLoadState.LoadingFromAssets(assetPath)
                }
            }.onFailure { err ->
                val isMissingFile = err is java.io.FileNotFoundException
                if (isMissingFile) {
                    _modelLoadState.value = AvatarModelLoadState.AwaitingModelAsset(
                        expectedAssetPath = assetPath,
                        detailMessage = "Place your character GLB at app/src/main/assets/$assetPath. Interactive 3D Holo-Rig active."
                    )
                } else {
                    _modelLoadState.value = AvatarModelLoadState.Error(
                        assetPath = assetPath,
                        reason = err.message ?: "Failed to load 3D model."
                    )
                }
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    fun attachSurfaceView(surfaceView: SurfaceView, lifecycle: Lifecycle) {
        if (attachedLifecycle != lifecycle) {
            attachedLifecycle?.removeObserver(lifecycleObserver)
            attachedLifecycle = lifecycle
            lifecycle.addObserver(lifecycleObserver)
        }
        if (choreographer == null) {
            choreographer = Choreographer.getInstance()
        }
        try {
            if (modelViewer == null) {
                val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
                    isOpaque = false
                }
                uiHelper = helper
                val viewer = ModelViewer(surfaceView = surfaceView, uiHelper = helper)
                surfaceView.setOnTouchListener { _, event ->
                    viewer.onTouchEvent(event)
                    true
                }
                viewer.scene.skybox = null
                viewer.view.blendMode = View.BlendMode.TRANSLUCENT
                viewer.renderer.clearOptions = viewer.renderer.clearOptions.apply {
                    clear = true
                }
                modelViewer = viewer
                applyQualitySettingsToViewer()
            }
            probeAndLoadConfiguredModel()
            startRenderLoop()
        } catch (e: Exception) {
            _modelLoadState.value = AvatarModelLoadState.Error(
                assetPath = _configuration.value.primaryGlbAssetPath,
                reason = "OpenGL ES 3.0 initialization notice: ${e.localizedMessage ?: "Using procedural 3D canvas"}"
            )
            startRenderLoop()
        }
    }
    fun startRenderLoop() {
        if (isRenderingActive) return
        if (choreographer == null) {
            choreographer = Choreographer.getInstance()
        }
        isRenderingActive = true
        lastFrameTimeNanos = 0L
        choreographer?.postFrameCallback(frameCallback)
    }
    fun stopRenderLoop() {
        isRenderingActive = false
        choreographer?.removeFrameCallback(frameCallback)
    }
    fun releaseGpuResources() {
        stopRenderLoop()
        attachedLifecycle?.removeObserver(lifecycleObserver)
        attachedLifecycle = null
        try {
            modelViewer?.destroyModel()
        } catch (_: Exception) {}
        modelViewer = null
        uiHelper = null
    }
    private fun applyQualitySettingsToViewer() {
        val viewer = modelViewer ?: return
        val tier = _configuration.value.performanceTier
        try {
            viewer.view.apply {
                renderQuality = renderQuality.apply {
                    hdrColorBuffer = View.QualityLevel.MEDIUM
                }
                dynamicResolutionOptions = dynamicResolutionOptions.apply {
                    enabled = tier.enableDynamicResolution
                    quality = View.QualityLevel.MEDIUM
                }
                antiAliasing = View.AntiAliasing.FXAA
                ambientOcclusionOptions = ambientOcclusionOptions.apply {
                    enabled = tier.enableBloomAndAo
                }
                bloomOptions = bloomOptions.apply {
                    enabled = tier.enableBloomAndAo
                }
            }
        } catch (_: Exception) {}
    }
    companion object {
        @Volatile
        private var isFilamentInitialized = false
        private fun ensureFilamentInitialized() {
            if (!isFilamentInitialized) {
                synchronized(this) {
                    if (!isFilamentInitialized) {
                        runCatching {
                            Utils.init()
                            isFilamentInitialized = true
                        }
                    }
                }
            }
        }
    }
}
