package io.github.theonionsarewatching.nova.ui

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText

class KeypadEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    var preImeKeyHandler: ((KeyEvent) -> Boolean)? = null

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
