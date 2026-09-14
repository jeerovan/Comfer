package com.jeerovan.comfer

import com.jeerovan.comfer.ui.layerScale
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerScaleTest {
    @Test fun unmeasuredOrInvalidGeometryNeverReachesAndroidAsInfinity() {
        for (size in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(1f, layerScale(48f, size), 0f)
            assertEquals(1f, layerScale(size, 48f), 0f)
        }
        assertEquals(1f, layerScale(Float.MAX_VALUE, Float.MIN_VALUE), 0f)
    }
    @Test fun ordinaryDrawerInterpolationRetainsItsScale() {
        assertEquals(.5f, layerScale(24f, 48f), 0f)
        assertEquals(1.5f, layerScale(72f, 48f), 0f)
    }
}
