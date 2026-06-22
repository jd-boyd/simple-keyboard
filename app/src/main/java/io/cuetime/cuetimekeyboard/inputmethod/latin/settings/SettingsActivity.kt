/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
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

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceActivity
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import io.cuetime.cuetimekeyboard.inputmethod.R

class SettingsActivity : PreferenceActivity() {
    override fun onStart() {
        super.onStart()

        var enabled = false
        try {
            enabled = isInputMethodOfThisImeEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Exception in check if input method is enabled", e)
        }

        if (!enabled) {
            val context: Context = this
            val builder = AlertDialog.Builder(this)
            builder.setMessage(R.string.setup_message)
            builder.setPositiveButton(
                android.R.string.ok
            ) { dialog: android.content.DialogInterface, _: Int ->
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                dialog.dismiss()
            }
            builder.setNegativeButton(
                android.R.string.cancel
            ) { _: android.content.DialogInterface, _: Int -> finish() }
            builder.setCancelable(false)

            builder.create().show()
        }
    }

    private val isInputMethodOfThisImeEnabled: Boolean
        get() {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val imePackageName = packageName
            for (imi in imm.enabledInputMethodList) {
                if (imi.packageName == imePackageName) {
                    return true
                }
            }
            return false
        }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val container = findViewById<View>(android.R.id.content)
            container.setOnApplyWindowInsetsListener { view: View, windowInsets: WindowInsets ->
                val insets = windowInsets.getInsets(WindowInsets.Type.systemBars())
                val mlp = view.layoutParams as MarginLayoutParams
                mlp.topMargin = insets.top
                mlp.leftMargin = insets.left
                mlp.bottomMargin = insets.bottom
                mlp.rightMargin = insets.right
                view.layoutParams = mlp
                WindowInsets.CONSUMED
            }
        }

        actionBar?.let { actionBar ->
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeButtonEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getIntent(): Intent {
        val intent = super.getIntent()
        val fragment = intent.getStringExtra(EXTRA_SHOW_FRAGMENT)
        if (fragment == null) {
            intent.putExtra(EXTRA_SHOW_FRAGMENT, DEFAULT_FRAGMENT)
        }
        intent.putExtra(EXTRA_NO_HEADERS, true)
        return intent
    }

    override fun isValidFragment(fragmentName: String?): Boolean {
        return true
    }

    companion object {
        private val DEFAULT_FRAGMENT: String = SettingsFragment::class.java.name
        private val TAG: String = SettingsActivity::class.java.simpleName
    }
}
