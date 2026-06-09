package com.auskraft.purepdf.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.ratingDataStore: DataStore<Preferences> by preferencesDataStore(name = "rating")

/**
 * Tracks launch count and whether the user has rated, and opens the store / feedback email.
 *
 * PLACEHOLDER: the app isn't published yet — [STORE_URL] points at a not-yet-live Play listing
 * and [FEEDBACK_EMAIL] is a placeholder. Replace both before release.
 */
class RatingManager(private val context: Context) {

    private object Keys {
        val LAUNCHES = intPreferencesKey("launch_count")
        val PROMPTED = booleanPreferencesKey("prompted")
        val DONE = booleanPreferencesKey("done")
    }

    suspend fun registerLaunch() {
        context.ratingDataStore.edit { it[Keys.LAUNCHES] = (it[Keys.LAUNCHES] ?: 0) + 1 }
    }

    /** Auto-prompt once, only after a few launches, and never again once rated. */
    suspend fun shouldAutoPrompt(): Boolean {
        val prefs = context.ratingDataStore.data.first()
        val done = prefs[Keys.DONE] ?: false
        val prompted = prefs[Keys.PROMPTED] ?: false
        val launches = prefs[Keys.LAUNCHES] ?: 0
        return !done && !prompted && launches >= PROMPT_AFTER_LAUNCHES
    }

    suspend fun markPrompted() = context.ratingDataStore.edit { it[Keys.PROMPTED] = true }.let { }

    suspend fun markDone() = context.ratingDataStore.edit { it[Keys.DONE] = true }.let { }

    /** 4–5 stars → open the store listing (market:// with a web fallback). */
    fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(STORE_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.recoverCatching { context.startActivity(web) }
    }

    /** 1–3 stars → open a feedback email rather than sending an unhappy user to the store. */
    fun openFeedbackEmail() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Pure PDF — отзыв")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    companion object {
        const val PROMPT_AFTER_LAUNCHES = 3
        // TODO: replace with the real store listing + support address once published.
        const val STORE_URL = "https://play.google.com/store/apps/details?id=com.auskraft.purepdf"
        const val FEEDBACK_EMAIL = "auskraft@gmail.com"
    }
}
