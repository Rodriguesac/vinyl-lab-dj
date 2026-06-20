package com.rodriguesacai.vinyllab

import android.app.Application
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

data class VinylUiState(
    val trackName: String = "Escolha uma música do seu celular",
    val hasTrack: Boolean = false,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val loopEnabled: Boolean = false,
    val loopStartMs: Long = 0L,
    val loopEndMs: Long = 0L,
    val reverseJogEnabled: Boolean = false,
    val errorMessage: String? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val player = ExoPlayer.Builder(application).build()
    private var ticker: Job? = null
    private var reverseJogJob: Job? = null
    private var resumeAfterScratch = false

    var uiState = VinylUiState()
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlayerState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlayerState()
            }

            override fun onPlayerError(error: PlaybackException) {
                uiState = uiState.copy(
                    isPlaying = false,
                    errorMessage = "Não foi possível reproduzir este arquivo. Tente outro áudio."
                )
            }
        })
        ticker = viewModelScope.launch {
            while (isActive) {
                if (uiState.hasTrack) {
                    enforceLoopIfNeeded()
                    syncPlayerState()
                }
                delay(180)
            }
        }
    }

    fun loadTrack(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        stopReverseJog()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        uiState = uiState.copy(
            trackName = queryFileName(uri),
            hasTrack = true,
            isPlaying = false,
            durationMs = 0L,
            positionMs = 0L,
            errorMessage = null,
            loopEnabled = false,
            loopStartMs = 0L,
            loopEndMs = 0L
        )
    }

    fun togglePlayPause() {
        if (!uiState.hasTrack) return
        if (uiState.reverseJogEnabled) stopReverseJog()
        if (player.isPlaying) player.pause() else player.play()
        syncPlayerState()
    }

    fun setSpeed(speed: Float) {
        val safe = speed.coerceIn(0.5f, 2f)
        player.playbackParameters = PlaybackParameters(safe, uiState.pitch)
        uiState = uiState.copy(speed = safe)
    }

    fun setPitch(pitch: Float) {
        val safe = pitch.coerceIn(0.5f, 2f)
        player.playbackParameters = PlaybackParameters(uiState.speed, safe)
        uiState = uiState.copy(pitch = safe)
    }

    fun seekTo(positionMs: Long) {
        if (!uiState.hasTrack) return
        player.seekTo(positionMs.coerceIn(0L, safeDuration()))
        syncPlayerState()
    }

    fun jogBy(deltaMs: Long) {
        if (!uiState.hasTrack) return
        val next = (player.currentPosition + deltaMs).coerceIn(0L, safeDuration())
        player.seekTo(next)
        syncPlayerState()
    }

    fun startScratch() {
        if (!uiState.hasTrack) return
        resumeAfterScratch = player.isPlaying
        player.pause()
        syncPlayerState()
    }

    fun endScratch() {
        if (resumeAfterScratch && !uiState.reverseJogEnabled) player.play()
        resumeAfterScratch = false
        syncPlayerState()
    }

    fun toggleLoop() {
        if (!uiState.hasTrack) return
        if (uiState.loopEnabled) {
            uiState = uiState.copy(loopEnabled = false)
        } else {
            val start = player.currentPosition.coerceAtLeast(0L)
            val end = min(start + 8_000L, safeDuration())
            uiState = uiState.copy(
                loopEnabled = end > start,
                loopStartMs = start,
                loopEndMs = end
            )
        }
    }

    /**
     * ExoPlayer does not decode arbitrary local files backwards. This mode is a jog tool:
     * it pauses sound and moves the playhead back in small steps while the platter spins backwards.
     */
    fun toggleReverseJog() {
        if (!uiState.hasTrack) return
        if (uiState.reverseJogEnabled) {
            stopReverseJog()
            return
        }
        player.pause()
        uiState = uiState.copy(reverseJogEnabled = true, isPlaying = false)
        reverseJogJob = viewModelScope.launch {
            while (isActive && uiState.reverseJogEnabled) {
                val next = max(0L, player.currentPosition - 310L)
                player.seekTo(next)
                uiState = uiState.copy(positionMs = next)
                if (next == 0L) {
                    stopReverseJog()
                    break
                }
                delay(70)
            }
        }
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    private fun stopReverseJog() {
        reverseJogJob?.cancel()
        reverseJogJob = null
        uiState = uiState.copy(reverseJogEnabled = false)
        syncPlayerState()
    }

    private fun enforceLoopIfNeeded() {
        val state = uiState
        if (state.loopEnabled && state.loopEndMs > state.loopStartMs && player.currentPosition >= state.loopEndMs) {
            player.seekTo(state.loopStartMs)
        }
    }

    private fun syncPlayerState() {
        val duration = player.duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: uiState.durationMs
        uiState = uiState.copy(
            durationMs = duration.coerceAtLeast(0L),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            isPlaying = player.isPlaying && !uiState.reverseJogEnabled
        )
    }

    private fun safeDuration(): Long = uiState.durationMs.coerceAtLeast(player.duration.coerceAtLeast(0L))

    private fun queryFileName(uri: Uri): String {
        var name: String? = null
        val cursor: Cursor? = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index)
            }
        }
        return name?.takeIf { it.isNotBlank() } ?: "Faixa selecionada"
    }

    override fun onCleared() {
        ticker?.cancel()
        reverseJogJob?.cancel()
        player.release()
        super.onCleared()
    }
}
