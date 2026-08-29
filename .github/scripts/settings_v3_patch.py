from pathlib import Path

p = Path('app/src/main/java/com/wasl/app/SettingsHubScreen.kt')
s = p.read_text()

s = s.replace('.padding(PaddingValues(horizontal = 20.dp, vertical = 18.dp)),\n            verticalArrangement = Arrangement.spacedBy(14.dp),', '.padding(PaddingValues(horizontal = 18.dp, vertical = 12.dp)),\n            verticalArrangement = Arrangement.spacedBy(10.dp),', 1)

old_header = '''@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-header-stacked"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text("رجوع") }
                SettingsHeaderText()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-header-inline"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onBack) { Text("رجوع") }
                SettingsHeaderText(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsHeaderText(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "الإعدادات",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "المظهر، الأمان، التذكيرات والنسخ الاحتياطي",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
'''
new_header = '''@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (shouldStackDenseRows(maxWidth)) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("settings-header-stacked"),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                SettingsHeaderText()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("settings-header-inline"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                SettingsHeaderText(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsHeaderText(modifier: Modifier = Modifier) {
    Text(
        text = "الإعدادات",
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
    )
}
'''
assert old_header in s
s = s.replace(old_header, new_header, 1)

old_card = '''@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
'''
new_card = '''@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
'''
assert old_card in s
s = s.replace(old_card, new_card, 1)

# Make setting rows feel like compact native preference rows.
s = s.replace('horizontalArrangement = Arrangement.spacedBy(12.dp),\n        ) {\n            Column(modifier = Modifier.weight(1f)) {', 'horizontalArrangement = Arrangement.spacedBy(8.dp),\n        ) {\n            Column(modifier = Modifier.weight(1f)) {', 1)
s = s.replace('style = MaterialTheme.typography.titleMedium,\n                    color = MaterialTheme.colorScheme.onSurface,', 'style = MaterialTheme.typography.bodyLarge,\n                    fontWeight = FontWeight.SemiBold,\n                    color = MaterialTheme.colorScheme.onSurface,', 1)

p.write_text(s)
