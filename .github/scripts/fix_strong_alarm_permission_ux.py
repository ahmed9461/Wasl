from pathlib import Path


def patch(path: str, old: str, new: str, count: int = 1) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, count), encoding="utf-8")


wasl = "app/src/main/java/com/wasl/app/WaslApp.kt"
details = "app/src/main/java/com/wasl/app/AccountDetailsScreen.kt"
test = "app/src/androidTest/java/com/wasl/app/DueDateUiInstrumentedTest.kt"

patch(
    wasl,
    """                                onStrongAlarmChange = { enabled ->
                                    homeViewModel.updateStrongAlarm(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                    if (enabled && !exactAlarmsAvailable) requestExactAlarmAccess()
                                },
                                notificationPermissionGranted = notificationsAvailable,""",
    """                                onStrongAlarmChange = homeViewModel::updateStrongAlarm,
                                onRequestExactAlarmAccess = ::requestExactAlarmAccess,
                                notificationPermissionGranted = notificationsAvailable,""",
)
patch(
    wasl,
    """                                onDueScheduleStrongAlarmChange = { enabled ->
                                    detailsViewModel.updateDueScheduleStrongAlarm(enabled)
                                    if (enabled && !notificationsAvailable) requestNotificationAccess()
                                    if (enabled && !exactAlarmsAvailable) requestExactAlarmAccess()
                                },
                                onConfirmDueSchedule = detailsViewModel::confirmDueSchedule,""",
    """                                onDueScheduleStrongAlarmChange = detailsViewModel::updateDueScheduleStrongAlarm,
                                onRequestExactAlarmAccess = ::requestExactAlarmAccess,
                                onConfirmDueSchedule = detailsViewModel::confirmDueSchedule,""",
)
patch(
    wasl,
    """    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,""",
    """    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    notificationPermissionGranted: Boolean,""",
)
patch(
    wasl,
    """            onReminderChange = onReminderChange,
            onStrongAlarmChange = onStrongAlarmChange,
            notificationPermissionGranted = notificationPermissionGranted,""",
    """            onReminderChange = onReminderChange,
            onStrongAlarmChange = onStrongAlarmChange,
            onRequestExactAlarmAccess = onRequestExactAlarmAccess,
            notificationPermissionGranted = notificationPermissionGranted,""",
)
patch(
    wasl,
    """    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,""",
    """    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    notificationPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onSave: () -> Unit,""",
)
patch(
    wasl,
    """                if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                    Text(
                        \"Android يحتاج إذن «المنبهات والتذكيرات» لتشغيل المنبه القوي بدقة. المتابعة الذكية ستبقى فعالة.\",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(\"create-exact-alarm-permission-warning\"),
                    )
                }""",
    """                if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            \"Android يحتاج إذن «المنبهات والتذكيرات» لتشغيل المنبه القوي بدقة. المتابعة الذكية ستبقى فعالة.\",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(\"create-exact-alarm-permission-warning\"),
                        )
                        TextButton(
                            onClick = onRequestExactAlarmAccess,
                            enabled = !isSaving,
                            modifier = Modifier.testTag(\"create-request-exact-alarm-access\"),
                        ) {
                            Text(\"السماح بالمنبه الدقيق\")
                        }
                    }
                }""",
)
patch(
    wasl,
    """                onStrongAlarmChange = {},
                notificationPermissionGranted = true,""",
    """                onStrongAlarmChange = {},
                onRequestExactAlarmAccess = {},
                notificationPermissionGranted = true,""",
)

patch(
    details,
    """    onDueScheduleReminderChange: (Boolean) -> Unit,
    onDueScheduleStrongAlarmChange: (Boolean) -> Unit,
    onConfirmDueSchedule: () -> Unit,""",
    """    onDueScheduleReminderChange: (Boolean) -> Unit,
    onDueScheduleStrongAlarmChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onConfirmDueSchedule: () -> Unit,""",
)
patch(
    details,
    """            onReminderChange = onDueScheduleReminderChange,
            onStrongAlarmChange = onDueScheduleStrongAlarmChange,
            onConfirm = onConfirmDueSchedule,""",
    """            onReminderChange = onDueScheduleReminderChange,
            onStrongAlarmChange = onDueScheduleStrongAlarmChange,
            onRequestExactAlarmAccess = onRequestExactAlarmAccess,
            onConfirm = onConfirmDueSchedule,""",
)
patch(
    details,
    """    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {""",
    """    onReminderChange: (Boolean) -> Unit,
    onStrongAlarmChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onConfirm: () -> Unit,
) {""",
)
patch(
    details,
    """                if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                    Text(
                        \"المنبه القوي محفوظ، لكن Android يحتاج إذن «المنبهات والتذكيرات» لتشغيله بدقة. ستستمر المتابعة الذكية.\",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(\"exact-alarm-permission-warning\"),
                    )
                }""",
    """                if (form.strongAlarmEnabled && !exactAlarmAccessGranted) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            \"المنبه القوي محفوظ، لكن Android يحتاج إذن «المنبهات والتذكيرات» لتشغيله بدقة. ستستمر المتابعة الذكية.\",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(\"exact-alarm-permission-warning\"),
                        )
                        TextButton(
                            onClick = onRequestExactAlarmAccess,
                            enabled = !isSaving,
                            modifier = Modifier.testTag(\"request-exact-alarm-access\"),
                        ) {
                            Text(\"السماح بالمنبه الدقيق\")
                        }
                    }
                }""",
)

patch(test, 'composeRule.onNodeWithText("موعد التذكير")', 'composeRule.onNodeWithText("موعد المتابعة الأساسي")')
patch(
    test,
    """        waitForTag(\"exact-alarm-permission-warning\")
        composeRule.onNodeWithTag(\"exact-alarm-permission-warning\").assertIsDisplayed()""",
    """        waitForTag(\"exact-alarm-permission-warning\")
        composeRule.onNodeWithTag(\"exact-alarm-permission-warning\")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(\"request-exact-alarm-access\")
            .performScrollTo()
            .assertIsDisplayed()""",
)
