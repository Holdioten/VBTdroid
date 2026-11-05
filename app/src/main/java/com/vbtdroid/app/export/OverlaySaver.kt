package com.vbtdroid.app.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Saves the finished overlay into the public gallery without a directory picker.
 *
 * Android 10+: MediaStore with `Pictures/VBTdroid`, no storage permission.
 * Android 8/9: public Pictures when the legacy permission is granted, otherwise the app's
 * external Pictures directory (still user-visible via file managers).
 */
object OverlaySaver {
    const val ALBUM = "VBTdroid"

    fun save(context: Context, source: File, displayName: String): Uri? {
        if (!source.isFile || source.length() <= 0L) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, displayName)
        } else {
            saveLegacy(context, source, displayName)
        }
    }

    private fun saveViaMediaStore(context: Context, source: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM",
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open export destination")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun saveLegacy(context: Context, source: File, displayName: String): Uri? {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        val target = runCatching {
            publicDir.mkdirs()
            File(publicDir, displayName).also { destination ->
                source.copyTo(destination, overwrite = true)
            }
        }.getOrNull() ?: runCatching {
            val fallbackDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
                ALBUM,
            )
            fallbackDir.mkdirs()
            File(fallbackDir, displayName).also { destination ->
                source.copyTo(destination, overwrite = true)
            }
        }.getOrNull()
        return target?.let { Uri.fromFile(it) }
    }
}
