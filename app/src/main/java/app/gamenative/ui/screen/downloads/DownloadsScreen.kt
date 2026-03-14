package app.gamenative.ui.screen.downloads

import android.text.format.Formatter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.ui.component.dialog.MessageDialog
import app.gamenative.ui.data.DownloadItemState
import app.gamenative.ui.data.DownloadsState
import app.gamenative.ui.model.DownloadsViewModel
import app.gamenative.ui.screen.settings.ContainerStorageManagerContent
import app.gamenative.ui.screen.settings.ContainerStorageManagerTransientUi
import app.gamenative.ui.screen.settings.rememberContainerStorageManagerUiState
import app.gamenative.ui.theme.PluviaTheme
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import kotlinx.coroutines.delay

private enum class DownloadsSection(
    val titleResId: Int,
    val icon: ImageVector,
) {
    Downloads(
        titleResId = R.string.settings_downloads_title,
        icon = Icons.Default.Download,
    ),
    Storage(
        titleResId = R.string.settings_storage_manage_title,
        icon = Icons.Default.Storage,
    ),
}

@Composable
fun HomeDownloadsScreen(
    onBack: () -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val storageManagerState = rememberContainerStorageManagerUiState()
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(DownloadsSection.Downloads.ordinal) }
    val sections = remember { DownloadsSection.values().toList() }
    val selectedSection = sections.getOrElse(selectedSectionIndex) { DownloadsSection.Downloads }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .displayCutoutPadding(),
    ) {
        DownloadsHeader(
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DownloadsSidebar(
                sections = sections,
                selectedSection = selectedSection,
                onSectionSelected = { selectedSectionIndex = it.ordinal },
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight(),
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                color = PluviaTheme.colors.surfacePanel.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
                shadowElevation = 12.dp,
            ) {
                when (selectedSection) {
                    DownloadsSection.Downloads -> DownloadsContent(
                        state = state,
                        onResumeDownload = { item ->
                            viewModel.onResumeDownload(item.appId, item.gameSource)
                        },
                        onPauseDownload = { item ->
                            viewModel.onPauseDownload(item.appId, item.gameSource)
                        },
                        onCancelDownload = { item ->
                            viewModel.onCancelDownload(item.appId, item.gameSource)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                    )

                    DownloadsSection.Storage -> ContainerStorageManagerContent(
                        state = storageManagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                    )
                }
            }
        }
    }

    val confirmation = state.cancelConfirmation
    MessageDialog(
        visible = confirmation != null,
        title = stringResource(R.string.cancel_download_prompt_title),
        message = confirmation?.gameName?.let {
            stringResource(R.string.downloads_cancel_confirm, it)
        },
        confirmBtnText = stringResource(R.string.yes),
        dismissBtnText = stringResource(R.string.no),
        onConfirmClick = { viewModel.onConfirmCancel() },
        onDismissClick = { viewModel.onDismissCancel() },
        onDismissRequest = { viewModel.onDismissCancel() },
    )

    ContainerStorageManagerTransientUi(storageManagerState)
}

