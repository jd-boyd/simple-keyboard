/*
 * Copyright (C) 2014 The Android Open Source Project
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
package rkr.simplekeyboard.inputmethod.latin

import android.content.res.Resources
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.common.LocaleUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils

/**
 * A keyboard layout for a locale.
 */
class Subtype {
    /**
     * Get the locale string.
     * @return the locale string.
     */
    val locale: String

    /**
     * Get the keyboard layout set name (internal).
     * @return the keyboard layout set name.
     */
    val keyboardLayoutSet: String
    private val mLayoutNameRes: Int
    private val mLayoutNameStr: String?
    private val mShowLayoutInName: Boolean
    private val mResources: Resources

    /**
     * Create a subtype.
     * @param locale the locale for the layout in the format of "ll_cc_variant" where "ll" is a
     * language code, "cc" is a country code.
     * @param layoutSet the keyboard layout set name.
     * @param layoutNameRes the keyboard layout name resource ID to use for display instead of the
     * name of the language.
     * @param showLayoutInName flag to indicate if the display name of the keyboard layout should be
     * used in the main display name of the subtype
     * (eg: "English (US) (QWERTY)" vs "English (US)").
     * @param resources the resources to use.
     */
    constructor(
        locale: String, layoutSet: String, layoutNameRes: Int,
        showLayoutInName: Boolean, resources: Resources
    ) {
        this.locale = locale
        this.keyboardLayoutSet = layoutSet
        mLayoutNameRes = layoutNameRes
        mLayoutNameStr = null
        mShowLayoutInName = showLayoutInName
        mResources = resources
    }

    /**
     * Create a subtype.
     * @param locale the locale for the layout in the format of "ll_cc_variant" where "ll" is a
     * language code, "cc" is a country code.
     * @param layoutSet the keyboard layout set name.
     * @param layoutNameStr the keyboard layout name string to use for display instead of the name
     * of the language.
     * @param showLayoutInName flag to indicate if the display name of the keyboard layout should be
     * used in the main display name of the subtype
     * (eg: "English (US) (QWERTY)" vs "English (US)").
     * @param resources the resources to use.
     */
    constructor(
        locale: String, layoutSet: String, layoutNameStr: String?,
        showLayoutInName: Boolean, resources: Resources
    ) {
        this.locale = locale
        this.keyboardLayoutSet = layoutSet
        mLayoutNameRes = NO_RESOURCE
        mLayoutNameStr = layoutNameStr
        mShowLayoutInName = showLayoutInName
        mResources = resources
    }

    val localeObject: Locale?
        /**
         * Get the locale object.
         * @return the locale object.
         */
        get() = LocaleUtils.constructLocaleFromString(
            this.locale
        )

    val name: String
        /**
         * Get the display name for the subtype. This should be something like "English (US)" or
         * "English (US) (QWERTY)".
         * @return the display name.
         */
        get() {
            val localeDisplayName =
                LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(this.locale)
            if (mShowLayoutInName) {
                if (mLayoutNameRes != NO_RESOURCE) {
                    return mResources.getString(
                        R.string.subtype_generic_layout, localeDisplayName,
                        mResources.getString(mLayoutNameRes)
                    )
                }
                if (mLayoutNameStr != null) {
                    return mResources.getString(
                        R.string.subtype_generic_layout, localeDisplayName,
                        mLayoutNameStr
                    )
                }
            }
            return localeDisplayName
        }

    val layoutDisplayName: String
        /**
         * Get the display name for the keyboard layout. This should be something like "QWERTY".
         * @return the display name for the keyboard layout.
         */
        get() {
            val displayName: String?
            if (mLayoutNameRes != NO_RESOURCE) {
                displayName = mResources.getString(mLayoutNameRes)
            } else if (mLayoutNameStr != null) {
                displayName = mLayoutNameStr
            } else {
                displayName = LocaleResourceUtils.getLanguageDisplayNameInSystemLocale(this.locale)
            }
            return displayName
        }

    override fun equals(o: Any?): Boolean {
        if (o !is Subtype) {
            return false
        }
        val other = o
        return this.locale == other.locale && this.keyboardLayoutSet == other.keyboardLayoutSet
    }

    override fun hashCode(): Int {
        var hashCode = 31 + locale.hashCode()
        hashCode = hashCode * 31 + keyboardLayoutSet.hashCode()
        return hashCode
    }

    override fun toString(): String {
        return "subtype " + this.locale + ":" + this.keyboardLayoutSet
    }

    companion object {
        private const val NO_RESOURCE = 0
    }
}
