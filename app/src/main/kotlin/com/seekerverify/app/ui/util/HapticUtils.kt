package com.seekerverify.app.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import com.seekerverify.app.data.AppPreferences

fun View.hapticTap(prefs: AppPreferences) {
    if (prefs.isHapticsEnabled()) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

fun View.hapticLongPress(prefs: AppPreferences) {
    if (prefs.isHapticsEnabled()) {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
