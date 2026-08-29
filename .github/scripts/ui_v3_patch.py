from pathlib import Path
import re

p = Path('app/src/main/java/com/wasl/app/WaslApp.kt')
s = p.read_text()

home = r'''@Composable
private fun WaslHomeScreen(
    state: HomeUiState,
    onOpenHome: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCreate: () -> Unit,
    onDismissCreateTypePicker: () -> Unit,
    onCreateIndividual: () -> Unit,
    onCreateGroupExpense: () -> Unit,
    onOpenAccount: (com.wasl.domain.DebtId) -> Unit,
    onDismissCreate: () -> Unit,
    onDismissGroupExpense: () -> Unit,
    onToggleGroupParticipant: (PersonId) -> Unit,
    onGroupParticipantAmountChange: (PersonId, String) -> Unit,
    onGroupCurrencyChange: (CurrencyCode) -> Unit,
    onGroupDirectionChange: (DebtDirection) -> Unit,
    onGroupDescriptionChange: (String) -> Unit,
    onGroupNotesChange: (String) -> Unit,
    onReviewGroupExpense: () -> Unit,
    onEditGroupExpenseReview: () -> Unit,
    onConfirmGroupExpense: () -> Unit,
    onPersonModeChange: (DebtPersonMode) -> Unit,
    onPersonNameChange: (String) -> Unit,
    onPeopleQueryChange: (String) -> Unit,
    onSelectPerson: (PersonId) -> Unit,
    onRetryPeople: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    onDirectionChange: (DebtDirection) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,
    onSuccessShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val openNaturalEntry = LocalOpenNaturalEntry.current
    LaunchedEffect(state.successMessage) {
        val message = state.successMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onSuccessShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onCreateIndividual,
                            modifier = Modifier.weight(1f).testTag("home-add-entry"),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("＋ إضافة حساب", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { openNaturalEntry?.invoke() },
                            enabled = openNaturalEntry != null,
                            modifier = Modifier.weight(1f).testTag("home-natural-entry"),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("إدخال ذكي") }
                    }
                }
                WaslTopLevelNavigation(
                    selected = WaslTopLevelDestination.HOME,
                    onOpenHome = onOpenHome,
                    onOpenToday = onOpenToday,
                    onOpenSearch = onOpenSearch,
                )
            }
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("home-header") { HomeHeroCard(accountCount = state.accounts.size) }
            if (state.isLoading) {
                item("home-loading") {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                item("home-overview-heading") {
                    HomeSectionHeader(
                        title = "إجمالي الحسابات",
                        subtitle = "كل عملة مستقلة؛ لا يتم خلط الأرصدة.",
                        tagPrefix = "home-overview",
                    )
                }
                item("home-currency-overview") { HomeCurrencyOverview(state) }
                state.loadError?.let { error -> item("home-error") { StatusCard(error, true) } }
                if (state.accounts.isEmpty() && state.loadError == null) {
                    item("home-empty") { StatusCard("أضف أول حساب لتبدأ متابعة الحقوق والالتزامات.", false) }
                } else {
                    item("home-accounts-heading") {
                        HomeSectionHeader(
                            title = "الحسابات",
                            subtitle = if (state.accounts.size == 1) "حساب واحد" else "${state.accounts.size} حسابات",
                            count = state.accounts.size,
                            tagPrefix = "home-accounts",
                        )
                    }
                    items(state.accounts, key = { it.ledger.header.id.value }) { account ->
                        AccountCard(account) { onOpenAccount(account.ledger.header.id) }
                    }
                }
                item("home-group-expense") {
                    TextButton(onClick = onCreateGroupExpense, modifier = Modifier.fillMaxWidth()) { Text("عملية جماعية") }
                }
            }
        }
    }

    if (state.isGroupExpenseDialogOpen) {
        GroupExpenseDialog(
            form = state.groupExpenseForm,
            step = state.groupExpenseStep,
            preview = state.groupExpensePreview,
            error = state.groupExpenseError,
            isSaving = state.isSaving,
            peopleQuery = state.peopleQuery,
            selectablePeople = state.selectablePeople,
            isPeopleLoading = state.isPeopleLoading,
            peopleLoadError = state.peopleLoadError,
            hasMorePeople = state.hasMorePeople,
            onDismiss = onDismissGroupExpense,
            onPeopleQueryChange = onPeopleQueryChange,
            onToggleParticipant = onToggleGroupParticipant,
            onParticipantAmountChange = onGroupParticipantAmountChange,
            onCurrencyChange = onGroupCurrencyChange,
            onDirectionChange = onGroupDirectionChange,
            onDescriptionChange = onGroupDescriptionChange,
            onNotesChange = onGroupNotesChange,
            onRetryPeople = onRetryPeople,
            onReview = onReviewGroupExpense,
            onEditReview = onEditGroupExpenseReview,
            onConfirm = onConfirmGroupExpense,
        )
    }

    if (state.isCreateDialogOpen) {
        CreateDebtDialog(
            form = state.createForm,
            isSaving = state.isSaving,
            error = state.formError,
            peopleQuery = state.peopleQuery,
            selectablePeople = state.selectablePeople,
            isPeopleLoading = state.isPeopleLoading,
            peopleLoadError = state.peopleLoadError,
            hasMorePeople = state.hasMorePeople,
            onDismiss = onDismissCreate,
            onPersonModeChange = onPersonModeChange,
            onPersonNameChange = onPersonNameChange,
            onPeopleQueryChange = onPeopleQueryChange,
            onSelectPerson = onSelectPerson,
            onRetryPeople = onRetryPeople,
            onAmountChange = onAmountChange,
            onCurrencyChange = onCurrencyChange,
            onDirectionChange = onDirectionChange,
            onDescriptionChange = onDescriptionChange,
            onDueDateChange = onDueDateChange,
            onReminderChange = onReminderChange,
            onStrongAlarmChange = onStrongAlarmChange,
            onRequestExactAlarmAccess = onRequestExactAlarmAccess,
            notificationPermissionGranted = notificationPermissionGranted,
            exactAlarmAccessGranted = exactAlarmAccessGranted,
            onSave = onSave,
        )
    }
}

@Composable
private fun HomeCurrencyOverview(state: HomeUiState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("home-currency-overview-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                supportedCurrencies.forEach { currency ->
                    CurrencyBalanceTile(
                        currency,
                        state.balanceSummary.receivableByCurrency[currency] ?: Money.zero(currency),
                        state.balanceSummary.payableByCurrency[currency] ?: Money.zero(currency),
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("home-currency-overview-inline"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                supportedCurrencies.forEach { currency ->
                    CurrencyBalanceTile(
                        currency,
                        state.balanceSummary.receivableByCurrency[currency] ?: Money.zero(currency),
                        state.balanceSummary.payableByCurrency[currency] ?: Money.zero(currency),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyBalanceTile(currency: CurrencyCode, receivable: Money, payable: Money, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(currency.value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(formatMoney(receivable), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text("لي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!payable.isZero) {
                Text("عليّ ${formatMoney(payable)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
            }
        }
    }
}

'''

