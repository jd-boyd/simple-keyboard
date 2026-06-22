/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2020 Raimondas Rimkus
 * Copyright (C) 2026 Joshua D. Boyd
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
package io.cuetime.cuetimekeyboard.inputmethod.latin.settings

import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.preference.PreferenceScreen
import android.util.Log
import io.cuetime.cuetimekeyboard.inputmethod.compat.PreferenceManagerCompat.getDeviceSharedPreferences

/**
 * A base abstract class for a [PreferenceFragment] that implements a nested
 * [PreferenceScreen] of the main preference screen.
 */
abstract class SubScreenFragment :
    PreferenceFragment(),
    OnSharedPreferenceChangeListener {
    private var mSharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null

    fun setPreferenceEnabled(
        prefKey: String?,
        enabled: Boolean,
    ) {
        setPreferenceEnabled(prefKey, enabled, getPreferenceScreen())
    }

    val sharedPreferences: SharedPreferences?
        get() = getDeviceSharedPreferences(getActivity())

    override fun addPreferencesFromResource(preferencesResId: Int) {
        super.addPreferencesFromResource(preferencesResId)

        val restrictionKeys =
            this.sharedPreferences!!.getStringSet(Settings.ACTIVE_RESTRICTIONS, null)
        if (restrictionKeys != null && !restrictionKeys.isEmpty()) {
            val group: PreferenceGroup = getPreferenceScreen()
            val count = group.preferenceCount
            for (index in 0 until count) {
                val preference = group.getPreference(index)
                if (restrictionKeys.contains(preference.key)) {
                    preference.isEnabled = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.setStorageDeviceProtected()

        mSharedPreferenceChangeListener = OnSharedPreferenceChangeListener { prefs, key ->
            val fragment = this@SubScreenFragment
            val context: Context? = fragment.activity
            if (context == null || fragment.preferenceScreen == null) {
                val tag = fragment.javaClass.simpleName
                Log.w(tag, "onSharedPreferenceChanged called before activity starts.")
                return@OnSharedPreferenceChangeListener
            }
            BackupManager(context).dataChanged()
            fragment.onSharedPreferenceChanged(prefs, key)
        }
        this.sharedPreferences?.registerOnSharedPreferenceChangeListener(
            mSharedPreferenceChangeListener
        )
    }

    override fun onDestroy() {
        this.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(
            mSharedPreferenceChangeListener
        )
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        prefs: SharedPreferences?,
        key: String?,
    ) {
        // This method may be overridden by an extended class.
    }

    companion object {
        fun setPreferenceEnabled(
            prefKey: String?,
            enabled: Boolean,
            screen: PreferenceScreen,
        ) {
            val preference = screen.findPreference(prefKey)
            if (preference != null) {
                preference.isEnabled = enabled
            }
        }
    }
}
