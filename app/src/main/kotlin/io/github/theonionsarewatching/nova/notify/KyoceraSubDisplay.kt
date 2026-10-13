package io.github.theonionsarewatching.nova.notify

import android.app.Notification
import android.content.Context
import io.github.theonionsarewatching.nova.R

object KyoceraSubDisplay {
    private const val NOTIFICATION_ID = 13_999
    private const val CATEGORY_MESSAGE = 2
    private const val LAYOUT_TEXT = 0x010900af
    private const val DISPLAY_DURATION_MS = 15_000

    fun showNewMessage(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val extenderClass = Class.forName(
                "jp.kyocera.sublcd.SubLcdNotificationExtender"
            )
            var extender = extenderClass.getConstructor(Context::class.java)
                .newInstance(appContext)
            extender = extenderClass.getMethod("setCategory", Int::class.javaPrimitiveType)
                .invoke(extender, CATEGORY_MESSAGE)
            extender = extenderClass.getMethod("setLayout", Int::class.javaPrimitiveType)
                .invoke(extender, LAYOUT_TEXT)
            extender = extenderClass.getMethod("setPriority", Int::class.javaPrimitiveType)
                .invoke(extender, 0)
            extender = extenderClass.getMethod("setIcon", Int::class.javaPrimitiveType)
                .invoke(extender, R.drawable.ic_notification)
            extender = extenderClass.getMethod("setText", CharSequence::class.java)
                .invoke(extender, appContext.getString(R.string.new_message))
            extender = extenderClass.getMethod("setDuration", Int::class.javaPrimitiveType)
                .invoke(extender, DISPLAY_DURATION_MS)

            @Suppress("DEPRECATION")
            val notification = Notification.Builder(appContext)
                .extend(extender as Notification.Extender)
                .build()
            val subDisplayManager = manager(appContext)
            subDisplayManager.javaClass.getMethod(
                "notify",
                Int::class.javaPrimitiveType,
                Notification::class.java
            ).invoke(subDisplayManager, NOTIFICATION_ID, notification)
        }
    }

    fun cancel(context: Context) {
        runCatching {
            val subDisplayManager = manager(context.applicationContext)
            subDisplayManager.javaClass.getMethod("cancel", Int::class.javaPrimitiveType)
                .invoke(subDisplayManager, NOTIFICATION_ID)
        }
    }

    private fun manager(context: Context): Any {
        val managerClass = Class.forName("jp.kyocera.sublcd.SubLcdManager")
        return managerClass.getMethod("getInstance", Context::class.java)
            .invoke(null, context)
            ?: error("Kyocera sub-display manager unavailable")
    }
}
