from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def replace_once(path, old, new):
    text = read(path)
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


def replace_span(path, start, end, replacement):
    text = read(path)
    a = text.find(start)
    if a < 0:
        raise SystemExit(f"Start marker not found in {path}: {start!r}")
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f"End marker not found in {path}: {end!r}")
    write(path, text[:a] + replacement + text[b:])


# Home creation flow: strong alarm is opt-in and keeps smart follow-up enabled as fallback.
path = "app/src/main/java/com/wasl/app/HomeViewModel.kt"
replace_once(path,
'import com.wasl.app.data.RecordNotFoundException\n',
'import com.wasl.app.data.RecordNotFoundException\nimport com.wasl.app.data.StrongAlarmRequest\n')
replace_once(path,
'''    val dueDate: LocalDate? = null,
    val remindOnDueDate: Boolean = false,
)''',
'''    val dueDate: LocalDate? = null,
    val remindOnDueDate: Boolean = false,
    val strongAlarmEnabled: Boolean = false,
)''')
replace_once(path,
'''            dueDate = value,
            remindOnDueDate = if (value == null) false else remindOnDueDate,
        )''',
'''            dueDate = value,
            remindOnDueDate = if (value == null) false else remindOnDueDate,
            strongAlarmEnabled = if (value == null) false else strongAlarmEnabled,
        )''')
replace_once(path,
'''    fun updateRemindOnDueDate(value: Boolean) = updateForm {
        copy(remindOnDueDate = value && dueDate != null)
    }''',
'''    fun updateRemindOnDueDate(value: Boolean) = updateForm {
        val enabled = value && dueDate != null
        copy(
            remindOnDueDate = enabled,
            strongAlarmEnabled = strongAlarmEnabled && enabled,
        )
    }

    fun updateStrongAlarm(value: Boolean) = updateForm {
        val enabled = value && dueDate != null
        copy(
            strongAlarmEnabled = enabled,
            remindOnDueDate = if (enabled) true else remindOnDueDate,
        )
    }''')
replace_once(path,
'''        if (form.remindOnDueDate && form.dueDate == null) {
            _uiState.update { it.copy(formError = "اختر تاريخ الاستحقاق قبل تفعيل التذكير.") }
            return
        }''',
'''        if ((form.remindOnDueDate || form.strongAlarmEnabled) && form.dueDate == null) {
            _uiState.update {
                it.copy(formError = "اختر تاريخ الاستحقاق قبل تفعيل المتابعة أو المنبه.")
            }
            return
        }''')
replace_once(path,
'''            reminder = if (form.remindOnDueDate) {
                DueReminderRequest(
                    id = idFactory(),
                    triggerAt = ReminderTime.dueDateTrigger(
                        dueDate = requireNotNull(form.dueDate),
                        now = now,
                        zoneId = zoneId,
                    ),
                    zoneId = zoneId,
                )
            } else {
                null
            },
            timestamp = now,''',
'''            reminder = if (form.remindOnDueDate) {
                DueReminderRequest(
                    id = idFactory(),
                    triggerAt = ReminderTime.dueDateTrigger(
                        dueDate = requireNotNull(form.dueDate),
                        now = now,
                        zoneId = zoneId,
                    ),
                    zoneId = zoneId,
                )
            } else {
                null
            },
            strongAlarm = if (form.strongAlarmEnabled) {
                StrongAlarmRequest(
                    id = idFactory(),
                    triggerAt = ReminderTime.dueDateTrigger(
                        dueDate = requireNotNull(form.dueDate),
                        now = now,
                        zoneId = zoneId,
                    ),
                    zoneId = zoneId,
                )
            } else {
                null
            },
            timestamp = now,''')
replace_once(path,
'''                            dueReminder = identity.reminder,
                        ),''',
'''                            dueReminder = identity.reminder,
                            strongAlarm = identity.strongAlarm,
                        ),''')
replace_once(path,
'''                            dueReminder = identity.reminder,
                        ),''',
'''                            dueReminder = identity.reminder,
                            strongAlarm = identity.strongAlarm,
                        ),''')
