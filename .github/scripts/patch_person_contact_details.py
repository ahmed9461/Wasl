from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"expected block not found in {path}: {old[:80]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one block in {path}, found {text.count(old)}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


models = "app/src/main/java/com/wasl/app/data/RepositoryModels.kt"
replace_once(
    models,
    """    val personName: String,\n    val direction: DebtDirection,""",
    """    val personName: String,\n    val personPhone: String? = null,\n    val personEmail: String? = null,\n    val direction: DebtDirection,""",
)
replace_once(
    models,
    """        require(personName.isNotBlank()) { \"Person name cannot be blank.\" }\n        require(originalAmount.minorUnits > 0L) { \"Original amount must be positive.\" }""",
    """        require(personName.isNotBlank()) { \"Person name cannot be blank.\" }\n        require(personPhone == null || personPhone.isNotBlank()) {\n            \"Person phone must be null or non-blank.\"\n        }\n        require(personEmail == null || personEmail.isNotBlank()) {\n            \"Person email must be null or non-blank.\"\n        }\n        require(originalAmount.minorUnits > 0L) { \"Original amount must be positive.\" }""",
)

home = "app/src/main/java/com/wasl/app/HomeViewModel.kt"
replace_once(
    home,
    """    val personMode: DebtPersonMode = DebtPersonMode.NEW,\n    val personName: String = \"\",\n    val selectedPerson: ExistingPersonSelection? = null,""",
    """    val personMode: DebtPersonMode = DebtPersonMode.NEW,\n    val personName: String = \"\",\n    val personPhone: String = \"\",\n    val personEmail: String = \"\",\n    val personNotes: String = \"\",\n    val selectedPerson: ExistingPersonSelection? = null,""",
)
replace_once(
    home,
    """    fun updatePersonName(value: String) = updateForm { copy(personName = value) }\n\n    fun updatePeopleQuery""",
    """    fun updatePersonName(value: String) = updateForm { copy(personName = value) }\n\n    fun updatePersonPhone(value: String) = updateForm { copy(personPhone = value) }\n\n    fun updatePersonEmail(value: String) = updateForm { copy(personEmail = value) }\n\n    fun updatePersonNotes(value: String) = updateForm { copy(personNotes = value) }\n\n    fun updatePeopleQuery""",
)
replace_once(
    home,
    """                            personName = personName,\n                            direction = form.direction,""",
    """                            personName = personName,\n                            personPhone = form.personPhone.trim().ifEmpty { null },\n                            personEmail = form.personEmail.trim().ifEmpty { null },\n                            personNotes = form.personNotes.trim().ifEmpty { null },\n                            direction = form.direction,""",
)

repo = "app/src/main/java/com/wasl/app/data/local/RoomWaslRepository.kt"
replace_once(
    repo,
    """        val normalizedName = command.personName.trim()\n        if (personDao.findById(command.personId.value) != null) {""",
    """        val normalizedName = command.personName.trim()\n        val normalizedPhone = command.personPhone?.trim()?.ifEmpty { null }\n        val normalizedEmail = command.personEmail?.trim()?.ifEmpty { null }\n        val normalizedPersonNotes = command.personNotes?.trim()?.ifEmpty { null }\n        if (personDao.findById(command.personId.value) != null) {""",
)
replace_once(
    repo,
    """                displayName = normalizedName,\n                phone = null,\n                email = null,\n                photoUri = null,\n                notes = command.personNotes?.trim(),""",
    """                displayName = normalizedName,\n                phone = normalizedPhone,\n                email = normalizedEmail,\n                photoUri = null,\n                notes = normalizedPersonNotes,""",
)
replace_once(
    repo,
    """        val expectedPersonNotes = command.personNotes?.trim()\n        val matches = debtCreationMatches(command.toDebtCreation(), aggregate, persisted) &&\n            persisted.person.displayName == command.personName.trim() &&\n            persisted.person.notes == expectedPersonNotes &&""",
    """        val expectedPhone = command.personPhone?.trim()?.ifEmpty { null }\n        val expectedEmail = command.personEmail?.trim()?.ifEmpty { null }\n        val expectedPersonNotes = command.personNotes?.trim()?.ifEmpty { null }\n        val matches = debtCreationMatches(command.toDebtCreation(), aggregate, persisted) &&\n            persisted.person.displayName == command.personName.trim() &&\n            persisted.person.phone == expectedPhone &&\n            persisted.person.email == expectedEmail &&\n            persisted.person.notes == expectedPersonNotes &&""",
)

