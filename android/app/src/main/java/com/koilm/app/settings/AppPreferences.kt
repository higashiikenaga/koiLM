package com.koilm.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "koilm_prefs")

/**
 * 年齢確認済みフラグと表現レベル設定を端末内(DataStore/内部ストレージ)にのみ保存する。
 * サーバー通信・外部送信は一切行わない完全ローカルの年齢確認・コンテンツレベル管理。
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        val AGE_VERIFIED = booleanPreferencesKey("age_verified")
        val CONTENT_LEVEL = stringPreferencesKey("content_level")
        val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
        val MIN_INTERVAL_HOURS = intPreferencesKey("min_interval_hours")
        val MAX_INTERVAL_HOURS = intPreferencesKey("max_interval_hours")
        val REPLY_MODE = stringPreferencesKey("reply_mode")
        val ROMANTIC_TARGET = stringPreferencesKey("romantic_target")
        val ROMANTIC_TARGET_SELECTED = booleanPreferencesKey("romantic_target_selected")
        val AFFECTION_SYSTEM_ENABLED = booleanPreferencesKey("affection_system_enabled")
        val BUSY_HOURS_START = intPreferencesKey("busy_hours_start")
        val BUSY_HOURS_END = intPreferencesKey("busy_hours_end")
    }

    companion object {
        // 既定値: 23時〜7時は自発メッセージを送らない、2〜6時間おきのランダムなタイミングで送る。
        const val DEFAULT_QUIET_HOURS_START = 23
        const val DEFAULT_QUIET_HOURS_END = 7
        const val DEFAULT_MIN_INTERVAL_HOURS = 2
        const val DEFAULT_MAX_INTERVAL_HOURS = 6

        // 既定値: 9時〜18時は「仕事中」扱いとし、既読すら付かない(タイミングモードの通知が
        // その時間帯終了までまとめて遅延する)。
        const val DEFAULT_BUSY_HOURS_START = 9
        const val DEFAULT_BUSY_HOURS_END = 18
    }

    /** 自発メッセージを送らない時間帯の開始時刻(0-23時)。日をまたぐ場合(例: 23時〜7時)も指定可能。 */
    val quietHoursStart: Flow<Int> =
        context.dataStore.data.map { it[Keys.QUIET_HOURS_START] ?: DEFAULT_QUIET_HOURS_START }

    /** 自発メッセージを送らない時間帯の終了時刻(0-23時)。 */
    val quietHoursEnd: Flow<Int> =
        context.dataStore.data.map { it[Keys.QUIET_HOURS_END] ?: DEFAULT_QUIET_HOURS_END }

    /** 自発メッセージの最短間隔(時間)。 */
    val minIntervalHours: Flow<Int> =
        context.dataStore.data.map { it[Keys.MIN_INTERVAL_HOURS] ?: DEFAULT_MIN_INTERVAL_HOURS }

    /** 自発メッセージの最長間隔(時間)。 */
    val maxIntervalHours: Flow<Int> =
        context.dataStore.data.map { it[Keys.MAX_INTERVAL_HOURS] ?: DEFAULT_MAX_INTERVAL_HOURS }

    suspend fun setNotificationTiming(
        quietHoursStart: Int,
        quietHoursEnd: Int,
        minIntervalHours: Int,
        maxIntervalHours: Int,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QUIET_HOURS_START] = quietHoursStart.coerceIn(0, 23)
            prefs[Keys.QUIET_HOURS_END] = quietHoursEnd.coerceIn(0, 23)
            val safeMin = minIntervalHours.coerceAtLeast(1)
            prefs[Keys.MIN_INTERVAL_HOURS] = safeMin
            prefs[Keys.MAX_INTERVAL_HOURS] = maxIntervalHours.coerceAtLeast(safeMin)
        }
    }

    /** 「即返信」か「タイミング(最大15分以内にランダムで返信)」か。 */
    val replyMode: Flow<ReplyMode> =
        context.dataStore.data.map { ReplyMode.fromName(it[Keys.REPLY_MODE]) }

    suspend fun setReplyMode(mode: ReplyMode) {
        context.dataStore.edit { prefs -> prefs[Keys.REPLY_MODE] = mode.name }
    }

    /** 起動時の「恋愛対象」選択がまだ完了していないか。 */
    val isRomanticTargetSelected: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ROMANTIC_TARGET_SELECTED] ?: false }

    val romanticTarget: Flow<RomanticTarget> =
        context.dataStore.data.map { RomanticTarget.fromName(it[Keys.ROMANTIC_TARGET]) }

    suspend fun setRomanticTarget(target: RomanticTarget) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROMANTIC_TARGET] = target.name
            prefs[Keys.ROMANTIC_TARGET_SELECTED] = true
        }
    }

    /** 好感度システム(既読タイミング・返信間隔の演出)全体のON/OFF。既定はON。 */
    val isAffectionSystemEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AFFECTION_SYSTEM_ENABLED] ?: true }

    suspend fun setAffectionSystemEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AFFECTION_SYSTEM_ENABLED] = enabled }
    }

    /** 「仕事中」等でキャラが既読すら付けられない時間帯の開始時刻(0-23時)。 */
    val busyHoursStart: Flow<Int> =
        context.dataStore.data.map { it[Keys.BUSY_HOURS_START] ?: DEFAULT_BUSY_HOURS_START }

    /** 同終了時刻。 */
    val busyHoursEnd: Flow<Int> =
        context.dataStore.data.map { it[Keys.BUSY_HOURS_END] ?: DEFAULT_BUSY_HOURS_END }

    suspend fun setBusyHours(start: Int, end: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BUSY_HOURS_START] = start.coerceIn(0, 23)
            prefs[Keys.BUSY_HOURS_END] = end.coerceIn(0, 23)
        }
    }

    val isAgeVerified: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AGE_VERIFIED] ?: false }

    val contentLevel: Flow<ContentLevel> =
        context.dataStore.data.map { ContentLevel.fromName(it[Keys.CONTENT_LEVEL]) }

    /** 年齢確認ダイアログでの回答を記録する。18歳未満の回答の場合はNSFWを強制的に解除する。 */
    suspend fun setAgeVerified(isAdult: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AGE_VERIFIED] = isAdult
            if (!isAdult) {
                prefs[Keys.CONTENT_LEVEL] = ContentLevel.NORMAL.name
            }
        }
    }

    /**
     * ユーザーが選択した表現レベルを保存する。
     * NSFWは年齢確認未完了の場合は保存を拒否し、SFWにフォールバックする(防御的チェック)。
     */
    suspend fun setContentLevel(level: ContentLevel) {
        context.dataStore.edit { prefs ->
            val verified = prefs[Keys.AGE_VERIFIED] ?: false
            val safeLevel = if (level.requiresAgeVerification && !verified) ContentLevel.SFW else level
            prefs[Keys.CONTENT_LEVEL] = safeLevel.name
        }
    }
}
