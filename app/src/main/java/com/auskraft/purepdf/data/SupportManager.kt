package com.auskraft.purepdf.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.supportDataStore by preferencesDataStore(name = "support")

/** Voluntary support links and prompt history, kept separate from document and app settings. */
class SupportManager(private val context: Context) {
    private object Keys {
        val FIRST_SEEN = longPreferencesKey("first_seen_v1")
        val LAST_SHOWN = longPreferencesKey("last_shown_v1")
        val PAYMENT_OPENED = booleanPreferencesKey("payment_opened_v1")
    }

    suspend fun shouldPrompt(now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.supportDataStore.data.first()
        if (prefs[Keys.PAYMENT_OPENED] == true) return false
        val first = prefs[Keys.FIRST_SEEN]
        if (first == null) {
            context.supportDataStore.edit { if (it[Keys.FIRST_SEEN] == null) it[Keys.FIRST_SEEN] = now }
            return false
        }
        return promptDue(first, prefs[Keys.LAST_SHOWN], now)
    }

    suspend fun markShown(now: Long = System.currentTimeMillis()) {
        context.supportDataStore.edit { it[Keys.LAST_SHOWN] = now }
    }

    /** A browser handoff is known; the result of a payment is not. */
    suspend fun markPaymentOpened() {
        context.supportDataStore.edit { it[Keys.PAYMENT_OPENED] = true }
    }

    fun openLink(uri: Uri): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    companion object {
        const val PAYMENT_URL = "https://auth.robokassa.ru/merchant/Invoice/_sAbttk5SUaBCsIUUO9iPg"
        const val TERMS_URL = "https://auskraft.github.io/apps-support/offer.html"
        const val ABOUT_URL = "https://auskraft.github.io/apps-support/"
        const val PROMPT_INTERVAL_MS = 14L * 24 * 60 * 60 * 1000

        internal fun promptDue(first: Long, last: Long?, now: Long): Boolean =
            now >= first && now - first >= PROMPT_INTERVAL_MS &&
                (last == null || (now >= last && now - last >= PROMPT_INTERVAL_MS))
    }
}
