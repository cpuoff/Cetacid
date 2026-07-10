@file:OptIn(ExperimentalMaterial3Api::class)

package org.cpuoff.cetacid.ui.views

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FilenameUtils
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.cpuoff.cetacid.Dialog
import org.cpuoff.cetacid.MainViewModel
import org.cpuoff.cetacid.R
import org.cpuoff.cetacid.data.Track
import org.cpuoff.cetacid.data.loadArtwork
import org.cpuoff.cetacid.globals.Strings
import org.cpuoff.cetacid.ui.components.DialogBase
import org.cpuoff.cetacid.utils.icuFormat

private val SUPPORTED_TAG_FORMATS = setOf(
    "mp3", "flac", "ogg", "m4a", "mp4", "wma", "wav", "aif", "aiff", "dsf"
)

private val INVALID_FILENAME_CHARS_REGEX = Regex("[\\\\/:*?\"<>|]")
private const val RESCAN_DELAY_MS = 500L
private const val COVER_ART_MAX_DIM = 500

private sealed class EditTagsSaveResult {
    data object Success : EditTagsSaveResult()
    data class Error(val message: String) : EditTagsSaveResult()
}

/** Result of processing a picked cover image: the file written to cache + the in-memory bitmap for preview. */
private data class ProcessedCover(val filePath: String, val bitmap: Bitmap)

@Stable
class EditTagsDialog(private val track: Track) : Dialog() {

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()
        val uiManager = viewModel.uiManager
        val configuration = LocalConfiguration.current
        val maxDialogHeight = (configuration.screenHeightDp * 0.7).dp

        var title by rememberSaveable { mutableStateOf(track.title ?: "") }
        var artist by rememberSaveable { mutableStateOf(track.artists.joinToString(", ")) }
        var album by rememberSaveable { mutableStateOf(track.album ?: "") }
        var albumArtist by rememberSaveable { mutableStateOf(track.albumArtists.joinToString(", ")) }
        var genre by rememberSaveable { mutableStateOf(track.genres.joinToString(", ")) }
        var year by rememberSaveable { mutableStateOf(track.year?.toString() ?: "") }
        var trackNumber by rememberSaveable { mutableStateOf(track.trackNumber?.toString() ?: "") }
        var discNumber by rememberSaveable { mutableStateOf(track.discNumber?.toString() ?: "") }
        var comment by rememberSaveable { mutableStateOf(track.comment ?: "") }
        var lyrics by rememberSaveable { mutableStateOf(track.unsyncedLyrics ?: "") }

        var isSaving by remember { mutableStateOf(false) }
        // در حال پردازش عکس انتخاب‌شده (کپی/دیکود/ریسایز/فشرده‌سازی). تا این true هست نه دکمه‌ی
        // تغییر کاور و نه دکمه‌ی Save فعال نیستن.
        var isProcessingImage by remember { mutableStateOf(false) }

        val originalArtwork = remember(track) {
            loadArtwork(context, track.id, track.path, sizeLimit = 256)
        }

        // مسیر فایل کاور پردازش‌شده روی دیسک - همینو به saveTagsToFile می‌دیم
        var pickedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
        // بیت‌مپ پیش‌نمایش - حالا مستقیماً همون لحظه‌ی پردازش عکس ست میشه (پایین‌تر)، نه با یه
        // مرحله‌ی جدا و دیرهنگام؛ همین موازی‌کاری قبلاً باعث می‌شد هم نمایش آپدیت نشه هم با زدن
        // زودهنگام Save، pickedImagePath هنوز null باشه.
        var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