replace_once(path,
'''                val schedulingFailed = created.dueReminder?.let { dueReminder ->
                    runCatching { reminderScheduler.schedule(dueReminder) }.isFailure
                } ?: false''',
'''                val schedulingFailed = listOfNotNull(
                    created.dueReminder,
                    created.strongAlarm,
                ).map { reminder ->
                    runCatching { reminderScheduler.schedule(reminder) }.isFailure
                }.any { it }''')
replace_once(path,
'''                        successMessage = if (schedulingFailed) {
                            "تم حفظ الحساب والتذكير، وستُعاد محاولة الجدولة تلقائيًا."
                        } else if (created.dueReminder != null) {
                            "تم حفظ الحساب وجدولة التذكير."
                        } else if (form.personMode == DebtPersonMode.EXISTING) {''',
'''                        successMessage = if (schedulingFailed && created.strongAlarm != null) {
                            "تم حفظ الحساب والمتابعة. المنبه القوي يحتاج إذن Android للمنبهات الدقيقة."
                        } else if (schedulingFailed) {
                            "تم حفظ الحساب والمتابعة، وستُعاد محاولة الجدولة تلقائيًا."
                        } else if (created.strongAlarm != null) {
                            "تم حفظ الحساب وتفعيل المتابعة والمنبه القوي."
                        } else if (created.dueReminder != null) {
                            "تم حفظ الحساب وتفعيل المتابعة الذكية."
                        } else if (form.personMode == DebtPersonMode.EXISTING) {''')
replace_once(path,
'''        val reminder: DueReminderRequest?,
        val timestamp: Instant,''',
'''        val reminder: DueReminderRequest?,
        val strongAlarm: StrongAlarmRequest?,
        val timestamp: Instant,''')

# Account Details state and command path.
path = "app/src/main/java/com/wasl/app/AccountDetailsViewModel.kt"
replace_once(path,
'import com.wasl.app.data.ReminderStatus\n',
'import com.wasl.app.data.ReminderStatus\nimport com.wasl.app.data.StrongAlarmRequest\n')
replace_once(path,
'''data class DueScheduleForm(
    val dueDate: LocalDate? = null,
    val remindOnDueDate: Boolean = false,
)''',
'''data class DueScheduleForm(
    val dueDate: LocalDate? = null,
    val remindOnDueDate: Boolean = false,
    val strongAlarmEnabled: Boolean = false,
)''')
replace_once(path,
'''        val reminderEnabled: Boolean,
        val platformSyncPending: Boolean,''',
'''        val reminderEnabled: Boolean,
        val strongAlarmEnabled: Boolean,
        val platformSyncPending: Boolean,''')
replace_once(path,
'''                    remindOnDueDate = account.dueReminder
                        ?.status
                        ?.let { it != ReminderStatus.CANCELLED }
                        ?: false,
                ),''',
'''                    remindOnDueDate = account.dueReminder
                        ?.status
                        ?.let { it != ReminderStatus.CANCELLED }
                        ?: false,
                    strongAlarmEnabled = account.strongAlarm
                        ?.status
                        ?.let { it != ReminderStatus.CANCELLED }
                        ?: false,
                ),''')
replace_once(path,
'''                    remindOnDueDate = if (value == null) false else {
                        it.dueScheduleForm.remindOnDueDate
                    },
                ),''',
'''                    remindOnDueDate = if (value == null) false else {
                        it.dueScheduleForm.remindOnDueDate
                    },
                    strongAlarmEnabled = if (value == null) false else {
                        it.dueScheduleForm.strongAlarmEnabled
                    },
                ),''')
