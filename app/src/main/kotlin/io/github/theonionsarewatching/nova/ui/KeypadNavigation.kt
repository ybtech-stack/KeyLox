package io.github.theonionsarewatching.nova.ui

import android.view.View

object KeypadNavigation {
    fun focus(target: View?): Boolean {
        if (target == null || !target.isShown || !target.isEnabled) return false
        target.isFocusable = true
        target.isFocusableInTouchMode = true
        if (!target.requestFocus()) {
            target.post { target.requestFocusFromTouch() }
        }
        return true
    }
}
