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

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardLayoutSet

/*
 * "Preferences" settings sub screen.
 *
 * This settings sub screen handles the following input preferences.
 * - Auto-capitalization
 * - Show separate number row
 * - Show special characters
 * - Show language switch key
 * - Show on-screen keyboard
 * - Switch to other keyboards
 * - Space swipe cursor move
 * - Delete swipe
 */
class PreferencesSettingsFragment : SubScreenFragment() {
    @Deprecated("Deprecated in Java")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs_screen_preferences)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            val pref = findPreference(Settings.PREF_USE_ON_SCREEN)
            if (pref != null) {
                preferenceScreen.removePreference(pref)
            }
        }
    }

    override fun onSharedPreferenceChanged(
        prefs: SharedPreferences?,
        key: String?,
    ) {
        if (key == Settings.PREF_SHOW_SPECIAL_CHARS ||
            key == Settings.PREF_SHOW_NUMBER_ROW
        ) {
            KeyboardLayoutSet.onKeyboardThemeChanged()
        }
    }
}