replace_once(path,
'''    fun updateDueScheduleReminder(value: Boolean) {
        pendingDueScheduleCommand = null
        _uiState.update {
            it.copy(
                dueScheduleForm = it.dueScheduleForm.copy(
                    remindOnDueDate = value && it.dueScheduleForm.dueDate != null,
                ),
                dueScheduleError = null,
            )
        }
    }''',
'''    fun updateDueScheduleReminder(value: Boolean) {
        pendingDueScheduleCommand = null
        _uiState.update {
            val enabled = value && it.dueScheduleForm.dueDate != null
            it.copy(
                dueScheduleForm = it.dueScheduleForm.copy(
                    remindOnDueDate = enabled,
                    strongAlarmEnabled = it.dueScheduleForm.strongAlarmEnabled && enabled,
                ),
                dueScheduleError = null,
            )
        }
    }

    fun updateDueScheduleStrongAlarm(value: Boolean) {
        pendingDueScheduleCommand = null
        _uiState.update {
            val enabled = value && it.dueScheduleForm.dueDate != null
            it.copy(
                dueScheduleForm = it.dueScheduleForm.copy(
                    strongAlarmEnabled = enabled,
                    remindOnDueDate = if (enabled) true else it.dueScheduleForm.remindOnDueDate,
                ),
                dueScheduleError = null,
            )
        }
    }''')
replace_once(path,
'''        if (form.remindOnDueDate && form.dueDate == null) {
            _uiState.update {
                it.copy(dueScheduleError = "اختر تاريخ الاستحقاق قبل تفعيل التذكير.")
            }
            return
        }''',
'''        if ((form.remindOnDueDate || form.strongAlarmEnabled) && form.dueDate == null) {
            _uiState.update {
                it.copy(dueScheduleError = "اختر تاريخ الاستحقاق قبل تفعيل المتابعة أو المنبه.")
            }
            return
        }''')
replace_once(path,
'''        val currentReminderEnabled = account.dueReminder
            ?.status
            ?.let { it != ReminderStatus.CANCELLED }
            ?: false
        if (pendingDueScheduleCommand == null &&
            form.dueDate == account.ledger.header.dueDate &&
            form.remindOnDueDate == currentReminderEnabled
        ) {
            _uiState.update { it.copy(dueScheduleError = "لم تغيّر الموعد أو التذكير.") }''',
'''        val currentReminderEnabled = account.dueReminder
            ?.status
            ?.let { it != ReminderStatus.CANCELLED }
            ?: false
        val currentStrongAlarmEnabled = account.strongAlarm
            ?.status
            ?.let { it != ReminderStatus.CANCELLED }
            ?: false
        if (pendingDueScheduleCommand == null &&
            form.dueDate == account.ledger.header.dueDate &&
            form.remindOnDueDate == currentReminderEnabled &&
            form.strongAlarmEnabled == currentStrongAlarmEnabled
        ) {
            _uiState.update { it.copy(dueScheduleError = "لم تغيّر الموعد أو المتابعة أو المنبه.") }''')
replace_once(path,
'''            UpdateDueScheduleCommand(
                commandId = idFactory(),
                auditEventId = idFactory(),
                debtId = debtId,
                dueDate = form.dueDate,
                dueReminder = reminder,
                updatedAt = updatedAt,''',
'''            val strongAlarm = if (form.strongAlarmEnabled) {
                StrongAlarmRequest(
                    id = account.strongAlarm?.id ?: idFactory(),
                    triggerAt = ReminderTime.dueDateTrigger(
                        dueDate = requireNotNull(form.dueDate),
                        now = now,
                        zoneId = zoneId,
                    ),
                    zoneId = zoneId,
                )
            } else {
                null
            }
            UpdateDueScheduleCommand(
                commandId = idFactory(),
                auditEventId = idFactory(),
                debtId = debtId,
                dueDate = form.dueDate,
                dueReminder = reminder,
                strongAlarm = strongAlarm,
                updatedAt = updatedAt,''')