        // شبکه‌ی نجات: فقط برای وقتی دیالوگ از نو ساخته میشه (مثلاً چرخش صفحه) و pickedImagePath
        // از rememberSaveable برگشته ولی selectedImageBitmap (که سیو نمیشه) خالیه.
        LaunchedEffect(pickedImagePath) {
            if (pickedImagePath != null && selectedImageBitmap == null) {
                selectedImageBitmap = withContext(Dispatchers.IO) {
                    try {
                        BitmapFactory.decodeFile(pickedImagePath)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            if (uri != null) {
                isProcessingImage = true
                coroutineScope.launch {
                    val processed = withContext(Dispatchers.IO) {
                        try {
                            // مرحله ۱: کپی کامل فایل از گالری تا باگ BitmapFactory رخ ندهد
                            val rawFile = File(context.cacheDir, "raw_cover_${track.id}.tmp")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                rawFile.outputStream().use { output -> input.copyTo(output) }
                            } ?: throw Exception("Could not open the selected image.")

                            // اول فقط ابعاد عکس رو می‌خونیم (بدون لود کامل) تا با عکس‌های خیلی
                            // بزرگ دوربین گوشی (۴۸-۲۰۰ مگاپیکسل) به OutOfMemory نخوریم؛ قبلاً
                            // decodeFile بدون sampleSize کل عکس اورجینال رو لود می‌کرد که هم کند
                            // بود هم ممکن بود با یه Error (نه Exception، پس catch نمی‌شد) بی‌صدا شکست بخوره.
                            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(rawFile.absolutePath, boundsOptions)
                            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                                throw Exception("The image is not readable.")
                            }

                            var sampleSize = 1
                            while (
                                boundsOptions.outWidth / (sampleSize * 2) >= COVER_ART_MAX_DIM &&
                                boundsOptions.outHeight / (sampleSize * 2) >= COVER_ART_MAX_DIM
                            ) {
                                sampleSize *= 2
                            }

                            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            val originalBitmap = BitmapFactory.decodeFile(rawFile.absolutePath, decodeOptions)
                                ?: throw Exception("The image is not readable.")

                            // مرحله ۲ (ایده خودتان): محدودیت سخت‌گیرانه برای سایز کاور (حداکثر ۵۰۰ پیکسل)
                            val maxDim = COVER_ART_MAX_DIM.toFloat()
                            val scale = minOf(maxDim / originalBitmap.width, maxDim / originalBitmap.height)
                            val scaledBitmap = if (scale < 1f) {
                                Bitmap.createScaledBitmap(
                                    originalBitmap,
                                    (originalBitmap.width * scale).toInt(),
                                    (originalBitmap.height * scale).toInt(),
                                    true
                                )
                            } else {
                                originalBitmap
                            }

                            // مرحله ۳: ذخیره با فرمت JPEG و کیفیت ۸۰ برای رسیدن به حداقل حجم ممکن
                            val processedFile = File(
                                context.cacheDir,
                                "picked_cover_${track.id}_${System.currentTimeMillis()}.jpg"
                            )
                            processedFile.outputStream().use { output ->
                                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
                            }

                            rawFile.delete()
                            ProcessedCover(processedFile.absolutePath, scaledBitmap)
                        } catch (e: Exception) {
                            Log.e("EditTagsDialog", "Failed to process image", e)
                            null
                        }
                    }

                    if (processed != null) {
                        // فایل انتخاب قبلی (اگه کاربر قبلاً یه عکس دیگه انتخاب کرده بود) رو پاک کن
                        val previousPath = pickedImagePath
                        if (previousPath != null && previousPath != processed.filePath) {
                            File(previousPath).delete()
                        }
                        // هر دو state رو همینجا، بلافاصله و با هم ست می‌کنیم: هم مسیر فایل (برای
                        // Save) هم بیت‌مپ (برای نمایش فوری توی همین صفحه)
                        pickedImagePath = processed.filePath
                        selectedImageBitmap = processed.bitmap
                    } else {
                        uiManager.toast("Error processing selected image.")
                    }
                    isProcessingImage = false
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                pickedImagePath?.let { path ->
                    try {
                        val f = File(path)
                        if (f.exists()) f.delete()
                    } catch (e: Exception) {
                        Log.d("EditTagsDialog", "Could not delete temp cover file: ${e.message}")
                    }
                }
            }
        }

        // Photo Picker (PickVisualMedia) نیازی به READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE نداره،
        // پس دیگه لازم نیست permission چک بشه - این خودش یه منبع خطای دیگه رو حذف می‌کنه.
        fun changeCoverArt() {
            if (isProcessingImage) return
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        val isSupportedFormat = remember(track.path) {
            FilenameUtils.getExtension(track.path).lowercase() in SUPPORTED_TAG_FORMATS
        }

        val originalTitle = remember { track.title }
        val needsRename = remember(title) {
            val newTitle = title.trim()
            newTitle.isNotEmpty() && newTitle != originalTitle && originalTitle != null
        }

        fun handleSaveResult(result: EditTagsSaveResult) {
            isSaving = false
            when (result) {
                is EditTagsSaveResult.Success -> {
                    uiManager.toast(Strings[R.string.toast_track_tags_saved])
                    viewModel.launchDelayedLibraryScan(RESCAN_DELAY_MS)
                    uiManager.closeDialog()
                }
                is EditTagsSaveResult.Error -> {
                    uiManager.toast(Strings[R.string.toast_track_tags_save_failed].icuFormat(result.message))
                }
            }
        }

        val writeResultLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    val imageFile = pickedImagePath?.let { File(it) }
                    val saveResult = saveTagsToFile(
                        context, track.uri, track.path, title, artist, album, albumArtist,
                        genre, year, trackNumber, discNumber, comment, lyrics, imageFile
                    )

                    if (saveResult is EditTagsSaveResult.Success && needsRename) {
                        tryRenameFile(context, track, title.trim())
                    }

                    withContext(Dispatchers.Main) { handleSaveResult(saveResult) }
                }
            } else {
                isSaving = false
                uiManager.toast(Strings[R.string.toast_track_tags_save_failed].icuFormat("Permission denied"))
            }
        }

