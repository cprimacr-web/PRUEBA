package com.musica.app

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

data class Song(val item: MediaItem, val title: String, val artist: String)

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        f.addListener({ controller = f.get() }, { it.run() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicaApp() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MusicaApp() {
        val ctx = LocalContext.current
        val dark = isSystemInDarkTheme()
        val scheme = if (Build.VERSION.SDK_INT >= 31)
            (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx))
        else (if (dark) darkColorScheme() else lightColorScheme())
        MaterialTheme(colorScheme = scheme) {
            val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                       else Manifest.permission.READ_EXTERNAL_STORAGE
            var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
            var granted by remember { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                granted = it; if (it) songs = loadSongs()
            }
            LaunchedEffect(Unit) { launcher.launch(perm) }

            var playing by remember { mutableStateOf(false) }
            var current by remember { mutableStateOf("") }
            DisposableEffect(controller) {
                val c = controller
                val l = object : Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) { playing = p }
                    override fun onMediaMetadataChanged(m: MediaMetadata) { current = m.title?.toString() ?: "" }
                }
                c?.addListener(l)
                onDispose { c?.removeListener(l) }
            }

            Scaffold(
                topBar = { LargeTopAppBar(title = { Text("MUSICA") }) },
                bottomBar = {
                    Surface(tonalElevation = 3.dp) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(current.ifEmpty { "Elige una canción" }, Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton({ controller?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, null) }
                            FilledIconButton({ controller?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                            }
                            IconButton({ controller?.seekToNext() }) { Icon(Icons.Default.SkipNext, null) }
                        }
                    }
                }
            ) { pad ->
                if (!granted || songs.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                        Text(if (!granted) "Permite el acceso a tu música" else "No se encontraron canciones")
                    }
                } else LazyColumn(Modifier.padding(pad)) {
                    itemsIndexed(songs) { i, s ->
                        ListItem(
                            headlineContent = { Text(s.title, maxLines = 1) },
                            supportingContent = { Text(s.artist, maxLines = 1) },
                            leadingContent = { Icon(Icons.Default.MusicNote, null) },
                            modifier = Modifier.clickable {
                                controller?.run {
                                    setMediaItems(songs.map { it.item }, i, 0L)
                                    prepare(); play()
                                }
                            })
                    }
                }
            }
        }
    }

    private fun loadSongs(): List<Song> {
        val out = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        contentResolver.query(uri, cols, "${MediaStore.Audio.Media.IS_MUSIC}!=0", null,
            "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val t = c.getString(1) ?: "Sin título"; val a = c.getString(2) ?: "Desconocido"
                val item = MediaItem.Builder().setUri(ContentUris.withAppendedId(uri, id))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(t).setArtist(a).build()).build()
                out.add(Song(item, t, a))
            }
        }
        return out
    }
}
