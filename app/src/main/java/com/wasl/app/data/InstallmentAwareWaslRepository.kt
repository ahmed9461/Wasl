package com.wasl.app.data

/**
 * Production-facing local data source that keeps the existing Wasl repository API
 * while also exposing installment-plan capabilities through the same dependency.
 *
 * Both delegates share the same Room database, so financial Ledger writes remain
 * the single source of truth and installment progress stays a derived projection.
 */
class InstallmentAwareWaslRepository(
    waslRepository: WaslRepository,
    installmentPlanStore: InstallmentPlanStore,
) : WaslRepository by waslRepository,
    InstallmentPlanStore by installmentPlanStore
