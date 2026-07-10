package org.cpuoff.cetacid.ui.views.preferences

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.cpuoff.cetacid.BuildConfig
import org.cpuoff.cetacid.Dialog
import org.cpuoff.cetacid.MainViewModel
import org.cpuoff.cetacid.R
import org.cpuoff.cetacid.globals.Strings
import org.cpuoff.cetacid.ui.components.DialogBase
import org.cpuoff.cetacid.utils.icuFormat

@Stable
class CheckUpdateDialog(
    private val latestVersion: String?,
    private val isUpdateAvailable: Boolean,
    private val errorMsg: String? = null
) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val context = LocalContext.current
        if (errorMsg != null) {
            DialogBase(
                title = Strings[R.string.preferences_update_error],
                onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
                confirmText = Strings[R.string.commons_ok]
            ) {
                Text(
                    text = Strings[R.string.preferences_update_error_message].icuFormat(errorMsg),
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else if (isUpdateAvailable && latestVersion != null) {
            DialogBase(
                title = Strings[R.string.preferences_update_available],
                onConfirm = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cpuoff/Cetacid/releases"))
                        )
                    } catch (e: Exception) {
                        Log.e("Cetacid", "Failed to open release link", e)
                    }
                    viewModel.uiManager.closeDialog()
                },
                onDismiss = { viewModel.uiManager.closeDialog() },
                confirmText = Strings[R.string.preferences_update_button],
                dismissText = Strings[R.string.commons_cancel]
            ) {
                Text(
                    text = Strings[R.string.preferences_update_available_message].icuFormat(latestVersion),
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            DialogBase(
                title = Strings[R.string.preferences_no_update_available],
                onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
                confirmText = Strings[R.string.commons_ok]
            ) {
                Text(
                    text = Strings[R.string.preferences_no_update_available_message].icuFormat(BuildConfig.VERSION_NAME),
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