replace_span(path,
'''                val persistedReminder = updated.dueReminder
''',
'''                pendingDueScheduleCommand = null
''',
'''                val activeReminders = listOfNotNull(updated.dueReminder, updated.strongAlarm)
                    .filter { it.status != ReminderStatus.CANCELLED }
                val activeIds = activeReminders.mapTo(mutableSetOf()) { it.id }
                val schedulingFailed = activeReminders.map { reminder ->
                    runCatching { reminderScheduler.schedule(reminder) }
                        .onFailure { runCatching { reminderScheduler.requestRecovery() } }
                        .isFailure
                }.any { it }
                val cancellationFailed = listOfNotNull(account.dueReminder, account.strongAlarm)
                    .filter { it.id !in activeIds }
                    .map { old -> runCatching { reminderScheduler.cancel(old.id) }.isFailure }
                    .any { it }
                val persistedReminderEnabled = updated.dueReminder
                    ?.status
                    ?.let { it != ReminderStatus.CANCELLED }
                    ?: false
                val persistedStrongAlarmEnabled = updated.strongAlarm
                    ?.status
                    ?.let { it != ReminderStatus.CANCELLED }
                    ?: false
                val platformSyncFailed = schedulingFailed || cancellationFailed
''')
replace_once(path,
'''                            reminderEnabled = persistedReminderEnabled,
                            platformSyncPending = platformSyncFailed,''',
'''                            reminderEnabled = persistedReminderEnabled,
                            strongAlarmEnabled = persistedStrongAlarmEnabled,
                            platformSyncPending = platformSyncFailed,''')

# Account details UI.
path = "app/src/main/java/com/wasl/app/AccountDetailsScreen.kt"
replace_once(path,
'''    onDueScheduleReminderChange: (Boolean) -> Unit,
    onConfirmDueSchedule: () -> Unit,''',
'''    onDueScheduleReminderChange: (Boolean) -> Unit,
    onDueScheduleStrongAlarmChange: (Boolean) -> Unit,
    onConfirmDueSchedule: () -> Unit,''')
replace_once(path,
'''    notificationPermissionGranted: Boolean,
    onNoticeShown: () -> Unit,''',
'''    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onNoticeShown: () -> Unit,''')
replace_once(path,
'''            notificationPermissionGranted = notificationPermissionGranted,
            onDismiss = onDismissDueSchedule,
            onDueDateChange = onDueScheduleDateChange,
            onReminderChange = onDueScheduleReminderChange,
            onConfirm = onConfirmDueSchedule,''',
'''            notificationPermissionGranted = notificationPermissionGranted,
            exactAlarmAccessGranted = exactAlarmAccessGranted,
            onDismiss = onDismissDueSchedule,
            onDueDateChange = onDueScheduleDateChange,
            onReminderChange = onDueScheduleReminderChange,
            onStrongAlarmChange = onDueScheduleStrongAlarmChange,
            onConfirm = onConfirmDueSchedule,''')
replace_once(path,
'''                    account.dueReminder?.let { reminder ->
                        MetadataRow("موعد التذكير", formatInstant(reminder.triggerAt))''',
'''                    account.dueReminder?.let { reminder ->
                        MetadataRow("موعد المتابعة الأساسي", formatInstant(reminder.triggerAt))''')
replace_once(path,
'''                    account.closedAt?.let { closedAt ->''',
'''                    account.strongAlarm
                        ?.takeIf { it.status != ReminderStatus.CANCELLED }
                        ?.let { alarm ->
                            MetadataRow("المنبه القوي", formatInstant(alarm.triggerAt))
                        }
                    account.closedAt?.let { closedAt ->''')
replace_once(path,
'''    notificationPermissionGranted: Boolean,
    onDismiss: () -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,''',
'''    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onDismiss: () -> Unit,
    onDueDateChange: (LocalDate?) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,''')
replace_once(path,
'''                if (form.remindOnDueDate && !notificationPermissionGranted) {
                    Text(
                        "ستُحفظ المتابعة، لكنها لن تظهر حتى تسمح بإشعارات وَصل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }''',
'''                if (form.remindOnDueDate && !notificationPermissionGranted) {
                    Text(
                        "ستُحفظ المتابعة، لكنها لن تظهر حتى تسمح بإشعارات وَصل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("منبه قوي إضافي", fontWeight = FontWeight.SemiBold)
                        Text(
                            "منبه دقيق قرابة 09:00 يوم الاستحقاق. المتابعة الذكية تبقى كبديل آمن.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = form.strongAlarmEnabled,
                        onCheckedChange = onStrongAlarmChange,
                        enabled = !isSaving && form.dueDate != null,
                        modifier = Modifier.testTag("edit-strong-alarm"),
                    )
                }
                if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                    Text(
                        "المنبه القوي محفوظ، لكن Android يحتاج إذن «المنبهات والتذكيرات» لتشغيله بدقة. ستستمر المتابعة الذكية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("exact-alarm-permission-warning"),
                    )
                }''')
