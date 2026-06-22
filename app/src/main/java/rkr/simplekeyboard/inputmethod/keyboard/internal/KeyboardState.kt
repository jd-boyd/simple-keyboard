/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2024 Raimondas Rimkus
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
package rkr.simplekeyboard.inputmethod.keyboard.internal

import android.text.TextUtils
import android.util.Log
import rkr.simplekeyboard.inputmethod.event.Event
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.utils.CapsModeUtils
import rkr.simplekeyboard.inputmethod.latin.utils.RecapitalizeStatus

/*
 * Keyboard state machine.
 *
 * This class contains all keyboard state transition logic.
 *
 * The input events are onLoadKeyboard,
 * onPressKey, onReleaseKey,
 * onEvent, onFinishSlidingInput,
 * onUpdateShiftState, onResetKeyboardStateToAlphabet.
 *  * The actions are SwitchActions's methods.
 */
class KeyboardState(
    private val mSwitchActions: SwitchActions,
) {
    interface SwitchActions {
        fun setAlphabetKeyboard()

        fun setAlphabetManualShiftedKeyboard()

        fun setAlphabetAutomaticShiftedKeyboard()

        fun setAlphabetShiftLockedKeyboard()

        fun setSymbolsKeyboard()

        fun setSymbolsShiftedKeyboard()

        /**
         * Request to call back [onUpdateShiftState].
         */
        fun requestUpdatingShiftState(
            autoCapsFlags: Int,
            recapitalizeMode: Int,
        )

        fun startDoubleTapShiftKeyTimer()

        val isInDoubleTapShiftKeyTimeout: Boolean

        fun cancelDoubleTapShiftKeyTimer()

        companion object {
            const val DEBUG_ACTION: Boolean = false

            const val DEBUG_TIMER_ACTION: Boolean = false
        }
    }

    private val mShiftKeyState = ShiftKeyState("Shift")
    private val mSymbolKeyState = ModifierKeyState("Symbol")

    private var mSwitchState: Int = SWITCH_STATE_ALPHA

    private var mIsAlphabetMode = false
    private val mAlphabetShiftState = AlphabetShiftState()
    private var mIsSymbolShifted = false
    private var mPrevMainKeyboardWasShiftLocked = false
    private var mPrevSymbolsKeyboardWasShifted = false
    private var mRecapitalizeMode: Int

    // For handling double tap.
    private var mIsInAlphabetUnshiftedFromShifted = false
    private var mIsInDoubleTapShiftKey = false

    fun onLoadKeyboard(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onLoadKeyboard: " + stateToString(autoCapsFlags, recapitalizeMode))
        }
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false)
        mPrevMainKeyboardWasShiftLocked = false
        mPrevSymbolsKeyboardWasShifted = false
        mShiftKeyState.onRelease()
        mSymbolKeyState.onRelease()

        setAlphabetKeyboard(autoCapsFlags, recapitalizeMode)
    }

    init {
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
    }

    private fun setShifted(shiftMode: Int) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setShifted: shiftMode=" + shiftModeToString(shiftMode) + " " + this)
        }
        if (!mIsAlphabetMode) return
        val prevShiftMode: Int =
            if (mAlphabetShiftState.isAutomaticShifted) {
                AUTOMATIC_SHIFT
            } else if (mAlphabetShiftState.isManualShifted) {
                MANUAL_SHIFT
            } else {
                UNSHIFT
            }
        when (shiftMode) {
            AUTOMATIC_SHIFT -> {
                mAlphabetShiftState.setAutomaticShifted()
                if (shiftMode != prevShiftMode) {
                    mSwitchActions.setAlphabetAutomaticShiftedKeyboard()
                }
            }

            MANUAL_SHIFT -> {
                mAlphabetShiftState.setShifted(true)
                if (shiftMode != prevShiftMode) {
                    mSwitchActions.setAlphabetManualShiftedKeyboard()
                }
            }

            UNSHIFT -> {
                mAlphabetShiftState.setShifted(false)
                if (0 != prevShiftMode) {
                    mSwitchActions.setAlphabetKeyboard()
                }
            }

            SHIFT_LOCK_SHIFTED -> {
                mAlphabetShiftState.setShifted(true)
            }
        }
    }

    private fun setShiftLocked(shiftLocked: Boolean) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setShiftLocked: shiftLocked=$shiftLocked $this")
        }
        if (!mIsAlphabetMode) return
        if (shiftLocked && (
                !mAlphabetShiftState.isShiftLocked ||
                    mAlphabetShiftState.isShiftLockShifted
            )
        ) {
            mSwitchActions.setAlphabetShiftLockedKeyboard()
        }
        if (!shiftLocked && mAlphabetShiftState.isShiftLocked) {
            mSwitchActions.setAlphabetKeyboard()
        }
        mAlphabetShiftState.setShiftLocked(shiftLocked)
    }

    private fun toggleAlphabetAndSymbols(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(
                TAG,
                "toggleAlphabetAndSymbols: " +
                    stateToString(autoCapsFlags, recapitalizeMode),
            )
        }
        if (mIsAlphabetMode) {
            mPrevMainKeyboardWasShiftLocked = mAlphabetShiftState.isShiftLocked
            if (mPrevSymbolsKeyboardWasShifted) {
                setSymbolsShiftedKeyboard()
            } else {
                setSymbolsKeyboard()
            }
            mPrevSymbolsKeyboardWasShifted = false
        } else {
            mPrevSymbolsKeyboardWasShifted = mIsSymbolShifted
            setAlphabetKeyboard(autoCapsFlags, recapitalizeMode)
            if (mPrevMainKeyboardWasShiftLocked) {
                setShiftLocked(true)
            }
            mPrevMainKeyboardWasShiftLocked = false
        }
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    private fun resetKeyboardStateToAlphabet(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(
                TAG,
                "resetKeyboardStateToAlphabet: " +
                    stateToString(autoCapsFlags, recapitalizeMode),
            )
        }
        if (mIsAlphabetMode) return

        mPrevSymbolsKeyboardWasShifted = mIsSymbolShifted
        setAlphabetKeyboard(autoCapsFlags, recapitalizeMode)
        if (mPrevMainKeyboardWasShiftLocked) {
            setShiftLocked(true)
        }
        mPrevMainKeyboardWasShiftLocked = false
    }

    private fun toggleShiftInSymbols() {
        if (mIsSymbolShifted) {
            setSymbolsKeyboard()
        } else {
            setSymbolsShiftedKeyboard()
        }
    }

    private fun setAlphabetKeyboard(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard: " + stateToString(autoCapsFlags, recapitalizeMode))
        }

        mSwitchActions.setAlphabetKeyboard()
        mIsAlphabetMode = true
        mIsSymbolShifted = false
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
        mSwitchState = SWITCH_STATE_ALPHA
        mSwitchActions.requestUpdatingShiftState(autoCapsFlags, recapitalizeMode)
    }

    private fun setSymbolsKeyboard() {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setSymbolsKeyboard")
        }
        mSwitchActions.setSymbolsKeyboard()
        mIsAlphabetMode = false
        mIsSymbolShifted = false
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false)
        mSwitchState = SWITCH_STATE_SYMBOL_BEGIN
    }

    private fun setSymbolsShiftedKeyboard() {
        if (DEBUG_INTERNAL_ACTION) {
            Log.d(TAG, "setSymbolsShiftedKeyboard")
        }
        mSwitchActions.setSymbolsShiftedKeyboard()
        mIsAlphabetMode = false
        mIsSymbolShifted = true
        mRecapitalizeMode = RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false)
        mSwitchState = SWITCH_STATE_SYMBOL_BEGIN
    }

    fun onPressKey(
        code: Int,
        isSinglePointer: Boolean,
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG,
                (
                    "onPressKey: code=" + Constants.printableCode(code) +
                        " single=" + isSinglePointer +
                        " " + stateToString(autoCapsFlags, recapitalizeMode)
                ),
            )
        }
        if (code != Constants.CODE_SHIFT) {
            // Because the double tap shift key timer is to detect two consecutive shift key press,
            // it should be canceled when a non-shift key is pressed.
            mSwitchActions.cancelDoubleTapShiftKeyTimer()
        }
        if (code == Constants.CODE_SHIFT) {
            onPressShift()
        } else if (code == Constants.CODE_CAPSLOCK) {
            // Nothing to do here. See {@link #onReleaseKey(int,boolean)}.
        } else if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
            onPressSymbol(autoCapsFlags, recapitalizeMode)
        } else {
            mShiftKeyState.onOtherKeyPressed()
            mSymbolKeyState.onOtherKeyPressed()
            // It is required to reset the auto caps state when all the following conditions
            // are met:
            // 1) two or more fingers are in action
            // 2) in alphabet layout
            // 3) not in all characters caps mode
            // As for #3, please note that it's required to check even when the auto caps mode is
            // off because, for example, we may be in the #1 state within the manual temporary
            // shifted mode.
            if (!isSinglePointer && mIsAlphabetMode &&
                autoCapsFlags != TextUtils.CAP_MODE_CHARACTERS
            ) {
                val needsToResetAutoCaps =
                    (mAlphabetShiftState.isAutomaticShifted && !mShiftKeyState.isChording) ||
                        (mAlphabetShiftState.isManualShifted && mShiftKeyState.isReleasing)
                if (needsToResetAutoCaps) {
                    mSwitchActions.setAlphabetKeyboard()
                }
            }
        }
    }

    fun onReleaseKey(
        code: Int,
        withSliding: Boolean,
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG,
                (
                    "onReleaseKey: code=" + Constants.printableCode(code) +
                        " sliding=" + withSliding +
                        " " + stateToString(autoCapsFlags, recapitalizeMode)
                ),
            )
        }
        when (code) {
            Constants.CODE_SHIFT -> {
                onReleaseShift(withSliding, autoCapsFlags, recapitalizeMode)
            }

            Constants.CODE_CAPSLOCK -> {
                setShiftLocked(!mAlphabetShiftState.isShiftLocked)
            }

            Constants.CODE_SWITCH_ALPHA_SYMBOL -> {
                onReleaseSymbol(withSliding, autoCapsFlags, recapitalizeMode)
            }
        }
    }

    private fun onPressSymbol(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode)
        mSymbolKeyState.onPress()
        mSwitchState = SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL
    }

    private fun onReleaseSymbol(
        withSliding: Boolean,
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (mSymbolKeyState.isChording) {
            // Switch back to the previous keyboard mode if the user chords the mode change key and
            // another key, then releases the mode change key.
            toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode)
        } else if (!withSliding) {
            // If the mode change key is being released without sliding, we should forget the
            // previous symbols keyboard shift state and simply switch back to symbols layout
            // (never symbols shifted) next time the mode gets changed to symbols layout.
            mPrevSymbolsKeyboardWasShifted = false
        }
        mSymbolKeyState.onRelease()
    }

    fun onUpdateShiftState(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onUpdateShiftState: " + stateToString(autoCapsFlags, recapitalizeMode))
        }
        mRecapitalizeMode = recapitalizeMode
        updateAlphabetShiftState(autoCapsFlags, recapitalizeMode)
    }

    // TODO: Remove this method. Come up with a more comprehensive way to reset the keyboard layout
    // when a keyboard layout set doesn't get reloaded in LatinIME.onStartInputViewInternal().
    fun onResetKeyboardStateToAlphabet(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_EVENT) {
            Log.d(
                TAG,
                "onResetKeyboardStateToAlphabet: " +
                    stateToString(autoCapsFlags, recapitalizeMode),
            )
        }
        resetKeyboardStateToAlphabet(autoCapsFlags, recapitalizeMode)
    }

    private fun updateShiftStateForRecapitalize(recapitalizeMode: Int) {
        when (recapitalizeMode) {
            RecapitalizeStatus.CAPS_MODE_ALL_UPPER -> {
                setShifted(
                    SHIFT_LOCK_SHIFTED,
                )
            }

            RecapitalizeStatus.CAPS_MODE_FIRST_WORD_UPPER -> {
                setShifted(AUTOMATIC_SHIFT)
            }

            RecapitalizeStatus.CAPS_MODE_ALL_LOWER, RecapitalizeStatus.CAPS_MODE_ORIGINAL_MIXED_CASE -> {
                setShifted(
                    UNSHIFT,
                )
            }

            else -> {
                setShifted(UNSHIFT)
            }
        }
    }

    private fun updateAlphabetShiftState(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (!mIsAlphabetMode) return
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != recapitalizeMode) {
            // We are recapitalizing. Match the keyboard to the current recapitalize state.
            updateShiftStateForRecapitalize(recapitalizeMode)
            return
        }
        if (!mShiftKeyState.isReleasing) {
            // Ignore update shift state event while the shift key is being pressed (including
            // chording).
            return
        }
        if (!mAlphabetShiftState.isShiftLocked && !mShiftKeyState.isIgnoring) {
            if (mShiftKeyState.isReleasing && autoCapsFlags != Constants.TextUtils.CAP_MODE_OFF) {
                // Only when shift key is releasing, automatic temporary upper case will be set.
                setShifted(AUTOMATIC_SHIFT)
            } else {
                setShifted(if (mShiftKeyState.isChording) MANUAL_SHIFT else UNSHIFT)
            }
        }
    }

    private fun onPressShift() {
        // If we are recapitalizing, we don't do any of the normal processing, including
        // importantly the double tap timer.
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != mRecapitalizeMode) {
            return
        }
        if (mIsAlphabetMode) {
            mIsInDoubleTapShiftKey = mSwitchActions.isInDoubleTapShiftKeyTimeout
            if (!mIsInDoubleTapShiftKey) {
                // This is first tap.
                mSwitchActions.startDoubleTapShiftKeyTimer()
            }
            if (mIsInDoubleTapShiftKey) {
                if (mAlphabetShiftState.isManualShifted || mIsInAlphabetUnshiftedFromShifted) {
                    // Shift key has been double tapped while in manual shifted or automatic
                    // shifted state.
                    setShiftLocked(true)
                } else {
                    // Shift key has been double tapped while in normal state. This is the second
                    // tap to disable shift locked state, so just ignore this.
                }
            } else {
                if (mAlphabetShiftState.isShiftLocked) {
                    // Shift key is pressed while shift locked state, we will treat this state as
                    // shift lock shifted state and mark as if shift key pressed while normal
                    // state.
                    setShifted(SHIFT_LOCK_SHIFTED)
                    mShiftKeyState.onPress()
                } else if (mAlphabetShiftState.isAutomaticShifted) {
                    // Shift key pressed while automatic shifted isn't considered a manual shift
                    // since it doesn't change the keyboard into a shifted state.
                    mShiftKeyState.onPress()
                } else if (mAlphabetShiftState.isShiftedOrShiftLocked) {
                    // In manual shifted state, we just record shift key has been pressing while
                    // shifted state.
                    mShiftKeyState.onPressOnShifted()
                } else {
                    // In base layout, chording or manual shifted mode is started.
                    setShifted(MANUAL_SHIFT)
                    mShiftKeyState.onPress()
                }
            }
        } else {
            // In symbol mode, just toggle symbol and symbol more keyboard.
            toggleShiftInSymbols()
            mSwitchState = SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE
            mShiftKeyState.onPress()
        }
    }

    private fun onReleaseShift(
        withSliding: Boolean,
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE != mRecapitalizeMode) {
            // We are recapitalizing. We should match the keyboard state to the recapitalize
            // state in priority.
            updateShiftStateForRecapitalize(mRecapitalizeMode)
        } else if (mIsAlphabetMode) {
            val isShiftLocked = mAlphabetShiftState.isShiftLocked
            mIsInAlphabetUnshiftedFromShifted = false
            if (mIsInDoubleTapShiftKey) {
                // Double tap shift key has been handled in {@link #onPressShift}, so that just
                // ignore this release shift key here.
                mIsInDoubleTapShiftKey = false
            } else if (mShiftKeyState.isChording) {
                if (mAlphabetShiftState.isShiftLockShifted) {
                    // After chording input while shift locked state.
                    setShiftLocked(true)
                } else {
                    // After chording input while normal state.
                    setShifted(UNSHIFT)
                }
                // After chording input, automatic shift state may have been changed depending on
                // what characters were input.
                mShiftKeyState.onRelease()
                mSwitchActions.requestUpdatingShiftState(autoCapsFlags, recapitalizeMode)
                return
            } else if (isShiftLocked &&
                !mAlphabetShiftState.isShiftLockShifted &&
                (mShiftKeyState.isPressing || mShiftKeyState.isPressingOnShifted) &&
                !withSliding
            ) {
                // Shift has been long pressed, ignore this release.
            } else if (isShiftLocked && !mShiftKeyState.isIgnoring && !withSliding) {
                // Shift has been pressed without chording while shift locked state.
                setShiftLocked(false)
            } else if (mAlphabetShiftState.isShiftedOrShiftLocked &&
                mShiftKeyState.isPressingOnShifted && !withSliding
            ) {
                // Shift has been pressed without chording while shifted state.
                setShifted(UNSHIFT)
                mIsInAlphabetUnshiftedFromShifted = true
            } else if (mAlphabetShiftState.isAutomaticShifted && mShiftKeyState.isPressing &&
                !withSliding
            ) {
                // Shift has been pressed without chording while automatic shifted
                setShifted(UNSHIFT)
                mIsInAlphabetUnshiftedFromShifted = true
            }
        } else {
            // In symbol mode, switch back to the previous keyboard mode if the user chords the
            // shift key and another key, then releases the shift key.
            if (mShiftKeyState.isChording) {
                toggleShiftInSymbols()
            }
        }
        mShiftKeyState.onRelease()
    }

    fun onFinishSlidingInput(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onFinishSlidingInput: " + stateToString(autoCapsFlags, recapitalizeMode))
        }
        // Switch back to the previous keyboard mode if the user cancels sliding input.
        when (mSwitchState) {
            SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL -> {
                toggleAlphabetAndSymbols(
                    autoCapsFlags,
                    recapitalizeMode,
                )
            }

            SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE -> {
                toggleShiftInSymbols()
            }
        }
    }

    fun onEvent(
        event: Event,
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ) {
        val code = if (event.isFunctionalKeyEvent) event.mKeyCode else event.mCodePoint
        if (DEBUG_EVENT) {
            Log.d(
                TAG,
                (
                    "onEvent: code=" + Constants.printableCode(code) +
                        " " + stateToString(autoCapsFlags, recapitalizeMode)
                ),
            )
        }

        when (mSwitchState) {
            SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL -> {
                if (code == Constants.CODE_SWITCH_ALPHA_SYMBOL) {
                    // Detected only the mode change key has been pressed, and then released.
                    mSwitchState =
                        if (mIsAlphabetMode) {
                            SWITCH_STATE_ALPHA
                        } else {
                            SWITCH_STATE_SYMBOL_BEGIN
                        }
                }
            }

            SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE -> {
                if (code == Constants.CODE_SHIFT) {
                    // Detected only the shift key has been pressed on symbol layout, and then
                    // released.
                    mSwitchState = SWITCH_STATE_SYMBOL_BEGIN
                }
            }

            SWITCH_STATE_SYMBOL_BEGIN -> {
                if (!isSpaceOrEnter(code) && (
                        Constants.isLetterCode(code) ||
                            code == Constants.CODE_OUTPUT_TEXT
                    )
                ) {
                    mSwitchState = SWITCH_STATE_SYMBOL
                }
            }

            SWITCH_STATE_SYMBOL -> {
                // Switch back to alpha keyboard mode if user types one or more non-space/enter
                // characters followed by a space/enter.
                if (isSpaceOrEnter(code)) {
                    toggleAlphabetAndSymbols(autoCapsFlags, recapitalizeMode)
                    mPrevSymbolsKeyboardWasShifted = false
                }
            }
        }

        // If the code is a letter, update keyboard shift state.
        if (Constants.isLetterCode(code)) {
            updateAlphabetShiftState(autoCapsFlags, recapitalizeMode)
        }
    }

    override fun toString(): String =
        (
            "[keyboard=" + (
                if (mIsAlphabetMode) {
                    mAlphabetShiftState.toString()
                } else {
                    (if (mIsSymbolShifted) "SYMBOLS_SHIFTED" else "SYMBOLS")
                }
            ) +
                " shift=" + mShiftKeyState +
                " symbol=" + mSymbolKeyState +
                " switch=" + switchStateToString(mSwitchState) + "]"
        )

    private fun stateToString(
        autoCapsFlags: Int,
        recapitalizeMode: Int,
    ): String =
        (
            this.toString() + " autoCapsFlags=" + CapsModeUtils.flagsToString(autoCapsFlags) +
                " recapitalizeMode=" + RecapitalizeStatus.modeToString(recapitalizeMode)
        )

    companion object {
        private val TAG: String = KeyboardState::class.java.getSimpleName()
        private const val DEBUG_EVENT = false
        private const val DEBUG_INTERNAL_ACTION = false

        // TODO: Merge #mSwitchState}, #mIsAlphabetMode #mAlphabetShiftState,
        // #mIsSymbolShifted, #mPrevMainKeyboardWasShiftLocked, and
        // #mPrevSymbolsKeyboardWasShifted} into single state variable.
        private const val SWITCH_STATE_ALPHA = 0
        private const val SWITCH_STATE_SYMBOL_BEGIN = 1
        private const val SWITCH_STATE_SYMBOL = 2
        private const val SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL = 3
        private const val SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE = 4

        // Constants for {@link SavedKeyboardState#mShiftMode} and {@link #setShifted(int)}.
        private const val UNSHIFT = 0
        private const val MANUAL_SHIFT = 1
        private const val AUTOMATIC_SHIFT = 2
        private const val SHIFT_LOCK_SHIFTED = 3

        private fun isSpaceOrEnter(c: Int): Boolean = c == Constants.CODE_SPACE || c == Constants.CODE_ENTER

        fun shiftModeToString(shiftMode: Int): String? =
            when (shiftMode) {
                UNSHIFT -> "UNSHIFT"
                MANUAL_SHIFT -> "MANUAL"
                AUTOMATIC_SHIFT -> "AUTOMATIC"
                else -> null
            }

        private fun switchStateToString(switchState: Int): String? =
            when (switchState) {
                SWITCH_STATE_ALPHA -> "ALPHA"
                SWITCH_STATE_SYMBOL_BEGIN -> "SYMBOL-BEGIN"
                SWITCH_STATE_SYMBOL -> "SYMBOL"
                SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL -> "MOMENTARY-ALPHA-SYMBOL"
                SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE -> "MOMENTARY-SYMBOL-MORE"
                else -> null
            }
    }
}
