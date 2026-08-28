package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Persistence boundary for the «طالبني» feature.
 *
 * Implementations must keep payment claims outside the financial ledger and
 * preserve history rather than overwriting or deleting prior claims.
 */
interface PaymentClaimStore {
    fun observeClaims(debtId: DebtId): Flow<List<PaymentClaimRecord>>

    fun observeOpenClaims(onOrBefore: LocalDate): Flow<List<PaymentClaimRecord>>

    suspend fun createClaim(command: CreatePaymentClaimCommand): PaymentClaimRecord

    suspend fun resolveClaim(command: ResolvePaymentClaimCommand): PaymentClaimRecord
}

object UnavailablePaymentClaimStore : PaymentClaimStore {
    override fun observeClaims(debtId: DebtId): Flow<List<PaymentClaimRecord>> = flowOf(emptyList())

    override fun observeOpenClaims(onOrBefore: LocalDate): Flow<List<PaymentClaimRecord>> =
        flowOf(emptyList())

    override suspend fun createClaim(command: CreatePaymentClaimCommand): PaymentClaimRecord =
        error("Payment claims are unavailable.")

    override suspend fun resolveClaim(command: ResolvePaymentClaimCommand): PaymentClaimRecord =
        error("Payment claims are unavailable.")
}