replace_once(path,
'''        reminderEnabled -> "تم تحديث موعد الاستحقاق وتفعيل المتابعة الذكية في حساب $personName."
        else -> "تم تحديث موعد الاستحقاق دون تذكير في حساب $personName."''',
'''        strongAlarmEnabled && reminderEnabled ->
            "تم تحديث موعد الاستحقاق وتفعيل المتابعة الذكية والمنبه القوي في حساب $personName."
        reminderEnabled -> "تم تحديث موعد الاستحقاق وتفعيل المتابعة الذكية في حساب $personName."
        else -> "تم تحديث موعد الاستحقاق دون متابعة في حساب $personName."''')

# WaslApp: exact-alarm state is a special-access setting, not runtime permission.
path = "app/src/main/java/com/wasl/app/WaslApp.kt"
replace_once(path,
'import com.wasl.app.reminder.NoOpReminderScheduler\n',
'import com.wasl.app.reminder.ExactAlarmAccess\nimport com.wasl.app.reminder.NoOpReminderScheduler\n')
replace_once(path,
'''    todayZoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    requestedDebtId: String? = null,''',
'''    todayZoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    exactAlarmAccessOverride: Boolean? = null,
    requestedDebtId: String? = null,''')
replace_once(path,
'''    var notificationsAvailable by remember {
        mutableStateOf(canPostNotifications(context))
    }
    var permissionRequestAttempted''',
'''    var notificationsAvailable by remember {
        mutableStateOf(canPostNotifications(context))
    }
    var exactAlarmsAvailable by remember(exactAlarmAccessOverride) {
        mutableStateOf(exactAlarmAccessOverride ?: ExactAlarmAccess.canSchedule(context))
    }
    var permissionRequestAttempted''')
replace_once(path,
'''            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAvailable = canPostNotifications(context)
            }''',
'''            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAvailable = canPostNotifications(context)
                val exactWasAvailable = exactAlarmsAvailable
                exactAlarmsAvailable = exactAlarmAccessOverride
                    ?: ExactAlarmAccess.canSchedule(context)
                if (!exactWasAvailable && exactAlarmsAvailable) {
                    reminderScheduler.requestRecovery()
                }
            }''')
replace_once(path,
'''    LaunchedEffect(requestedDebtId) {''',
'''    fun requestExactAlarmAccess() {
        if (exactAlarmAccessOverride != null) return
        ExactAlarmAccess.requestIntent(context)?.let(context::startActivity)
    }
    LaunchedEffect(requestedDebtId) {''')
replace_once(path,
'''                                onReminderChange = { enabled ->
                                    homeViewModel.updateRemindOnDueDate(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                },
                                notificationPermissionGranted = notificationsAvailable,''',
'''                                onReminderChange = { enabled ->
                                    homeViewModel.updateRemindOnDueDate(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                },
                                onStrongAlarmChange = { enabled ->
                                    homeViewModel.updateStrongAlarm(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                    if (enabled && !exactAlarmsAvailable) requestExactAlarmAccess()
                                },
                                notificationPermissionGranted = notificationsAvailable,
                                exactAlarmAccessGranted = exactAlarmsAvailable,''')
