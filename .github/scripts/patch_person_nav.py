from pathlib import Path

main = Path("app/src/main/java/com/wasl/app/MainActivity.kt")
text = main.read_text()
needle = """                    CompositionLocalProvider(
                        LocalOpenInstallmentsHub provides {"""
replacement = """                    CompositionLocalProvider(
                        LocalOpenPersonTimeline provides { personId ->
                            startActivity(
                                Intent(this@MainActivity, PersonTimelineActivity::class.java)
                                    .putExtra(PersonTimelineActivity.EXTRA_PERSON_ID, personId.value),
                            )
                        },
                        LocalOpenInstallmentsHub provides {"""
if needle not in text:
    raise SystemExit("MainActivity composition local marker not found")
main.write_text(text.replace(needle, replacement, 1))

screen = Path("app/src/main/java/com/wasl/app/AccountDetailsScreen.kt")
text = screen.read_text()
needle = """private fun AccountSummaryCard(
    account: AccountOverview,
    onOpenDueSchedule: () -> Unit,
) {
    val ledger = account.ledger"""
replacement = """private fun AccountSummaryCard(
    account: AccountOverview,
    onOpenDueSchedule: () -> Unit,
) {
    val openPersonTimeline = LocalOpenPersonTimeline.current
    val ledger = account.ledger"""
if needle not in text:
    raise SystemExit("AccountSummaryCard marker not found")
text = text.replace(needle, replacement, 1)
needle = """            if (!ledger.balance.isZero) {
                TextButton("""
replacement = """            OutlinedButton(
                onClick = { openPersonTimeline(account.person.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open-person-timeline"),
            ) {
                Text("صفحة الشخص والسجل الموحد")
            }

            if (!ledger.balance.isZero) {
                TextButton("""
if needle not in text:
    raise SystemExit("due schedule action marker not found")
screen.write_text(text.replace(needle, replacement, 1))
