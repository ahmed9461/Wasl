package com.wasl.app.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.GeneralReminderRecord
import com.wasl.app.data.PersonRecord
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneralReminderNotificationPublisherInstrumentedTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reminderId = "general-notification-action-test"

    @AfterTest
    fun tearDown() {
        context.getSystemService(NotificationManager::class.java)
            .cancel(reminderId, GeneralReminderNotificationPublisher.NOTIFICATION_ID)
    }

    @Test
    fun generalReminderUsesBodyToOpenAccountAndSameThreeSafeButtons() {
        val now = Instant.parse("2026-08-26T12:00:00Z")
        val debtId = DebtId("general-action-debt")
        val reminder = GeneralReminderRecord(
            id = reminderId,
            debtId = debtId,
            triggerAt = now.plusSeconds(3_600),
            zoneId = ZoneOffset.UTC,
            status = ReminderStatus.SCHEDULED,
            createdAt = now,
            updatedAt = now,
        )
        val account = AccountOverview(
            person = PersonRecord(
                id = PersonId("general-action-person"),
                displayName = "عميل المتابعة",
                createdAt = now,
                updatedAt = now,
            ),
            ledger = DebtLedger(
                DebtHeader(
                    id = debtId,
                    personId = PersonId("general-action-person"),
                    direction = DebtDirection.RECEIVABLE,
                    originalAmount = Money(75_000L, CurrencyCode.YER),
                    openedAt = now,
                ),
            ),
            lifecycleState = DebtLifecycleState.ACTIVE,
        )

        GeneralReminderNotificationPublisher(context).publish(reminder, account)

        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var posted = manager.activeNotifications.firstOrNull { it.tag == reminderId }
        while (posted == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50L)
            posted = manager.activeNotifications.firstOrNull { it.tag == reminderId }
        }
        assertNotNull(posted)
        assertNotNull(posted.notification.contentIntent)
        assertEquals(
            listOf("دفع جزء", "تم السداد", "ذكرني لاحقًا"),
            posted.notification.actions.orEmpty().map { it.title.toString() },
        )
    }
}