app = "app/src/main/java/com/wasl/app/WaslApp.kt"
replace_once(
    app,
    """                                    onPersonNameChange = homeViewModel::updatePersonName,\n                                    onPeopleQueryChange = homeViewModel::updatePeopleQuery,""",
    """                                    onPersonNameChange = homeViewModel::updatePersonName,\n                                    onPersonPhoneChange = homeViewModel::updatePersonPhone,\n                                    onPersonEmailChange = homeViewModel::updatePersonEmail,\n                                    onPersonNotesChange = homeViewModel::updatePersonNotes,\n                                    onPeopleQueryChange = homeViewModel::updatePeopleQuery,""",
)
replace_once(
    app,
    """    onPersonModeChange: (DebtPersonMode) -> Unit,\n    onPersonNameChange: (String) -> Unit,\n    onPeopleQueryChange: (String) -> Unit,""",
    """    onPersonModeChange: (DebtPersonMode) -> Unit,\n    onPersonNameChange: (String) -> Unit,\n    onPersonPhoneChange: (String) -> Unit,\n    onPersonEmailChange: (String) -> Unit,\n    onPersonNotesChange: (String) -> Unit,\n    onPeopleQueryChange: (String) -> Unit,""",
)
replace_once(
    app,
    """            onPersonModeChange = onPersonModeChange,\n            onPersonNameChange = onPersonNameChange,\n            onPeopleQueryChange = onPeopleQueryChange,""",
    """            onPersonModeChange = onPersonModeChange,\n            onPersonNameChange = onPersonNameChange,\n            onPersonPhoneChange = onPersonPhoneChange,\n            onPersonEmailChange = onPersonEmailChange,\n            onPersonNotesChange = onPersonNotesChange,\n            onPeopleQueryChange = onPeopleQueryChange,""",
)
replace_once(
    app,
    """    onPersonModeChange: (DebtPersonMode) -> Unit,\n    onPersonNameChange: (String) -> Unit,\n    onPeopleQueryChange: (String) -> Unit,\n    onSelectPerson: (PersonId) -> Unit,""",
    """    onPersonModeChange: (DebtPersonMode) -> Unit,\n    onPersonNameChange: (String) -> Unit,\n    onPersonPhoneChange: (String) -> Unit,\n    onPersonEmailChange: (String) -> Unit,\n    onPersonNotesChange: (String) -> Unit,\n    onPeopleQueryChange: (String) -> Unit,\n    onSelectPerson: (PersonId) -> Unit,""",
)
old_name_field = """                    OutlinedTextField(\n                        value = form.personName,\n                        onValueChange = onPersonNameChange,\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .testTag(\"create-person-name\"),\n                        label = { Text(\"اسم الشخص\") },\n                        singleLine = true,\n                        enabled = !isSaving,\n                        shape = MaterialTheme.shapes.medium,\n                    )\n"""
new_name_fields = old_name_field + """                    OutlinedTextField(\n                        value = form.personPhone,\n                        onValueChange = onPersonPhoneChange,\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .testTag(\"create-person-phone\"),\n                        label = { Text(\"رقم الجوال — اختياري\") },\n                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),\n                        singleLine = true,\n                        enabled = !isSaving,\n                        shape = MaterialTheme.shapes.medium,\n                    )\n                    OutlinedTextField(\n                        value = form.personEmail,\n                        onValueChange = onPersonEmailChange,\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .testTag(\"create-person-email\"),\n                        label = { Text(\"البريد الإلكتروني — اختياري\") },\n                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),\n                        singleLine = true,\n                        enabled = !isSaving,\n                        shape = MaterialTheme.shapes.medium,\n                    )\n                    OutlinedTextField(\n                        value = form.personNotes,\n                        onValueChange = onPersonNotesChange,\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .testTag(\"create-person-notes\"),\n                        label = { Text(\"ملاحظات الشخص — اختياري\") },\n                        minLines = 2,\n                        maxLines = 3,\n                        enabled = !isSaving,\n                        shape = MaterialTheme.shapes.medium,\n                    )\n"""
replace_once(app, old_name_field, new_name_fields)

print("person contact details patch applied")
