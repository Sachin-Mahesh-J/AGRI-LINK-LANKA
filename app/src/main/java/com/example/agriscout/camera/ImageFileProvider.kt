package com.example.agriscout.camera

import android.content.Context
import java.io.File

object ImageFileProvider {
    fun createReportImageFile(context: Context): File = createImageFile(context, "report")

    fun createFarmImageFile(context: Context): File = createImageFile(context, "farm")

    fun createVisitImageFile(context: Context): File = createImageFile(context, "visit")

    private fun createImageFile(context: Context, prefix: String): File {
        val imageDir = File(context.filesDir, "images").apply { mkdirs() }
        return File(imageDir, "${prefix}_${System.currentTimeMillis()}.jpg")
    }
}
