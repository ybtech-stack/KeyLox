package io.github.theonionsarewatching.nova.ui

import android.view.KeyEvent
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

    fun handleMove(event: KeyEvent, keyCode: Int, target: () -> View?): Boolean {
        if (event.keyCode != keyCode) return false
        if (event.action == KeyEvent.ACTION_DOWN) focus(target())
        return true
    }
}
