package io.github.theonionsarewatching.nova.ui

import android.view.KeyEvent
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import io.github.theonionsarewatching.nova.R

/** The same compose actions in new messages and replies, without extra focus stops. */
object ComposeOptions {
    fun show(
        activity: BaseActivity,
        target: EditText,
        onSend: () -> Unit,
        onAttach: () -> Unit,
        onRecord: () -> Unit,
        onSchedule: () -> Unit,
        onConversation: (() -> Unit)? = null
    ) {
        val items = arrayListOf(
            activity.getString(R.string.softkey_send) to onSend,
            activity.getString(R.string.softkey_attach) to onAttach,
            activity.getString(R.string.attach_menu_record) to onRecord,
            activity.getString(R.string.schedule_send) to onSchedule
        )
        val clip = activity.clipboardText()
        if (!clip.isNullOrBlank()) items += activity.getString(R.string.paste) to {
            val start = target.selectionStart.coerceAtLeast(0)
            val end = target.selectionEnd.coerceAtLeast(0)
            target.text.replace(minOf(start, end), maxOf(start, end), clip)
            target.requestFocus()
        }
        if (onConversation != null) {
            items += activity.getString(R.string.conversation_options) to onConversation
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.message_options)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .setNegativeButton(android.R.string.cancel, null)
            // Preserve the Sonim MENU echo fix when a softkey opens a menu.
            .setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_MENU }
            .show()
    }
}
