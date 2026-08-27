package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.domain.DebtDirection
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PaymentMessageTone {
    GENTLE,
    STANDARD,
    FORMAL,
}

data class PaymentMessageDraft(
    val tone: PaymentMessageTone,
    val title: String,
    val body: String,
)

internal object PaymentMessageTemplates {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US)

    fun forAccount(account: AccountOverview): List<PaymentMessageDraft> {
        require(account.ledger.header.direction == DebtDirection.RECEIVABLE) {
            "Payment request messages are only available for receivable accounts."
        }
        val personName = account.person.displayName.trim()
        val remaining = formatMessageMoney(account.ledger.balance)
        val dueText = account.ledger.header.dueDate?.let { dateFormatter.format(it) }
        val duePhrase = dueText?.let { "، وموعد الاستحقاق المسجل هو $it" }.orEmpty()

        return listOf(
            PaymentMessageDraft(
                tone = PaymentMessageTone.GENTLE,
                title = "لطيف",
                body = "مرحبًا $personName، تذكير بسيط بأن المتبقي في حسابنا هو $remaining$duePhrase. متى ما تيسر لك السداد أكون شاكرًا لك.",
            ),
            PaymentMessageDraft(
                tone = PaymentMessageTone.STANDARD,
                title = "عادي",
                body = "مرحبًا $personName، المتبقي في الحساب هو $remaining$duePhrase. فضلاً أفدني بموعد مناسب للسداد، وشكرًا لك.",
            ),
            PaymentMessageDraft(
                tone = PaymentMessageTone.FORMAL,
                title = "رسمي",
                body = "السلام عليكم $personName، نود تذكيركم بأن الرصيد المتبقي في الحساب هو $remaining$duePhrase. نرجو التكرم بتحديد موعد السداد المناسب. مع الشكر والتقدير.",
            ),
        )
    }

    private fun formatMessageMoney(money: Money): String {
        val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
        val major = BigDecimal.valueOf(money.minorUnits, fractionDigits)
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            isGroupingUsed = true
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
        return "${formatter.format(major)} ${money.currency.value}"
    }
}
