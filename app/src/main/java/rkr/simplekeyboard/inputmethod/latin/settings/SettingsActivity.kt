/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
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
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.utils.FragmentUtils

class SettingsActivity : PreferenceActivity() {
    override fun onStart() {
        super.onStart()

        var enabled = false
        try {
            enabled = this.isInputMethodOfThisImeEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Exception in check if input method is enabled", e)
        }

        if (!enabled) {
            val context: Context = this
            val builder = AlertDialog.Builder(this)
            builder.setMessage(R.string.setup_message)
            builder.setPositiveButton(
                android.R.string.ok,
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface, id: Int) {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        dialog.dismiss()
                    }
                })
            builder.setNegativeButton(
                android.R.string.cancel,
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, id: Int) {
                        finish()
                    }
                })
            builder.setCancelable(false)

            builder.create().show()
        }
    }

    private val isInputMethodOfThisImeEnabled: Boolean
        /**
         * Check if this IME is enabled in the system.
         * @return whether this IME is enabled in the system.
         */
        get() {
            val imm =
                getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val imePackageName = getPackageName()
            for (imi in imm.getEnabledInputMethodList()) {
                if (imi.getPackageName() == imePackageName) {
                    return true
                }
            }
            return false
        }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val container = getListView().getParent().getParent() as View
            // com.android.internal.R.id.prefs_container in
            // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/layout/preference_list_content.xml
            container.setOnApplyWindowInsetsListener(View.OnApplyWindowInsetsListener { view: View?, windowInsets: WindowInsets? ->
                val insets = windowInsets!!.getInsets(WindowInsets.Type.systemBars())
                val mlp = view!!.getLayoutParams() as MarginLayoutParams
                mlp.topMargin = insets.top
                mlp.leftMargin = insets.left
                mlp.bottomMargin = insets.bottom
                mlp.rightMargin = insets.right
                view.setLayoutParams(mlp)
                WindowInsets.CONSUMED
            })
        }

        val actionBar = getActionBar()
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeButtonEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == android.R.id.home) {
            super.onBackPressed()
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

    public override fun isValidFragment(fragmentName: String?): Boolean {
        return FragmentUtils.isValidFragment(fragmentName)
    }

    companion object {
        private val DEFAULT_FRAGMENT: String = SettingsFragment::class.java.getName()
        private val TAG: String = SettingsActivity::class.java.getSimpleName()
    }
}
