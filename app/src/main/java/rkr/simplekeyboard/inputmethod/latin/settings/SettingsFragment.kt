/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.util.Log
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.utils.ApplicationUtils

class SettingsFragment : InputMethodSettingsFragment() {
    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        setHasOptionsMenu(true)
        addPreferencesFromResource(R.xml.prefs)
        val preferenceScreen = preferenceScreen
        preferenceScreen.title = ApplicationUtils.getActivityTitleResId(activity, SettingsActivity::class.java)
            .let { getString(it) }
        val res = resources

        findPreference("privacy_policy")?.setOnPreferenceClickListener(
            Preference.OnPreferenceClickListener {
                openUrl(res.getString(R.string.privacy_policy_url))
                true
            }
        )
        findPreference("license")?.setOnPreferenceClickListener(
            Preference.OnPreferenceClickListener {
                openUrl(res.getString(R.string.license_url))
                true
            }
        )
    }

    private fun openUrl(uri: String?) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Browser not found")
        }
    }

    companion object {
        private const val TAG = "SettingsFragment"
    }
}
