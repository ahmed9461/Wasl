from pathlib import Path

settings = Path("app/src/main/java/com/wasl/app/SettingsHubScreen.kt")
text = settings.read_text()
old = """    onBack: () -> Unit,
    onOpenDocuments: () -> Unit,
    onRestored: () -> Unit,"""
new = """    onBack: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenStatistics: () -> Unit,
    onRestored: () -> Unit,"""
if old not in text:
    raise SystemExit("SettingsHubRoute parameters marker not found")
text = text.replace(old, new, 1)

old = """            SettingsSectionCard(
                title = \"المستندات\","""
new = """            SettingsSectionCard(
                title = \"الإحصاءات\",
                subtitle = \"مؤشرات موضوعية من سجل الحسابات ووعود السداد.\",
            ) {
                Text(
                    text = \"لا توجد تقييمات للأشخاص، ولا يتم جمع مبالغ العملات المختلفة.\",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(\"open-objective-statistics\"),
                    onClick = onOpenStatistics,
                ) {
                    Text(\"فتح الإحصاءات\")
                }
            }

            SettingsSectionCard(
                title = \"المستندات\","""
if old not in text:
    raise SystemExit("Settings section marker not found")
settings.write_text(text.replace(old, new, 1))

main = Path("app/src/main/java/com/wasl/app/MainActivity.kt")
text = main.read_text()
old = """                                onOpenDocuments = {
                                    settingsOpen = false
                                    documentsDebtId = null
                                    documentsOpen = true
                                },
                                onRestored = {"""
new = """                                onOpenDocuments = {
                                    settingsOpen = false
                                    documentsDebtId = null
                                    documentsOpen = true
                                },
                                onOpenStatistics = {
                                    startActivity(
                                        Intent(this@MainActivity, StatisticsActivity::class.java),
                                    )
                                },
                                onRestored = {"""
if old not in text:
    raise SystemExit("MainActivity settings route marker not found")
main.write_text(text.replace(old, new, 1))
