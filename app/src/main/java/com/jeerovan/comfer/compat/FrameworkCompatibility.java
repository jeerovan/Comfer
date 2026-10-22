package com.jeerovan.comfer.compat;

import android.annotation.SuppressLint;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityManager;
import androidx.annotation.Keep;

/** Exact linkage fallbacks for incomplete API-34 frameworks (release attempt 53-02).
 * Keep these boundaries intact through R8; never rewrite this class into itself.
 */
@Keep
public final class FrameworkCompatibility {
    private FrameworkCompatibility() {}

    @SuppressLint("NewApi")
    public static AccessibilityNodeInfo.AccessibilityAction getActionScrollInDirection() {
        try {
            return AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION;
        } catch (NoSuchFieldError missing) {
            // 58-R01: some API-34 runtimes omit this field. Construct the same
            // action AndroidX uses before API 34, preserving its ID and null label.
            // Catch only the absent field; keep healthy platform identity/errors.
            return new AccessibilityNodeInfo.AccessibilityAction(
                    android.R.id.accessibilityActionScrollInDirection, null);
        }
    }

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

    @SuppressLint("NewApi")
    public static void setAccessibilityDataSensitive(AccessibilityNodeInfo node, boolean sensitive) {
        try {
            node.setAccessibilityDataSensitive(sensitive);
        } catch (NoSuchMethodError missing) {
            // Some runtimes report API 34 but omit the node API as well as the event API.
            // Preserve text, password flags, actions and bounds, as on pre-34 AndroidX.
        }
    }

    @SuppressLint("NewApi")
    public static boolean isRequestFromAccessibilityTool(AccessibilityManager manager) {
        try {
            return manager.isRequestFromAccessibilityTool();
        } catch (NoSuchMethodError missing) {
            // Match AndroidX's pre-34 behavior. Do not cache: working frameworks
            // can return a different value for each accessibility request.
            return true;
        }
    }
}
