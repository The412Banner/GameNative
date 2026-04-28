package app.gamenative.utils

import android.content.Context
import app.gamenative.service.SteamService
import com.winlator.container.Container
import com.winlator.core.FileUtils
import com.winlator.core.envvars.EnvVars
import java.io.File
import java.util.Locale
import timber.log.Timber
import kotlin.jvm.JvmStatic

/**
 * Manages the lsfg-vk Vulkan implicit layer for frame generation.
 *
 * The layer works by intercepting vkQueuePresentKHR inside the container's
 * Vulkan driver and running Lossless Scaling frame generation (LSFG_3_1 /
 * LSFG_3_1P) transparently. No overlay, no MediaProjection — it hooks the
 * real swapchain presentation path.
 *
 * Flow:
 * 1. At launch time: install the layer .so + manifest into the container's
 *    filesystem where the Vulkan loader discovers implicit layers.
 * 2. Copy Lossless.dll from the Steam install dir (app 993090) into the
 *    container's ~/.local/share/lsfg-vk/ directory.
 * 3. Write conf.toml with the DLL path, multiplier, flow scale, and
 *    performance mode. Set env vars so the layer finds its config.
 * 4. At runtime: the Vulkan loader loads the layer, which hooks
 *    vkCreateSwapchainKHR / vkQueuePresentKHR and runs framegen on the
 *    game's actual swapchain images.
 */
object LsfgVkManager {
    private const val TAG = "LsfgVkManager"

    // Steam app ID for Lossless Scaling (used to auto-find the DLL)
    private const val LOSSLESS_SCALING_APP_ID = 993090
    private const val LOSSLESS_DLL_NAME = "Lossless.dll"

    // Paths inside the container's HOME (relative to rootDir)
    private const val CONFIG_RELATIVE_PATH = ".config/lsfg-vk/conf.toml"
    private const val LIB_RELATIVE_DIR = ".local/lib"
    private const val LAYER_RELATIVE_DIR = ".local/share/vulkan/implicit_layer.d"
    private const val DLL_RELATIVE_DIR = ".local/share/lsfg-vk"
    private const val LIB_FILENAME = "liblsfg-vk-layer.so"
    private const val MANIFEST_FILENAME = "VkLayer_LS_frame_generation.json"
    private const val VERSION_FILENAME = ".lsfg_vk_runtime_version"

    // Relative path from implicit_layer.d back to lib/
    private const val MANIFEST_LIBRARY_PATH = "../../../lib/$LIB_FILENAME"

    // Process identifier written to conf.toml [[game]] exe field.
    // Under Wine, /proc/self/exe points to the Wine loader, so we use this
    // stable identifier instead. Set via LSFG_PROCESS env var.
    private const val PROCESS_EXE_IDENTIFIER = "gamenative-lsfg"

    // Container extra keys
    const val EXTRA_ARMED = "lsfgEnabled"
    const val EXTRA_MULTIPLIER = "lsfgMultiplier"
    const val EXTRA_FLOW_SCALE = "lsfgFlowScale"
    const val EXTRA_PERFORMANCE_MODE = "lsfgPerformanceMode"

    // Environment variables consumed by the lsfg-vk layer
    private const val ENV_DISABLE = "DISABLE_LSFG"
    private const val ENV_CONFIG = "LSFG_CONFIG"
    private const val ENV_PROCESS = "LSFG_PROCESS"

    // Current runtime version (bumped when the bundled .so changes)
    private const val RUNTIME_VERSION = "v1.4.0-android-arm64-v8a-ahb-no-props"

    // Asset paths
    private const val ASSET_DIR = "lsfg_vk/android_arm64_v8a"
    private const val ASSET_LIB = "$ASSET_DIR/$LIB_FILENAME"
    private const val ASSET_MANIFEST = "$ASSET_DIR/$MANIFEST_FILENAME"

    // ---- Public API --------------------------------------------------------

    /** Whether LSFG is supported for this container's variant. */
    @JvmStatic
    fun isSupported(container: Container): Boolean =
        container.containerVariant.equals(Container.BIONIC, ignoreCase = true)

