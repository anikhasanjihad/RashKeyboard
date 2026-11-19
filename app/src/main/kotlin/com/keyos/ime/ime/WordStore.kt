package com.keyos.ime.ime

import android.content.Context
import com.keyos.engine.WordPredictor

/** Persists the user's learned words in app-private storage. Never leaves the device. */
object WordStore {
    private const val FILE = "keyos_words"
    private const val KEY = "user_words"

    fun load(context: Context, predictor: WordPredictor) {
        val data = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        predictor.importUser(data)
    }

    fun save(context: Context, predictor: WordPredictor) {
        if (!predictor.dirty) return
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, predictor.exportUser()).apply()
        predictor.markSaved()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
