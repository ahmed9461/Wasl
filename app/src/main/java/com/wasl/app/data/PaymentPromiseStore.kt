package com.wasl.app.data

import com.wasl.domain.DebtId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface PaymentPromiseStore {
    fun observePaymentPromises(debtId: DebtId): Flow<List<PaymentPromiseRecord>>

    suspend fun createPaymentPromise(command: CreatePaymentPromiseCommand): PaymentPromiseRecord

    suspend fun resolvePaymentPromise(command: ResolvePaymentPromiseCommand): PaymentPromiseRecord
}

object UnavailablePaymentPromiseStore : PaymentPromiseStore {
    override fun observePaymentPromises(debtId: DebtId): Flow<List<PaymentPromiseRecord>> =
        flowOf(emptyList())

    override suspend fun createPaymentPromise(
        command: CreatePaymentPromiseCommand,
    ): PaymentPromiseRecord = error("Payment promises are unavailable.")

    override suspend fun resolvePaymentPromise(
        command: ResolvePaymentPromiseCommand,
    ): PaymentPromiseRecord = error("Payment promises are unavailable.")
}
