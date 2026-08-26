package com.wasl.app.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wasl.app.MainActivity
import com.wasl.app.R
import com.wasl.domain.DebtId

internal object ReminderNotificationActions {
    const val ACTION_SNOOZE = "com.wasl.app.action.SNOOZE_REMINDER"
    const val EXTRA_SNOOZE_DEBT_ID = "com.wasl.app.extra.SNOOZE_DEBT_ID"
    const val EXTRA_NOTIFICATION_TAG = "com.wasl.app.extra.NOTIFICATION_TAG"
    const val EXTRA_NOTIFICATION_ID = "com.wasl.app.extra.NOTIFICATION_ID"
    const val EXTRA_PAYMENT_INTENT = "com.wasl.app.extra.PAYMENT_INTENT"
    const val PAYMENT_INTENT_PARTIAL = "PARTIAL"
    const val PAYMENT_INTENT_FULL = "FULL"

    fun addActions(
        builder: NotificationCompat.Builder,
        context: Context,
        debtId: DebtId,
        notificationTag: String,
        notificationId: Int,
    ): NotificationCompat.Builder = builder
        .addAction(
            R.drawable.ic_launcher_foreground,
            "فتح الحساب",
            activityPendingIntent(
                context = context,
                debtId = debtId,
                requestCode = requestCode(notificationTag, ACTION_OPEN_OFFSET),
            ),
        )
        .addAction(
            R.drawable.ic_launcher_foreground,
            "دفع جزء",
            activityPendingIntent(
                context = context,
                debtId = debtId,
                requestCode = requestCode(notificationTag, ACTION_PARTIAL_PAYMENT_OFFSET),
                paymentIntent = PAYMENT_INTENT_PARTIAL,
            ),
        )
        .addAction(
            R.drawable.ic_launcher_foreground,
            "تم السداد",
            activityPendingIntent(
                context = context,
                debtId = debtId,
                requestCode = requestCode(notificationTag, ACTION_FULL_PAYMENT_OFFSET),
                paymentIntent = PAYMENT_INTENT_FULL,
            ),
        )
        .addAction(
            R.drawable.ic_launcher_foreground,
            "ذكرني لاحقًا",
            PendingIntent.getBroadcast(
                context,
                requestCode(notificationTag, ACTION_SNOOZE_OFFSET),
                Intent(context, ReminderNotificationActionReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtra(EXTRA_SNOOZE_DEBT_ID, debtId.value)
                    putExtra(EXTRA_NOTIFICATION_TAG, notificationTag)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    fun openAccountIntent(
        context: Context,
        debtId: DebtId,
        paymentIntent: String? = null,
    ): Intent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_OPEN_DEBT
        putExtra(MainActivity.EXTRA_DEBT_ID, debtId.value)
        paymentIntent?.let { putExtra(EXTRA_PAYMENT_INTENT, it) }
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    private fun activityPendingIntent(
        context: Context,
        debtId: DebtId,
        requestCode: Int,
        paymentIntent: String? = null,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        openAccountIntent(context, debtId, paymentIntent),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun requestCode(notificationTag: String, offset: Int): Int =
        31 * notificationTag.hashCode() + offset

    private const val ACTION_OPEN_OFFSET = 11
    private const val ACTION_PARTIAL_PAYMENT_OFFSET = 12
    private const val ACTION_FULL_PAYMENT_OFFSET = 13
    private const val ACTION_SNOOZE_OFFSET = 14
}
