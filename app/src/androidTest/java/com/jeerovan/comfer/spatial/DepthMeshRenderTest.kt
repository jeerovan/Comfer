package com.jeerovan.comfer.spatial

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class DepthMeshRenderTest {
    @Test fun extremeTiltRendersWithoutTransparentEdges() {
        val context = InstrumentationRegistry.getInstrumentation().context
        for (scene in 1..2) {
            val texture = context.assets.open("spatial/wallpaper-$scene.jpg").use { BitmapFactory.decodeStream(it)!! }
            val mesh = context.assets.open("spatial/wallpaper-$scene.depth").bufferedReader().use { DepthMesh.parse(it.readText()) }
            val output = Bitmap.createBitmap(380, 822, Bitmap.Config.ARGB_8888)
            try {
                for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                    output.eraseColor(Color.TRANSPARENT)
                    Canvas(output).drawBitmapMesh(texture, mesh.columns, mesh.rows,
                        mesh.project(380f, 822f, texture.width, texture.height, x, y, x*0.08f, y*0.08f), 0, null, 0, Paint(Paint.FILTER_BITMAP_FLAG))
                    for (edgeX in listOf(0, 379)) for (edgeY in 0 until 822) {
                        assertEquals(255, Color.alpha(output.getPixel(edgeX, edgeY)))
                    }
                    for (edgeY in listOf(0, 821)) for (edgeX in 0 until 380) {
                        assertEquals(255, Color.alpha(output.getPixel(edgeX, edgeY)))
                    }
                }
            } finally { texture.recycle(); output.recycle() }
        }
    }
}
