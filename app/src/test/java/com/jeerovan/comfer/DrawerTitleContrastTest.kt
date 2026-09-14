package com.jeerovan.comfer

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class DrawerTitleContrastTest {
    @Test fun blackAndWhiteChooseOppositeTextAndMidtonesUseHigherContrast() {
        assertEquals(Color.Black, contrastingTitleColor(Color.White))
        assertEquals(Color.White, contrastingTitleColor(Color.Black))
        assertEquals(Color.Black, contrastingTitleColor(Color(0xff808080)))
        assertEquals(Color.White, contrastingTitleColor(Color(0xff404040)))
    }
    @Test fun localRegionsCanChooseDifferentColorsOnOneWallpaper() {
        val map = WallpaperSamples(4, 2, intArrayOf(-1,-1,0xff000000.toInt(),0xff000000.toInt(),-1,-1,0xff000000.toInt(),0xff000000.toInt()))
        assertEquals(Color.Black, contrastingTitleColor(requireNotNull(map.background(Rect(0f,0f,100f,90f),400f,200f,false))))
        assertEquals(Color.White, contrastingTitleColor(requireNotNull(map.background(Rect(300f,0f,399f,90f),400f,200f,false))))
    }
    @Test fun cropAndOffscreenCoordinatesStayBounded() {
        val map = WallpaperSamples(4, 1, intArrayOf(0xff000000.toInt(),-1,-1,0xff000000.toInt()))
        assertEquals(Color.Black, contrastingTitleColor(requireNotNull(map.background(Rect(10f,10f,90f,90f),100f,100f,false))))
        assertNotNull(map.background(Rect(-200f,-200f,900f,900f),100f,100f,true))
        assertNull(map.background(Rect.Zero,0f,100f,false))
    }
}
