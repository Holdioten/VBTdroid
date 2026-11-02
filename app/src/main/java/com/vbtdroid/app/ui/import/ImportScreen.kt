package com.vbtdroid.app.ui.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.video.VideoMeta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen() {
    val context = LocalContext.current
    val decoder = remember(context) { VideoDecoder(context) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var videoMeta by remember { mutableStateOf<VideoMeta?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedUri = uri
            fileName = null
            videoMeta = null
            failed = false
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        loading = true
        failed = false
        try {
            fileName = queryDisplayName(context, uri)
            videoMeta = withContext(Dispatchers.IO) { decoder.readMeta(uri) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.import_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { picker.launch(arrayOf("video/*")) }) {
            Text(stringResource(R.string.import_choose_video))
        }
        Spacer(Modifier.height(24.dp))
        if (loading) {
            CircularProgressIndicator()
        }
        if (failed) {
            Text(stringResource(R.string.import_error))
        }
        fileName?.let { Text(stringResource(R.string.import_file_name, it)) }
        videoMeta?.let { meta ->
            Text(stringResource(R.string.import_duration, meta.durationSec))
            Text(stringResource(R.string.import_resolution, meta.width, meta.height))
            Text(stringResource(R.string.import_effective_fps, meta.effectiveFps))
        }
    }
}

private suspend fun queryDisplayName(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0) cursor.getString(column) else null
        } else {
            null
        }
    } ?: uri.lastPathSegment ?: uri.toString()
}
