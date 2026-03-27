package app.gamenative.ui.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.DownloadInfo
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.db.dao.EpicGameDao
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.events.AndroidEvent
import app.gamenative.service.SteamService
import app.gamenative.service.amazon.AmazonConstants
import app.gamenative.service.amazon.AmazonService
import app.gamenative.service.epic.EpicConstants
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGConstants
import app.gamenative.service.gog.GOGService
import app.gamenative.ui.data.CancelConfirmation
import app.gamenative.ui.data.DownloadItemState
import app.gamenative.ui.data.DownloadItemStatus
import app.gamenative.ui.data.DownloadsState
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.CustomGameScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val steamAppDao: SteamAppDao,
    private val epicGameDao: EpicGameDao,
    private val gogGameDao: GOGGameDao,
    private val amazonGameDao: AmazonGameDao,
) : ViewModel() {
    private data class ProgressBinding(
        val info: DownloadInfo,
        val listener: (Float) -> Unit,
    )

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    private val gameNameCache = ConcurrentHashMap<String, String>()
    private val gameIconCache = ConcurrentHashMap<String, String>()
    private val progressBindings = ConcurrentHashMap<String, ProgressBinding>()
    private val activeDownloads = ConcurrentHashMap<String, DownloadItemState>()

    private val finishedDownloads = ConcurrentHashMap<String, DownloadItemState>()
    private val pausedDownloads = ConcurrentHashMap.newKeySet<String>()
    private val pendingCancelledDownloads = ConcurrentHashMap.newKeySet<String>()
    private val recentFailureMessages = ConcurrentHashMap<String, String>()
    @Volatile
    private var isScreenVisible = false

    @Volatile
    private var lastTrackedDownloads: Map<String, DownloadItemState> = emptyMap()

    private val onDownloadStatusChanged: (AndroidEvent.DownloadStatusChanged) -> Unit = { event ->
        if (isScreenVisible) {
            viewModelScope.launch(Dispatchers.IO) {
                handleDownloadStatusChanged(event)
            }
        }
    }

    init {
        PluviaApp.events.on<AndroidEvent.DownloadStatusChanged, Unit>(onDownloadStatusChanged)
    }

    override fun onCleared() {
        PluviaApp.events.off<AndroidEvent.DownloadStatusChanged, Unit>(onDownloadStatusChanged)
        detachAllProgressListeners()
        super.onCleared()
    }

    private fun downloadKey(gameSource: GameSource, appId: String): String = "${gameSource.name}_$appId"

    fun onScreenVisible() {
        if (isScreenVisible) return
        isScreenVisible = true
        viewModelScope.launch(Dispatchers.IO) {
            syncProgressBindingsWithServices()
            syncPartialDownloadsForAllSources()
            publishCombinedState()
        }
    }

    fun onScreenHidden() {
        isScreenVisible = false
        detachAllProgressListeners()
    }

    private suspend fun handleDownloadStatusChanged(@Suppress("UNUSED_PARAMETER") event: AndroidEvent.DownloadStatusChanged) {
        // DownloadStatusChanged does not include source-specific canonical IDs.
        // Reconcile active bindings from all services on each status edge.
        syncProgressBindingsWithServices()
        syncPartialDownloadsForAllSources()
        publishCombinedState()
    }

    private suspend fun updateActiveDownloadItem(gameSource: GameSource, appId: String, info: DownloadInfo) {
        val metadata = when (gameSource) {
            GameSource.STEAM -> appId.toIntOrNull()?.let { getSteamMetadata(it) }
            GameSource.EPIC -> appId.toIntOrNull()?.let { getEpicMetadata(it) }
            GameSource.GOG -> getGOGMetadata(appId)
            GameSource.AMAZON -> getAmazonMetadata(appId)
            GameSource.CUSTOM_GAME -> null
        } ?: return

        val item = buildActiveDownloadItem(
            appId = appId,
            gameSource = gameSource,
            gameName = metadata.first,
            iconUrl = metadata.second,
            info = info,
        )
        activeDownloads[item.uniqueId] = item
        finishedDownloads.remove(item.uniqueId)
    }

    private fun attachProgressListener(key: String, gameSource: GameSource, appId: String, info: DownloadInfo) {
        if (progressBindings.containsKey(key)) return
        val listener: (Float) -> Unit = listener@{
            if (!isScreenVisible) return@listener
            viewModelScope.launch(Dispatchers.IO) {
                updateActiveDownloadItem(gameSource, appId, info)
                publishCombinedState()
            }
        }
        info.addProgressListener(listener)
        progressBindings[key] = ProgressBinding(info, listener)
    }

    private fun detachProgressListener(key: String) {
        progressBindings.remove(key)?.let { binding ->
            binding.info.removeProgressListener(binding.listener)
        }
    }

    private fun detachAllProgressListeners() {
        progressBindings.values.forEach { binding ->
            binding.info.removeProgressListener(binding.listener)
        }
        progressBindings.clear()
    }

    private suspend fun syncProgressBindingsWithServices() {
        val activeInfos = LinkedHashMap<String, Pair<DownloadInfo, Pair<GameSource, String>>>()

        SteamService.getActiveDownloads().forEach { (appId, info) ->
            val appIdString = appId.toString()
            activeInfos[downloadKey(GameSource.STEAM, appIdString)] = info to (GameSource.STEAM to appIdString)
        }
        EpicService.getActiveDownloads().forEach { (appId, info) ->
            val appIdString = appId.toString()
            activeInfos[downloadKey(GameSource.EPIC, appIdString)] = info to (GameSource.EPIC to appIdString)
        }
        GOGService.getActiveDownloads().forEach { (gameId, info) ->
            activeInfos[downloadKey(GameSource.GOG, gameId)] = info to (GameSource.GOG to gameId)
        }
        AmazonService.getActiveDownloads().forEach { (productId, info) ->
            activeInfos[downloadKey(GameSource.AMAZON, productId)] = info to (GameSource.AMAZON to productId)
        }

        activeInfos.forEach { (key, value) ->
            val (info, pair) = value
            attachProgressListener(key, pair.first, pair.second, info)
            updateActiveDownloadItem(pair.first, pair.second, info)
        }

        val staleKeys = progressBindings.keys - activeInfos.keys
        staleKeys.forEach { key ->
            detachProgressListener(key)
            finalizeStoppedDownload(key)
        }
    }

    private suspend fun syncPartialDownloadsForAllSources() {
        syncPartialDownloadsForSource(GameSource.STEAM)
        syncPartialDownloadsForSource(GameSource.EPIC)
        syncPartialDownloadsForSource(GameSource.GOG)
        syncPartialDownloadsForSource(GameSource.AMAZON)
    }

    private suspend fun syncPartialDownloadsForSource(gameSource: GameSource) {
        val partialIds = when (gameSource) {
            GameSource.STEAM -> SteamService.getPartialDownloads().map { it.toString() }
            GameSource.EPIC -> EpicService.getPartialDownloads().map { it.toString() }
            GameSource.GOG -> GOGService.getPartialDownloads()
            GameSource.AMAZON -> AmazonService.getPartialDownloads(appContext)
            GameSource.CUSTOM_GAME -> emptyList()
        }

        val partialKeys = partialIds.map { appId -> downloadKey(gameSource, appId) }.toSet()
        activeDownloads.values
            .filter { item -> item.gameSource == gameSource && item.isPartial && item.uniqueId !in partialKeys }
            .forEach { item -> activeDownloads.remove(item.uniqueId) }

        for (appId in partialIds) {
            val key = downloadKey(gameSource, appId)
            if (activeDownloads[key]?.isActive == true) continue
            val metadata = when (gameSource) {
                GameSource.STEAM -> appId.toIntOrNull()?.let { getSteamMetadata(it) }
                GameSource.EPIC -> appId.toIntOrNull()?.let { getEpicMetadata(it) }
                GameSource.GOG -> getGOGMetadata(appId)
                GameSource.AMAZON -> getAmazonMetadata(appId)
                GameSource.CUSTOM_GAME -> null
            } ?: continue
            activeDownloads[key] = buildPartialDownloadItem(appId, gameSource, metadata.first, metadata.second)
            finishedDownloads.remove(key)
        }
    }

    private suspend fun finalizeStoppedDownload(key: String) {
        val previousItem = activeDownloads.remove(key) ?: lastTrackedDownloads[key] ?: return
        val finishedStatus = when {
            pendingCancelledDownloads.remove(key) -> DownloadItemStatus.CANCELLED
            isInstalled(previousItem.gameSource, previousItem.appId) -> DownloadItemStatus.COMPLETED
            else -> DownloadItemStatus.FAILED
        }

        val finishedMessage = when (finishedStatus) {
            DownloadItemStatus.COMPLETED -> appContext.getString(R.string.downloads_status_complete)
            DownloadItemStatus.CANCELLED -> appContext.getString(R.string.downloads_status_cancelled)
            DownloadItemStatus.FAILED -> recentFailureMessages[key] ?: appContext.getString(R.string.downloads_status_failed)
            else -> previousItem.statusMessage
        }

        finishedDownloads[key] = previousItem.copy(
            progress = if (finishedStatus == DownloadItemStatus.COMPLETED) 1f else previousItem.progress,
            bytesDownloaded = if (finishedStatus == DownloadItemStatus.COMPLETED) {
                previousItem.bytesTotal ?: previousItem.bytesDownloaded
            } else {
                previousItem.bytesDownloaded
            },
            etaMs = null,
            statusMessage = finishedMessage,
            isActive = false,
            isPartial = false,
            status = finishedStatus,
            updatedAtMs = System.currentTimeMillis(),
        )

        pausedDownloads.remove(key)
        if (finishedStatus != DownloadItemStatus.FAILED) {
            recentFailureMessages.remove(key)
        }
    }

    private fun publishCombinedState() {
        trimFinishedDownloads()
        lastTrackedDownloads = HashMap(activeDownloads)
        _state.update {
            it.copy(downloads = sortDownloads(activeDownloads.values + finishedDownloads.values))
        }
    }

    private fun normalizeStatusMessage(message: String?): String? {
        return message
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !it.equals("null", ignoreCase = true) }
    }

    private fun failureMessage(message: String?): String {
        return normalizeStatusMessage(message) ?: appContext.getString(R.string.downloads_status_failed)
    }

    private suspend fun getSteamMetadata(appId: Int): Pair<String, String> {
        val key = downloadKey(GameSource.STEAM, appId.toString())
        val cachedName = gameNameCache[key]
        val cachedIcon = gameIconCache[key]
        if (cachedName != null && cachedIcon != null) return Pair(cachedName, cachedIcon)

        val app = steamAppDao.findApp(appId)
        val name = cachedName ?: (app?.name ?: "Steam App $appId").also { gameNameCache[key] = it }
        val icon = cachedIcon ?: (
            if (app != null && app.clientIconHash.isNotEmpty()) {
                "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/apps/${app.id}/${app.clientIconHash}.ico"
            } else {
                ""
            }
            ).also { gameIconCache[key] = it }

        return Pair(name, icon)
    }

    private suspend fun getEpicMetadata(appId: Int): Pair<String, String> {
        val key = downloadKey(GameSource.EPIC, appId.toString())
        val cachedName = gameNameCache[key]
        val cachedIcon = gameIconCache[key]
        if (cachedName != null && cachedIcon != null) return Pair(cachedName, cachedIcon)

        val game = epicGameDao.getById(appId)
        val name = cachedName ?: (game?.title ?: "Epic Game $appId").also { gameNameCache[key] = it }
        val icon = cachedIcon ?: (game?.artCover ?: "").also { gameIconCache[key] = it }

        return Pair(name, icon)
    }

    private suspend fun getGOGMetadata(gameId: String): Pair<String, String> {
        val key = downloadKey(GameSource.GOG, gameId)
        val cachedName = gameNameCache[key]
        val cachedIcon = gameIconCache[key]
        if (cachedName != null && cachedIcon != null) return Pair(cachedName, cachedIcon)

        val game = gogGameDao.getById(gameId)
        val name = cachedName ?: (game?.title ?: "GOG Game $gameId").also { gameNameCache[key] = it }
        val icon = cachedIcon ?: (game?.imageUrl?.ifEmpty { game.iconUrl } ?: "").also { gameIconCache[key] = it }

        return Pair(name, icon)
    }

    private suspend fun getAmazonMetadata(productId: String): Pair<String, String> {
        val key = downloadKey(GameSource.AMAZON, productId)
        val cachedName = gameNameCache[key]
        val cachedIcon = gameIconCache[key]
        if (cachedName != null && cachedIcon != null) return Pair(cachedName, cachedIcon)

        val game = amazonGameDao.getByProductId(productId)
        val name = cachedName ?: (game?.title ?: "Amazon Game").also { gameNameCache[key] = it }
        val icon = cachedIcon ?: (game?.artUrl ?: "").also { gameIconCache[key] = it }

        return Pair(name, icon)
    }

    suspend fun resolveLibraryItem(gameSource: GameSource, appId: String): LibraryItem? {
        val libraryAppId = "${gameSource.name}_$appId"

        return when (gameSource) {
            GameSource.STEAM -> {
                val numericAppId = appId.toIntOrNull() ?: return null
                val app = steamAppDao.findApp(numericAppId) ?: SteamService.getAppInfoOf(numericAppId)
                app?.let {
                    LibraryItem(
                        appId = libraryAppId,
                        name = it.name,
                        iconHash = it.clientIconHash,
                        capsuleImageUrl = it.getCapsuleUrl(),
                        headerImageUrl = it.getHeaderImageUrl().orEmpty().ifEmpty { it.headerUrl },
                        heroImageUrl = it.getHeroUrl().ifEmpty { it.headerUrl },
                        gameSource = GameSource.STEAM,
                    )
                }
            }

            GameSource.GOG -> {
                gogGameDao.getById(appId)?.let { game ->
                    LibraryItem(
                        appId = libraryAppId,
                        name = game.title,
                        iconHash = game.iconUrl.ifEmpty { game.imageUrl },
                        capsuleImageUrl = game.iconUrl.ifEmpty { game.imageUrl },
                        headerImageUrl = game.imageUrl.ifEmpty { game.iconUrl },
                        heroImageUrl = game.imageUrl.ifEmpty { game.iconUrl },
                        gameSource = GameSource.GOG,
                    )
                }
            }

            GameSource.EPIC -> {
                val numericAppId = appId.toIntOrNull() ?: return null
                epicGameDao.getById(numericAppId)?.let { game ->
                    LibraryItem(
                        appId = libraryAppId,
                        name = game.title,
                        iconHash = game.artSquare.ifEmpty { game.artCover },
                        capsuleImageUrl = game.artCover.ifEmpty { game.artSquare },
                        headerImageUrl = game.artPortrait.ifEmpty { game.artSquare.ifEmpty { game.artCover } },
                        heroImageUrl = game.artPortrait.ifEmpty { game.artSquare.ifEmpty { game.artCover } },
                        gameSource = GameSource.EPIC,
                    )
                }
            }

            GameSource.AMAZON -> {
                val game = amazonGameDao.getByProductId(appId)
                    ?: appId.toIntOrNull()?.let { amazonGameDao.getByAppId(it) }
                game?.let {
                    LibraryItem(
                        appId = "${GameSource.AMAZON.name}_${it.appId}",
                        name = it.title,
                        iconHash = it.artUrl,
                        capsuleImageUrl = it.artUrl,
                        headerImageUrl = it.heroUrl.ifEmpty { it.artUrl },
                        heroImageUrl = it.heroUrl.ifEmpty { it.artUrl },
                        gameSource = GameSource.AMAZON,
                    )
                }
            }

            GameSource.CUSTOM_GAME -> {
                CustomGameScanner.scanAsLibraryItems(query = "")
                    .firstOrNull { it.appId == libraryAppId }
            }
        }
    }

    private fun buildActiveDownloadItem(
        appId: String,
        gameSource: GameSource,
        gameName: String,
        iconUrl: String,
        info: app.gamenative.data.DownloadInfo,
    ): DownloadItemState {
        val key = downloadKey(gameSource, appId)
        val rawProgress = info.getProgress()
        val statusMessage = normalizeStatusMessage(info.getStatusMessageFlow().value)
        val status = when {
            rawProgress < 0f || statusMessage?.startsWith("Failed", ignoreCase = true) == true -> DownloadItemStatus.FAILED
            info.isActive() -> DownloadItemStatus.DOWNLOADING
            else -> DownloadItemStatus.PAUSED
        }

        if (status == DownloadItemStatus.DOWNLOADING) {
            recentFailureMessages.remove(key)
        } else if (status == DownloadItemStatus.FAILED) {
            recentFailureMessages[key] = failureMessage(statusMessage)
        }

        val (downloaded, total) = info.getBytesProgress()
        return DownloadItemState(
            appId = appId,
            gameSource = gameSource,
            gameName = gameName,
            iconUrl = iconUrl,
            progress = rawProgress.takeIf { it >= 0f },
            bytesDownloaded = downloaded.takeIf { total > 0L },
            bytesTotal = total.takeIf { it > 0L },
            etaMs = info.getEstimatedTimeRemaining(),
            statusMessage = statusMessage,
            isActive = info.isActive(),
            isPartial = false,
            status = status,
        )
    }

    private fun buildPartialDownloadItem(
        appId: String,
        gameSource: GameSource,
        gameName: String,
        iconUrl: String,
    ): DownloadItemState {
        val key = downloadKey(gameSource, appId)
        val status = when {
            pausedDownloads.contains(key) -> DownloadItemStatus.PAUSED
            recentFailureMessages.containsKey(key) -> DownloadItemStatus.FAILED
            else -> DownloadItemStatus.RESUMABLE
        }
        val statusMessage = when (status) {
            DownloadItemStatus.PAUSED -> appContext.getString(R.string.downloads_status_paused)
            DownloadItemStatus.FAILED -> recentFailureMessages[key] ?: appContext.getString(R.string.downloads_status_failed)
            DownloadItemStatus.RESUMABLE -> appContext.getString(R.string.downloads_resume_available)
            else -> null
        }

        return DownloadItemState(
            appId = appId,
            gameSource = gameSource,
            gameName = gameName,
            iconUrl = iconUrl,
            progress = null,
            bytesDownloaded = null,
            bytesTotal = null,
            etaMs = null,
            statusMessage = statusMessage,
            isActive = false,
            isPartial = true,
            status = status,
        )
    }

    private suspend fun isInstalled(gameSource: GameSource, appId: String): Boolean {
        return when (gameSource) {
            GameSource.STEAM -> appId.toIntOrNull()?.let { SteamService.isAppInstalled(it) } ?: false
            GameSource.EPIC -> appId.toIntOrNull()?.let { epicGameDao.getById(it)?.isInstalled == true } ?: false
            GameSource.GOG -> gogGameDao.getById(appId)?.isInstalled == true
            GameSource.AMAZON -> amazonGameDao.getByProductId(appId)?.isInstalled == true
            GameSource.CUSTOM_GAME -> false
        }
    }

    private fun sortDownloads(items: Collection<DownloadItemState>): LinkedHashMap<String, DownloadItemState> {
        val sortedItems = items.sortedWith(
            compareBy<DownloadItemState> { item ->
                when {
                    item.status == DownloadItemStatus.DOWNLOADING -> 0
                    item.isPartial -> 1
                    item.status == DownloadItemStatus.COMPLETED -> 2
                    item.status == DownloadItemStatus.CANCELLED -> 3
                    else -> 4
                }
            }
                .thenByDescending { item -> if (item.isFinished) item.updatedAtMs else 0L }
                .thenBy { item ->
                    if (item.isFinished) {
                        ""
                    } else {
                        item.gameName.lowercase()
                    }
                },
        )

        return LinkedHashMap<String, DownloadItemState>().apply {
            sortedItems.forEach { item -> put(item.uniqueId, item) }
        }
    }

    private fun trimFinishedDownloads(maxEntries: Int = 20) {
        if (finishedDownloads.size <= maxEntries) return

        finishedDownloads.values
            .sortedByDescending { it.updatedAtMs }
            .drop(maxEntries)
            .forEach { item -> finishedDownloads.remove(item.uniqueId) }
    }

    fun onPauseDownload(item: DownloadItemState) {
        if (!item.canPause) return

        val key = item.uniqueId
        pausedDownloads.add(key)
        recentFailureMessages.remove(key)

        viewModelScope.launch(Dispatchers.IO) {
            when (item.gameSource) {
                GameSource.STEAM -> {
                    val id = item.appId.toIntOrNull() ?: return@launch
                    SteamService.getAppDownloadInfo(id)?.cancel()
                }

                GameSource.EPIC -> {
                    val id = item.appId.toIntOrNull() ?: return@launch
                    EpicService.cancelDownload(id)
                }

                GameSource.GOG -> GOGService.cancelDownload(item.appId)
                GameSource.AMAZON -> AmazonService.cancelDownload(item.appId)
                GameSource.CUSTOM_GAME -> Unit
            }
        }
    }

    fun onResumeDownload(item: DownloadItemState) {
        if (!item.canResume) return

        val key = item.uniqueId
        pausedDownloads.remove(key)
        pendingCancelledDownloads.remove(key)
        recentFailureMessages.remove(key)
        finishedDownloads.remove(key)

        viewModelScope.launch(Dispatchers.IO) {
            when (item.gameSource) {
                GameSource.STEAM -> {
                    val id = item.appId.toIntOrNull() ?: return@launch
                    SteamService.downloadApp(id)
                }

                GameSource.GOG -> {
                    val game = gogGameDao.getById(item.appId) ?: return@launch
                    val installPath = game.installPath.ifBlank { GOGConstants.getGameInstallPath(game.title) }
                    val container = ContainerUtils.getOrCreateContainer(appContext, "${GameSource.GOG.name}_${item.appId}")
                    val language = ContainerUtils.toContainerData(container).language
                    val result = GOGService.downloadGame(appContext, item.appId, installPath, language)
                    result.exceptionOrNull()?.message?.let { recentFailureMessages[key] = it }
                }

                GameSource.EPIC -> {
                    val id = item.appId.toIntOrNull() ?: return@launch
                    val game = epicGameDao.getById(id) ?: return@launch
                    val installPath = game.installPath.ifBlank {
                        EpicConstants.getGameInstallPath(appContext, game.appName)
                    }
                    val container = ContainerUtils.getOrCreateContainer(appContext, "${GameSource.EPIC.name}_${item.appId}")
                    val language = ContainerUtils.toContainerData(container).language
                    val result = EpicService.downloadGame(appContext, id, emptyList(), installPath, language)
                    result.exceptionOrNull()?.message?.let { recentFailureMessages[key] = it }
                }

                GameSource.AMAZON -> {
                    val game = amazonGameDao.getByProductId(item.appId) ?: return@launch
                    val installPath = game.installPath.ifBlank {
                        AmazonConstants.getGameInstallPath(appContext, game.title)
                    }
                    val result = AmazonService.downloadGame(appContext, item.appId, installPath)
                    result.exceptionOrNull()?.message?.let { recentFailureMessages[key] = it }
                }

                GameSource.CUSTOM_GAME -> Unit
            }
        }
    }

    fun onPauseAll() {
        state.value.downloads.values
            .filter { it.canPause }
            .forEach(::onPauseDownload)
    }

    fun onResumeAll() {
        state.value.downloads.values
            .filter { it.canResume }
            .forEach(::onResumeDownload)
    }

    fun onCancelAll() {
        state.value.downloads.values
            .filter { it.canCancel }
            .forEach { item -> cancelDownloadNow(item.appId, item.gameSource, item.gameName) }
    }

    fun onClearFinished() {
        finishedDownloads.clear()
        publishCombinedState()
    }

    fun onCancelDownload(item: DownloadItemState) {
        _state.update {
            it.copy(
                cancelConfirmation = CancelConfirmation(
                    appId = item.appId,
                    gameSource = item.gameSource,
                    gameName = item.gameName,
                ),
            )
        }
    }

    fun onDismissCancel() {
        _state.update { it.copy(cancelConfirmation = null) }
    }

    fun onConfirmCancel() {
        val confirmation = _state.value.cancelConfirmation ?: return
        _state.update { it.copy(cancelConfirmation = null) }
        cancelDownloadNow(confirmation.appId, confirmation.gameSource, confirmation.gameName)
    }

    private fun cancelDownloadNow(appId: String, gameSource: GameSource, gameName: String) {
        val key = downloadKey(gameSource, appId)
        pendingCancelledDownloads.add(key)
        pausedDownloads.remove(key)
        recentFailureMessages.remove(key)

        viewModelScope.launch(Dispatchers.IO) {
            when (gameSource) {
                GameSource.STEAM -> {
                    val id = appId.toIntOrNull() ?: return@launch
                    SteamService.getAppDownloadInfo(id)?.cancel()
                    SteamService.deleteApp(id)
                    PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(id))
                }

                GameSource.EPIC -> {
                    val id = appId.toIntOrNull() ?: return@launch
                    EpicService.cancelDownload(id)
                    EpicService.deleteGame(appContext, id)
                }

                GameSource.GOG -> {
                    GOGService.cancelDownload(appId)
                    val game = gogGameDao.getById(appId)
                    if (game != null) {
                        GOGService.deleteGame(
                            appContext,
                            LibraryItem(
                                appId = appId,
                                name = game.title.ifBlank { gameName },
                                gameSource = GameSource.GOG,
                            ),
                        )
                    }
                }

                GameSource.AMAZON -> {
                    AmazonService.cancelDownload(appId)
                    AmazonService.deleteGame(appContext, appId)
                }

                GameSource.CUSTOM_GAME -> Unit
            }
        }
    }
}
