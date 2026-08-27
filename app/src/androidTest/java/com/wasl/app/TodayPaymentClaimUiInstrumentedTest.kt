package com.wasl.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.CreatePaymentClaimCommand
import com.wasl.app.data.CreatePersonWithDebtCommand
import com.wasl.app.data.InstallmentAwareWaslRepository
import com.wasl.app.data.PaymentClaimFollowUpKind
import com.wasl.app.data.local.RoomInstallmentPlanStore
import com.wasl.app.data.local.RoomPaymentClaimStore
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayPaymentClaimUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var roomRepository: RoomWaslRepository
    private lateinit var claimStore: RoomPaymentClaimStore
    private lateinit var repository: InstallmentAwareWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-today-claims-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        roomRepository = RoomWaslRepository(database)
        claimStore = RoomPaymentClaimStore(database)
        repository = InstallmentAwareWaslRepository(
            waslRepository = roomRepository,
            installmentPlanStore = RoomInstallmentPlanStore(database, roomRepository),
            paymentClaimStore = claimStore,
        )

        runBlocking {
            roomRepository.createPersonWithDebt(
                CreatePersonWithDebtCommand(
                    personId = PersonId("person-claim-today"),
                    debtId = DebtId("debt-claim-today"),
                    personName = "ناصر",
                    direction = DebtDirection.PAYABLE,
                    originalAmount = Money(90_000L, CurrencyCode.YER),
                    openedAt = Instant.parse("2026-08-20T08:00:00Z"),
                    createdAt = Instant.parse("2026-08-20T08:00:00Z"),
                    description = "حساب طالبني",
                ),
            )
            createClaim("claim-overdue", PaymentClaimFollowUpKind.CUSTOM, LocalDate.parse("2026-08-22"))
            createClaim("claim-today", PaymentClaimFollowUpKind.TODAY, LocalDate.parse("2026-08-24"))
            createClaim("claim-future", PaymentClaimFollowUpKind.CUSTOM, LocalDate.parse("2026-08-25"))
            createClaim("claim-salary", PaymentClaimFollowUpKind.SALARY, null)
        }
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun todayShowsOnlyClaimsWhoseFollowUpDateIsDue() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "today-payment-claim-ui-test",
                todayClock = Clock.fixed(Instant.parse("2026-08-24T10:00:00Z"), ZoneOffset.UTC),
                todayZoneIdProvider = { ZoneOffset.UTC },
            )
        }

        waitForTag("nav-today")
        composeRule.onNodeWithTag("nav-today").performClick()

        scrollToTag("today-claim-claim-overdue")
        composeRule.onNodeWithTag("today-claim-claim-overdue").assertIsDisplayed()
        composeRule.onNodeWithText("مطالبات متأخرة").assertIsDisplayed()

        scrollToTag("today-claim-claim-today")
        composeRule.onNodeWithTag("today-claim-claim-today").assertIsDisplayed()
        composeRule.onNodeWithText("مطالبات اليوم").assertIsDisplayed()

        check(composeRule.onAllNodes(hasTestTag("today-claim-claim-future")).fetchSemanticsNodes().isEmpty())
        check(composeRule.onAllNodes(hasTestTag("today-claim-claim-salary")).fetchSemanticsNodes().isEmpty())

        scrollToTag("today-open-claim-claim-today")
        composeRule.onNodeWithTag("today-open-claim-claim-today").performClick()
        waitForTag("account-remaining")
        composeRule.onNodeWithText("ناصر").assertIsDisplayed()
    }

    private suspend fun createClaim(id: String, kind: PaymentClaimFollowUpKind, followUpDate: LocalDate?) {
        claimStore.createClaim(
            CreatePaymentClaimCommand(
                commandId = "command-$id",
                claimId = id,
                debtId = DebtId("debt-claim-today"),
                claimedAt = Instant.parse("2026-08-23T08:00:00Z"),
                followUpKind = kind,
                followUpDate = followUpDate,
                note = "متابعة $id",
                createdAt = Instant.parse("2026-08-23T08:00:00Z"),
            ),
        )
    }

    private fun scrollToTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNode(hasScrollAction()).fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
        }
    }
}