    /** Whether LSFG is armed (enabled + Lossless.dll found) for this container. */
    @JvmStatic
    fun isArmed(container: Container): Boolean =
        isSupported(container) &&
            parseBool(container.getExtra(EXTRA_ARMED, "false")) &&
            isDllAvailable()

    /** Whether Lossless Scaling is installed (Lossless.dll exists in Steam dir). */
    @JvmStatic
    fun isDllAvailable(): Boolean = findSteamDll() != null

    /** Get the auto-resolved DLL path inside the container, or null. */
    @JvmStatic
    fun containerDllPath(container: Container): String? {
        if (!isDllAvailable()) return null
        return File(container.rootDir, "$DLL_RELATIVE_DIR/$LOSSLESS_DLL_NAME").absolutePath
    }

    /** Get the multiplier (2-4, default 2). */
    fun multiplier(container: Container): Int =
        container.getExtra(EXTRA_MULTIPLIER, "2").toIntOrNull()?.coerceIn(2, 4) ?: 2

    /** Get the flow scale (0.25-1.0, default 1.0). */
    fun flowScale(container: Container): Float =
        container.getExtra(EXTRA_FLOW_SCALE, "1.0").toFloatOrNull()?.coerceIn(0.25f, 1.0f) ?: 1.0f

    /** Get whether performance mode is enabled. */
    fun performanceMode(container: Container): Boolean =
        parseBool(container.getExtra(EXTRA_PERFORMANCE_MODE, "false"))

