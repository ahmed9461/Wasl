package com.wasl.app.data

/**
 * Production-facing local data source that keeps the existing Wasl repository API
 * while also exposing installment-plan, payment-claim, and advanced-search capabilities
 * through the same dependency.
 *
 * All delegates share the same Room database, so financial Ledger writes remain
 * the single source of truth while claims stay non-financial historical records
 * and search stays a read-only derived projection.
 */
class InstallmentAwareWaslRepository(
    waslRepository: WaslRepository,
    installmentPlanStore: InstallmentPlanStore,
    advancedSearchStore: AdvancedSearchStore = UnavailableAdvancedSearchStore,
    paymentClaimStore: PaymentClaimStore = UnavailablePaymentClaimStore,
) : WaslRepository by waslRepository,
    InstallmentPlanStore by installmentPlanStore,
    AdvancedSearchStore by advancedSearchStore,
    PaymentClaimStore by paymentClaimStore
