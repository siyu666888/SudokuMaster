package com.example.sudokumaster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.sudokumaster.domain.model.AppSettings
import com.example.sudokumaster.domain.model.FontScale
import com.example.sudokumaster.domain.model.HighlightColor
import com.example.sudokumaster.domain.model.HintMode
import com.example.sudokumaster.domain.model.Palette
import com.example.sudokumaster.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "sudoku_settings")

/** 每日提示次数上限（提示道具：每日重置）。 */
const val DAILY_HINT_LIMIT = 20

class SettingsDataStore(private val context: Context) {

    private val ds: DataStore<Preferences> = context.dataStore

    val settings: Flow<AppSettings> = ds.data.map { mapPrefs(it) }

    /** 今日剩余提示次数（跨天自动归零）。 */
    val dailyHintsLeft: Flow<Int> = ds.data.map { p ->
        val today = todayStr()
        val used = if ((p[KEY_HINT_DATE] ?: "") == today) (p[KEY_HINT_USED] ?: 0) else 0
        (DAILY_HINT_LIMIT - used).coerceAtLeast(0)
    }

    /** 消耗一次提示；返回是否成功（当日额度内）。 */
    suspend fun consumeHint(): Boolean {
        var ok = false
        ds.edit { p ->
            val today = todayStr()
            val used = if ((p[KEY_HINT_DATE] ?: "") == today) (p[KEY_HINT_USED] ?: 0) else 0
            if (used < DAILY_HINT_LIMIT) {
                p[KEY_HINT_DATE] = today
                p[KEY_HINT_USED] = used + 1
                ok = true
            }
        }
        return ok
    }

    suspend fun update(transform: AppSettings.() -> AppSettings) {
        ds.edit { prefs ->
            val next = mapPrefs(prefs).transform()
            writePrefs(prefs, next)
        }
    }

    suspend fun get(): AppSettings = settings.first()

    private fun mapPrefs(p: Preferences): AppSettings = AppSettings(
        theme = ThemeMode.valueOf(p[KEY_THEME] ?: "SYSTEM"),
        highlightColor = HighlightColor.valueOf(p[KEY_HL] ?: "BLUE"),
        fontScale = FontScale.valueOf(p[KEY_FONT] ?: "STANDARD"),
        palette = Palette.valueOf(p[KEY_PALETTE] ?: "BLUE"),
        longPressNote = p[KEY_LONG] ?: true,
        hintMode = HintMode.valueOf(p[KEY_HINT] ?: "FILL"),
        autoNote = p[KEY_AUTO] ?: true,
        fastGen = p[KEY_FAST] ?: false,
        showError = p[KEY_ERR] ?: true,
        highlightSame = p[KEY_SAME] ?: true,
        autoExclude = p[KEY_AUTOEX] ?: false,
        vibrate = p[KEY_VIB] ?: true,
        sound = p[KEY_SND] ?: true,
        selectedSizeId = p[KEY_SIZE] ?: "9"
    )

    private fun writePrefs(p: MutablePreferences, s: AppSettings) {
        p[KEY_THEME] = s.theme.name
        p[KEY_HL] = s.highlightColor.name
        p[KEY_FONT] = s.fontScale.name
        p[KEY_PALETTE] = s.palette.name
        p[KEY_LONG] = s.longPressNote
        p[KEY_HINT] = s.hintMode.name
        p[KEY_AUTO] = s.autoNote
        p[KEY_FAST] = s.fastGen
        p[KEY_ERR] = s.showError
        p[KEY_SAME] = s.highlightSame
        p[KEY_AUTOEX] = s.autoExclude
        p[KEY_VIB] = s.vibrate
        p[KEY_SND] = s.sound
        p[KEY_SIZE] = s.selectedSizeId
    }

    private fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_HL = stringPreferencesKey("highlight_color")
        private val KEY_FONT = stringPreferencesKey("font_scale")
        private val KEY_PALETTE = stringPreferencesKey("palette")
        private val KEY_LONG = booleanPreferencesKey("long_press_note")
        private val KEY_HINT = stringPreferencesKey("hint_mode")
        private val KEY_AUTO = booleanPreferencesKey("auto_note")
        private val KEY_FAST = booleanPreferencesKey("fast_gen")
        private val KEY_ERR = booleanPreferencesKey("show_error")
        private val KEY_SAME = booleanPreferencesKey("highlight_same")
        private val KEY_AUTOEX = booleanPreferencesKey("auto_exclude")
        private val KEY_VIB = booleanPreferencesKey("vibrate")
        private val KEY_SND = booleanPreferencesKey("sound")
        private val KEY_SIZE = stringPreferencesKey("selected_size")
        private val KEY_HINT_DATE = stringPreferencesKey("hint_date")
        private val KEY_HINT_USED = intPreferencesKey("hint_used")
    }
}
