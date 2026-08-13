package com.wasl.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class DebtAggregate(
    @Embedded
    val debt: DebtEntity,
    @Relation(
        parentColumn = "person_id",
        entityColumn = "id",
    )
    val person: PersonEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "debt_id",
    )
    val entries: List<LedgerEntryEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subject_id",
    )
    val reminders: List<ReminderEntity>,
)
