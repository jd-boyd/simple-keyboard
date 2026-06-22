/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
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

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.AudioAndHapticFeedbackManager

/**
 * "Preferences" settings sub screen.
 * 
 * This settings sub screen handles the following input preferences.
 * - Vibrate on keypress
 * - Keypress vibration duration
 * - Sound on keypress
 * - Keypress sound volume
 * - Popup on keypress
 * - Key long press delay
 */
class KeyPressSettingsFragment : SubScreenFragment() {
    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_key_press)

        val context: Context? = getActivity()

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context)

        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            val pref = findPreference(Settings.PREF_VIBRATE_ON)
            if (pref != null) {
                getPreferenceScreen().removePreference(pref)
            }
        }

        setupKeypressSoundVolumeSettings()
        setupKeyLongpressTimeoutSettings()
    }

    private fun setupKeypressSoundVolumeSettings() {
        val pref = findPreference(
            Settings.PREF_KEYPRESS_SOUND_VOLUME
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
                return (floatValue * PERCENTAGE_FLOAT).toInt()
            }

            override fun writeValue(value: Int, key: String?) {
                prefs!!.edit().putFloat(key, getValueFromPercentage(value)).apply()
            }

            override fun writeDefaultValue(key: String?) {
                prefs!!.edit().remove(key).apply()
            }

            override fun readValue(key: String?): Int {
                return getPercentageFromValue(Settings.readKeypressSoundVolume(prefs!!))
            }

            override fun readDefaultValue(key: String?): Int {
                return getPercentageFromValue(Settings.readDefaultKeypressSoundVolume())
            }

            override fun getValueText(value: Int): String {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default)
                }
                return value.toString()
            }

            override fun feedbackValue(value: Int) {
                AudioAndHapticFeedbackManager.getInstance().playSoundEffect(
                    AudioManager.FX_KEYPRESS_STANDARD, getValueFromPercentage(value)
                )
            }
        })
    }

    private fun setupKeyLongpressTimeoutSettings() {
        val prefs = sharedPreferences
        val res = getResources()
        val pref = findPreference(
            Settings.PREF_KEY_LONGPRESS_TIMEOUT
        ) as SeekBarDialogPreference?
        if (pref == null) {
            return
        }
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String?) {
                prefs!!.edit().putInt(key, value).apply()
            }

            override fun writeDefaultValue(key: String?) {
                prefs!!.edit().remove(key).apply()
            }

            override fun readValue(key: String?): Int {
                return Settings.readKeyLongpressTimeout(prefs!!, res)
            }

            override fun readDefaultValue(key: String?): Int {
                return Settings.readDefaultKeyLongpressTimeout(res)
            }

            override fun getValueText(value: Int): String {
                return res.getString(R.string.abbreviation_unit_milliseconds, value)
            }

            override fun feedbackValue(value: Int) {}
        })
    }
}
