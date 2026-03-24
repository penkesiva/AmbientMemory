package com.ambientmemory.timeline.data

import android.content.Context
import java.io.File
import java.util.UUID

class ImageStorage(context: Context) {
    private val root = File(context.filesDir, "captures").apply { mkdirs() }

    fun newImageFile(captureSessionId: Long): File {
        val name = "${captureSessionId}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
        return File(root, name)
    }

    fun deleteAll() {
        root.listFiles()?.forEach { it.delete() }
    }

    companion object {
        fun fromPath(path: String): File = File(path)
    }
}
