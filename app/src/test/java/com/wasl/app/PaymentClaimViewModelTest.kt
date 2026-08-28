package com.wasl.app

import com.wasl.app.data.CreatePaymentClaimCommand
import com.wasl.app.data.PaymentClaimFollowUpKind
import com.wasl.app.data.PaymentClaimRecord
import com.wasl.app.data.PaymentClaimStatus
import com.wasl.app.data.PaymentClaimStore
import com.wasl.app.data.ResolvePaymentClaimCommand
import com.wasl.domain.DebtId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentClaimViewModelTest {
    @Test
    fun todayClaimIsPersistedWithoutGuessingAnotherDate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = FakePaymentClaimStore()
            val clock = Clock.fixed(Instant.parse("2026-08-27T10:30:00Z"), ZoneOffset.UTC)
            val ids = ArrayDeque(listOf("command-1", "claim-1"))
            val viewModel = PaymentClaimViewModel(
                debtId = DebtId("debt-1"),
                store = store,
                clock = clock,
                zoneIdProvider = { ZoneId.of("Asia/Aden") },
                idFactory = { ids.removeFirst() },
            )

            viewModel.openCreate()
            viewModel.updateKind(PaymentClaimFollowUpKind.TODAY)
            viewModel.confirmCreate()
            advanceUntilIdle()

            val persisted = store.created.single()
            assertEquals(LocalDate.parse("2026-08-27"), persisted.followUpDate)
            assertEquals(PaymentClaimFollowUpKind.TODAY, persisted.followUpKind)
            assertFalse(viewModel.uiState.value.isCreateDialogOpen)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun salaryClaimRemainsDateFree() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = FakePaymentClaimStore()
            val viewModel = PaymentClaimViewModel(
                debtId = DebtId("debt-2"),
                store = store,
                clock = Clock.fixed(Instant.parse("2026-08-27T10:30:00Z"), ZoneOffset.UTC),
                zoneIdProvider = { ZoneId.of("Asia/Aden") },
                idFactory = sequenceOf("command-2", "claim-2").iterator()::next,
            )

            viewModel.openCreate()
            viewModel.updateKind(PaymentClaimFollowUpKind.SALARY)
            viewModel.confirmCreate()
            advanceUntilIdle()

            val persisted = store.created.single()
            assertEquals(PaymentClaimFollowUpKind.SALARY, persisted.followUpKind)
            assertNull(persisted.followUpDate)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakePaymentClaimStore : PaymentClaimStore {
        val created = mutableListOf<CreatePaymentClaimCommand>()
        private val claims = MutableStateFlow<List<PaymentClaimRecord>>(emptyList())

        override fun observeClaims(debtId: DebtId): Flow<List<PaymentClaimRecord>> = claims

        override fun observeOpenClaims(onOrBefore: LocalDate): Flow<List<PaymentClaimRecord>> = claims

        override suspend fun createClaim(command: CreatePaymentClaimCommand): PaymentClaimRecord {
            created += command
            return PaymentClaimRecord(
                id = command.claimId,
                debtId = command.debtId,
                claimedAt = command.claimedAt,
                followUpKind = command.followUpKind,
                followUpDate = command.followUpDate,
                note = command.note,
                status = PaymentClaimStatus.ACTIVE,
                createdAt = command.createdAt,
            ).also { claims.value = listOf(it) }
        }

        override suspend fun resolveClaim(command: ResolvePaymentClaimCommand): PaymentClaimRecord {
            val current = claims.value.single { it.id == command.claimId }
            return current.copy(
                status = command.status,
                resolvedAt = command.resolvedAt,
                resolutionNote = command.note,
            ).also { claims.value = listOf(it) }
        }
    }
}