@Composable
private fun DownloadsHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BackButton(onClick = onBack)

        Text(
            text = stringResource(R.string.settings_downloads_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DownloadsSidebar(
    sections: List<DownloadsSection>,
    selectedSection: DownloadsSection,
    onSectionSelected: (DownloadsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember {
        sections.associateWith { FocusRequester() }
    }
    var requestedInitialFocus by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = PluviaTheme.colors.surfacePanel.copy(alpha = 0.88f),
        tonalElevation = 1.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEach { section ->
                DownloadsSidebarItem(
                    section = section,
                    selected = selectedSection == section,
                    onClick = { onSectionSelected(section) },
                    focusRequester = focusRequesters.getValue(section),
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    LaunchedEffect(selectedSection, requestedInitialFocus) {
        if (requestedInitialFocus) return@LaunchedEffect
        val focusRequester = focusRequesters.getValue(selectedSection)
        repeat(3) {
            try {
                focusRequester.requestFocus()
                requestedInitialFocus = true
                return@LaunchedEffect
            } catch (_: Exception) {
                delay(80)
            }
        }
    }
}

@Composable
private fun DownloadsSidebarItem(
    section: DownloadsSection,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val accentColor = when (section) {
        DownloadsSection.Downloads -> PluviaTheme.colors.accentCyan
        DownloadsSection.Storage -> PluviaTheme.colors.accentPurple
    }
    val isHighlighted = selected || isFocused

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (isHighlighted) {
                    Modifier.background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isFocused) 0.18f else 0.12f),
                                accentColor.copy(alpha = 0.05f),
                            ),
                        ),
                    )
                } else {
                    Modifier.background(Color.Transparent)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> accentColor.copy(alpha = 0.65f)
                    selected -> accentColor.copy(alpha = 0.32f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(18.dp),
            )
            .focusRequester(focusRequester)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isFocused -> accentColor.copy(alpha = 0.22f)
                        selected -> accentColor.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = stringResource(section.titleResId),
                tint = when {
                    isHighlighted -> accentColor
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DownloadsContent(
    state: DownloadsState,
    onResumeDownload: (DownloadItemState) -> Unit,
    onPauseDownload: (DownloadItemState) -> Unit,
    onCancelDownload: (DownloadItemState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = stringResource(R.string.settings_downloads_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.downloads_overview_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = PluviaTheme.colors.textMuted,
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))

        if (state.downloads.isEmpty()) {
            EmptyDownloadsContent(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(
                    items = state.downloads.values.toList(),
                    key = { "${it.gameSource}_${it.appId}" },
                ) { item ->
                    DownloadItemCard(
                        item = item,
                        onResume = { onResumeDownload(item) },
                        onPause = { onPauseDownload(item) },
                        onCancel = { onCancelDownload(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackButton(
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
        label = "backButtonScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (isFocused) {
                    PluviaTheme.colors.accentCyan.copy(alpha = 0.2f)
                } else {
                    PluviaTheme.colors.surfaceElevated
                }
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, PluviaTheme.colors.accentCyan.copy(alpha = 0.6f), CircleShape)
                } else {
                    Modifier.border(1.dp, PluviaTheme.colors.borderDefault.copy(alpha = 0.3f), CircleShape)
                }
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
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.back),
            tint = if (isFocused) PluviaTheme.colors.accentCyan else Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun EmptyDownloadsContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            Text(
                text = stringResource(R.string.downloads_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItemState,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val detailText = item.etaMs?.let(::formatEta) ?: item.statusMessage

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoilImage(
                imageModel = { item.iconUrl.ifEmpty { null } },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    contentDescription = item.gameName,
                ),
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.gameName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    item.progress?.let { progress ->
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = PluviaTheme.colors.statusDownloading,
                        )
                    }
                }

                item.progress?.let { progress ->
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PluviaTheme.colors.statusDownloading,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                when {
                    item.bytesTotal != null && item.bytesDownloaded != null -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val bytesText = if (item.bytesTotal > 0) {
                                "${Formatter.formatFileSize(context, item.bytesDownloaded)} / ${Formatter.formatFileSize(context, item.bytesTotal)}"
                            } else {
                                Formatter.formatFileSize(context, item.bytesDownloaded)
                            }
                            Text(
                                text = bytesText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )

                            detailText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    !detailText.isNullOrBlank() -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.isPartial) {
                    DownloadActionButton(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.resume_download),
                        accentColor = PluviaTheme.colors.accentSuccess,
                        onClick = onResume,
                    )
                } else {
                    DownloadActionButton(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.pause_download),
                        accentColor = PluviaTheme.colors.accentWarning,
                        onClick = onPause,
                    )
                }

                DownloadActionButton(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    accentColor = PluviaTheme.colors.accentDanger,
                    onClick = onCancel,
                )
            }
        }
    }
}

@Composable
private fun DownloadActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "downloadActionButtonScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isFocused) {
                    accentColor.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) {
                    accentColor.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                },
                shape = CircleShape,
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
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatEta(etaMs: Long): String {
    val totalSeconds = etaMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
