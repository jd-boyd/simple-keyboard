/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2021 wittmane
 * Copyright (C) 2017 Raimondas Rimkus
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
package rkr.simplekeyboard.inputmethod.latin.utils

import rkr.simplekeyboard.inputmethod.latin.settings.AppearanceSettingsFragment
import rkr.simplekeyboard.inputmethod.latin.settings.KeyPressSettingsFragment
import rkr.simplekeyboard.inputmethod.latin.settings.LanguagesSettingsFragment
import rkr.simplekeyboard.inputmethod.latin.settings.PreferencesSettingsFragment
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsFragment
import rkr.simplekeyboard.inputmethod.latin.settings.SingleLanguageSettingsFragment
import rkr.simplekeyboard.inputmethod.latin.settings.ThemeSettingsFragment

object FragmentUtils {
    private val sLatinImeFragments = HashSet<String?>()

    init {
        sLatinImeFragments.add(PreferencesSettingsFragment::class.java.getName())
        sLatinImeFragments.add(KeyPressSettingsFragment::class.java.getName())
        sLatinImeFragments.add(AppearanceSettingsFragment::class.java.getName())
        sLatinImeFragments.add(ThemeSettingsFragment::class.java.getName())
        sLatinImeFragments.add(SettingsFragment::class.java.getName())
        sLatinImeFragments.add(LanguagesSettingsFragment::class.java.getName())
        sLatinImeFragments.add(SingleLanguageSettingsFragment::class.java.getName())
    }

    @JvmStatic
    fun isValidFragment(fragmentName: String?): Boolean {
        return sLatinImeFragments.contains(fragmentName)
    }
}
