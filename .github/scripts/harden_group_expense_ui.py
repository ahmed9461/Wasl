from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


home = Path("app/src/main/java/com/wasl/app/HomeViewModel.kt")
replace_once(
    home,
    '''        val now = Instant.now(clock)\n        val shares = parsed.map { (participant, amount) ->\n            GroupExpenseShare(\n                id = GroupExpenseShareId(idFactory()),\n                debtId = DebtId(idFactory()),\n                personId = participant.person.id,\n                amount = amount,\n            )\n        }\n        val total = parsed.fold(Money.zero(form.currency)) { sum, (_, amount) -> sum.plus(amount) }\n        val command = CreateGroupExpenseCommand(\n            commandId = idFactory(),\n            expense = GroupExpense(\n                id = GroupExpenseId(idFactory()),\n                direction = form.direction,\n                totalAmount = total,\n                occurredAt = now,\n                description = description,\n                notes = form.notes.trim().ifEmpty { null },\n                shares = shares,\n            ),\n            createdAt = now,\n        )\n''',
    '''        val command = try {\n            val now = Instant.now(clock)\n            val shares = parsed.map { (participant, amount) ->\n                GroupExpenseShare(\n                    id = GroupExpenseShareId(idFactory()),\n                    debtId = DebtId(idFactory()),\n                    personId = participant.person.id,\n                    amount = amount,\n                )\n            }\n            val total = parsed.fold(Money.zero(form.currency)) { sum, (_, amount) ->\n                sum.plus(amount)\n            }\n            CreateGroupExpenseCommand(\n                commandId = idFactory(),\n                expense = GroupExpense(\n                    id = GroupExpenseId(idFactory()),\n                    direction = form.direction,\n                    totalAmount = total,\n                    occurredAt = now,\n                    description = description,\n                    notes = form.notes.trim().ifEmpty { null },\n                    shares = shares,\n                ),\n                createdAt = now,\n            )\n        } catch (_: ArithmeticException) {\n            _uiState.update {\n                it.copy(groupExpenseError = "إجمالي الحصص يتجاوز النطاق المالي المدعوم.")\n            }\n            return\n        } catch (_: IllegalArgumentException) {\n            _uiState.update {\n                it.copy(groupExpenseError = "تعذر تجهيز العملية للمراجعة. راجع البيانات وأعد المحاولة.")\n            }\n            return\n        }\n''',
)

ui_test = Path("app/src/androidTest/java/com/wasl/app/GroupExpenseUiInstrumentedTest.kt")
replace_once(
    ui_test,
    "import androidx.compose.ui.test.performClick\nimport androidx.compose.ui.test.performTextInput\n",
    "import androidx.compose.ui.test.performClick\nimport androidx.compose.ui.test.performScrollTo\nimport androidx.compose.ui.test.performTextInput\n",
)
replace_once(
    ui_test,
    '''        composeRule.onNodeWithTag("group-participant-p1-stacked", useUnmergedTree = true).assertIsDisplayed()\n        composeRule.onNodeWithTag("group-participant-p2-stacked", useUnmergedTree = true).assertIsDisplayed()\n''',
    '''        composeRule.onNodeWithTag("group-participant-p1-stacked", useUnmergedTree = true)\n            .performScrollTo()\n            .assertIsDisplayed()\n        composeRule.onNodeWithTag("group-participant-p2-stacked", useUnmergedTree = true)\n            .performScrollTo()\n            .assertIsDisplayed()\n''',
)
replace_once(
    ui_test,
    '''        composeRule.onNodeWithTag("group-review-share-p1").assertIsDisplayed()\n        composeRule.onNodeWithTag("group-review-share-p2").assertIsDisplayed()\n''',
    '''        composeRule.onNodeWithTag("group-review-share-p1")\n            .performScrollTo()\n            .assertIsDisplayed()\n        composeRule.onNodeWithTag("group-review-share-p2")\n            .performScrollTo()\n            .assertIsDisplayed()\n''',
)

mvp = Path("app/src/androidTest/java/com/wasl/app/MvpAcceptanceInstrumentedTest.kt")
replace_once(
    mvp,
    "        assertEquals(9, backup.schemaVersion)\n",
    "        assertEquals(10, backup.schemaVersion)\n",
)
