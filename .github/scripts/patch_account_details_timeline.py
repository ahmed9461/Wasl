from pathlib import Path

path = Path("app/src/main/java/com/wasl/app/AccountDetailsScreen.kt")
source = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    source = source.replace(old, new, 1)


replace_once(
    '                            Text("العملة: ${account.ledger.balance.currency.value}")',
    '                            Text("العملة: ${ltrIsolate(account.ledger.balance.currency.value)}")',
    "payment currency isolation",
)

replace_once(
'''            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("دفعة مسجلة", fontWeight = FontWeight.Bold)
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (isReversed) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Text(
                        text = if (isReversed) "معكوسة" else "فعّالة",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = if (isReversed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }''',
'''            PaymentTimelineStatusHeader(isReversed = isReversed)''',
    "payment timeline header",
)

replace_once(
    '                MetadataRow("إيصال السداد", receipt.documentNumber)',
    '                MetadataRow("إيصال السداد", ltrIsolate(receipt.documentNumber))',
    "receipt number isolation",
)

replace_once(
'''                        Row(
                            modifier = Modifier.align(Alignment.End),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TextButton(
                                onClick = { onShareReceipt(receipt) },
                                modifier = Modifier.testTag("share-receipt-${receipt.id}"),
                            ) {
                                Text("مشاركة")
                            }
                            TextButton(
                                onClick = { onOpenReceipt(receipt) },
                                modifier = Modifier.testTag("open-receipt-${receipt.id}"),
                            ) {
                                Text("فتح PDF")
                            }
                        }''',
'''                        AccountTimelineDualActions(
                            firstLabel = "مشاركة",
                            secondLabel = "فتح PDF",
                            firstTag = "share-receipt-${receipt.id}",
                            secondTag = "open-receipt-${receipt.id}",
                            onFirst = { onShareReceipt(receipt) },
                            onSecond = { onOpenReceipt(receipt) },
                        )''',
    "receipt dual actions",
)

replace_once(
'''            if (!isReversed) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (document == null) {
                        TextButton(
                            onClick = onIssueReceipt,
                            modifier = Modifier.testTag("issue-receipt-${payment.id.value}"),
                        ) {
                            Text("إصدار إيصال")
                        }
                    }
                    TextButton(
                        onClick = onReverse,
                        modifier = Modifier.testTag("reverse-payment-${payment.id.value}"),
                    ) {
                        Text("عكس الدفعة")
                    }
                }
            }''',
'''            if (!isReversed) {
                if (document == null) {
                    AccountTimelineDualActions(
                        firstLabel = "إصدار إيصال",
                        secondLabel = "عكس الدفعة",
                        firstTag = "issue-receipt-${payment.id.value}",
                        secondTag = "reverse-payment-${payment.id.value}",
                        onFirst = onIssueReceipt,
                        onSecond = onReverse,
                    )
                } else {
                    TextButton(
                        onClick = onReverse,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("reverse-payment-${payment.id.value}"),
                    ) {
                        Text("عكس الدفعة")
                    }
                }
            }''',
    "issue reverse actions",
)

replace_once(
'''@Composable
private fun DetailMoneyRow(
    label: String,
    money: Money,
    valueModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Text(
            text = formatMoney(money),
            modifier = valueModifier,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, textAlign = TextAlign.End)
    }
}''',
'''@Composable
private fun DetailMoneyRow(
    label: String,
    money: Money,
    valueModifier: Modifier = Modifier,
) {
    AdaptiveDetailMoneyRow(
        label = label,
        money = money,
        valueModifier = valueModifier,
    )
}

@Composable
private fun MetadataRow(label: String, value: String) {
    AdaptiveMetadataRow(label = label, value = value)
}''',
    "adaptive detail rows",
)

replace_once(
'''private fun formatInstant(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm", Locale.US)
    return "\\u2066${formatter.format(instant.atZone(ZoneId.systemDefault()))}\\u2069"
}

private fun formatLocalTime(time: LocalTime): String =
    "%02d:%02d".format(Locale.US, time.hour, time.minute)

private fun formatDate(date: LocalDate): String = "\\u2066$date\\u2069"''',
'''private fun formatInstant(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm", Locale.US)
    return ltrIsolate(formatter.format(instant.atZone(ZoneId.systemDefault())))
}

private fun formatLocalTime(time: LocalTime): String =
    ltrIsolate("%02d:%02d".format(Locale.US, time.hour, time.minute))

private fun formatDate(date: LocalDate): String = ltrIsolate(date.toString())''',
    "date time isolation",
)

replace_once(
'''    is AccountOperationNotice.PaymentReceiptIssuedNotice ->
        "تم تجهيز إيصال السداد $documentNumber وحفظه في سجل الدفعة."''',
'''    is AccountOperationNotice.PaymentReceiptIssuedNotice ->
        "تم تجهيز إيصال السداد ${ltrIsolate(documentNumber)} وحفظه في سجل الدفعة."''',
    "receipt notice isolation",
)

path.write_text(source, encoding="utf-8")
