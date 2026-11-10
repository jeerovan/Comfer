package com.jeerovan.comfer

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import org.junit.Assert.*
import org.junit.Test

/** Controlled allocation failures: never exhaust the physical device heap. */
internal class MemoryFailureDrawable(private val failClone: Boolean = false) : Drawable() {
    var draws = 0
    override fun getIntrinsicWidth() = 80
    override fun getIntrinsicHeight() = 40
    override fun draw(canvas: Canvas) { draws++; throw OutOfMemoryError("controlled drawable allocation failure") }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState? = if (!failClone) null else object : ConstantState() {
        override fun newDrawable(): Drawable = throw OutOfMemoryError("controlled drawable clone failure")
        override fun newDrawable(res: Resources?): Drawable = newDrawable()
        override fun getChangingConfigurations() = 0
    }
}

class DrawableMemoryTest {
    @Test fun failedOwnedSnapshotsAreReleasedWithoutSwallowingOtherErrors() {
        for (failure in listOf(OutOfMemoryError("controlled"), IllegalStateException("controlled"), AssertionError("unrelated"))) {
            val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
            val drawable = object : Drawable() {
                override fun draw(canvas: Canvas) { throw failure }
                override fun setAlpha(alpha: Int) = Unit
                override fun setColorFilter(colorFilter: ColorFilter?) = Unit
                @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
            }.apply { setBounds(1, 2, 3, 4) }
            val escaped = runCatching { drawable.drawOwnedBitmap(bitmap) }.exceptionOrNull()
            assertSame(failure, escaped)
            assertTrue("No failed snapshot remains allocated", bitmap.isRecycled)
            assertEquals(android.graphics.Rect(1, 2, 3, 4), drawable.bounds)
            if (failure is RuntimeException) assertNull("Existing runtime failure fallback is preserved", drawable.toOwnedBitmap())
        }
    }
    @Test fun snapshotDoesNotRecycleSourceBitmapOrSwallowUnrelatedError() {
        val source = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            val snapshot = requireNotNull(android.graphics.drawable.BitmapDrawable(Resources.getSystem(), source).toOwnedBitmap())
            assertNotSame(source, snapshot)
            snapshot.recycle()
            assertFalse(source.isRecycled)
            assertEquals(Color.BLUE, source.getPixel(1, 1))
        } finally { source.recycle() }
        val error = AssertionError("unrelated")
        val drawable = object : android.graphics.drawable.ColorDrawable(Color.RED) {
            override fun getConstantState(): ConstantState? = null
            override fun draw(canvas: Canvas) { throw error }
        }
        assertSame(error, runCatching { drawable.toOwnedBitmap() }.exceptionOrNull())
    }
    @Test fun memoryFailureReturnsFallbackAndRestoresBounds() {
        val drawable = MemoryFailureDrawable().apply { setBounds(3, 4, 17, 19) }
        assertNull("A failed icon must use the existing fallback", drawable.toOwnedBitmap())
        assertEquals(android.graphics.Rect(3, 4, 17, 19), drawable.bounds)
        assertEquals(1, drawable.draws)
    }
    @Test fun cloneMemoryFailureAlsoReturnsFallback() {
        assertNull(MemoryFailureDrawable(failClone = true).toOwnedBitmap())
    }
    @Test fun healthySnapshotRemainsBoundedOwnedAndReusableAfterFailure() {
        // A separate successful source must still render after one failed icon.
        runCatching { MemoryFailureDrawable().toOwnedBitmap() }
        val drawable = object : Drawable() {
            override fun getIntrinsicWidth() = 2048
            override fun getIntrinsicHeight() = 1024
            override fun draw(canvas: Canvas) = canvas.drawColor(Color.RED)
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.OPAQUE
        }.apply { setBounds(2, 3, 4, 5) }
        val bitmap = requireNotNull(drawable.toOwnedBitmap(128))
        try {
            assertEquals(128, bitmap.width)
            assertEquals(64, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(64, 32))
            assertFalse(bitmap.isRecycled)
            assertEquals(android.graphics.Rect(2, 3, 4, 5), drawable.bounds)
        } finally { bitmap.recycle() }
    }
    @Test fun invalidBitmapLimitStillRejectsCallerError() {
        try { MemoryFailureDrawable().toOwnedBitmap(0); fail("Invalid limit must be rejected") }
        catch (_: IllegalArgumentException) { }
    }
}
