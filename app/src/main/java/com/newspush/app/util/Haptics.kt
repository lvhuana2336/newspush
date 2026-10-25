package com.newspush.app.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 使用系统 haptic 语义，不需要 VIBRATE 权限，也会尊重系统的触感设置。
 */
class Haptics(
    private val view: View,
    private val enabled: Boolean
) {
    fun tick() {
        perform(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun confirm() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        perform(constant)
    }

    fun reject() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        perform(constant)
    }

    fun longPress() {
        perform(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun perform(constant: Int) {
        if (enabled) {
            view.performHapticFeedback(constant)
        }
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
