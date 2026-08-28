from pathlib import Path

path = Path("app/src/main/java/com/wasl/app/WaslApp.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:100]!r}")
    text = text.replace(old, new, 1)


replace_once(
    """                                    onOpenCreate = homeViewModel::openCreateDialog,\n                                    onOpenAccount = { debtId ->\n""",
    """                                    onOpenCreate = homeViewModel::openCreateTypePicker,\n                                    onDismissCreateTypePicker = homeViewModel::dismissCreateTypePicker,\n                                    onCreateIndividual = homeViewModel::openCreateDialog,\n                                    onCreateGroupExpense = homeViewModel::openGroupExpenseDialog,\n                                    onOpenAccount = { debtId ->\n""",
)

replace_once(
    """                                    onDismissCreate = homeViewModel::dismissCreateDialog,\n                                    onPersonModeChange = homeViewModel::updatePersonMode,\n""",
    """                                    onDismissCreate = homeViewModel::dismissCreateDialog,\n                                    onDismissGroupExpense = homeViewModel::dismissGroupExpenseDialog,\n                                    onToggleGroupParticipant = homeViewModel::toggleGroupParticipant,\n                                    onGroupParticipantAmountChange = homeViewModel::updateGroupParticipantAmount,\n                                    onGroupCurrencyChange = homeViewModel::updateGroupCurrency,\n                                    onGroupDirectionChange = homeViewModel::updateGroupDirection,\n                                    onGroupDescriptionChange = homeViewModel::updateGroupDescription,\n                                    onGroupNotesChange = homeViewModel::updateGroupNotes,\n                                    onReviewGroupExpense = homeViewModel::reviewGroupExpense,\n                                    onEditGroupExpenseReview = homeViewModel::editGroupExpenseReview,\n                                    onConfirmGroupExpense = homeViewModel::confirmGroupExpense,\n                                    onPersonModeChange = homeViewModel::updatePersonMode,\n""",
)

replace_once(
    """    onOpenSearch: () -> Unit,\n    onOpenCreate: () -> Unit,\n    onOpenAccount: (com.wasl.domain.DebtId) -> Unit,\n    onDismissCreate: () -> Unit,\n""",
    """    onOpenSearch: () -> Unit,\n    onOpenCreate: () -> Unit,\n    onDismissCreateTypePicker: () -> Unit,\n    onCreateIndividual: () -> Unit,\n    onCreateGroupExpense: () -> Unit,\n    onOpenAccount: (com.wasl.domain.DebtId) -> Unit,\n    onDismissCreate: () -> Unit,\n    onDismissGroupExpense: () -> Unit,\n    onToggleGroupParticipant: (PersonId) -> Unit,\n    onGroupParticipantAmountChange: (PersonId, String) -> Unit,\n    onGroupCurrencyChange: (CurrencyCode) -> Unit,\n    onGroupDirectionChange: (DebtDirection) -> Unit,\n    onGroupDescriptionChange: (String) -> Unit,\n    onGroupNotesChange: (String) -> Unit,\n    onReviewGroupExpense: () -> Unit,\n    onEditGroupExpenseReview: () -> Unit,\n    onConfirmGroupExpense: () -> Unit,\n""",
)

replace_once(
    """    if (state.isCreateDialogOpen) {\n        CreateDebtDialog(\n""",
    """    if (state.isCreateTypePickerOpen) {\n        CreateEntryTypeDialog(\n            onDismiss = onDismissCreateTypePicker,\n            onCreateIndividual = onCreateIndividual,\n            onCreateGroupExpense = onCreateGroupExpense,\n        )\n    }\n\n    if (state.isGroupExpenseDialogOpen) {\n        GroupExpenseDialog(\n            form = state.groupExpenseForm,\n            step = state.groupExpenseStep,\n            preview = state.groupExpensePreview,\n            error = state.groupExpenseError,\n            isSaving = state.isSaving,\n            peopleQuery = state.peopleQuery,\n            selectablePeople = state.selectablePeople,\n            isPeopleLoading = state.isPeopleLoading,\n            peopleLoadError = state.peopleLoadError,\n            hasMorePeople = state.hasMorePeople,\n            onDismiss = onDismissGroupExpense,\n            onPeopleQueryChange = onPeopleQueryChange,\n            onToggleParticipant = onToggleGroupParticipant,\n            onParticipantAmountChange = onGroupParticipantAmountChange,\n            onCurrencyChange = onGroupCurrencyChange,\n            onDirectionChange = onGroupDirectionChange,\n            onDescriptionChange = onGroupDescriptionChange,\n            onNotesChange = onGroupNotesChange,\n            onRetryPeople = onRetryPeople,\n            onReview = onReviewGroupExpense,\n            onEditReview = onEditGroupExpenseReview,\n            onConfirm = onConfirmGroupExpense,\n        )\n    }\n\n    if (state.isCreateDialogOpen) {\n        CreateDebtDialog(\n""",
)

replace_once(
    """                onOpenSearch = {},\n                onOpenCreate = {},\n                onOpenAccount = {},\n                onDismissCreate = {},\n""",
    """                onOpenSearch = {},\n                onOpenCreate = {},\n                onDismissCreateTypePicker = {},\n                onCreateIndividual = {},\n                onCreateGroupExpense = {},\n                onOpenAccount = {},\n                onDismissCreate = {},\n                onDismissGroupExpense = {},\n                onToggleGroupParticipant = {},\n                onGroupParticipantAmountChange = { _, _ -> },\n                onGroupCurrencyChange = {},\n                onGroupDirectionChange = {},\n                onGroupDescriptionChange = {},\n                onGroupNotesChange = {},\n                onReviewGroupExpense = {},\n                onEditGroupExpenseReview = {},\n                onConfirmGroupExpense = {},\n""",
)

path.write_text(text, encoding="utf-8")
