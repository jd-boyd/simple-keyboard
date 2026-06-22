/*
 * Copyright (C) 2010 The Android Open Source Project
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
package rkr.simplekeyboard.inputmethod.keyboard.internal

import android.util.Log

// package
internal open class ModifierKeyState(
    @JvmField protected val mName: String?,
) {
    @JvmField
    protected var mState: Int = RELEASING

    fun onPress() {
        mState = PRESSING
    }

    fun onRelease() {
        mState = RELEASING
    }

    open fun onOtherKeyPressed() {
        val oldState = mState
        if (oldState == PRESSING) mState = CHORDING
        if (DEBUG) Log.d(TAG, mName + ".onOtherKeyPressed: " + toString(oldState) + " > " + this)
    }

    val isPressing: Boolean
        get() = mState == PRESSING

    val isReleasing: Boolean
        get() = mState == RELEASING

    val isChording: Boolean
        get() = mState == CHORDING

    override fun toString(): String = toString(mState)

    protected open fun toString(state: Int): String =
        when (state) {
            RELEASING -> "RELEASING"
            PRESSING -> "PRESSING"
            CHORDING -> "CHORDING"
            else -> "UNKNOWN"
        }

    companion object {
        @JvmField
        protected val TAG: String = ModifierKeyState::class.java.getSimpleName()
        protected const val DEBUG: Boolean = false

        protected const val RELEASING: Int = 0
        protected const val PRESSING: Int = 1
        protected const val CHORDING: Int = 2
    }
}
