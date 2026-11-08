package app.seeds.money

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.seeds.data.repo.MoneyRepository
import app.seeds.domain.MomoParser
import app.seeds.notify.Notifier
import app.seeds.seeds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Picks up Mobile Money confirmations and asks what the money was for. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        // Multi-part SMS arrive as several PDUs from the same sender.
        val sender = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val at = messages.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val c = context.seeds
                val s = c.settings.current()
                if (!s.moneyEnabled) return@launch
                if (!MoneyRepository.isMoneySender(sender, s.momoSenders) || !MomoParser.looksLikeMoney(body)) return@launch
                val txn = c.money.recordSms(body, at) ?: return@launch
                Notifier.money(context, txn, s.currency, c.money.suggestions(txn))
            } finally {
                pending.finish()
            }
        }
    }
}
