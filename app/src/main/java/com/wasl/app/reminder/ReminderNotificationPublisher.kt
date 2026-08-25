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
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    UPCOMING_ACCOUNTS_CHANNEL_ID,
                    "المواعيد القادمة",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "تنبيهات وَصل قبل مواعيد استحقاق الحسابات"
                },
                NotificationChannel(
                    DUE_ACCOUNTS_CHANNEL_ID,
                    "مواعيد الحسابات",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "تذكيرات وَصل في يوم استحقاق الحسابات"
                },
                NotificationChannel(
                    OVERDUE_ACCOUNTS_CHANNEL_ID,
                    "الحسابات المتأخرة",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "متابعة الحسابات التي تجاوزت موعد الاستحقاق"
                },
                NotificationChannel(
                    ALARMS_CHANNEL_ID,
                    "المنبهات القوية",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "قناة مخصصة للمنبهات الصريحة التي يفعّلها المستخدم"
                },
                NotificationChannel(
                    INSTALLMENTS_CHANNEL_ID,
                    "الأقساط",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "تنبيهات مواعيد الأقساط في وَصل"
                },
                NotificationChannel(
                    PROMISES_CHANNEL_ID,
                    "وعود السداد",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "تنبيهات وعود السداد المحفوظة في وَصل"
                },
            ),
        )
    }

    fun canNotify(): Boolean {
        ensureChannels()
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun publish(
        reminder: ReminderRecord,
        account: AccountOverview,
        occurrence: ReminderOccurrence = ReminderOccurrence.DUE_DATE,
    ): Boolean {
        check(canNotify()) { "Notifications are disabled." }
        ensureChannels()
        val channelId = occurrence.channelId()
        if (!isChannelEnabled(channelId)) return false

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
        val title = occurrence.title()
        val body = occurrence.body(
            personName = account.person.displayName,
            money = formatMoney(account),
        )
        val publicNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("تذكير من وَصل")
            .setContentText("افتح التطبيق لمراجعة حساب يحتاج إلى انتباهك.")
            .build()
        val notification = NotificationCompat.Builder(context, channelId)
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
        return true
    }

    private fun isChannelEnabled(channelId: String): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(channelId)
                ?.importance != NotificationManager.IMPORTANCE_NONE

    private fun ReminderOccurrence.channelId(): String = when (this) {
        ReminderOccurrence.UPCOMING_DAY_BEFORE -> UPCOMING_ACCOUNTS_CHANNEL_ID
        ReminderOccurrence.DUE_DATE -> DUE_ACCOUNTS_CHANNEL_ID
        ReminderOccurrence.OVERDUE_TWO_DAYS,
        ReminderOccurrence.OVERDUE_WEEKLY,
        -> OVERDUE_ACCOUNTS_CHANNEL_ID
    }

    private fun ReminderOccurrence.title(): String = when (this) {
        ReminderOccurrence.UPCOMING_DAY_BEFORE -> "موعد حساب غدًا"
        ReminderOccurrence.DUE_DATE -> "موعد استحقاق حساب"
        ReminderOccurrence.OVERDUE_TWO_DAYS -> "حساب متأخر"
        ReminderOccurrence.OVERDUE_WEEKLY -> "متابعة حساب متأخر"
    }

    private fun ReminderOccurrence.body(personName: String, money: String): String = when (this) {
        ReminderOccurrence.UPCOMING_DAY_BEFORE ->
            "غدًا موعد استحقاق حساب $personName. المتبقي $money."

        ReminderOccurrence.DUE_DATE ->
            "حساب $personName مستحق اليوم. المتبقي $money."

        ReminderOccurrence.OVERDUE_TWO_DAYS ->
            "حساب $personName تجاوز موعده ويحتاج متابعة. المتبقي $money."

        ReminderOccurrence.OVERDUE_WEEKLY ->
            "حساب $personName ما زال متأخرًا. المتبقي $money."
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
        const val UPCOMING_ACCOUNTS_CHANNEL_ID = "wasl_upcoming_accounts"
        const val DUE_ACCOUNTS_CHANNEL_ID = "wasl_due_accounts"
        const val OVERDUE_ACCOUNTS_CHANNEL_ID = "wasl_overdue_accounts"
        const val ALARMS_CHANNEL_ID = "wasl_alarms"
        const val INSTALLMENTS_CHANNEL_ID = "wasl_installments"
        const val PROMISES_CHANNEL_ID = "wasl_payment_promises"
        const val NOTIFICATION_ID = 1001
    }
}
