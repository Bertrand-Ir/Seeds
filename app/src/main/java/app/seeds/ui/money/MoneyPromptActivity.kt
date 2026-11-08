package app.seeds.ui.money

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.seeds.seeds
import app.seeds.ui.theme.SeedsTheme
import kotlinx.coroutines.launch

/**
 * The money popup: floats over whatever you're doing the moment MoMo money moves,
 * asks what it was for, and gets out of the way. Walks through any other unlabeled
 * payments afterwards so nothing piles up.
 */
class MoneyPromptActivity : ComponentActivity() {
    private var txnId by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        txnId = intent.getLongExtra(EXTRA_TXN, -1)
        if (txnId <= 0) { finish(); return }
        setContent {
            val settings by seeds.settings.flow.collectAsStateWithLifecycle(null)
            val s = settings ?: return@setContent
            val scope = rememberCoroutineScope()
            SeedsTheme(s.theme) {
                Box(Modifier.fillMaxSize())
                key(txnId) {
                    TxnSheet(txnId, s, quick = true) {
                        // Labeled (or deleted) → offer the next unlabeled payment. Swiped away → "later", close.
                        scope.launch {
                            val cur = seeds.money.txn(txnId)
                            val handled = cur == null || cur.categoryId != null
                            val next = if (handled) seeds.money.unlabeledNow().firstOrNull { it.id != txnId } else null
                            if (next != null) txnId = next.id else finish()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getLongExtra(EXTRA_TXN, -1).takeIf { it > 0 }?.let { txnId = it }
    }

    companion object {
        const val EXTRA_TXN = "txn"

        fun intent(ctx: Context, txnId: Long): Intent =
            Intent(ctx, MoneyPromptActivity::class.java)
                .putExtra(EXTRA_TXN, txnId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Pop up right away. Android only allows this from the background with "Display over other apps". */
        fun tryShow(ctx: Context, txnId: Long): Boolean {
            if (!AndroidSettings.canDrawOverlays(ctx)) return false
            return runCatching { ctx.startActivity(intent(ctx, txnId)) }.isSuccess
        }
    }
}
