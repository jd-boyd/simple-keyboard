/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2021 wittmane
 * Copyright (C) 2021 Raimondas Rimkus
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
import android.content.DialogInterface.OnMultiChoiceClickListener
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.compat.MenuItemIconColorCompat
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager
import rkr.simplekeyboard.inputmethod.latin.Subtype
import rkr.simplekeyboard.inputmethod.latin.common.LocaleUtils
import rkr.simplekeyboard.inputmethod.latin.common.LocaleUtils.LocaleComparator
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypeLocaleUtils
import java.util.Locale
import java.util.SortedSet
import java.util.TreeSet

/**
 * "Languages" settings sub screen.
 */
class LanguagesSettingsFragment : PreferenceFragment() {
    private var mRichImm: RichInputMethodManager? = null
    private var mUsedLocaleNames: Array<CharSequence?>? = null
    private var mUsedLocaleValues: Array<String?>? = null
    private var mUnusedLocaleNames: Array<CharSequence?>? = null
    private var mUnusedLocaleValues: Array<String?>? = null
    private var mAlertDialog: AlertDialog? = null
    private var mView: View? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        RichInputMethodManager.init(getActivity())
        mRichImm = RichInputMethodManager.getInstance()

        addPreferencesFromResource(R.xml.empty_settings)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater?,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mView = super.onCreateView(inflater, container, savedInstanceState)
        return mView
    }

    override fun onStart() {
        super.onStart()
        buildContent()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.remove_language, menu)
        inflater.inflate(R.menu.add_language, menu)

        val addLanguageMenuItem = menu.findItem(R.id.action_add_language)
        MenuItemIconColorCompat.matchMenuIconColor(
            mView, addLanguageMenuItem,
            getActivity().getActionBar()
        )
        val removeLanguageMenuItem = menu.findItem(R.id.action_remove_language)
        MenuItemIconColorCompat.matchMenuIconColor(
            mView, removeLanguageMenuItem,
            getActivity().getActionBar()
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.action_add_language) {
            showAddLanguagePopup()
        } else if (itemId == R.id.action_remove_language) {
            showRemoveLanguagePopup()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        if (mUsedLocaleNames != null) {
            menu.findItem(R.id.action_remove_language).setVisible(mUsedLocaleNames!!.size > 1)
        }
    }

    /**
     * Build the preferences and them to this settings screen.
     */
    private fun buildContent() {
        val context: Context? = getActivity()
        val group: PreferenceGroup = getPreferenceScreen()
        group.removeAll()

        val languageCategory = PreferenceCategory(context)
        languageCategory.setTitle(R.string.user_languages)
        group.addPreference(languageCategory)

        val comparator: Comparator<Locale?> = LocaleComparator()
        val enabledSubtypes = mRichImm!!.getEnabledSubtypes(false)
        val usedLocales = getUsedLocales(enabledSubtypes, comparator)
        val unusedLocales = getUnusedLocales(usedLocales, comparator)

        buildLanguagePreferences(usedLocales, group, context)
        setLocaleEntries(usedLocales, unusedLocales)
    }

    /**
     * Get all of the unique languages from the subtypes that have been enabled.
     * @param subtypes the list of subtypes for this IME that have been enabled.
     * @param comparator the comparator to sort the languages.
     * @return a set of locales for the used languages sorted using the specified comparator.
     */
    private fun getUsedLocales(
        subtypes: MutableSet<Subtype>,
        comparator: Comparator<Locale?>?
    ): SortedSet<Locale> {
        val locales: SortedSet<Locale> = TreeSet<Locale>(comparator)

        for (subtype in subtypes) {
            if (DEBUG_SUBTYPE_ID) {
                Log.d(
                    TAG, String.format(
                        "Enabled subtype: %-6s 0x%08x %11d %s",
                        subtype.locale, subtype.hashCode(), subtype.hashCode(),
                        subtype.name
                    )
                )
            }
            locales.add(subtype.localeObject!!)
        }

        return locales
    }

    /**
     * Get the list of languages supported by this IME that aren't included in
     * [.getUsedLocales].
     * @param usedLocales the used locales.
     * @param comparator the comparator to sort the languages.
     * @return a set of locales for the unused languages sorted using the specified comparator.
     */
    private fun getUnusedLocales(
        usedLocales: MutableSet<Locale>,
        comparator: Comparator<Locale?>?
    ): SortedSet<Locale> {
        val locales: SortedSet<Locale> = TreeSet<Locale>(comparator)
        for (localeString in SubtypeLocaleUtils.getSupportedLocales()) {
            val locale = LocaleUtils.constructLocaleFromString(localeString)
            if (usedLocales.contains(locale)) {
                continue
            }
            locales.add(locale)
        }
        return locales
    }

    /**
     * Create a language preference for each of the specified locales in the preference group. These
     * preferences will be added to the group in the order of the locales that are passed in.
     * @param locales the locales to add preferences for.
     * @param group the preference group to add preferences to.
     * @param context the context for this application.
     */
    private fun buildLanguagePreferences(
        locales: SortedSet<Locale>,
        group: PreferenceGroup,
        context: Context?
    ) {
        for (locale in locales) {
            val localeString = LocaleUtils.getLocaleString(locale)
            val pref = SingleLanguagePreference(context, localeString)
            group.addPreference(pref)
        }
    }

    /**
     * Set the lists of used languages that can be removed and unused languages that can be added.
     * @param usedLocales the enabled locales for this IME.
     * @param unusedLocales the unused locales that are supported in this IME.
     */
    private fun setLocaleEntries(
        usedLocales: SortedSet<Locale>,
        unusedLocales: SortedSet<Locale>
    ) {
        mUsedLocaleNames = arrayOfNulls<CharSequence>(usedLocales.size)
        mUsedLocaleValues = arrayOfNulls<String>(usedLocales.size)
        var i = 0
        for (locale in usedLocales) {
            val localeString = LocaleUtils.getLocaleString(locale)
            mUsedLocaleValues!![i] = localeString
            mUsedLocaleNames!![i] =
                LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(localeString)
            i++
        }

        mUnusedLocaleNames = arrayOfNulls<CharSequence>(unusedLocales.size)
        mUnusedLocaleValues = arrayOfNulls<String>(unusedLocales.size)
        i = 0
        for (locale in unusedLocales) {
            val localeString = LocaleUtils.getLocaleString(locale)
            mUnusedLocaleValues!![i] = localeString
            mUnusedLocaleNames!![i] =
                LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(localeString)
            i++
        }
    }

    /**
     * Show the popup to add a new language.
     */
    private fun showAddLanguagePopup() {
        showMultiChoiceDialog(
            mUnusedLocaleNames, R.string.add_language, R.string.add, true,
            object : OnMultiChoiceDialogAcceptListener {
                override fun onClick(checkedItems: BooleanArray?) {
                    if (checkedItems == null) return
                    // enable the default layout for all of the checked languages
                    for (i in checkedItems.indices) {
                        if (!checkedItems[i]) {
                            continue
                        }
                        val subtype = SubtypeLocaleUtils.getDefaultSubtype(
                            mUnusedLocaleValues!![i],
                            this@LanguagesSettingsFragment.getResources()
                        )
                        mRichImm!!.addSubtype(subtype)
                    }

                    // refresh the list of enabled languages
                    getActivity().invalidateOptionsMenu()
                    buildContent()
                }
            })
    }

    /**
     * Show the popup to remove an existing language.
     */
    private fun showRemoveLanguagePopup() {
        showMultiChoiceDialog(
            mUsedLocaleNames!!, R.string.remove_language, R.string.remove, false,
            object : OnMultiChoiceDialogAcceptListener {
                override fun onClick(checkedItems: BooleanArray?) {
                    if (checkedItems == null) return
                    // disable the layouts for all of the checked languages
                    for (i in checkedItems.indices) {
                        if (!checkedItems[i]) {
                            continue
                        }
                        val subtypes =
                            mRichImm!!.getEnabledSubtypesForLocale(mUsedLocaleValues!![i])
                        for (subtype in subtypes) {
                            mRichImm!!.removeSubtype(subtype)
                        }
                    }

                    // refresh the list of enabled languages
                    getActivity().invalidateOptionsMenu()
                    buildContent()
                }
            })
    }

    /**
     * Show a multi-select popup.
     * @param names the list of the choice display names.
     * @param titleRes the title of the dialog.
     * @param positiveButtonRes the text for the positive button.
     * @param allowAllChecked whether the positive button should be enabled when all items are
     * checked.
     * @param listener the listener for when the user clicks the positive button.
     */
    private fun showMultiChoiceDialog(
        names: Array<CharSequence?>?,
        titleRes: Int,
        positiveButtonRes: Int,
        allowAllChecked: Boolean,
        listener: OnMultiChoiceDialogAcceptListener
    ) {
        val checkedItems = BooleanArray(names?.size ?: 0)
        mAlertDialog = AlertDialog.Builder(getActivity())
            .setTitle(titleRes)
            .setMultiChoiceItems(
                names, checkedItems,
                OnMultiChoiceClickListener { _: DialogInterface?, _: Int, _: Boolean ->
                    // make sure the positive button is only enabled when at least one
                    // item is checked and when not all of the items are checked (unless
                    // allowAllChecked is true)
                    var hasCheckedItem = false
                    var hasUncheckedItem = false
                    for (itemChecked in checkedItems) {
                        if (itemChecked) {
                            hasCheckedItem = true
                            if (allowAllChecked) {
                                break
                            }
                        } else {
                            hasUncheckedItem = true
                        }
                        if (hasCheckedItem && hasUncheckedItem) {
                            break
                        }
                    }
                    mAlertDialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                        hasCheckedItem && (hasUncheckedItem || allowAllChecked)
                    )
                })
            .setPositiveButton(positiveButtonRes) { _: DialogInterface?, _: Int ->
                listener.onClick(checkedItems)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        mAlertDialog!!.show()
        // disable the positive button since nothing is checked by default
        mAlertDialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false)
    }

    /**
     * Interface used to add some code to run when the positive button on a multi-select dialog is
     * clicked.
     */
    private interface OnMultiChoiceDialogAcceptListener {
        /**
         * Handler triggered when the positive button of the dialog is clicked.
         * @param checkedItems a list of whether each item in the dialog was checked.
         */
        fun onClick(checkedItems: BooleanArray?)
    }

    /**
     * Preference to link to a language specific settings screen.
     */
    private class SingleLanguagePreference(context: Context?, private val mLocale: String?) :
        Preference(context) {
        private var mExtras: Bundle? = null

        /**
         * Create a new preference for a language.
         * @param context the context for this application.
         * @param localeString a string specification of a locale, in a format of "ll_cc_variant",
         * where "ll" is a language code, "cc" is a country code.
         */
        init {
            setTitle(LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(mLocale))
            setFragment(SingleLanguageSettingsFragment::class.java.getName())
        }

        override fun getExtras(): Bundle? {
            if (mExtras == null) {
                mExtras = Bundle()
                mExtras!!.putString(SingleLanguageSettingsFragment.LOCALE_BUNDLE_KEY, mLocale)
            }
            return mExtras
        }

        override fun peekExtras(): Bundle? {
            return mExtras
        }
    }

    companion object {
        private val TAG: String = LanguagesSettingsFragment::class.java.getSimpleName()

        private const val DEBUG_SUBTYPE_ID = false
    }
}
