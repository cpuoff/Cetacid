package org.cpuoff.cetacid.ui.views.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.cpuoff.cetacid.Dialog
import org.cpuoff.cetacid.MainViewModel
import org.cpuoff.cetacid.R
import org.cpuoff.cetacid.globals.Strings
import org.cpuoff.cetacid.ui.components.DialogBase
import org.cpuoff.cetacid.ui.components.EmptyListIndicator

@Stable
class PlaylistIoSyncLogDialog : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val syncLog by viewModel.playlistManager.syncLog.collectAsStateWithLifecycle()

        DialogBase(
            title = Strings[R.string.playlist_io_sync_log],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            if (syncLog == null) {
                EmptyListIndicator()
            } else {
                Text(
                    syncLog!!,
                    modifier =
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Stable
class PlaylistIoSyncHelpDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        DialogBase(
            title = Strings[R.string.playlist_io_sync_help],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            Column(
                modifier =
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(Strings[R.string.playlist_io_sync_help_body])
            }
        }
    }
}
