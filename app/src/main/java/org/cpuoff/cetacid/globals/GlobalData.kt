package org.cpuoff.cetacid.globals

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.cpuoff.cetacid.data.LibraryIndex
import org.cpuoff.cetacid.data.PlayerState
import org.cpuoff.cetacid.data.PlayerTransientState
import org.cpuoff.cetacid.data.PlaylistManager
import org.cpuoff.cetacid.data.Preferences
import org.cpuoff.cetacid.data.UnfilteredTrackIndex

/**
 * These are meant for sharing data between contexts. End consumers should not read these directly!
 *
 * Initialized and saved by [org.cpuoff.cetacid.MainApplication].
 */
object GlobalData {
    val initialized = AtomicBoolean(false)
    
    /** 
     * Deferred for async initialization waiting. Use this instead of busy-wait loops.
     * Completes when [initialized] becomes true.
     */
    val initializationComplete = CompletableDeferred<Unit>()

    @Volatile lateinit var preferences: MutableStateFlow<Preferences>
    @Volatile lateinit var unfilteredTrackIndex: MutableStateFlow<UnfilteredTrackIndex>
    @Volatile lateinit var playerState: MutableStateFlow<PlayerState>

    val playerTransientState = MutableStateFlow(PlayerTransientState())

    @Volatile lateinit var libraryIndex: StateFlow<LibraryIndex>

    @Volatile lateinit var playlistManager: PlaylistManager
}
