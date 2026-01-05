package com.shortsblocker.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app preferences using SharedPreferences.
 */
object AppPreferences {
    private const val PREFS_NAME = "shortsblocker_prefs"
    private const val KEY_BLOCK_ACTION = "block_action"

    /**
     * Available actions when shorts/reels are detected.
     */
    enum class BlockAction(val value: Int) {
        BACK(0),      // Press back button (default)
        HOME(1),      // Go to home screen
        RECENTS(2);   // Open recent apps

        companion object {
            fun fromValue(value: Int): BlockAction {
                return entries.find { it.value == value } ?: BACK
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get the current block action preference.
     */
    fun getBlockAction(context: Context): BlockAction {
        val value = getPrefs(context).getInt(KEY_BLOCK_ACTION, BlockAction.BACK.value)
        return BlockAction.fromValue(value)
    }

    /**
     * Set the block action preference.
     */
    fun setBlockAction(context: Context, action: BlockAction) {
        getPrefs(context).edit()
            .putInt(KEY_BLOCK_ACTION, action.value)
            .apply()
    }
}
