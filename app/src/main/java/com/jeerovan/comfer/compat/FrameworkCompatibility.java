package com.jeerovan.comfer.compat;

import android.annotation.SuppressLint;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Keep;

/** Exact linkage fallbacks for incomplete API-34 frameworks (release attempt 53-02).
 * Keep these boundaries intact through R8; never rewrite this class into itself.
 */
@Keep
public final class FrameworkCompatibility {
    private FrameworkCompatibility() {}

    @SuppressLint("NewApi")
    public static int systemOverlays() {
        try {
            return WindowInsets.Type.systemOverlays();
        } catch (NoSuchMethodError missing) {
            // No platform overlay type exists. Other inset type bits are unaffected.
            return 0;
        }
    }

    @SuppressLint("NewApi")
    public static void setAccessibilityDataSensitive(AccessibilityEvent event, boolean sensitive) {
        try {
            event.setAccessibilityDataSensitive(sensitive);
        } catch (NoSuchMethodError missing) {
            // Match AndroidX's pre-34 behavior when the platform cannot store this flag.
            // Preserve the event and its other accessibility/password metadata.
        }
    }
}
