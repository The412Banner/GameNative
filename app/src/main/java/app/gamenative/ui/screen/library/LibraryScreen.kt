package app.gamenative.ui.screen.library

import android.content.res.Configuration
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.GameCompatibilityStatus
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.ui.component.GamepadAction
import app.gamenative.ui.component.GamepadActionBar
import app.gamenative.ui.component.GamepadButton
import app.gamenative.ui.component.LibraryActions
import app.gamenative.ui.components.rememberCustomGameFolderPicker
import app.gamenative.ui.components.requestPermissionsForPath
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.LibraryTab
import app.gamenative.ui.enums.LibraryTab.Companion.next
import app.gamenative.ui.enums.LibraryTab.Companion.previous
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.enums.SortOption
import app.gamenative.ui.internal.fakeAppInfo
import app.gamenative.ui.model.LibraryViewModel
import app.gamenative.ui.screen.library.components.LibraryCarouselPane
import app.gamenative.ui.screen.library.components.LibraryDetailPane
import app.gamenative.ui.screen.library.components.LibraryListPane
import app.gamenative.ui.screen.library.components.LibraryOptionsPanel
import app.gamenative.ui.screen.library.components.LibrarySearchBar
import app.gamenative.ui.screen.library.components.LibraryTabBar
import app.gamenative.ui.screen.library.components.SystemMenu
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.utils.CustomGameScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onClickPlay: (String, Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onNavigateRoute: (String) -> Unit,
    onLogout: () -> Unit,
    onGoOnline: () -> Unit,
    isOffline: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LibraryScreenContent(
        state = state,
        listState = viewModel.listState,
        sheetState = sheetState,
        onFilterChanged = viewModel::onFilterChanged,
        onPageChange = viewModel::onPageChange,
        onModalBottomSheet = viewModel::onModalBottomSheet,
        onIsSearching = viewModel::onIsSearching,
        onSearchQuery = viewModel::onSearchQuery,
        onRefresh = viewModel::onRefresh,
        onClickPlay = onClickPlay,
        onTestGraphics = onTestGraphics,
        onNavigateRoute = onNavigateRoute,
        onLogout = onLogout,
        onGoOnline = onGoOnline,
        onSourceToggle = viewModel::onSourceToggle,
        onAddCustomGameFolder = viewModel::addCustomGameFolder,
        onSortOptionChanged = viewModel::onSortOptionChanged,
        onOptionsPanelToggle = viewModel::onOptionsPanelToggle,
        onTabChanged = viewModel::onTabChanged,
        isOffline = isOffline,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenContent(
    state: LibraryState,
    listState: LazyGridState,
    sheetState: SheetState,
    onFilterChanged: (AppFilter) -> Unit,
    onPageChange: (Int) -> Unit,
    onModalBottomSheet: (Boolean) -> Unit,
    onIsSearching: (Boolean) -> Unit,
    onSearchQuery: (String) -> Unit,
    onClickPlay: (String, Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onLogout: () -> Unit,
    onGoOnline: () -> Unit,
    onSourceToggle: (GameSource) -> Unit,
    onAddCustomGameFolder: (String) -> Unit,
    onSortOptionChanged: (SortOption) -> Unit,
    onOptionsPanelToggle: (Boolean) -> Unit,
    onTabChanged: (LibraryTab) -> Unit,
    isOffline: Boolean = false,
) {
    val context = LocalContext.current
    var selectedAppId by remember { mutableStateOf<String?>(null) }
    val isViewWide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var currentPaneType by remember { mutableStateOf(PrefManager.libraryLayout) }

    // Initialize layout if undecided
    LaunchedEffect(Unit) {
        if (currentPaneType == PaneType.UNDECIDED) {
            currentPaneType = if (isViewWide) PaneType.GRID_HERO else PaneType.GRID_CAPSULE
            PrefManager.libraryLayout = currentPaneType
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val gridFirstItemFocusRequester = remember { FocusRequester() }
    val carouselFocusRequester = remember { FocusRequester() }
    var gridFocusTargetListIndex by remember { mutableIntStateOf(0) }
    var pendingGridFocusRequest by remember { mutableStateOf(false) }
    var pendingCarouselFocusRequest by remember { mutableStateOf(false) }

    var isSystemMenuOpen by remember { mutableStateOf(false) }
    // Keep a stable reference to the selected item so detail view doesn't disappear during list refresh/pagination.
    var selectedLibraryItem by remember { mutableStateOf<LibraryItem?>(null) }
    val filterFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    // Dialog state for add custom game prompt
    var showAddCustomGameDialog by remember { mutableStateOf(false) }
    var dontShowAgain by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    val folderPicker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            // When a folder is selected via OpenDocumentTree, the user has already granted
            // URI permissions for that specific folder. We should verify we can access it
            // rather than checking for broad storage permissions.
            val folder = java.io.File(path)
            val canAccess = try {
                folder.exists() && (folder.isDirectory && folder.canRead())
            } catch (e: Exception) {
                false
            }

            // Only request permissions if we can't access the folder AND it's outside the sandbox
            // (folders selected via OpenDocumentTree should already be accessible)
            if (!canAccess && !CustomGameScanner.hasStoragePermission(context, path)) {
                requestPermissionsForPath(context, path, storagePermissionLauncher)
            }
            onAddCustomGameFolder(path)
        },
        onFailure = { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )

    // Handle opening folder picker (with dialog check)
    val onAddCustomGameClick = {
        if (PrefManager.showAddCustomGameDialog) {
            showAddCustomGameDialog = true
        } else {
            folderPicker.launchPicker()
        }
    }

    // Catch-all: when on root library view with nothing open, back/B opens system menu
    // instead of exiting the app. Declared first = lowest priority.
    BackHandler(
        enabled = selectedAppId == null && selectedLibraryItem == null &&
            !isSystemMenuOpen && !state.isOptionsPanelOpen && !state.isSearching,
    ) {
        isSystemMenuOpen = true
    }

    BackHandler(enabled = isSystemMenuOpen) {
        isSystemMenuOpen = false
    }

    BackHandler(enabled = state.isOptionsPanelOpen) {
        onOptionsPanelToggle(false)
    }

    BackHandler(enabled = state.isSearching && selectedAppId == null) {
        onIsSearching(false)
        onSearchQuery("")
    }

    BackHandler(selectedLibraryItem != null) {
        selectedAppId = null
        selectedLibraryItem = null
    }

    // Refresh list when navigating back from detail view
    LaunchedEffect(selectedAppId) {
        if (selectedAppId == null) {
            // Trigger refresh by calling onSearchQuery with current query
            // This will call onFilterApps() which re-scans Custom Games
            val currentQuery = state.searchQuery
            onSearchQuery(currentQuery)
        }
    }

    // Apply top padding differently for list vs game detail pages.
    // On the game page we want to hide the top padding when the status bar is hidden.
    val safePaddingModifier = if (selectedLibraryItem != null) {
        // Detail (game) page: use actual status bar height when status bar is visible,
        // or 0.dp when status bar is hidden
        val topPadding = if (PrefManager.hideStatusBarWhenNotInGame) {
            0.dp
        } else {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        }
        Modifier.padding(top = topPadding)
    } else {
        Modifier
    }

    // Recapture focus on the root container when the list becomes empty (e.g. switching to
    // an empty tab), so bumper key events keep working even when there are no grid items.
    // Include currentTab as a key so switching between empty tabs (e.g. GoG→Epic) still
    // recaptures focus. Add a small delay to ensure composition is complete before requesting.
    // TODO: This fix is incomplete - R1/L1 still stops working at empty tabs like GoG.
    // Need deeper investigation into focus system behavior when carousel/list has no items.
    LaunchedEffect(state.appInfoList.isEmpty(), state.currentTab) {
        if (state.appInfoList.isEmpty()) {
            kotlinx.coroutines.delay(50)
            try {
                rootFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // FocusRequester not yet attached during initial composition
            }
        }
    }

    LaunchedEffect(pendingGridFocusRequest, gridFocusTargetListIndex, state.appInfoList.size) {
        if (pendingGridFocusRequest && state.appInfoList.isNotEmpty()) {
            try {
                gridFirstItemFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // FocusRequester not yet attached during recomposition
            }
            pendingGridFocusRequest = false
        }
    }

    LaunchedEffect(pendingCarouselFocusRequest, state.appInfoList.size) {
        if (pendingCarouselFocusRequest && state.appInfoList.isNotEmpty()) {
            try {
                carouselFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // FocusRequester not yet attached during recomposition
            }
            pendingCarouselFocusRequest = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(safePaddingModifier)
            .focusRequester(rootFocusRequester)
            .focusable()
            .focusGroup()
            .onPreviewKeyEvent { keyEvent ->
                // TODO: consider abstracting this
                // Handle gamepad buttons
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        // L1 button - previous tab
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
                                onTabChanged(state.currentTab.previous())
                                true
                            } else {
                                false
                            }
                        }

                        // R1 button - next tab
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
                                onTabChanged(state.currentTab.next())
                                true
                            } else {
                                false
                            }
                        }

                        // SELECT button - toggle options panel (library filters/sort)
                        KeyEvent.KEYCODE_BUTTON_SELECT -> {
                            if (selectedAppId == null && !isSystemMenuOpen) {
                                onOptionsPanelToggle(!state.isOptionsPanelOpen)
                                true
                            } else {
                                false
                            }
                        }

                        // START button - toggle system menu (profile/settings)
                        KeyEvent.KEYCODE_BUTTON_START,
                        KeyEvent.KEYCODE_MENU,
                        -> {
                            if (selectedAppId == null && !state.isOptionsPanelOpen) {
                                isSystemMenuOpen = !isSystemMenuOpen
                                true
                            } else {
                                false
                            }
                        }

                        // Y button - toggle search
                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
                                onIsSearching(!state.isSearching)
                                true
                            } else {
                                false
                            }
                        }

                        // X button - add custom game
                        KeyEvent.KEYCODE_BUTTON_X -> {
                            if (selectedAppId == null && !state.isSearching && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
                                onAddCustomGameClick()
                                true
                            } else {
                                false
                            }
                        }

                        // B button - contextual back / open system menu
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            if (selectedAppId != null) {
                                // Let LibraryAppScreen handle its own B-button
                                false
                            } else if (isSystemMenuOpen) {
                                isSystemMenuOpen = false
                                true
                            } else if (state.isOptionsPanelOpen) {
                                onOptionsPanelToggle(false)
                                true
                            } else if (state.isSearching) {
                                onIsSearching(false)
                                onSearchQuery("")
                                true
                            } else {
                                // Root library view: open system menu
                                isSystemMenuOpen = true
                                true
                            }
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        if (selectedAppId == null) {
            // Use Box to allow content to scroll behind the tab bar
            Box(modifier = Modifier.fillMaxSize()) {
                // Library list (content scrolls behind tab bar)
                if (currentPaneType == PaneType.CAROUSEL) {
                    LibraryCarouselPane(
                        state = state,
                        listState = listState,
                        onPageChange = onPageChange,
                        onNavigate = { appId ->
                            selectedAppId = appId
                            selectedLibraryItem = state.appInfoList.find { it.appId == appId }
                        },
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                        carouselFocusRequester = carouselFocusRequester,
                    )
                } else {
                    LibraryListPane(
                        state = state,
                        listState = listState,
                        currentLayout = currentPaneType,
                        firstGridItemFocusRequester = gridFirstItemFocusRequester,
                        focusTargetListIndex = gridFocusTargetListIndex,
                        onPageChange = onPageChange,
                        onNavigate = { appId ->
                            selectedAppId = appId
                            selectedLibraryItem = state.appInfoList.find { it.appId == appId }
                        },
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Top overlay: Tab bar OR Search bar
                if (state.isSearching) {
                    // Search overlay replaces tab bar when searching
                    // TODO: Gamepad focus is a bit wonky whenever we show the search bar
                    LibrarySearchBar(
                        isVisible = true,
                        searchQuery = state.searchQuery,
                        resultCount = state.totalAppsInFilter,
                        listState = listState,
                        onSearchQuery = onSearchQuery,
                        onDismiss = { onIsSearching(false) },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                    )
                } else {
                    // Tab bar when not searching
                    LibraryTabBar(
                        currentTab = state.currentTab,
                        tabCounts = mapOf(
                            LibraryTab.ALL to state.allCount,
                            LibraryTab.STEAM to state.steamCount,
                            LibraryTab.GOG to state.gogCount,
                            LibraryTab.EPIC to state.epicCount,
                            LibraryTab.LOCAL to state.localCount,
                        ),
                        onTabSelected = onTabChanged,
                        onOptionsClick = { onOptionsPanelToggle(true) },
                        onSearchClick = { onIsSearching(true) },
                        onAddGameClick = onAddCustomGameClick,
                        onMenuClick = { isSystemMenuOpen = true },
                        onNavigateDownToGrid = {
                            if (state.appInfoList.isNotEmpty()) {
                                if (currentPaneType == PaneType.CAROUSEL) {
                                    pendingCarouselFocusRequest = true
                                } else {
                                    gridFocusTargetListIndex = listState.firstVisibleItemIndex
                                        .coerceIn(0, state.appInfoList.lastIndex)
                                    pendingGridFocusRequest = true
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                    )
                }
            }
        } else {
            LibraryDetailPane(
                libraryItem = selectedLibraryItem,
                onBack = {
                    selectedAppId = null
                    selectedLibraryItem = null
                },
                onClickPlay = {
                    selectedLibraryItem?.let { libraryItem ->
                        onClickPlay(libraryItem.appId, it)
                    }
                },
                onTestGraphics = {
                    selectedLibraryItem?.let { libraryItem ->
                        onTestGraphics(libraryItem.appId)
                    }
                },
            )
        }

        // Bottom action bar
        if (selectedAppId == null && !state.isOptionsPanelOpen && !isSystemMenuOpen) {
            val libraryActions = if (state.isSearching) {
                listOf(
                    LibraryActions.select,
                    GamepadAction(
                        button = GamepadButton.B,
                        labelResId = R.string.back,
                        onClick = {
                            onIsSearching(false)
                            onSearchQuery("")
                        },
                    ),
                )
            } else {
                listOf(
                    LibraryActions.select,
                    GamepadAction(
                        button = GamepadButton.SELECT,
                        labelResId = R.string.options,
                        onClick = { onOptionsPanelToggle(true) },
                    ),
                    GamepadAction(
                        button = GamepadButton.START,
                        labelResId = R.string.action_system,
                        onClick = { isSystemMenuOpen = true },
                    ),
                    GamepadAction(
                        button = GamepadButton.B,
                        labelResId = R.string.menu,
                        onClick = { isSystemMenuOpen = true },
                    ),
                    GamepadAction(
                        button = GamepadButton.Y,
                        labelResId = R.string.search,
                        onClick = { onIsSearching(true) },
                    ),
                    GamepadAction(
                        button = GamepadButton.X,
                        labelResId = R.string.action_add_game,
                        onClick = onAddCustomGameClick,
                    ),
                )
            }

            GamepadActionBar(
                actions = libraryActions,
                modifier = Modifier.align(Alignment.BottomCenter),
                visible = true,
            )
        }

        // Options panel (SELECT) - renders on top of everything
        if (selectedAppId == null) {
            LibraryOptionsPanel(
                isOpen = state.isOptionsPanelOpen,
                onDismiss = { onOptionsPanelToggle(false) },
                selectedFilters = state.appInfoSortType,
                onFilterChanged = onFilterChanged,
                currentSortOption = state.currentSortOption,
                onSortOptionChanged = onSortOptionChanged,
                currentView = currentPaneType,
                onViewChanged = { newPaneType ->
                    PrefManager.libraryLayout = newPaneType
                    currentPaneType = newPaneType
                },
            )

            // System menu (START) - renders on top of everything
            SystemMenu(
                isOpen = isSystemMenuOpen,
                onDismiss = { isSystemMenuOpen = false },
                onNavigateRoute = onNavigateRoute,
                onLogout = onLogout,
                onGoOnline = onGoOnline,
                isOffline = isOffline,
            )
        }

        // Add custom game dialog
        if (showAddCustomGameDialog) {
            AlertDialog(
                onDismissRequest = { showAddCustomGameDialog = false },
                title = { Text(stringResource(R.string.add_custom_game_dialog_title)) },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.add_custom_game_dialog_message),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = dontShowAgain,
                                onCheckedChange = { dontShowAgain = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.add_custom_game_dont_show_again),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (dontShowAgain) {
                                PrefManager.showAddCustomGameDialog = false
                            }
                            showAddCustomGameDialog = false
                            folderPicker.launchPicker()
                        },
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddCustomGameDialog = false },
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}

/***********
 * PREVIEW *
 ***********/

@OptIn(ExperimentalMaterial3Api::class)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:width=1080px,height=1920px,dpi=440,orientation=landscape",
)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "id:pixel_tablet",
)
@Composable
private fun Preview_LibraryScreenContent() {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    PrefManager.init(context)
    var state by remember {
        mutableStateOf(
            LibraryState(
                appInfoList = List(15) { idx ->
                    val item = fakeAppInfo(idx)
                    LibraryItem(
                        index = idx,
                        appId = "${GameSource.STEAM.name}_${item.id}",
                        name = item.name,
                        iconHash = item.iconHash,
                    )
                },
                // Add compatibility map for preview
                compatibilityMap = mapOf(
                    "Game 0" to GameCompatibilityStatus.COMPATIBLE,
                    "Game 1" to GameCompatibilityStatus.GPU_COMPATIBLE,
                    "Game 2" to GameCompatibilityStatus.NOT_COMPATIBLE,
                    "Game 3" to GameCompatibilityStatus.UNKNOWN,
                ),
            ),
        )
    }
    PluviaTheme {
        LibraryScreenContent(
            listState = rememberLazyGridState(),
            state = state,
            sheetState = sheetState,
            onIsSearching = {},
            onSearchQuery = {},
            onFilterChanged = { },
            onPageChange = { },
            onModalBottomSheet = {
                val currentState = state.modalBottomSheet
                println("State: $currentState")
                state = state.copy(modalBottomSheet = !currentState)
            },
            onClickPlay = { _, _ -> },
            onTestGraphics = { },
            onRefresh = { },
            onNavigateRoute = {},
            onLogout = {},
            onGoOnline = {},
            onSourceToggle = {},
            onAddCustomGameFolder = {},
            onSortOptionChanged = {},
            onOptionsPanelToggle = { isOpen ->
                state = state.copy(isOptionsPanelOpen = isOpen)
            },
            onTabChanged = { tab ->
                state = state.copy(currentTab = tab)
            },
        )
    }
}