replace_once(path,
'''                                onDueScheduleReminderChange = { enabled ->
                                    detailsViewModel.updateDueScheduleReminder(enabled)
                                    if (enabled && !notificationsAvailable) {
                                        requestNotificationAccess()
                                    }
                                },
                                onConfirmDueSchedule = detailsViewModel::confirmDueSchedule,''',
'''                                onDueScheduleReminderChange = { enabled ->
                                    detailsViewModel.updateDueScheduleReminder(enabled)
                                    if (enabled && !notificationsAvailable) {
                                        requestNotificationAccess()
                                    }
                                },
                                onDueScheduleStrongAlarmChange = { enabled ->
                                    detailsViewModel.updateDueScheduleStrongAlarm(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                    if (enabled && !exactAlarmsAvailable) requestExactAlarmAccess()
                                },
                                onConfirmDueSchedule = detailsViewModel::confirmDueSchedule,''')
replace_once(path,
'''                                notificationPermissionGranted = notificationsAvailable,
                                onNoticeShown = detailsViewModel::clearNotice,''',
'''                                notificationPermissionGranted = notificationsAvailable,
                                exactAlarmAccessGranted = exactAlarmsAvailable,
                                onNoticeShown = detailsViewModel::clearNotice,''')
replace_once(path,
'''    onReminderChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    onSave: () -> Unit,''',
'''    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,''')
replace_once(path,
'''            onReminderChange = onReminderChange,
            notificationPermissionGranted = notificationPermissionGranted,
            onSave = onSave,''',
'''            onReminderChange = onReminderChange,
            onStrongAlarmChange = onStrongAlarmChange,
            notificationPermissionGranted = notificationPermissionGranted,
            exactAlarmAccessGranted = exactAlarmAccessGranted,
            onSave = onSave,''')
replace_once(path,
'''    onReminderChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    onSave: () -> Unit,
) {''',
'''    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,
) {''')
replace_once(path,
'''                if (form.remindOnDueDate && !notificationPermissionGranted) {
                    Text(
                        "سيُحفظ التذكير، لكن لن يظهر الإشعار حتى تسمح بإشعارات وَصل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }''',
'''                if (form.remindOnDueDate && !notificationPermissionGranted) {
                    Text(
                        "ستُحفظ المتابعة، لكن لن يظهر الإشعار حتى تسمح بإشعارات وَصل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("منبه قوي إضافي", fontWeight = FontWeight.SemiBold)
                        Text(
                            "منبه دقيق قرابة 09:00 يوم الاستحقاق مع إبقاء المتابعة الذكية كبديل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = form.strongAlarmEnabled,
                        onCheckedChange = onStrongAlarmChange,
                        enabled = !isSaving && form.dueDate != null,
                        modifier = Modifier.testTag("create-strong-alarm"),
                    )
                }
                if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                    Text(
                        "Android يحتاج إذن «المنبهات والتذكيرات» لتشغيل المنبه القوي بدقة. المتابعة الذكية ستبقى فعالة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("create-exact-alarm-permission-warning"),
                    )
                }''')

# Unit test for exact-alarm recovery gate.
path = "app/src/test/java/com/wasl/app/reminder/ReminderRecoveryPolicyTest.kt"
replace_once(path,
'import com.wasl.app.data.ReminderRecord\n',
'import com.wasl.app.data.ReminderRecord\nimport com.wasl.app.data.ReminderScheduleType\nimport com.wasl.app.data.ReminderType\n')
replace_once(path,
'''    private fun reminder(
''',
'''    @Test
    fun strongAlarmNeedsExactAccessAndMustStillBeInTheFuture() {
        val futureAlarm = reminder(ReminderStatus.SCHEDULED).copy(
            type = ReminderType.STRONG_ALARM,
            scheduleType = ReminderScheduleType.EXACT_ALARM,
        )
        val blocked = planReminderRecovery(
            stored = futureAlarm,
            currentZone = ZoneOffset.UTC,
            now = now,
            canNotify = true,
            canScheduleExactAlarms = false,
        )
        assertFalse(blocked.shouldSchedule)

        val allowed = planReminderRecovery(
            stored = futureAlarm,
            currentZone = ZoneOffset.UTC,
            now = now,
            canNotify = true,
            canScheduleExactAlarms = true,
        )
        assertTrue(allowed.shouldSchedule)

        val past = planReminderRecovery(
            stored = futureAlarm.copy(triggerAt = now.minusSeconds(1)),
            currentZone = ZoneOffset.UTC,
            now = now,
            canNotify = true,
            canScheduleExactAlarms = true,
        )
        assertFalse(past.shouldSchedule)
    }

    private fun reminder(
''')

