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
import com.wasl.app.data.ReminderRecord
import com.wasl.domain.MoneyInputParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class ReminderNotificationPublisher(
    private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            DUE_ACCOUNTS_CHANNEL_ID,
            "مواعيد الحسابات",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "تذكيرات مواعيد استحقاق الحسابات المحفوظة في وَصل"
        }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun publish(reminder: ReminderRecord, account: AccountOverview) {
        check(canNotify()) { "Notifications are disabled." }
        ensureChannels()
        val openAccount = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_DEBT
            putExtra(MainActivity.EXTRA_DEBT_ID, reminder.debtId.value)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            openAccount,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = "حساب ${account.person.displayName} مستحق اليوم. " +
            "المتبقي ${formatMoney(account)}."
        val publicNotification = NotificationCompat.Builder(context, DUE_ACCOUNTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("تذكير من وَصل")
            .setContentText("افتح التطبيق لمراجعة حساب مستحق.")
            .build()
        val notification = NotificationCompat.Builder(context, DUE_ACCOUNTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("موعد استحقاق حساب")
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
        const val DUE_ACCOUNTS_CHANNEL_ID = "wasl_due_accounts"
        const val NOTIFICATION_ID = 1001
    }
}
