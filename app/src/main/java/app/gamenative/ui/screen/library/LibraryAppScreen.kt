package app.gamenative.ui.screen.library

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.LibraryItem
import app.gamenative.service.SteamService
import app.gamenative.ui.component.GamepadAction
import app.gamenative.ui.component.GamepadActionBar
import app.gamenative.ui.component.GamepadButton
import app.gamenative.ui.component.LoadingScreen
import app.gamenative.ui.component.topbar.BackButton
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.ui.internal.fakeAppInfo
import app.gamenative.ui.screen.library.components.GameOptionsPanel
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.AdaptiveHeroHeight
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import app.gamenative.utils.SteamUtils
import com.winlator.container.ContainerData
import com.winlator.xenvironment.ImageFsInstaller
import com.winlator.fexcore.FEXCoreManager
import app.gamenative.ui.screen.library.appscreen.SteamAppScreen
import app.gamenative.ui.screen.library.appscreen.CustomGameAppScreen
import app.gamenative.ui.screen.library.appscreen.GOGAppScreen
import app.gamenative.ui.screen.library.appscreen.EpicAppScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// https://partner.steamgames.com/doc/store/assets/libraryassets#4

@Composable
private fun SkeletonText(
    modifier: Modifier = Modifier,
    lines: Int = 1,
    lineHeight: Int = 16,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

    Column(modifier = modifier) {
        repeat(lines) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.7f else 1f)
                    .height(lineHeight.dp)
                    .background(
                        color = color,
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
            if (index < lines - 1) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInstalled: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "primaryActionScale",
    )

    val buttonColor = when {
        isDownloading -> PluviaTheme.colors.statusDownloading
        isInstalled -> PluviaTheme.colors.statusInstalled
        else -> PluviaTheme.colors.statusAvailable
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) buttonColor else buttonColor.copy(alpha = 0.5f),
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .focusRequester(focusRequester)
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isDownloading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.width(100.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
                Text(
                    text = "${(downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (isInstalled) Icons.Default.PlayArrow else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Icon-only action button for the overlay action bar
 */
@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "actionIconScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) {
                    Color.White.copy(alpha = 0.2f)
                } else {
                    Color.White.copy(alpha = 0.1f)
                },
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Info card for game details with optional status indicator
 */
@Composable
private fun InfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    statusColor: Color? = null,
    isCompact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(if (isCompact) 14.dp else 18.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (statusColor != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = value,
                    style = if (isCompact) {
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = if (statusColor != null) statusColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    libraryItem: LibraryItem,
    onClickPlay: (Boolean) -> Unit,
    onTestGraphics: () -> Unit,
    onBack: () -> Unit,
) {
    // Get the appropriate screen model based on game source
    val screenModel = remember(libraryItem.gameSource) {
        when (libraryItem.gameSource) {
            app.gamenative.data.GameSource.STEAM -> SteamAppScreen()
            app.gamenative.data.GameSource.CUSTOM_GAME -> CustomGameAppScreen()
            app.gamenative.data.GameSource.GOG -> GOGAppScreen()
            app.gamenative.data.GameSource.EPIC -> EpicAppScreen()
        }
    }

    // Render the content using the model
    screenModel.Content(
        libraryItem = libraryItem,
        onClickPlay = onClickPlay,
        onTestGraphics = onTestGraphics,
        onBack = onBack,
    )
}

/**
 * Formats bytes into a human-readable string (KB, MB, GB).
 * Uses binary units (1024 base).
 */
private fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format("%.1f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

@Composable
internal fun AppScreenContent(
    modifier: Modifier = Modifier,
    displayInfo: GameDisplayInfo,
    isInstalled: Boolean,
    isValidToDownload: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    hasPartialDownload: Boolean,
    isUpdatePending: Boolean,
    downloadInfo: app.gamenative.data.DownloadInfo? = null,
    onDownloadInstallClick: () -> Unit,
    onPauseResumeClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onBack: () -> Unit = {},
    vararg optionsMenu: AppMenuOption,
) {
    // Determine Wi-Fi connectivity for 'Wi-Fi only' preference
    val context = LocalContext.current
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val wifiConnected = capabilities?.run {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } == true
    val wifiAllowed = !PrefManager.downloadOnWifiOnly || wifiConnected
    val scrollState = rememberScrollState()

    var optionsMenuVisible by remember { mutableStateOf(false) }

    // Focus requesters for gamepad navigation
    val playButtonFocusRequester = remember { FocusRequester() }

    // Calculate parallax offset based on scroll
    val parallaxOffset = scrollState.value * 0.5f

    LaunchedEffect(displayInfo.appId) {
        scrollState.animateScrollTo(0)
    }

    LaunchedEffect(Unit) {
        playButtonFocusRequester.requestFocus()
    }

    // Handle gamepad button presses
    val handleKeyEvent: (KeyEvent) -> Boolean = { event ->
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                // SELECT button - open options menu
                KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    optionsMenuVisible = true
                    true
                }

                // START button - primary action (play/download/pause)
                KeyEvent.KEYCODE_BUTTON_START -> {
                    if (isDownloading || hasPartialDownload) {
                        onPauseResumeClick()
                    } else {
                        onDownloadInstallClick()
                    }
                    true
                }

                // B button - back
                KeyEvent.KEYCODE_BUTTON_B -> {
                    if (optionsMenuVisible) {
                        optionsMenuVisible = false
                    } else {
                        onBack()
                    }
                    true
                }

                else -> false
            }
        } else {
            false
        }
    }

    // Button state calculations
    val isResume = !isDownloading && hasPartialDownload
    val pauseResumeEnabled = if (isResume) wifiAllowed else true
    val isInstall = !isInstalled
    val installEnabled = if (isInstall) wifiAllowed && hasInternet else true
    val buttonEnabled = if (isInstalled) {
        installEnabled
    } else {
        installEnabled && isValidToDownload
    }

    // Handle back press when options panel is open
    BackHandler(enabled = optionsMenuVisible) {
        optionsMenuVisible = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { handleKeyEvent(it.nativeKeyEvent) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Hero Section with Game Image Background
        Box(modifier = Modifier.weight(1f)) {
            // Hero background image
            if (displayInfo.heroImageUrl != null) {
            CoilImage(
                modifier = Modifier.fillMaxSize(),
                    imageModel = { displayInfo.heroImageUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                loading = { LoadingScreen() },
                failure = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Gradient background as fallback
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary
                        ) { }
                    }
                },
                previewPlaceholder = painterResource(R.drawable.testhero),
            )
            } else {
                // Fallback gradient background when no hero image
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary
                ) { }
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // Compatibility status overlay (bottom center)
            // Must be after gradient but before title to ensure visibility
            if (displayInfo.compatibilityMessage != null && displayInfo.compatibilityColor != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = displayInfo.compatibilityMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(displayInfo.compatibilityColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Back button (top left)
            Box(
                modifier = Modifier
                    .padding(20.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                BackButton(onClick = onBack)
            }

            // Settings/options button (top right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
            ) {
                IconButton(
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    onClick = { optionsMenuVisible = !optionsMenuVisible },
                    content = {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    },
                )

                DropdownMenu(
                    expanded = optionsMenuVisible,
                    onDismissRequest = { optionsMenuVisible = false },
                ) {
                    optionsMenu.forEach { menuOption ->
                        DropdownMenuItem(
                            text = { Text(menuOption.optionType.text) },
                            onClick = {
                                menuOption.onClick()
                                optionsMenuVisible = false
                            },
                        )
                    }
                }
            }

            // Game title and subtitle
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    text = displayInfo.name,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(0f, 2f),
                            blurRadius = 10f
                        )
                    ),
                    color = Color.White
                )

                Text(
                    text = "${displayInfo.developer} • ${remember(displayInfo.releaseDate) {
                        if (displayInfo.releaseDate > 0) {
                            SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(displayInfo.releaseDate * 1000))
                        } else {
                            ""
                        }
                    }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // Content section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pause/Resume and Delete when downloading or paused
                // Use hasPartialDownload from BaseAppScreen (implemented per game source)
                // Disable resume when Wi-Fi only is enabled and there's no Wi-Fi
                val isResume = !isDownloading && hasPartialDownload
                val pauseResumeEnabled = if (isResume) wifiAllowed else true
                if (isDownloading || hasPartialDownload) {
                    // Pause or Resume
                    Button(
                        enabled = pauseResumeEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = onPauseResumeClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(
                            text = if (isDownloading) stringResource(R.string.pause_download)
                                   else stringResource(R.string.resume_download),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    // Delete (Cancel) download data
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDeleteDownloadClick,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(stringResource(R.string.delete_app), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    }
                } else {
                    // Disable install when Wi-Fi only is enabled and there's no Wi-Fi
                    val isInstall = !isInstalled
                    val installEnabled = if (isInstall) wifiAllowed && hasInternet else true
                    // For installed games, button should always be enabled (regardless of isValidToDownload)
                    // For games that need installation, check isValidToDownload
                    val buttonEnabled = if (isInstalled) {
                        installEnabled // Installed games can always be played
                    } else {
                        installEnabled && isValidToDownload // Only check download validity when not installed
                    }
                    // Install or Play button
                    Button(
                        enabled = buttonEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDownloadInstallClick()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        val text = when {
                            isInstalled -> stringResource(R.string.run_app)
                            !hasInternet -> stringResource(R.string.library_need_internet)
                            !wifiConnected && PrefManager.downloadOnWifiOnly -> stringResource(R.string.library_wifi_only_enabled)
                            else -> stringResource(R.string.install_app)
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    // Uninstall/Delete button if already installed
                    // This is shared functionality - all game types show delete button when installed
                    // The action is handled by onDeleteDownloadClick which is implemented per game source
                    if (isInstalled) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onDeleteDownloadClick() },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.uninstall),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Download progress section
            if (isDownloading) {
                // downloadInfo passed from BaseAppScreen based on game source
                val statusMessageFlow = downloadInfo?.getStatusMessageFlow()
                val statusMessageState = statusMessageFlow?.collectAsState(initial = statusMessageFlow.value)
                val statusMessage = statusMessageState?.value

                // Use DownloadInfo's byte-based ETA when available for more stable estimates
                val timeLeftText = remember(displayInfo.appId, downloadProgress, downloadInfo, statusMessage) {
                    val etaMs = downloadInfo?.getEstimatedTimeRemaining()
                    if (etaMs != null && etaMs > 0L) {
                        val totalSeconds = etaMs / 1000
                        val minutesLeft = totalSeconds / 60
                        val secondsPart = totalSeconds % 60
                        "${minutesLeft}m ${secondsPart}s left"
                    } else if (downloadProgress in 0f..1f && downloadProgress < 1f) {
                        val statusText = statusMessage?.takeUnless { it.isBlank() }
                        statusText ?: "Calculating..."
                    } else {
                        ""
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                ) {
                    // Hero Section  (Parallax)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AdaptiveHeroHeight.get()),
                    ) {
                        // Hero background image
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationY = parallaxOffset
                                },
                        ) {
                            if (displayInfo.heroImageUrl != null) {
                                CoilImage(
                                    modifier = Modifier.fillMaxSize(),
                                    imageModel = { displayInfo.heroImageUrl },
                                    imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                                    loading = { LoadingScreen() },
                                    failure = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.primaryContainer,
                                                        ),
                                                    ),
                                                ),
                                        )
                                    },
                                    previewPlaceholder = painterResource(R.drawable.testhero),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                ),
                                            ),
                                        ),
                                )
                            }
                        }

                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.3f),
                                            Color.Black.copy(alpha = 0.85f),
                                        ),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY,
                                    ),
                                ),
                        )

                        // Back button (top left)
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .size(44.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            BackButton(onClick = onBack)
                        }

                        // Bottom overlay with title and action bar
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            // Game title
                            Text(
                                text = displayInfo.name,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 8f,
                                    ),
                                ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // Developer and year
                            Text(
                                text = "${displayInfo.developer} • ${
                                    remember(displayInfo.releaseDate) {
                                        if (displayInfo.releaseDate > 0) {
                                            SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(displayInfo.releaseDate * 1000))
                                        } else {
                                            ""
                                        }
                                    }
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Integrated action bar - overlaid on hero
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(12.dp)
                                    .focusGroup(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Primary action button (left-aligned)
                                if (isDownloading || hasPartialDownload) {
                                    PrimaryActionButton(
                                        text = if (isDownloading) {
                                            stringResource(R.string.pause_download)
                                        } else {
                                            stringResource(R.string.resume_download)
                                        },
                                        onClick = onPauseResumeClick,
                                        enabled = pauseResumeEnabled,
                                        isInstalled = false,
                                        isDownloading = isDownloading,
                                        downloadProgress = downloadProgress,
                                        focusRequester = playButtonFocusRequester,
                                    )
                                } else {
                                    val text = when {
                                        isInstalled -> stringResource(R.string.run_app)
                                        !hasInternet -> stringResource(R.string.library_need_internet)
                                        !wifiConnected && PrefManager.downloadOnWifiOnly -> stringResource(R.string.library_wifi_only_enabled)
                                        else -> stringResource(R.string.install_app)
                                    }
                                    PrimaryActionButton(
                                        text = text,
                                        onClick = onDownloadInstallClick,
                                        enabled = buttonEnabled,
                                        isInstalled = isInstalled,
                                        focusRequester = playButtonFocusRequester,
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Secondary action icons (right-aligned)
                                ActionIconButton(
                                    icon = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.options),
                                    onClick = { optionsMenuVisible = true },
                                )

                                if (isInstalled) {
                                    ActionIconButton(
                                        icon = Icons.Default.Cloud,
                                        contentDescription = stringResource(R.string.cloud),
                                        onClick = {
                                            optionsMenu.find { it.optionType == AppOptionMenuType.ForceCloudSync }?.onClick?.invoke()
                                        },
                                    )
                                }

                                if (isInstalled || hasPartialDownload) {
                                    ActionIconButton(
                                        icon = Icons.Default.Delete,
                                        contentDescription = if (isInstalled) stringResource(R.string.uninstall) else stringResource(R.string.delete_app),
                                        onClick = onDeleteDownloadClick,
                                    )
                                }
                            }

                            // Compatibility status (if applicable)
                            if (displayInfo.compatibilityMessage != null && displayInfo.compatibilityColor != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = displayInfo.compatibilityMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(displayInfo.compatibilityColor),
                                )
                            }
                        }
                    }

                    // Content section below hero with solid background
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(20.dp),
                    ) {
                        // Download progress section
                        if (isDownloading) {
                            val downloadInfo = SteamService.getAppDownloadInfo(displayInfo.gameId)
                            val statusMessageFlow = downloadInfo?.getStatusMessageFlow()
                            val statusMessageState = statusMessageFlow?.collectAsState(initial = statusMessageFlow.value)
                            val statusMessage = statusMessageState?.value

                            val timeLeftText = remember(displayInfo.appId, downloadProgress, downloadInfo, statusMessage) {
                                val etaMs = downloadInfo?.getEstimatedTimeRemaining()
                                if (etaMs != null && etaMs > 0L) {
                                    val totalSeconds = etaMs / 1000
                                    val minutesLeft = totalSeconds / 60
                                    val secondsPart = totalSeconds % 60
                                    "${minutesLeft}m ${secondsPart}s left"
                                } else if (downloadProgress in 0f..1f && downloadProgress < 1f) {
                                    statusMessage?.takeUnless { it.isBlank() } ?: "Calculating..."
                                } else {
                                    ""
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.installation_progress),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = "${(downloadProgress * 100f).toInt()}%",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val downloadingText = stringResource(R.string.downloading)
                                    val sizeText = remember(displayInfo.gameId, downloadProgress, downloadInfo) {
                                        val (bytesDone, bytesTotal) = downloadInfo?.getBytesProgress() ?: (0L to 0L)
                                        if (bytesTotal > 0L) {
                                            "${formatBytes(bytesDone)} / ${formatBytes(bytesTotal)}"
                                        } else if (bytesDone > 0L) {
                                            formatBytes(bytesDone)
                                        } else {
                                            downloadingText
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = sizeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = timeLeftText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Update available banner
                        if (isUpdatePending) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.update_available),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Button(
                                        onClick = onUpdateClick,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Text(stringResource(R.string.update_now))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Game information section
                        Text(
                            text = stringResource(R.string.game_information),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        // Info cards in 2-column grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val statusText = when {
                                isInstalled -> stringResource(R.string.installed)
                                isDownloading -> stringResource(R.string.installing)
                                else -> stringResource(R.string.not_installed)
                            }
                            val statusColor = when {
                                isInstalled -> PluviaTheme.colors.statusInstalled
                                isDownloading -> MaterialTheme.colorScheme.tertiary
                                else -> null
                            }
                            InfoCard(
                                label = stringResource(R.string.status),
                                value = statusText,
                                statusColor = statusColor,
                                isCompact = true,
                                modifier = Modifier.weight(1f),
                            )
                            InfoCard(
                                label = stringResource(R.string.size),
                                value = when {
                                    isInstalled && displayInfo.sizeOnDisk != null -> displayInfo.sizeOnDisk
                                    !isInstalled && displayInfo.sizeFromStore != null -> displayInfo.sizeFromStore
                                    else -> "Unknown"
                                },
                                isCompact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            InfoCard(
                                label = stringResource(R.string.developer),
                                value = displayInfo.developer,
                                isCompact = true,
                                modifier = Modifier.weight(1f),
                            )
                            InfoCard(
                                label = stringResource(R.string.release_date),
                                value = remember(displayInfo.releaseDate) {
                                    if (displayInfo.releaseDate > 0) {
                                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                            .format(Date(displayInfo.releaseDate * 1000))
                                    } else {
                                        "Unknown"
                                    }
                                },
                                isCompact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Install location (when installed)
                        if (isInstalled && displayInfo.installLocation != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            InfoCard(
                                label = stringResource(R.string.location),
                                value = displayInfo.installLocation,
                                isCompact = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Play time and last played
                        if (displayInfo.playtimeText != null || displayInfo.lastPlayedText != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (displayInfo.playtimeText != null) {
                                    InfoCard(
                                        label = stringResource(R.string.play_time),
                                        value = displayInfo.playtimeText,
                                        isCompact = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (displayInfo.lastPlayedText != null) {
                                    InfoCard(
                                        label = stringResource(R.string.last_played),
                                        value = displayInfo.lastPlayedText,
                                        isCompact = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            GamepadActionBar(
                actions = listOf(
                    if (isInstalled) {
                        GamepadAction(
                            button = GamepadButton.START,
                            labelResId = R.string.run_app,
                            onClick = onDownloadInstallClick,
                        )
                    } else if (isDownloading) {
                        GamepadAction(
                            button = GamepadButton.START,
                            labelResId = R.string.pause_download,
                            onClick = onPauseResumeClick,
                        )
                    } else if (hasPartialDownload) {
                        GamepadAction(
                            button = GamepadButton.START,
                            labelResId = R.string.resume_download,
                            onClick = onPauseResumeClick,
                        )
                    } else {
                        GamepadAction(
                            button = GamepadButton.START,
                            labelResId = R.string.install_app,
                            onClick = onDownloadInstallClick,
                        )
                    },
                    GamepadAction(
                        button = GamepadButton.SELECT,
                        labelResId = R.string.options,
                        onClick = { optionsMenuVisible = true },
                    ),
                    GamepadAction(
                        button = GamepadButton.B,
                        labelResId = R.string.back,
                        onClick = onBack,
                    ),
                ),
                visible = !optionsMenuVisible,
            )
        }

        // Options panel - slides in from right
        GameOptionsPanel(
            isOpen = optionsMenuVisible,
            onDismiss = { optionsMenuVisible = false },
            options = optionsMenu.toList(),
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
fun GameMigrationDialog(
    progress: Float,
    currentFile: String,
    movedFiles: Int,
    totalFiles: Int,
) {
    AlertDialog(
        onDismissRequest = {
            // We don't allow dismissal during move.
        },
        icon = { Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null) },
        title = { Text(text = stringResource(R.string.moving_files)) },
        text = {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.library_file_count, movedFiles + 1, totalFiles),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = currentFile,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { progress },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {},
    )
}

/***********
 * PREVIEW *
 ***********/

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(
    device = "spec:width=1920px,height=1080px,dpi=440",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
) // Odin2 Mini
@Composable
private fun Preview_AppScreen() {
    val context = LocalContext.current
    PrefManager.init(context)
    val intent = Intent(context, SteamService::class.java)
    context.startForegroundService(intent)
    var isDownloading by remember { mutableStateOf(false) }
    val fakeApp = fakeAppInfo(1)
    val displayInfo = GameDisplayInfo(
        name = fakeApp.name,
        developer = fakeApp.developer,
        releaseDate = fakeApp.releaseDate,
        heroImageUrl = fakeApp.getHeroUrl(),
        iconUrl = fakeApp.iconUrl,
        gameId = fakeApp.id,
        appId = "STEAM_${fakeApp.id}",
        installLocation = null,
        sizeOnDisk = null,
        sizeFromStore = null,
        lastPlayedText = null,
        playtimeText = null,
    )
    PluviaTheme {
        Surface {
            AppScreenContent(
                displayInfo = displayInfo,
                isInstalled = false,
                isValidToDownload = true,
                isDownloading = isDownloading,
                downloadProgress = .50f,
                hasPartialDownload = false,
                isUpdatePending = false,
                downloadInfo = null,
                onDownloadInstallClick = { isDownloading = !isDownloading },
                onPauseResumeClick = { },
                onDeleteDownloadClick = { },
                onUpdateClick = { },
                optionsMenu = AppOptionMenuType.entries.map {
                    AppMenuOption(
                        optionType = it,
                        onClick = { },
                    )
                }.toTypedArray(),
            )
        }
    }
}
}
