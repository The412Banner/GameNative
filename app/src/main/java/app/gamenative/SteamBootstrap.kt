package app.gamenative

import android.util.Log

/**
 * Bootstraps the Linux-side libsteamclient.so inside the Android process so
 * that Proton's lsteamclient.dll (running in the Wine subprocess we launch
 * for "real Steam" mode) has something to talk to via the Steam IPC sockets
 * exposed by libsteamclient.so itself.
 *
 * This is implemented in native code (see app/src/main/cpp/steambootstrap)
 * because the dlopen + CreateInterface + CreateSteamPipe + ConnectToGlobalUser
 * dance has to happen in a single process where the .so's globals stay live.
 *
 * NOTE on HOME: nativeInit() calls setenv("HOME", ...) inside the Android
 * process (the only place that affects libsteamclient.so's view of HOME). The
 * Wine subprocess we spawn afterwards is launched with an explicit envp via
 * ProcessHelper.exec(...), so its HOME is unchanged by what we do here.
 */
object SteamBootstrap {
    private const val TAG = "SteamBootstrap"

    @Volatile
    private var initialized: Boolean = false

    init {
        try {
            System.loadLibrary("steambootstrap")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load libsteambootstrap.so", t)
        }
    }

    @JvmStatic
    private external fun nativeInit(
        libPath: String,
        home: String,
        steam3Master: String,
        steamClientService: String,
        extraEnv: Array<String>,
        accountName: String?,
        refreshToken: String?,
        steamId64: Long,
    ): Int

    @JvmStatic
    private external fun nativeShutdown()

    /**
     * Boot the native Steam client. Safe to call multiple times: only the
     * first call performs the dlopen + handshake; subsequent calls are no-ops
     * (the native side guards on the global client pointer).
     *
     * @param libsteamclientPath Absolute path to the linux/android libsteamclient.so on disk.
     * @param wineSteamRootLinux Linux path to the dir that mirrors the Wine
     *   `C:\Program Files (x86)` directory. libsteamclient.so reads HOME to
     *   locate `<HOME>/Steam/config/config.vdf`, so this should be the parent
     *   of `Steam/`.
     * @param steam3Master       Value to publish as the `Steam3Master` env var (e.g. "tcp:127.0.0.1:57343").
     * @param steamClientService Value to publish as the `SteamClientService` env var (e.g. "tcp:127.0.0.1:57344").
     * @param extraEnv           Additional env vars to setenv() before the dlopen
     *   (e.g. `_STEAM_SETENV_MANAGER`, `STEAM_BASE_FOLDER`, `BREAKPAD_DUMP_LOCATION`,
     *   `ENABLE_VK_LAYER_VALVE_steam_overlay_1`, `STEAMVIDEOTOKEN`, …).
     * @param accountName        Steam account login name (the username typed at the
     *   Steam login box, NOT the persona name and NOT the email). If null/blank,
     *   the explicit logon is skipped and we fall back to the engine's cached
     *   auto-logon attempt.
     * @param refreshToken       JWT-style refresh token from your existing app
     *   login (the same value normally cached in `config.vdf`'s ConnectCache).
     * @param steamId64          The 64-bit SteamID for that account. 0 disables
     *   the explicit logon path.
     * @return 0 on success, negative status code on failure (see steam_bootstrap.c).
     */
    fun start(
        libsteamclientPath: String,
        wineSteamRootLinux: String,
        steam3Master: String,
        steamClientService: String,
        extraEnv: Map<String, String> = emptyMap(),
        accountName: String? = null,
        refreshToken: String? = null,
        steamId64: Long = 0L,
    ): Int {
        if (initialized) {
            Log.i(TAG, "Already initialized; skipping nativeInit()")
            return 0
        }

        val flat = ArrayList<String>(extraEnv.size * 2)
        for ((k, v) in extraEnv) {
            flat += k
            flat += v
        }

        val rc = try {
            nativeInit(
                libsteamclientPath,
                wineSteamRootLinux,
                steam3Master,
                steamClientService,
                flat.toTypedArray(),
                accountName?.takeIf { it.isNotEmpty() },
                refreshToken?.takeIf { it.isNotEmpty() },
                steamId64,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "nativeInit threw", t)
            return -100
        }

        if (rc == 0) {
            initialized = true
            Log.i(TAG, "libsteamclient.so initialized via dlopen at $libsteamclientPath")
        } else {
            Log.e(TAG, "nativeInit failed with rc=$rc (libPath=$libsteamclientPath)")
        }
        return rc
    }

    /**
     * Tear down the native Steam pipe + global user without unloading
     * libsteamclient.so. Call when a real-Steam game session ends so a
     * subsequent [start] call gets a fresh pipe / user.
     *
     * Safe to call when [start] never ran (or failed): the native side guards
     * on the cached pipe / user globals.
     */
    fun stop() {
        if (!initialized) {
            Log.i(TAG, "stop() called but not initialized; skipping")
            return
        }
        try {
            nativeShutdown()
        } catch (t: Throwable) {
            Log.e(TAG, "nativeShutdown threw", t)
        } finally {
            initialized = false
        }
    }
}
