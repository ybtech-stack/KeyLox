package io.github.theonionsarewatching.nova.ui

import android.content.Context
import android.text.Selection
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class KeypadEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    var preImeKeyHandler: ((KeyEvent) -> Boolean)? = null

    private var downPressTime = Long.MIN_VALUE
    private var downPressEventTime = Long.MIN_VALUE
    private var imeOwnsDownPress = false
    private var centerPressTime = Long.MIN_VALUE
    private var centerPressEventTime = Long.MIN_VALUE
    private var imeOwnsCenterPress = false

    /** Keep the editor as the last focus stop; Enter still inserts a newline. */
    internal fun handleComposeKey(event: KeyEvent, onSend: () -> Unit): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (deferDownToIme(event)) return false
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val content = text
                    val lines = layout
                    if (content != null && lines != null) {
                        if (event.isShiftPressed) Selection.extendDown(content, lines)
                        else Selection.moveDown(content, lines)
                    }
                }
                // At the last line Down stays here, without letting the IME
                // convert it into a newline or Android move focus to a label.
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                val newPress = event.downTime != centerPressTime ||
                    (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                        event.eventTime != centerPressEventTime)
                if (newPress) {
                    centerPressTime = event.downTime
                    centerPressEventTime = event.eventTime
                    imeOwnsCenterPress = hasImeComposingText()
                }
                // Selection owns the entire press, including repeats/release
                // delivered after the keyboard removes its composing spans.
                if (imeOwnsCenterPress) return false
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onSend()
                return true
            }
        }
        return false
    }

    internal fun deferDownToIme(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false
        val newPress = event.downTime != downPressTime ||
            (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                event.eventTime != downPressEventTime)
        if (newPress) {
            downPressTime = event.downTime
            downPressEventTime = event.eventTime
            // Kyocera's expanded word choices occupy the IME inset. Inline
            // composing text alone must not disable our no-newline Down path.
            imeOwnsDownPress = hasImeComposingText() &&
                ViewCompat.getRootWindowInsets(this)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        // Keep the same owner through pre-IME/Activity dispatch, repeats and
        // key-up, even if the IME changes its panel while handling the press.
        return imeOwnsDownPress
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (preImeKeyHandler?.invoke(event) == true) return true
        return super.onKeyPreIme(keyCode, event)
    }
}

internal fun EditText.hasImeComposingText(): Boolean {
    val editable = text ?: return false
    return BaseInputConnection.getComposingSpanStart(editable) >= 0 &&
        BaseInputConnection.getComposingSpanEnd(editable) >= 0
}
