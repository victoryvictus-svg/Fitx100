package com.fit100.tracker

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Shares only app-owned portrait files with the camera through temporary URI grants. */
class ProfilePhotoProvider : ContentProvider() {
    override fun onCreate() = true
    private fun photoFile(uri: Uri): File {
        require(uri.pathSegments.size == 1)
        val name = requireNotNull(uri.lastPathSegment)
        require(name.matches(Regex("portrait-[0-9]+\\.jpg")))
        return File(requireNotNull(context).filesDir, "profile-photos/$name")
    }
    override fun getType(uri: Uri): String { photoFile(uri); return "image/jpeg" }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = photoFile(uri)
        file.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = photoFile(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map { column -> when (column) { OpenableColumns.DISPLAY_NAME -> file.name; OpenableColumns.SIZE -> file.length(); else -> null } }.toTypedArray()) }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
}