create_dialog = r'''@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CreateDebtDialog(
    form: CreateDebtForm,
    isSaving: Boolean,
    error: String?,
    peopleQuery: String,
    selectablePeople: List<PersonRecord>,
    isPeopleLoading: Boolean,
    peopleLoadError: String?,
    hasMorePeople: Boolean,
    onDismiss: () -> Unit,
    onPersonModeChange: (DebtPersonMode) -> Unit,
    onPersonNameChange: (String) -> Unit,
    onPeopleQueryChange: (String) -> Unit,
    onSelectPerson: (PersonId) -> Unit,
    onRetryPeople: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    onDirectionChange: (DebtDirection) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var advancedOpen by remember(form.dueDate, form.remindOnDueDate, form.strongAlarmEnabled) {
        mutableStateOf(form.dueDate != null || form.remindOnDueDate || form.strongAlarmEnabled)
    }
    val onStrongAlarmTimeChange = LocalStrongAlarmTimeChange.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("إضافة حساب جديد", fontWeight = FontWeight.Bold)
                Text("البيانات الأساسية أولًا، وخيارات الاستحقاق عند الحاجة.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.direction == DebtDirection.RECEIVABLE, { onDirectionChange(DebtDirection.RECEIVABLE) },
                        { Text("لي عنده") }, enabled = !isSaving, modifier = Modifier.weight(1f))
                    FilterChip(form.direction == DebtDirection.PAYABLE, { onDirectionChange(DebtDirection.PAYABLE) },
                        { Text("عليّ له") }, enabled = !isSaving, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(form.personMode == DebtPersonMode.NEW, { onPersonModeChange(DebtPersonMode.NEW) },
                        { Text("شخص جديد") }, enabled = !isSaving,
                        modifier = Modifier.weight(1f).testTag("create-person-mode-new"))
                    FilterChip(form.personMode == DebtPersonMode.EXISTING, { onPersonModeChange(DebtPersonMode.EXISTING) },
                        { Text("شخص محفوظ") }, enabled = !isSaving,
                        modifier = Modifier.weight(1f).testTag("create-person-mode-existing"))
                }

                if (form.personMode == DebtPersonMode.NEW) {
                    OutlinedTextField(form.personName, onPersonNameChange,
                        Modifier.fillMaxWidth().testTag("create-person-name"),
                        label = { Text("الاسم") }, placeholder = { Text("مثال: أحمد") },
                        singleLine = true, enabled = !isSaving)
                } else {
                    form.selectedPerson?.let { selected ->
                        Surface(Modifier.fillMaxWidth().testTag("selected-existing-person"),
                            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(selected.displayName, Modifier.padding(11.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedTextField(peopleQuery, onPeopleQueryChange,
                        Modifier.fillMaxWidth().testTag("existing-person-query"),
                        label = { Text("ابحث عن شخص") }, singleLine = true, enabled = !isSaving)
                    when {
                        isPeopleLoading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                        }
                        peopleLoadError != null -> Column {
                            Text(peopleLoadError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetryPeople, modifier = Modifier.testTag("retry-existing-people")) { Text("إعادة المحاولة") }
                        }
                        selectablePeople.isEmpty() -> Text(
                            if (peopleQuery.isBlank()) "لا يوجد أشخاص محفوظون." else "لا توجد نتيجة مطابقة.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> {
                            selectablePeople.forEach { person ->
                                OutlinedButton(onClick = { onSelectPerson(person.id) }, enabled = !isSaving,
                                    modifier = Modifier.fillMaxWidth().testTag("existing-person-${person.id.value}")) {
                                    Text(person.displayName)
                                }
                            }
                            if (hasMorePeople) Text("اكتب اسمًا أدق لعرض نتائج أقل.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                OutlinedTextField(form.amount, onAmountChange,
                    Modifier.fillMaxWidth().testTag("create-debt-amount"),
                    label = { Text("المبلغ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, enabled = !isSaving)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    supportedCurrencies.forEach { currency ->
                        FilterChip(form.currency == currency, { onCurrencyChange(currency) }, { Text(currency.value) },
                            enabled = !isSaving, modifier = Modifier.weight(1f))
                    }
                }

                OutlinedTextField(form.description, onDescriptionChange, Modifier.fillMaxWidth(),
                    label = { Text("ملاحظات — اختياري") }, minLines = 1, maxLines = 2, enabled = !isSaving)

                OutlinedButton(onClick = { advancedOpen = !advancedOpen },
                    modifier = Modifier.fillMaxWidth().testTag("create-advanced-options"), enabled = !isSaving) {
                    Text(if (advancedOpen) "إخفاء خيارات الاستحقاق" else "خيارات إضافية · الاستحقاق والتذكير")
                }

                if (advancedOpen) {
                    OutlinedButton(onClick = { showDatePicker = true }, enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().testTag("create-due-date")) {
                        Text(form.dueDate?.format(dueDateFormatter) ?: "تاريخ الاستحقاق — اختياري")
                    }
                    if (form.dueDate != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("تذكير بالاستحقاق", fontWeight = FontWeight.SemiBold)
                                Text("متابعة تلقائية حتى السداد", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(form.remindOnDueDate, onReminderChange, enabled = !isSaving,
                                modifier = Modifier.testTag("create-due-reminder"))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("منبه دقيق", fontWeight = FontWeight.SemiBold)
                                Text("اختياري في يوم الاستحقاق", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(form.strongAlarmEnabled, onStrongAlarmChange, enabled = !isSaving,
                                modifier = Modifier.testTag("create-strong-alarm"))
                        }
                        if (form.strongAlarmEnabled) {
                            StrongAlarmTimeSelector(form.strongAlarmTime, !isSaving, onStrongAlarmTimeChange,
                                Modifier.fillMaxWidth(), "create-strong-alarm-time")
                        }
                        if (form.remindOnDueDate && !notificationPermissionGranted) {
                            Text("اسمح بإشعارات وَصل حتى تظهر التذكيرات.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                            TextButton(onClick = onRequestExactAlarmAccess,
                                modifier = Modifier.testTag("create-request-exact-alarm-access")) { Text("السماح بالمنبه الدقيق") }
                        }
                    }
                }

                error?.let {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(it, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.testTag("create-debt-save")) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(7.dp))
                }
                Text(if (isSaving) "جارٍ الحفظ" else "حفظ الحساب")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("إلغاء") } },
    )

    if (showDatePicker) {
        val initialSelection = form.dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialSelection)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { selected ->
                    onDueDateChange(Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate())
                }
                showDatePicker = false
            }, enabled = pickerState.selectedDateMillis != null) { Text("اختيار") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }) {
            DatePicker(state = pickerState)
        }
    }
}

'''

