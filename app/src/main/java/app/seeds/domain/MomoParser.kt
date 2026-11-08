package app.seeds.domain

import app.seeds.data.db.Direction

data class ParsedMoney(
    val amount: Long,
    val direction: Direction,
    val fee: Long,
    val balanceAfter: Long?,
    val counterparty: String?,
    val txId: String?,
)

/**
 * Heuristic parser for Mobile Money SMS (MTN MoMo / Airtel Money, Rwanda and similar).
 * It looks for an amount + currency, decides direction from the earliest money keyword,
 * then picks out fee, new balance, counterparty and transaction id when present.
 */
object MomoParser {
    private const val NUM = """(\d{1,3}(?:[,\s]\d{3})+|\d+)(?:\.\d+)?"""
    private val amountRx = Regex("""(?i)(?:(?:RWF|FRW|Rwf)\s*$NUM|$NUM\s*(?:RWF|FRW|Rwf))""")
    private val feeRx = Regex("""(?i)fee[s]?\s*(?:was|paid|of|is)?\s*:?\s*(?:RWF|FRW)?\s*$NUM""")
    private val balanceRx = Regex("""(?i)(?:new\s+balance|balance\s+is|bal(?:ance)?)\s*(?:is)?\s*:?\s*(?:RWF|FRW)?\s*$NUM""")
    private val txRx = Regex("""(?i)(?:Financial\s+Transaction\s+Id|Transaction\s+Id|TxId|Trans(?:action)?\s*ID|Ref(?:erence)?)\s*[:.]?\s*([A-Za-z0-9]{5,})""")
    private val fromRx = Regex("""(?i)\bfrom\s+([A-Za-z][A-Za-z0-9 .'&-]{1,40}?)\s*(?:\(|\bon\b|\bat\b|\.|,|\d{6,}|$)""")
    private val toRx = Regex("""(?i)\bto\s+([A-Za-z0-9][A-Za-z0-9 .'&-]{1,40}?)\s*(?:\(|\bwas\b|\bon\b|\bat\b|\bhas\b|\bwith\b|\.|,|\d{6,}|$)""")

    private val outWords = listOf(
        "payment of", "you have paid", "paid", "transferred to", "transfer to", "you have sent", "sent to", "sent",
        "withdrawn", "withdrawal", "cash out", "debited", "purchase", "bought", "airtime", "bundle",
    )
    private val inWords = listOf(
        "you have received", "received", "deposit", "deposited", "credited", "cash in", "refund", "reversed",
    )
    private val ignoreRx = Regex("""(?i)\b(failed|insufficient|not successful|unsuccessful|otp|verification code|one[- ]time)\b""")

    fun looksLikeMoney(body: String): Boolean = amountRx.containsMatchIn(body)

    fun parse(body: String): ParsedMoney? {
        val text = body.replace('\n', ' ')
        val lower = text.lowercase()
        if (ignoreRx.containsMatchIn(text)) return null

        val amount = amountRx.find(text)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toAmount() } ?: return null
        if (amount <= 0) return null

        val outIdx = outWords.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val inIdx = inWords.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val direction = when {
            outIdx == null && inIdx == null -> return null
            inIdx == null -> Direction.OUT
            outIdx == null -> Direction.IN
            inIdx < outIdx -> Direction.IN
            else -> Direction.OUT
        }

        val fee = feeRx.find(text)?.groupValues?.get(1)?.toAmount() ?: 0L
        val balance = balanceRx.findAll(text).lastOrNull()?.groupValues?.get(1)?.toAmount()
        val who = (if (direction == Direction.IN) fromRx else toRx).find(text)?.groupValues?.get(1)
            ?.trim()?.trimEnd('.', '-', ' ')
            ?.takeIf { it.length >= 2 && !it.equals("your", true) && !it.startsWith("your ", true) }
            ?.let(::titleCase)
        val tx = txRx.find(text)?.groupValues?.get(1)
        return ParsedMoney(amount, direction, fee, balance, who, tx)
    }

    private fun String.toAmount(): Long? = replace(",", "").replace(" ", "").substringBefore('.').toLongOrNull()

    private fun titleCase(s: String): String =
        s.lowercase().split(' ').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
