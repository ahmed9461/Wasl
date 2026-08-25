package com.wasl.app.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.PersonRecord
import com.wasl.app.data.ReminderRecord
import com.wasl.app.data.ReminderStatus
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtHeader
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotificationPublisherInstrumentedTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reminderId = "notification-test-reminder"

    @AfterTest
    fun tearDown() {
        context.getSystemService(NotificationManager::class.java).cancel(
            reminderId,
            ReminderNotificationPublisher.NOTIFICATION_ID,
        )
    }

    @Test
    fun ensureChannelsCreatesIndependentReminderCategories() {
        val publisher = ReminderNotificationPublisher(context)
        publisher.ensureChannels()
        val manager = context.getSystemService(NotificationManager::class.java)

        assertNotNull(
            manager.getNotificationChannel(ReminderNotificationPublisher.UPCOMING_ACCOUNTS_CHANNEL_ID),
        )
        assertNotNull(
            manager.getNotificationChannel(ReminderNotificationPublisher.DUE_ACCOUNTS_CHANNEL_ID),
        )
        assertNotNull(
            manager.getNotificationChannel(ReminderNotificationPublisher.OVERDUE_ACCOUNTS_CHANNEL_ID),
        )
        assertNotNull(
            manager.getNotificationChannel(ReminderNotificationPublisher.ALARMS_CHANNEL_ID),
        )
        assertNotNull(
            manager.getNotificationChannel(ReminderNotificationPublisher.INSTALLMENTS_CHANNEL_ID),
        )
        assertNotNull(
            manager.getNotificationChannel(ReminderNotificationPublisher.PROMISES_CHANNEL_ID),
        )
    }

    @Test
    fun dueReminderPublishesAVisibleNotificationWithStableTag() {
        val now = Instant.parse("2026-08-13T00:00:00Z")
        val reminder = reminder(now)
        val account = account(now, reminder)
        val publisher = ReminderNotificationPublisher(context)

        publisher.publish(reminder, account)

        val posted = awaitPostedNotification(
            context.getSystemService(NotificationManager::class.java),
        )
        assertNotNull(posted)
        assertNotNull(posted.notification.contentIntent)
    }

    @Test
    fun overdueOccurrencePublishesThroughTheSameStableReminderTag() {
        val now = Instant.parse("2026-08-13T00:00:00Z")
        val reminder = reminder(now)
        val account = account(now, reminder)
        val publisher = ReminderNotificationPublisher(context)

        publisher.publish(reminder, account, ReminderOccurrence.OVERDUE_TWO_DAYS)

        val posted = awaitPostedNotification(
            context.getSystemService(NotificationManager::class.java),
        )
        assertNotNull(posted)
        assertNotNull(posted.notification.contentIntent)
    }

    private fun reminder(now: Instant) = ReminderRecord(
        id = reminderId,
        debtId = DebtId("debt-1"),
        triggerAt = now,
        zoneId = ZoneOffset.UTC,
        status = ReminderStatus.SCHEDULED,
        createdAt = now,
        updatedAt = now,
    )

    private fun account(now: Instant, reminder: ReminderRecord) = AccountOverview(
        person = PersonRecord(
            id = PersonId("person-1"),
            displayName = "أحمد",
            createdAt = now,
            updatedAt = now,
        ),
        ledger = DebtLedger(
            DebtHeader(
                id = DebtId("debt-1"),
                personId = PersonId("person-1"),
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(100_000L, CurrencyCode.YER),
                openedAt = now,
            ),
        ),
        lifecycleState = DebtLifecycleState.ACTIVE,
        dueReminder = reminder,
    )

    private fun awaitPostedNotification(
        manager: NotificationManager,
    ): StatusBarNotification? {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        do {
            manager.activeNotifications.firstOrNull { it.tag == reminderId }?.let { return it }
            SystemClock.sleep(50L)
        } while (SystemClock.elapsedRealtime() < deadline)
        return null
    }
}
