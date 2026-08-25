package com.wasl.app.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wasl.app.MainActivity
import com.wasl.app.R
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.privacy.PrivacyPreferences
import com.wasl.domain.MoneyInputParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class GeneralReminderNotificationPublisher(
    private val context: Context,
) {
    private val privacyPreferences = PrivacyPreferences(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "التذكيرات العامة",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "تذكيرات متابعة عامة يحددها المستخدم لحسابات وَصل"
            },
        )
    }

    fun canNotify(): Boolean {
        ensureChannel()
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
                ?.importance != NotificationManager.IMPORTANCE_NONE
        return runtimePermissionGranted && appNotificationsEnabled && channelEnabled
    }

    @SuppressLint("MissingPermission")
    fun publish(
        reminder: GeneralReminderRecord,
        account: AccountOverview,
    ) {
        check(canNotify()) { "General reminder notifications are disabled." }
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_DEBT
                putExtra(MainActivity.EXTRA_DEBT_ID, reminder.debtId.value)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hideSensitive = privacyPreferences.hideSensitiveNotifications
        val title = if (hideSensitive) {
            "تذكير من وَصل"
        } else {
            "تذكير متابعة — ${account.person.displayName}"
        }
        val body = if (hideSensitive) {
            "لديك متابعة محفوظة تحتاج إلى انتباهك. افتح وَصل لمراجعتها."
        } else {
            "راجع حساب ${account.person.displayName}. المتبقي ${formatMoney(account)}."
        }
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("تذكير من وَصل")
            .setContentText("افتح التطبيق لمراجعة متابعة محفوظة.")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.id, NOTIFICATION_ID, notification)
    }

    private fun formatMoney(account: AccountOverview): String {
        val money = account.ledger.balance
        val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
        val value = BigDecimal.valueOf(money.minorUnits, fractionDigits)
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
            isGroupingUsed = true
        }
        return "${formatter.format(value)} ${money.currency.value}"
    }

    companion object {
        const val CHANNEL_ID = "wasl_general_reminders"
        const val NOTIFICATION_ID = 1003
    }
}