account_card = r'''@Composable
internal fun AccountCard(account: AccountOverview, onClick: () -> Unit) {
    val header = account.ledger.header
    val receivable = header.direction == DebtDirection.RECEIVABLE
    val tagPrefix = "account-${header.id.value}"
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tagPrefix),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (shouldStackDenseRows(maxWidth)) {
                    Column(Modifier.fillMaxWidth().testTag("$tagPrefix-header-stacked"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        AccountIdentityRow(account, receivable, Modifier.fillMaxWidth())
                        AccountDirectionBadge(receivable)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().testTag("$tagPrefix-header-inline"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccountIdentityRow(account, receivable, Modifier.weight(1f))
                        AccountDirectionBadge(receivable)
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (shouldStackDenseRows(maxWidth)) {
                    Column(Modifier.fillMaxWidth().testTag("$tagPrefix-balance-stacked"), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        AccountRemainingBalance(account)
                        AccountStateBadge(account.ledger.state)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().testTag("$tagPrefix-balance-inline"),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        AccountRemainingBalance(account)
                        AccountStateBadge(account.ledger.state)
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val dueText = header.dueDate?.let { "الاستحقاق ${it.format(dueDateFormatter)}" } ?: "بدون تاريخ استحقاق"
                if (shouldStackDenseRows(maxWidth)) {
                    Column(Modifier.fillMaxWidth().testTag("$tagPrefix-original-stacked"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الأصل ${formatMoney(header.originalAmount)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(Modifier.fillMaxWidth().testTag("$tagPrefix-original-inline"), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("الأصل ${formatMoney(header.originalAmount)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

'''

s, n1 = re.subn(r'@Composable\nprivate fun WaslHomeScreen\([\s\S]*?(?=@Composable\n@OptIn\(ExperimentalMaterial3Api::class\)\nprivate fun CreateDebtDialog)', home, s, count=1)
assert n1 == 1, f'home replacement count={n1}'
s, n2 = re.subn(r'@Composable\n@OptIn\(ExperimentalMaterial3Api::class\)\nprivate fun CreateDebtDialog\([\s\S]*?(?=@Composable\ninternal fun AccountCard)', create_dialog, s, count=1)
assert n2 == 1, f'create replacement count={n2}'
s, n3 = re.subn(r'@Composable\ninternal fun AccountCard\([\s\S]*?(?=@Composable\nprivate fun AccountIdentityRow)', account_card, s, count=1)
assert n3 == 1, f'account replacement count={n3}'
s = s.replace('import androidx.compose.material3.ExtendedFloatingActionButton\n', '')
p.write_text(s)

t = Path('app/src/androidTest/java/com/wasl/app/TodayUiInstrumentedTest.kt')
ts = t.read_text().replace('waitForText("اليوم لديك 2 أمور")', 'waitForText("ملخص اليوم")')
t.write_text(ts)
