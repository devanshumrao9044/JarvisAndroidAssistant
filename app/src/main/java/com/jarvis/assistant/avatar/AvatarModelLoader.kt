package com.jarvis.assistant.avatar

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.utils.ModelViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads glTF 2.0 Binary (.glb) models from Android assets into Google Filament's ModelViewer,
 * validates binary headers, and inspects actual skeletal animations and facial morph targets.
 * Never claims a model is loaded if the asset file is missing or invalid.
 */
class AvatarModelLoader(private val context: Context) {

    fun listAvailableGlbAssets(): List<String> {
        return try {
            context.assets.list("models")
                ?.filter { it.endsWith(".glb", ignoreCase = true) }
                ?.map { "models/$it" }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun readValidatedGlbBuffer(assetPath: String): Result<ByteBuffer> =
        withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetPath).use { inputStream ->
                    val bytes = inputStream.readBytes()
                    if (bytes.size < 20) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Asset '$assetPath' is too small (${bytes.size} bytes) to be a valid GLB file.")
                        )
                    }
                    val directBuffer = ByteBuffer.allocateDirect(bytes.size).apply {
                        order(ByteOrder.nativeOrder())
                        put(bytes)
                        rewind()
                    }
                    val magic = directBuffer.getInt(0)
                    val version = directBuffer.getInt(4)
                    if (magic != GLB_MAGIC_HEADER || version != 2) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Asset '$assetPath' does not have a valid glTF 2.0 binary (.glb) header.")
                        )
                    }
                    directBuffer.rewind()
                    Result.success(directBuffer)
                }
            } catch (_: FileNotFoundException) {
                Result.failure(FileNotFoundException("Model file '$assetPath' is not yet placed in app/src/main/assets/models/."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun applyBufferToModelViewer(
        assetPath: String,
        buffer: ByteBuffer,
        modelViewer: ModelViewer
    ): AvatarModelLoadState {
        return try {
            modelViewer.destroyModel()
            modelViewer.loadModelGlb(buffer)
            modelViewer.transformToUnitCube()

            val asset: FilamentAsset = modelViewer.asset
                ?: return AvatarModelLoadState.Error(
                    assetPath = assetPath,
                    reason = "Filament failed to parse GLB entities from '$assetPath'."
                )

            val animator = modelViewer.animator
            val clipNames = mutableListOf<String>()
            val clipCount = animator?.animationCount ?: 0
            for (i in 0 until clipCount) {
                val name = animator?.getAnimationName(i)?.ifBlank { "Clip_$i" } ?: "Clip_$i"
                clipNames.add(name)
            }

            val totalMorphTargets = inspectMorphTargetCount(modelViewer.engine, asset)

            AvatarModelLoadState.LoadedGlbModel(
                assetPath = assetPath,
                animationClips = clipNames,
                entityCount = asset.entities.size,
                hasFacialMorphTargets = totalMorphTargets > 0,
                detectedMorphTargetCount = totalMorphTargets
            )
        } catch (e: Exception) {
            AvatarModelLoadState.Error(
                assetPath = assetPath,
                reason = "GLB load error: ${e.localizedMessage ?: "Incompatible glTF structure"}"
            )
        }
    }

    private fun inspectMorphTargetCount(engine: Engine, asset: FilamentAsset): Int {
        val rcm = engine.renderableManager
        var maxMorphCount = 0
        for (entity in asset.entities) {
            if (rcm.hasComponent(entity)) {
                val instance = rcm.getInstance(entity)
                val count = rcm.getMorphTargetCount(instance)
                if (count > maxMorphCount) {
                    maxMorphCount = count
                }
            }
        }
        return maxMorphCount
    }

    companion object {
        private const val GLB_MAGIC_HEADER = 0x46546C67
    }
}
