/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2024 Raimondas Rimkus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cuetime.cuetimekeyboard.inputmethod.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.cuetime.cuetimekeyboard.inputmethod.R
import io.cuetime.cuetimekeyboard.inputmethod.compat.PreferenceManagerCompat.getDeviceSharedPreferences
import io.cuetime.cuetimekeyboard.inputmethod.latin.settings.Settings

class KeyboardTheme // Note: The themeId should be aligned with "themeId" attribute of Keyboard style
// in values/themes-<style>.xml.
    private constructor(
        @JvmField val mThemeId: Int,
        val mThemeName: String?,
        @JvmField val mStyleId: Int,
        @JvmField val mCustomColorSupport: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            return (other is KeyboardTheme) && other.mThemeId == mThemeId
        }

        override fun hashCode(): Int = mThemeId

        companion object {
            private val TAG: String = KeyboardTheme::class.java.getSimpleName()

            const val KEYBOARD_THEME_KEY: String = "pref_keyboard_theme_20140509"

        /*
         * These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
         * attributes' values in attrs.xml.
         */
            const val THEME_ID_LIGHT_BORDER: Int = 1
            const val THEME_ID_DARK_BORDER: Int = 2
            const val THEME_ID_LIGHT: Int = 3
            const val THEME_ID_DARK: Int = 4
            const val THEME_ID_SYSTEM: Int = 5
            const val THEME_ID_SYSTEM_BORDER: Int = 6
            const val DEFAULT_THEME_ID: Int = THEME_ID_SYSTEM

            // package private for testing
            val KEYBOARD_THEMES: Array<KeyboardTheme> =
                arrayOf(
                    KeyboardTheme(
                        THEME_ID_SYSTEM,
                        "LXXSystem",
                        R.style.KeyboardTheme_LXX_System,
                        false,
                    ),
                    KeyboardTheme(
                        THEME_ID_SYSTEM_BORDER,
                        "LXXSystemBorder",
                        R.style.KeyboardTheme_LXX_System_Border,
                        false,
                    ),
                    KeyboardTheme(THEME_ID_LIGHT, "LXXLight", R.style.KeyboardTheme_LXX_Light, true),
                    KeyboardTheme(THEME_ID_DARK, "LXXDark", R.style.KeyboardTheme_LXX_Dark, true),
                    KeyboardTheme(
                        THEME_ID_LIGHT_BORDER,
                        "LXXLightBorder",
                        R.style.KeyboardTheme_LXX_Light_Border,
                        true,
                    ),
                    KeyboardTheme(
                        THEME_ID_DARK_BORDER,
                        "LXXDarkBorder",
                        R.style.KeyboardTheme_LXX_Dark_Border,
                        true,
                    ),
                )

            // package private for testing
            fun searchKeyboardThemeById(themeId: Int): KeyboardTheme? {
                // TODO: This search algorithm isn't optimal if there are many themes.
                for (theme in KEYBOARD_THEMES) {
                    if (theme.mThemeId == themeId) {
                        return theme
                    }
                }
                return null
            }

            val defaultKeyboardTheme: KeyboardTheme?
                // package private for testing
                get() = searchKeyboardThemeById(DEFAULT_THEME_ID)

            @JvmStatic
            fun getKeyboardThemeName(themeId: Int): String? {
                val theme: KeyboardTheme? = searchKeyboardThemeById(themeId)
                return theme!!.mThemeName
            }

            @JvmStatic
            fun saveKeyboardThemeId(
                themeId: Int,
                prefs: SharedPreferences,
            ) {
                prefs.edit().putString(KEYBOARD_THEME_KEY, themeId.toString()).apply()
            }

            @JvmStatic
            fun getKeyboardTheme(context: Context): KeyboardTheme? {
                val prefs: SharedPreferences = checkNotNull(getDeviceSharedPreferences(context))
                return getKeyboardTheme(prefs)
            }

            @JvmStatic
            fun getKeyboardTheme(prefs: SharedPreferences): KeyboardTheme? {
                val themeIdString =
                    prefs.getString(KEYBOARD_THEME_KEY, null) ?: return searchKeyboardThemeById(
                        DEFAULT_THEME_ID,
                    )
                try {
                    val themeId = themeIdString.toInt()
                    val theme: KeyboardTheme? = searchKeyboardThemeById(themeId)
                    if (theme != null) {
                        return theme
                    }
                    Log.w(TAG, "Unknown keyboard theme in preference: $themeIdString")
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "Illegal keyboard theme in preference: $themeIdString", e)
                }
                // Remove preference that contains unknown or illegal theme id.
                prefs
                    .edit()
                    .remove(KEYBOARD_THEME_KEY)
                    .remove(Settings.PREF_KEYBOARD_COLOR)
                    .apply()
                return defaultKeyboardTheme
            }
        }
    }
