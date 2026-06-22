/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2024 wittmane
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
package rkr.simplekeyboard.inputmethod.latin.settings

import android.content.SharedPreferences
import android.os.Bundle
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardTheme.Companion.getKeyboardTheme

/**
 * "Appearance" settings sub screen.
 */
class AppearanceSettingsFragment : SubScreenFragment() {
    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_appearance)

        setupKeyboardHeightSettings()
        setupBottomOffsetPortraitSettings()
        setupKeyboardColorSettings()
    }

    override fun onResume() {
        super.onResume()

        ThemeSettingsFragment.updateKeyboardThemeSummary(findPreference(Settings.SCREEN_THEME))

        val colorPreference = findPreference(Settings.PREF_KEYBOARD_COLOR)
        if (colorPreference.isEnabled()) {
            val prefs = sharedPreferences
            val theme = KeyboardTheme.getKeyboardTheme(prefs!!)
            colorPreference.setEnabled(theme!!.mCustomColorSupport)
        }
    }

    public override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (KeyboardTheme.KEYBOARD_THEME_KEY == key) {
            ThemeSettingsFragment.updateKeyboardThemeSummary(findPreference(Settings.SCREEN_THEME))

            val theme = getKeyboardTheme(prefs)
            setPreferenceEnabled(Settings.PREF_KEYBOARD_COLOR, theme!!.mCustomColorSupport)
        }
    }

    private fun setupKeyboardHeightSettings() {
        val pref = findPreference(
            Settings.PREF_KEYBOARD_HEIGHT
        ) as SeekBarDialogPreference?
        if (pref == null) {
            return
        }
        val prefs = sharedPreferences
        val res = getResources()
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            private val PERCENTAGE_FLOAT = 100.0f

            fun getValueFromPercentage(percentage: Int): Float {
                return percentage / PERCENTAGE_FLOAT
            }

            fun getPercentageFromValue(floatValue: Float): Int {
                return Math.round(floatValue * PERCENTAGE_FLOAT)
            }

            override fun writeValue(value: Int, key: String?) {
                prefs!!.edit().putFloat(key, getValueFromPercentage(value)).apply()
            }

            override fun writeDefaultValue(key: String?) {
                prefs!!.edit().remove(key).apply()
            }

            override fun readValue(key: String?): Int {
                return getPercentageFromValue(Settings.readKeyboardHeight(prefs, 1f))
            }

            override fun readDefaultValue(key: String?): Int {
                return getPercentageFromValue(1f)
            }

            override fun getValueText(value: Int): String? {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default)
                }
                return res.getString(R.string.abbreviation_unit_percent, value)
            }

            override fun feedbackValue(value: Int) {}
        })
    }

    private fun setupBottomOffsetPortraitSettings() {
        val pref = findPreference(
            Settings.PREF_BOTTOM_OFFSET_PORTRAIT
        ) as SeekBarDialogPreference?
        if (pref == null) {
            return
        }
        val prefs = sharedPreferences
        val res = getResources()
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String?) {
                prefs!!.edit().putInt(key, value).apply()
            }

            override fun writeDefaultValue(key: String?) {
                prefs!!.edit().remove(key).apply()
            }

            override fun readValue(key: String?): Int {
                return Settings.readBottomOffsetPortrait(prefs)
            }

            override fun readDefaultValue(key: String?): Int {
                return Settings.DEFAULT_BOTTOM_OFFSET
            }

            override fun getValueText(value: Int): String? {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default)
                }
                return res.getString(R.string.abbreviation_unit_dp, value)
            }

            override fun feedbackValue(value: Int) {}
        })
    }

    private fun setupKeyboardColorSettings() {
        val pref = findPreference(
            Settings.PREF_KEYBOARD_COLOR
        ) as ColorDialogPreference?
        if (pref == null) {
            return
        }
        val prefs = sharedPreferences
        val context = this.getActivity().getApplicationContext()
        pref.setInterface(object : ColorDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String?) {
                prefs!!.edit().putInt(key, value).apply()
            }

            override fun readValue(key: String?): Int {
                return Settings.readKeyboardColor(prefs, context)
            }

            override fun writeDefaultValue(key: String?) {
                Settings.removeKeyboardColor(prefs)
            }
        })
    }
}