# Room/integration coverage.
write("app/src/androidTest/java/com/wasl/app/data/StrongAlarmStoreInstrumentedTest.kt", '''package com.wasl.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.local.RoomWaslRepository
import com.wasl.app.data.local.WaslDatabase
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrongAlarmStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: WaslDatabase
    private lateinit var repository: RoomWaslRepository

    @BeforeTest
    fun setUp() {
        databaseName = "wasl-strong-alarm-${UUID.randomUUID()}.db"
        database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        repository = RoomWaslRepository(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun smartReminderAndStrongAlarmCoexistAndAreCancelledWithDueDate() = runBlocking {
        val zone = ZoneId.of("Asia/Aden")
        val createdAt = Instant.parse("2026-08-25T06:00:00Z")
        val account = repository.createPersonWithDebt(
            CreatePersonWithDebtCommand(
                personId = PersonId("person-strong"),
                debtId = DebtId("debt-strong"),
                personName = "أحمد",
                direction = DebtDirection.RECEIVABLE,
                originalAmount = Money(10_000L, CurrencyCode.YER),
                openedAt = createdAt,
                createdAt = createdAt,
                dueDate = LocalDate.parse("2026-08-30"),
                dueReminder = DueReminderRequest(
                    id = "due-strong-test",
                    triggerAt = Instant.parse("2026-08-30T06:00:00Z"),
                    zoneId = zone,
                ),
                strongAlarm = StrongAlarmRequest(
                    id = "alarm-strong-test",
                    triggerAt = Instant.parse("2026-08-30T06:00:00Z"),
                    zoneId = zone,
                ),
            ),
        )
        assertNotNull(account.dueReminder)
        assertNotNull(account.strongAlarm)
        assertEquals(ReminderType.DUE_DATE, account.dueReminder?.type)
        assertEquals(ReminderType.STRONG_ALARM, account.strongAlarm?.type)

        val updated = repository.updateDueSchedule(
            UpdateDueScheduleCommand(
                commandId = "remove-due-and-alarm",
                auditEventId = "audit-remove-due-and-alarm",
                debtId = DebtId("debt-strong"),
                dueDate = null,
                dueReminder = null,
                strongAlarm = null,
                updatedAt = createdAt.plusSeconds(60),
            ),
        )
        assertEquals(ReminderStatus.CANCELLED, updated.dueReminder?.status)
        assertEquals(ReminderStatus.CANCELLED, updated.strongAlarm?.status)
        assertEquals(null, updated.ledger.header.dueDate)
        assertEquals(null, updated.dueScheduleAuditEvents.single().after.strongAlarm)
    }
}
''')

# Deterministic Compose guidance: exactAlarmAccessOverride avoids launching Android settings in test.
path = "app/src/androidTest/java/com/wasl/app/DueDateUiInstrumentedTest.kt"
replace_once(path,
'''    private fun waitForText(text: String) {''',
'''    @Test
    fun strongAlarmToggleShowsExactAlarmPermissionGuidance() {
        composeRule.setContent {
            WaslApp(
                repository = repository,
                instanceKey = "strong-alarm-ui-test",
                exactAlarmAccessOverride = false,
                requestedDebtId = "debt-due",
            )
        }

        waitForTag("edit-due-schedule")
        composeRule.onNodeWithTag("edit-due-schedule")
            .performScrollTo()
            .performClick()
        waitForTag("edit-strong-alarm")
        composeRule.onNodeWithTag("edit-strong-alarm").performClick()
        waitForTag("exact-alarm-permission-warning")
        composeRule.onNodeWithTag("exact-alarm-permission-warning").assertIsDisplayed()
    }

    private fun waitForText(text: String) {''')
