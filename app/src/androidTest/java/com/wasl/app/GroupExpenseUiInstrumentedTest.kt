package com.wasl.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.PersonRecord
import com.wasl.app.ui.theme.WaslTheme
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.GroupExpense
import com.wasl.domain.GroupExpenseId
import com.wasl.domain.GroupExpenseShare
import com.wasl.domain.GroupExpenseShareId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupExpenseUiInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontStacksGroupEditorChoicesAndParticipantRows() {
        val form = groupForm()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    GroupExpenseDialog(
                        form = form,
                        step = GroupExpenseEditorStep.EDIT,
                        preview = null,
                        error = null,
                        isSaving = false,
                        peopleQuery = "",
                        selectablePeople = people(),
                        isPeopleLoading = false,
                        peopleLoadError = null,
                        hasMorePeople = false,
                        onDismiss = {},
                        onPeopleQueryChange = {},
                        onToggleParticipant = {},
                        onParticipantAmountChange = { _, _ -> },
                        onCurrencyChange = {},
                        onDirectionChange = {},
                        onDescriptionChange = {},
                        onNotesChange = {},
                        onRetryPeople = {},
                        onReview = {},
                        onEditReview = {},
                        onConfirm = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("group-expense-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("group-direction-stacked", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("group-currency-stacked", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("group-participant-p1-stacked", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("group-participant-p2-stacked", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun largeFontParticipantRowRetainsAmountAndRemoveCallbacks() {
        var amount = ""
        var removes = 0
        val participant = GroupExpenseParticipantDraft(
            person = ExistingPersonSelection(PersonId("p1"), "محمد عبدالله الطويل للاختبار"),
        )

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                WaslTheme {
                    GroupParticipantAmountRow(
                        participant = participant,
                        currency = CurrencyCode.SAR,
                        isSaving = false,
                        onAmountChange = { amount = it },
                        onRemove = { removes += 1 },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("group-participant-p1-stacked", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("group-participant-p1-amount").performTextInput("125.50")
        composeRule.onNodeWithTag("group-participant-p1-remove").performClick()
        composeRule.runOnIdle {
            assertEquals("125.50", amount)
            assertEquals(1, removes)
        }
    }

    @Test
    fun reviewShowsEveryShareAndConfirmIsExplicit() {
        var confirms = 0
        var edits = 0
        val form = groupForm()
        val preview = GroupExpense(
            id = GroupExpenseId("group-1"),
            direction = DebtDirection.RECEIVABLE,
            totalAmount = Money(125_000L, CurrencyCode.YER),
            occurredAt = Instant.parse("2026-08-28T01:00:00Z"),
            description = "مشتريات مشتركة",
            notes = "مراجعة قبل الحفظ",
            shares = listOf(
                GroupExpenseShare(
                    id = GroupExpenseShareId("share-1"),
                    debtId = DebtId("debt-1"),
                    personId = PersonId("p1"),
                    amount = Money(100_000L, CurrencyCode.YER),
                ),
                GroupExpenseShare(
                    id = GroupExpenseShareId("share-2"),
                    debtId = DebtId("debt-2"),
                    personId = PersonId("p2"),
                    amount = Money(25_000L, CurrencyCode.YER),
                ),
            ),
        )

        composeRule.setContent {
            WaslTheme {
                GroupExpenseDialog(
                    form = form,
                    step = GroupExpenseEditorStep.REVIEW,
                    preview = preview,
                    error = null,
                    isSaving = false,
                    peopleQuery = "",
                    selectablePeople = people(),
                    isPeopleLoading = false,
                    peopleLoadError = null,
                    hasMorePeople = false,
                    onDismiss = {},
                    onPeopleQueryChange = {},
                    onToggleParticipant = {},
                    onParticipantAmountChange = { _, _ -> },
                    onCurrencyChange = {},
                    onDirectionChange = {},
                    onDescriptionChange = {},
                    onNotesChange = {},
                    onRetryPeople = {},
                    onReview = {},
                    onEditReview = { edits += 1 },
                    onConfirm = { confirms += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("group-expense-review").assertIsDisplayed()
        composeRule.onNodeWithTag("group-review-share-p1")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("group-review-share-p2")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, confirms)
            assertEquals(0, edits)
        }
        composeRule.onNodeWithTag("group-expense-confirm").performClick()
        composeRule.runOnIdle { assertEquals(1, confirms) }
    }

    @Test
    fun createTypePickerKeepsIndividualAndGroupPathsSeparate() {
        var individual = 0
        var group = 0
        composeRule.setContent {
            WaslTheme {
                CreateEntryTypeDialog(
                    onDismiss = {},
                    onCreateIndividual = { individual += 1 },
                    onCreateGroupExpense = { group += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("create-entry-type-picker").assertIsDisplayed()
        composeRule.onNodeWithTag("create-entry-individual").performClick()
        composeRule.onNodeWithTag("create-entry-group").performClick()
        composeRule.runOnIdle {
            assertEquals(1, individual)
            assertEquals(1, group)
        }
    }

    private fun groupForm(): GroupExpenseForm = GroupExpenseForm(
        currency = CurrencyCode.YER,
        direction = DebtDirection.RECEIVABLE,
        description = "مشتريات مشتركة",
        notes = "مراجعة قبل الحفظ",
        participants = listOf(
            GroupExpenseParticipantDraft(
                person = ExistingPersonSelection(PersonId("p1"), "أحمد"),
                amount = "100000",
            ),
            GroupExpenseParticipantDraft(
                person = ExistingPersonSelection(PersonId("p2"), "سالم"),
                amount = "25000",
            ),
        ),
    )

    private fun people(): List<PersonRecord> = listOf(
        PersonRecord(
            id = PersonId("p1"),
            displayName = "أحمد",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        PersonRecord(
            id = PersonId("p2"),
            displayName = "سالم",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
    )
}
