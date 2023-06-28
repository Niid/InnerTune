package com.zionhuang.music.ui.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastSumBy
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.utils.completed
import com.zionhuang.music.LocalDatabase
import com.zionhuang.music.LocalDownloadUtil
import com.zionhuang.music.LocalPlayerAwareWindowInsets
import com.zionhuang.music.LocalPlayerConnection
import com.zionhuang.music.R
import com.zionhuang.music.constants.AlbumThumbnailSize
import com.zionhuang.music.constants.PlaylistSongSortDescendingKey
import com.zionhuang.music.constants.PlaylistSongSortType
import com.zionhuang.music.constants.PlaylistSongSortTypeKey
import com.zionhuang.music.constants.ThumbnailCornerRadius
import com.zionhuang.music.db.entities.PlaylistSongMap
import com.zionhuang.music.extensions.toMediaItem
import com.zionhuang.music.models.toMediaMetadata
import com.zionhuang.music.playback.ExoDownloadService
import com.zionhuang.music.playback.queues.ListQueue
import com.zionhuang.music.ui.component.AutoResizeText
import com.zionhuang.music.ui.component.EmptyPlaceholder
import com.zionhuang.music.ui.component.FontSizeRange
import com.zionhuang.music.ui.component.LocalMenuState
import com.zionhuang.music.ui.component.SongListItem
import com.zionhuang.music.ui.component.SortHeader
import com.zionhuang.music.ui.component.TextFieldDialog
import com.zionhuang.music.ui.menu.SongMenu
import com.zionhuang.music.ui.utils.reordering.ReorderingLazyColumn
import com.zionhuang.music.ui.utils.reordering.animateItemPlacement
import com.zionhuang.music.ui.utils.reordering.draggedItem
import com.zionhuang.music.ui.utils.reordering.rememberReorderingState
import com.zionhuang.music.ui.utils.reordering.reorder
import com.zionhuang.music.utils.makeTimeString
import com.zionhuang.music.utils.rememberEnumPreference
import com.zionhuang.music.utils.rememberPreference
import com.zionhuang.music.viewmodels.LocalPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val playWhenReady by playerConnection.playWhenReady.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val playlistLength = remember(songs) {
        songs.fastSumBy { it.song.song.duration }
    }
    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSongSortTypeKey, PlaylistSongSortType.CUSTOM)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSongSortDescendingKey, true)

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED })
                    Download.STATE_COMPLETED
                else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED
                                || downloads[it.song.id]?.state == Download.STATE_DOWNLOADING
                                || downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    })
                    Download.STATE_DOWNLOADING
                else
                    Download.STATE_STOPPED
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        playlist?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
                title = { Text(text = stringResource(R.string.edit_playlist)) },
                onDismiss = { showEditDialog = false },
                initialTextFieldValue = TextFieldValue(playlistEntity.name, TextRange(playlistEntity.name.length)),
                onDone = { name ->
                    database.query {
                        update(playlistEntity.copy(name = name))
                    }
                }
            )
        }
    }

    val reorderingState = rememberReorderingState(
        lazyListState = lazyListState,
        key = songs,
        onDragEnd = { fromIndex, toIndex ->
            database.query {
                move(viewModel.playlistId, fromIndex, toIndex)
            }
        },
        extraItemCount = 1
    )

    ReorderingLazyColumn(
        reorderingState = reorderingState,
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
        playlist?.let { playlist ->
            if (playlist.songCount == 0) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.playlist_is_empty)
                    )
                }
            } else {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (playlist.thumbnails.size == 1) {
                                AsyncImage(
                                    model = playlist.thumbnails[0],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(AlbumThumbnailSize)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                )
                            } else if (playlist.thumbnails.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(AlbumThumbnailSize)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                ) {
                                    listOf(
                                        Alignment.TopStart,
                                        Alignment.TopEnd,
                                        Alignment.BottomStart,
                                        Alignment.BottomEnd
                                    ).fastForEachIndexed { index, alignment ->
                                        AsyncImage(
                                            model = playlist.thumbnails.getOrNull(index),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .align(alignment)
                                                .size(AlbumThumbnailSize / 2)
                                        )
                                    }
                                }
                            }

                            Column(
                                verticalArrangement = Arrangement.Center,
                            ) {
                                AutoResizeText(
                                    text = playlist.playlist.name,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSizeRange = FontSizeRange(16.sp, 22.sp)
                                )

                                Text(
                                    text = pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal
                                )

                                Text(
                                    text = makeTimeString(playlistLength * 1000L),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal
                                )

                                Row {
                                    IconButton(
                                        onClick = { showEditDialog = true }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.edit),
                                            contentDescription = null
                                        )
                                    }

                                    if (playlist.playlist.browseId != null) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val playlistPage = YouTube.playlist(playlist.playlist.browseId).completed().getOrNull() ?: return@launch
                                                    database.transaction {
                                                        clearPlaylist(playlist.id)
                                                        playlistPage.songs
                                                            .map(SongItem::toMediaMetadata)
                                                            .onEach(::insert)
                                                            .mapIndexed { position, song ->
                                                                PlaylistSongMap(
                                                                    songId = song.id,
                                                                    playlistId = playlist.id,
                                                                    position = position
                                                                )
                                                            }
                                                            .forEach(::insert)
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.sync),
                                                contentDescription = null
                                            )
                                        }
                                    }

                                    when (downloadState) {
                                        Download.STATE_COMPLETED -> {
                                            IconButton(
                                                onClick = {
                                                    songs.forEach { song ->
                                                        DownloadService.sendRemoveDownload(
                                                            context,
                                                            ExoDownloadService::class.java,
                                                            song.song.id,
                                                            false
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.offline),
                                                    contentDescription = null
                                                )
                                            }
                                        }

                                        Download.STATE_DOWNLOADING -> {
                                            IconButton(
                                                onClick = {
                                                    songs.forEach { song ->
                                                        DownloadService.sendRemoveDownload(
                                                            context,
                                                            ExoDownloadService::class.java,
                                                            song.song.id,
                                                            false
                                                        )
                                                    }
                                                }
                                            ) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        else -> {
                                            IconButton(
                                                onClick = {
                                                    songs.forEach { song ->
                                                        val downloadRequest = DownloadRequest.Builder(song.song.id, song.song.id.toUri())
                                                            .setCustomCacheKey(song.song.id)
                                                            .setData(song.song.song.title.toByteArray())
                                                            .build()
                                                        DownloadService.sendAddDownload(
                                                            context,
                                                            ExoDownloadService::class.java,
                                                            downloadRequest,
                                                            false
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.download),
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = playlist.playlist.name,
                                            items = songs.map { it.song.toMediaItem() }
                                        )
                                    )
                                },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.play))
                            }

                            OutlinedButton(
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = playlist.playlist.name,
                                            items = songs.shuffled().map { it.song.toMediaItem() }
                                        )
                                    )
                                },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.shuffle))
                            }
                        }
                    }
                }

                item {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                PlaylistSongSortType.NAME -> R.string.sort_by_name
                                PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                            }
                        },
                        trailingText = ""
                    )
                }
            }
        }

        itemsIndexed(
            items = songs,
            key = { _, song -> song.map.id }
        ) { index, song ->
            val currentItem by rememberUpdatedState(song)
            val dismissState = rememberDismissState(
                positionalThreshold = { totalDistance ->
                    totalDistance
                },
                confirmValueChange = { dismissValue ->
                    if (dismissValue == DismissValue.DismissedToEnd || dismissValue == DismissValue.DismissedToStart) {
                        database.transaction {
                            move(currentItem.map.playlistId, currentItem.map.position, Int.MAX_VALUE)
                            delete(currentItem.map.copy(position = Int.MAX_VALUE))
                        }
                        true
                    } else {
                        false
                    }
                }
            )

            SwipeToDismiss(
                state = dismissState,
                background = {},
                dismissContent = {
                    SongListItem(
                        song = song.song,
                        isPlaying = song.song.id == mediaMetadata?.id,
                        playWhenReady = playWhenReady,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song.song,
                                            navController = navController,
                                            playerConnection = playerConnection,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null
                                )
                            }

                            if (sortType == PlaylistSongSortType.CUSTOM) {
                                IconButton(
                                    onClick = { },
                                    modifier = Modifier.reorder(reorderingState = reorderingState, index = index)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.drag_handle),
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = playlist!!.playlist.name,
                                        items = songs.map { it.song.toMediaItem() },
                                        startIndex = index
                                    )
                                )
                            }
                            .animateItemPlacement(reorderingState = reorderingState)
                            .draggedItem(reorderingState = reorderingState, index = index)
                    )
                }
            )
        }
    }

    TopAppBar(
        title = { if (showTopBarTitle) Text(playlist?.playlist?.name.orEmpty()) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
