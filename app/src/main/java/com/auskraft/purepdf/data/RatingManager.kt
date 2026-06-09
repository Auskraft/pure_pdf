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
 * The app isn't published yet, so [STORE_URL] is empty and [hasStore] is false — high ratings
 * just thank the user. Set [STORE_URL] to the listing once published.
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

    val hasStore: Boolean get() = STORE_URL.isNotBlank()

    /** 4–5 stars → open the store listing (once a [STORE_URL] is set). */
    fun openStore() {
        if (STORE_URL.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(STORE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
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
        /** Set to the store listing URL once the app is published. */
        const val STORE_URL = ""
        const val FEEDBACK_EMAIL = "auskraft@gmail.com"
    }
}
