from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


wasl_path = Path("app/src/main/java/com/wasl/app/WaslApp.kt")
text = wasl_path.read_text()

text = replace_once(
    text,
    '''            ExtendedFloatingActionButton(
                onClick = onOpenCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {''',
    '''            ExtendedFloatingActionButton(
                modifier = Modifier.testTag("home-add-entry"),
                onClick = onOpenCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {''',
    "home FAB tag",
)

text = replace_once(
    text,
    '''            item("home-header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "دفترك المالي الشخصي",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(
                        text = "وَصل",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "كل حساب له وصل",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }''',
    '''            item("home-header") {
                HomeHeroCard(accountCount = state.accounts.size)
            }''',
    "home hero",
)

text = replace_once(
    text,
    '''                item("home-overview-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "ملخصك المالي",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "نظرة سريعة على الحقوق والالتزامات حسب العملة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }''',
    '''                item("home-overview-heading") {
                    HomeSectionHeader(
                        title = "ملخصك المالي",
                        subtitle = "نظرة سريعة على الحقوق والالتزامات حسب العملة.",
                        tagPrefix = "home-overview",
                    )
                }''',
    "home overview heading",
)

text = replace_once(
    text,
    '''                    item("home-accounts-heading") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "الحسابات",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${state.accounts.size} حساب محفوظ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text(
                                    text = state.accounts.size.toString(),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }''',
    '''                    item("home-accounts-heading") {
                        HomeSectionHeader(
                            title = "الحسابات",
                            subtitle = "${state.accounts.size} حساب محفوظ",
                            count = state.accounts.size,
                            tagPrefix = "home-accounts",
                        )
                    }''',
    "home accounts heading",
)

wasl_path.write_text(text)

group_path = Path("app/src/main/java/com/wasl/app/GroupExpenseDialogs.kt")
group = group_path.read_text()

group = replace_once(
    group,
    '''                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create-entry-individual"),
                    onClick = onCreateIndividual,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("حساب فردي", fontWeight = FontWeight.Bold)
                        Text(
                            text = "دين أو حق مع شخص واحد",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create-entry-group"),
                    onClick = onCreateGroupExpense,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("عملية جماعية", fontWeight = FontWeight.Bold)
                        Text(
                            text = "عملية واحدة بحصص موزعة على شخصين أو أكثر",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }''',
    '''                CreateEntryOption(
                    title = "حساب فردي",
                    description = "دين أو حق مع شخص واحد",
                    primary = true,
                    testTag = "create-entry-individual",
                    onClick = onCreateIndividual,
                )
                CreateEntryOption(
                    title = "عملية جماعية",
                    description = "عملية واحدة بحصص موزعة على شخصين أو أكثر",
                    primary = false,
                    testTag = "create-entry-group",
                    onClick = onCreateGroupExpense,
                )''',
    "create entry options",
)

group_path.write_text(group)