        fun saveTagsWithPermission() {
            if (isSaving || isProcessingImage) return
            isSaving = true

            coroutineScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intentSender = MediaStore.createWriteRequest(
                        context.contentResolver, listOf(track.uri)
                    ).intentSender
                    writeResultLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                } else {
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val imageFile = pickedImagePath?.let { File(it) }
                        val saveResult = saveTagsToFile(
                            context, track.uri, track.path, title, artist, album, albumArtist,
                            genre, year, trackNumber, discNumber, comment, lyrics, imageFile
                        )

                        if (saveResult is EditTagsSaveResult.Success && needsRename) {
                            tryRenameFile(context, track, title.trim())
                        }

                        withContext(Dispatchers.Main) { handleSaveResult(saveResult) }
                    }
                }
            }
        }

        DialogBase(
            title = Strings[R.string.track_edit_tags_title],
            onConfirm = { saveTagsWithPermission() },
            onDismiss = { uiManager.closeDialog() },
            confirmText = Strings[R.string.track_edit_tags_save],
            confirmEnabled = isSupportedFormat && !isSaving && !isProcessingImage,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
            ) {
                if (!isSupportedFormat) {
                    Text(
                        Strings[R.string.track_edit_tags_unsupported_format],
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (isSupportedFormat) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Strings[R.string.track_edit_tags_cover_art],
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val bitmapToDisplay = selectedImageBitmap ?: originalArtwork
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .padding(end = 16.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmapToDisplay != null) {
                                Image(
                                    bitmap = bitmapToDisplay.asImageBitmap(),
                                    contentDescription = Strings[R.string.track_edit_tags_cover_art],
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isProcessingImage) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        Button(
                            onClick = { changeCoverArt() },
                            enabled = !isProcessingImage,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(text = Strings[R.string.track_edit_tags_change_cover])
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(Strings[R.string.track_edit_tags_title_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text(Strings[R.string.track_edit_tags_artist_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text(Strings[R.string.track_edit_tags_album_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = albumArtist, onValueChange = { albumArtist = it }, label = { Text(Strings[R.string.track_edit_tags_album_artist_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text(Strings[R.string.track_edit_tags_genre_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text(Strings[R.string.track_edit_tags_year_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = trackNumber, onValueChange = { trackNumber = it }, label = { Text(Strings[R.string.track_edit_tags_track_number_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = discNumber, onValueChange = { discNumber = it }, label = { Text(Strings[R.string.track_edit_tags_disc_number_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text(Strings[R.string.track_edit_tags_comment_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, minLines = 2, maxLines = 3)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, label = { Text(Strings[R.string.track_edit_tags_lyrics_field]) }, modifier = Modifier.fillMaxWidth(), enabled = isSupportedFormat, minLines = 2, maxLines = 4)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun saveTagsToFile(
    context: android.content.Context,
    trackUri: Uri,
    path: String,
    title: String,
    artist: String,
    album: String,
    albumArtist: String,
    genre: String,
    year: String,
    trackNumber: String,
    discNumber: String,
    comment: String,
    lyrics: String,
    imageFile: File?
): EditTagsSaveResult {
    val extension = FilenameUtils.getExtension(path)
    val tempFile = File(context.cacheDir, "temp_edit_${System.currentTimeMillis()}.$extension")
    return try {
        context.contentResolver.openInputStream(trackUri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw Exception("Failed to open input stream")

        val audioFile = try {
            AudioFileIO.read(tempFile)
        } catch (e: Exception) {
            throw Exception("The file format is not supported by the editor.")
        }

        val tag = audioFile.tagOrCreateAndSetDefault

        fun setOrDeleteField(key: FieldKey, value: String) {
            if (value.isNotBlank()) tag.setField(key, value.trim()) else tag.deleteField(key)
        }

        fun setOrDeleteNumericField(key: FieldKey, value: String) {
            val trimmed = value.trim()
            if (trimmed.isNotBlank()) tag.setField(key, trimmed) else tag.deleteField(key)
        }

        setOrDeleteField(FieldKey.TITLE, title)
        setOrDeleteField(FieldKey.ARTIST, artist)
        setOrDeleteField(FieldKey.ALBUM, album)
        setOrDeleteField(FieldKey.ALBUM_ARTIST, albumArtist)
        setOrDeleteField(FieldKey.GENRE, genre)
        setOrDeleteNumericField(FieldKey.YEAR, year)
        setOrDeleteNumericField(FieldKey.TRACK, trackNumber)
        setOrDeleteNumericField(FieldKey.DISC_NO, discNumber)
        setOrDeleteField(FieldKey.COMMENT, comment)
        setOrDeleteField(FieldKey.LYRICS, lyrics)

        if (imageFile != null && imageFile.exists()) {
            try {
                tag.deleteArtworkField()
                val artwork = ArtworkFactory.createArtworkFromFile(imageFile)
                artwork.pictureType = 3 // Front Cover
                tag.setField(artwork)
            } catch (e: Exception) {
                Log.e("EditTagsDialog", "Failed to set artwork", e)
            }
        }

        audioFile.commit()

        context.contentResolver.openOutputStream(trackUri, "rwt")?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw Exception("Error writing to the original file.")

        try {
            val values = ContentValues().apply { put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000) }
            context.contentResolver.update(trackUri, values, null, null)
        } catch (e: Exception) {
            Log.d("EditTagsDialog", "MediaStore cache update failed")
        }

        EditTagsSaveResult.Success
    } catch (e: Exception) {
        EditTagsSaveResult.Error(e.message ?: "Unknown error")
    } finally {
        if (tempFile.exists()) tempFile.delete()
    }
}

private fun tryRenameFile(context: android.content.Context, track: Track, newTitle: String) {
    try {
        val extension = FilenameUtils.getExtension(track.path)
        val sanitizedTitle = newTitle.replace(INVALID_FILENAME_CHARS_REGEX, "_")
        if (sanitizedTitle.isBlank() || sanitizedTitle.all { it == '_' }) return

        val newFileName = "$sanitizedTitle.$extension"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val values = ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, newFileName) }
            context.contentResolver.update(track.uri, values, null, null)
        } else {
            val oldFile = File(track.path)
            val newFile = File(oldFile.parent, newFileName)
            if (oldFile.exists() && !newFile.exists()) oldFile.renameTo(newFile)
        }
    } catch (e: Exception) {
        Log.d("EditTagsDialog", "Could not rename file: ${e.message}")
    }
}
