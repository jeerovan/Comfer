package com.jeerovan.comfer

import android.content.res.Resources
import androidx.annotation.StringRes

/** Keep guide instructions consistent with the buttons and tabs in the selected language. */
internal fun localizedGuideBody(resources: Resources, @StringRes body: Int): String {
    val labels = when (body) {
        R.string.app_guide_start_body -> intArrayOf(R.string.notification_all)
        R.string.app_guide_tabs_body -> intArrayOf(R.string.notification_all, R.string.ui_delete, R.string.notification_more, R.string.ui_history, R.string.ui_create_content_rule, R.string.notification_cancel, R.string.notification_settings_tab, R.string.ui_quiet_hours, R.string.ui_filters, R.string.ui_connection_and_privacy, R.string.ui_focus_timers, R.string.notification_schedule_title, R.string.guide_android_back)
        R.string.app_guide_gestures_body -> intArrayOf(R.string.ui_history, R.string.notification_saved, R.string.notification_all, R.string.notification_chronological, R.string.guide_android_back, R.string.notification_more, R.string.notification_settings_tab)
        R.string.app_guide_actions_body -> intArrayOf(R.string.notification_cancel, R.string.guide_android_back)
        R.string.app_guide_dismiss_body -> intArrayOf(R.string.notification_dismiss_selected, R.string.notification_confirm_dismiss_selected, R.string.notification_quick_snooze, R.string.notification_cancel, R.string.guide_android_back)
        R.string.app_guide_more_body -> intArrayOf(R.string.notification_protect, R.string.notification_unprotect)
        R.string.app_guide_settings_body -> intArrayOf(R.string.notification_grouped, R.string.notification_chronological, R.string.notification_pause_automation, R.string.notification_resume_automation, R.string.ui_filters, R.string.ui_test_only, R.string.notification_refresh, R.string.notification_quiet_settings)
        R.string.app_guide_quiet_body -> intArrayOf(R.string.notification_end_focus, R.string.ui_allow_precise_timing, R.string.ui_focus_timers, R.string.notification_apply_quiet_schedule, R.string.notification_disable_quiet_schedule)
        R.string.app_guide_privacy_body -> intArrayOf(R.string.ui_history)
        R.string.app_guide_howto_open_body -> intArrayOf(R.string.ui_history, R.string.notification_settings_tab, R.string.notification_saved, R.string.notification_all)
        R.string.app_guide_howto_mute_body -> intArrayOf(R.string.notification_more, R.string.notification_sound_settings, R.string.guide_android_back)
        R.string.app_guide_howto_focus_body -> intArrayOf(R.string.notification_settings_tab, R.string.ui_quiet_hours, R.string.ui_focus_timers, R.string.notification_schedule_title, R.string.notification_apply_quiet_schedule)
        R.string.app_guide_howto_clear_body -> intArrayOf(R.string.notification_dismiss_selected, R.string.notification_confirm_dismiss_selected, R.string.notification_cancel, R.string.guide_android_back)
        R.string.app_guide_howto_recover_body -> intArrayOf(R.string.notification_saved, R.string.notification_settings_tab, R.string.notification_access_settings, R.string.notification_refresh, R.string.ui_history)
        else -> intArrayOf()
    }
    return resources.getString(body, *labels.map { resources.getString(it) }.toTypedArray())
}