    /**
     * Install the layer runtime + DLL into the container's filesystem.
     * Called during container startup in BionicProgramLauncherComponent.
     *
     * Installs:
     * - liblsfg-vk-layer.so → ~/.local/lib/
     * - VkLayer_LS_frame_generation.json → ~/.local/share/vulkan/implicit_layer.d/
     * - Lossless.dll → ~/.local/share/lsfg-vk/  (copied from Steam install dir)
     *
     * Uses versioned caching to skip redundant copies.
     *
     * @return true if installation succeeded or was already up-to-date
     */
    @JvmStatic
    fun ensureRuntimeInstalled(context: Context, container: Container): Boolean {
        if (!isSupported(container)) return false

        val rootDir = container.rootDir
        val localLibDir = File(rootDir, LIB_RELATIVE_DIR)
        val layerDir = File(rootDir, LAYER_RELATIVE_DIR)
        val dllDir = File(rootDir, DLL_RELATIVE_DIR)
        val libFile = File(localLibDir, LIB_FILENAME)
        val manifestFile = File(layerDir, MANIFEST_FILENAME)
        val versionFile = File(layerDir, VERSION_FILENAME)

        val installedVersion = versionFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        val needsInstall = installedVersion != RUNTIME_VERSION ||
            !libFile.isFile || !manifestFile.isFile

        var success = true

        if (needsInstall) {
            try {
                localLibDir.mkdirs()
                layerDir.mkdirs()

                // Copy the layer .so from assets
                FileUtils.copy(context, ASSET_LIB, libFile)
                // Write the manifest with patched library_path
                val manifestText = context.assets.open(ASSET_MANIFEST)
                    .bufferedReader().use { it.readText() }
                    .replace(
                        "\"library_path\": \"$LIB_FILENAME\"",
                        "\"library_path\": \"$MANIFEST_LIBRARY_PATH\""
                    )
                FileUtils.writeString(manifestFile, manifestText)
                FileUtils.writeString(versionFile, RUNTIME_VERSION)

                // Set executable permissions
                if (libFile.exists()) FileUtils.chmod(libFile, 0b111101101)
                if (manifestFile.exists()) FileUtils.chmod(manifestFile, 0b110100100)
                if (versionFile.exists()) FileUtils.chmod(versionFile, 0b110100100)

                val ok = libFile.isFile && manifestFile.isFile
                if (ok) {
                    Timber.tag(TAG).i("Installed LSFG runtime %s into %s", RUNTIME_VERSION, rootDir)
                } else {
                    Timber.tag(TAG).e("Runtime installation verification failed")
                    success = false
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to install LSFG runtime")
                success = false
            }
        } else {
            Timber.tag(TAG).d("Runtime %s already installed in %s", RUNTIME_VERSION, rootDir)
        }

        // Copy Lossless.dll from Steam install dir into the container
        val dllFile = File(dllDir, LOSSLESS_DLL_NAME)
        val steamDll = findSteamDll()
        if (steamDll != null) {
            try {
                if (!dllFile.isFile || dllFile.length() != steamDll.length()) {
                    dllDir.mkdirs()
                    steamDll.inputStream().use { input ->
                        dllFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (dllFile.exists()) FileUtils.chmod(dllFile, 0b110100100)
                    Timber.tag(TAG).i("Copied Lossless.dll (%d bytes) into %s", dllFile.length(), dllDir)
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to copy Lossless.dll into container")
                success = false
            }
        } else if (parseBool(container.getExtra(EXTRA_ARMED, "false"))) {
            Timber.tag(TAG).w("LSFG enabled but Lossless.dll not found in Steam dir")
            success = false
        }

        return success
    }

    /**
     * Write the lsfg-vk conf.toml for this container.
     * The layer reads this on init to find the DLL and game settings.
     *
     * @return true if the config was written successfully
     */
    @JvmStatic
    fun writeConfig(container: Container): Boolean {
        if (!isSupported(container)) return false

        return try {
            val dllPath = containerDllPath(container)
            val armed = parseBool(container.getExtra(EXTRA_ARMED, "false")) && dllPath != null
            val configFile = File(container.rootDir, CONFIG_RELATIVE_PATH)
            val configText = buildConfigToml(
                dllPath = dllPath,
                enabled = armed,
                multiplier = multiplier(container),
                flowScale = flowScale(container),
                performanceMode = performanceMode(container),
            )
            val ok = FileUtils.writeString(configFile, configText)
            if (ok && configFile.exists()) {
                FileUtils.chmod(configFile, 0b110100100)
            }
            ok
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to write LSFG conf.toml")
            false
        }
    }

    /**
     * Apply LSFG-related environment variables to the launch environment.
     * Called during container startup in BionicProgramLauncherComponent.
     *
     * @return true if LSFG is armed and env vars were applied
     */
    /** Stale manifest from the old research/lsfgvk-poc branch. */
    private const val STALE_MANIFEST_FILENAME = "VkLayer_LSFGVK_frame_generation.json"

    @JvmStatic
    fun applyLaunchEnv(container: Container, envVars: EnvVars): Boolean {
        // Clear any stale env vars first
        envVars.remove(ENV_DISABLE)
        envVars.remove(ENV_CONFIG)
        envVars.remove(ENV_PROCESS)

        // Always clean up stale layer artifacts from old research branch
        cleanupStaleLayerFiles(container)

        if (!isSupported(container)) {
            // Remove the manifest so the Vulkan loader can't find the layer
            disableLayerInContainer(container)
            return false
        }

        val dllPath = containerDllPath(container)
        val armed = parseBool(container.getExtra(EXTRA_ARMED, "false")) && dllPath != null

        if (!armed) {
            // Remove the manifest so the Vulkan loader can't find the layer
            disableLayerInContainer(container)
            Timber.tag(TAG).i("LSFG disabled (enabled=%s, dll=%s)",
                container.getExtra(EXTRA_ARMED, "false"), dllPath ?: "null")
            return false
        }

        envVars.put(ENV_CONFIG, configFile(container).absolutePath)
        envVars.put(ENV_PROCESS, PROCESS_EXE_IDENTIFIER)

        // Add the container's implicit_layer.d to VK_LAYER_PATH so the
        // Vulkan loader discovers the lsfg-vk layer installed there.
        // The static VK_LAYER_PATH only covers /usr/share/vulkan/implicit_layer.d,
        // but we install the layer into the container's ~/.local/share/vulkan/.
        val containerLayerDir = File(container.rootDir, LAYER_RELATIVE_DIR)
        val existingLayerPath = envVars["VK_LAYER_PATH"] ?: ""
        if (existingLayerPath.isNotEmpty()) {
            envVars.put("VK_LAYER_PATH", "$existingLayerPath:${containerLayerDir.absolutePath}")
        } else {
            envVars.put("VK_LAYER_PATH", containerLayerDir.absolutePath)
        }

        Timber.tag(TAG).i(
            "LSFG armed: dll=%s, multiplier=%d, flowScale=%.2f, perf=%s",
            dllPath, multiplier(container), flowScale(container),
            if (performanceMode(container)) "on" else "off"
        )
        return true
    }

    /**
     * Remove the layer manifest so the Vulkan loader can't discover it.
     * Called when LSFG is disabled to ensure no stale layer is loaded.
     */
    private fun disableLayerInContainer(container: Container) {
        val layerDir = File(container.rootDir, LAYER_RELATIVE_DIR)
        val manifest = File(layerDir, MANIFEST_FILENAME)
        if (manifest.exists()) {
            manifest.delete()
            Timber.tag(TAG).d("Removed LSFG manifest to disable layer")
        }
    }

    /**
     * Clean up stale layer files from the old research/lsfgvk-poc branch.
     * The old branch used a different layer name (VK_LAYER_LSFGVK) and
     * different disable env var (DISABLE_LSFGVK vs DISABLE_LSFG).
     * These stale manifests cause the Vulkan loader to load the layer
     * even when LSFG is "disabled" in our new code.
     */
    private fun cleanupStaleLayerFiles(container: Container) {
        val layerDir = File(container.rootDir, LAYER_RELATIVE_DIR)
        val staleManifest = File(layerDir, STALE_MANIFEST_FILENAME)
        if (staleManifest.exists()) {
            staleManifest.delete()
            Timber.tag(TAG).i("Removed stale VK_LAYER_LSFGVK manifest from old research branch")
        }
    }

    // ---- DLL discovery -----------------------------------------------------

    /**
     * Find Lossless.dll in the Steam install directory for app 993090.
     * Returns the File if it exists, null otherwise.
     */
    private fun findSteamDll(): File? {
        val appDir = SteamService.getAppDirPath(LOSSLESS_SCALING_APP_ID)
        val dll = File(appDir, LOSSLESS_DLL_NAME)
        return dll.takeIf { it.isFile }
    }

    // ---- Helpers -----------------------------------------------------------

    private fun configFile(container: Container): File =
        File(container.rootDir, CONFIG_RELATIVE_PATH)

    private fun buildConfigToml(
        dllPath: String?,
        enabled: Boolean,
        multiplier: Int,
        flowScale: Float,
        performanceMode: Boolean,
    ): String = buildString {
        appendLine("version = 1")
        appendLine()
        appendLine("[global]")
        if (!dllPath.isNullOrBlank()) {
            appendLine("dll = ${tomlString(dllPath)}")
        }
        appendLine("no_fp16 = false")
        appendLine()

        if (enabled && !dllPath.isNullOrBlank()) {
            appendLine("[[game]]")
            appendLine("exe = ${tomlString(PROCESS_EXE_IDENTIFIER)}")
            appendLine("multiplier = ${multiplier.coerceIn(2, 4)}")
            appendLine("flow_scale = ${formatFlowScale(flowScale)}")
            appendLine("performance_mode = ${if (performanceMode) "true" else "false"}")
            appendLine("hdr_mode = false")
            appendLine("experimental_present_mode = ${tomlString("fifo")}")
        }
    }

    private fun tomlString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(ch)
            }
        }
        append('"')
    }

    /** Parse boolean from container extra (handles "true"/"false" and "1"/"0"). */
    private fun parseBool(value: String): Boolean =
        value.equals("true", ignoreCase = true) || value == "1"

    private fun formatFlowScale(value: Float): String =
        String.format(Locale.US, "%.2f", value.coerceIn(0.25f, 1.0f))
}
