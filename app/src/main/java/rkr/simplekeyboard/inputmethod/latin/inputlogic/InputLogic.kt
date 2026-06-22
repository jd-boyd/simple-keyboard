/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2025 Camille019
 * Copyright (C) 2023 Md. Rifat Hasan Jihan
 * Copyright (C) 2021 wittmane
 * Copyright (C) 2019 Emmanuel
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
package rkr.simplekeyboard.inputmethod.latin.inputlogic

import android.os.SystemClock
import android.text.TextUtils
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import rkr.simplekeyboard.inputmethod.event.Event
import rkr.simplekeyboard.inputmethod.event.InputTransaction
import rkr.simplekeyboard.inputmethod.latin.LatinIME
import rkr.simplekeyboard.inputmethod.latin.RichInputConnection
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues
import rkr.simplekeyboard.inputmethod.latin.utils.InputTypeUtils
import rkr.simplekeyboard.inputmethod.latin.utils.RecapitalizeStatus
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypeLocaleUtils

/**
 * This class manages the input logic.
 */
class InputLogic( // TODO : Remove this member when we can.
    val mLatinIME: LatinIME,
) {
    // This has package visibility so it can be accessed from InputLogicHandler.
    @JvmField
    val mConnection: RichInputConnection = RichInputConnection(mLatinIME)
    private val mRecapitalizeStatus = RecapitalizeStatus()

    /**
     * Initializes the input logic for input in an editor.
     *
     * Call this when input starts or restarts in some editor (typically, in onStartInputView).
     */
    fun startInput() {
        mRecapitalizeStatus.disable() // Do not perform recapitalize until the cursor is moved once
    }

    fun clearCaches() {
        mConnection.clearCaches()
    }

    /**
     * Call this when the subtype changes.
     */
    fun onSubtypeChanged() {
        startInput()
    }

    /**
     * React to a string input.
     *
     * This is triggered by keys that input many characters at once, like the ".com" key or
     * some additional keys for example.
     *
     * @param settingsValues the current values of the settings.
     * @param event the input event containing the data.
     * @return the complete transaction object
     */
    fun onTextInput(
        settingsValues: SettingsValues?,
        event: Event,
    ): InputTransaction {
        val rawText = event.textToCommit.toString()
        val inputTransaction = InputTransaction(settingsValues)
        val text = performSpecificTldProcessingOnTextInput(rawText)
        mConnection.commitText(text, 1)
        // Space state must be updated before calling updateShiftState
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
        return inputTransaction
    }

    /**
     * Consider an update to the cursor position. Evaluate whether this update has happened as
     * part of normal typing or whether it was an explicit cursor move by the user. In any case,
     * do the necessary adjustments.
     * @param newSelStart new selection start
     * @param newSelEnd new selection end
     */
    fun onUpdateSelection(
        newSelStart: Int,
        newSelEnd: Int,
    ) {
        mConnection.updateSelection(newSelStart, newSelEnd)
    }

    fun reloadTextCache() {
        mConnection.reloadTextCache()

        mRecapitalizeStatus.enable()
        mRecapitalizeStatus.stop()
    }

    /**
     * React to a code input. It may be a code point to insert, or a symbolic value that influences
     * the keyboard behavior.
     *
     * Typically, this is called whenever a key is pressed on the software keyboard. This is not
     * the entry point for gesture input; see the onBatchInput* family of functions for this.
     *
     * @param settingsValues the current settings values.
     * @param event the event to handle.
     * @return the complete transaction object
     */
    fun onCodeInput(
        settingsValues: SettingsValues?,
        event: Event?,
    ): InputTransaction {
        val inputTransaction = InputTransaction(settingsValues)

        var currentEvent = event
        while (null != currentEvent) {
            if (currentEvent.isConsumed) {
                handleConsumedEvent(currentEvent)
            } else if (currentEvent.isFunctionalKeyEvent) {
                handleFunctionalEvent(currentEvent, inputTransaction)
            } else {
                handleNonFunctionalEvent(currentEvent, inputTransaction)
            }
            currentEvent = currentEvent.mNextEvent
        }
        return inputTransaction
    }

    /**
     * Handle a consumed event.
     *
     * Consumed events represent events that have already been consumed, typically by the
     * combining chain.
     *
     * @param event The event to handle.
     */
    private fun handleConsumedEvent(event: Event) {
        // A consumed event may have text to commit and an update to the composing state, so
        // we evaluate both. With some combiners, it's possible than an event contains both,
        // and we enter both of the following if clauses.
        val textToCommit = event.textToCommit
        if (!TextUtils.isEmpty(textToCommit)) {
            mConnection.commitText(textToCommit, 1)
        }
    }

    /**
     * Handle a functional key event.
     *
     * A functional event is a special key, like delete, shift, emoji, or the settings key.
     * Non-special keys are those that generate a single code point.
     * This includes all letters, digits, punctuation, separators, emoji. It excludes keys that
     * manage keyboard-related stuff like shift, language switch, settings, layout switch, or
     * any key that results in multiple code points like the ".com" key.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleFunctionalEvent(
        event: Event,
        inputTransaction: InputTransaction,
    ) {
        when (event.mKeyCode) {
            Constants.CODE_DELETE -> {
                handleBackspaceEvent(event, inputTransaction)
            }

            Constants.CODE_SHIFT -> {
                performRecapitalization()
                inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
            }

            Constants.CODE_CAPSLOCK -> {}

            Constants.CODE_SYMBOL_SHIFT -> {}

            Constants.CODE_SWITCH_ALPHA_SYMBOL -> {}

            Constants.CODE_SETTINGS -> {
                onSettingsKeyPressed()
            }

            Constants.CODE_PASTE -> {
                mConnection.pasteClipboard()
            }

            Constants.CODE_ACTION_NEXT -> {
                performEditorAction(EditorInfo.IME_ACTION_NEXT)
            }

            Constants.CODE_ACTION_PREVIOUS -> {
                performEditorAction(EditorInfo.IME_ACTION_PREVIOUS)
            }

            Constants.CODE_LANGUAGE_SWITCH -> {
                handleLanguageSwitchKey()
            }

            Constants.CODE_SHIFT_ENTER -> {
                sendDownUpKeyEvent(
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.META_SHIFT_ON,
                )
            }

            else -> {
                throw RuntimeException("Unknown key code : " + event.mKeyCode)
            }
        }
    }

    /**
     * Handle an event that is not a functional event.
     *
     * These events are generally events that cause input, but in some cases they may do other
     * things like trigger an editor action.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleNonFunctionalEvent(
        event: Event,
        inputTransaction: InputTransaction,
    ) {
        when (event.mCodePoint) {
            Constants.CODE_ENTER -> {
                val editorInfo = this.currentInputEditorInfo
                val imeOptionsActionId =
                    InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo)
                if (InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId) {
                    // Either we have an actionLabel and we should performEditorAction with
                    // actionId regardless of its value.
                    performEditorAction(editorInfo.actionId)
                } else if (EditorInfo.IME_ACTION_NONE != imeOptionsActionId) {
                    // We didn't have an actionLabel, but we had another action to execute.
                    // EditorInfo.IME_ACTION_NONE explicitly means no action. In contrast,
                    // EditorInfo.IME_ACTION_UNSPECIFIED is the default value for an action, so it
                    // means there should be an action and the app didn't bother to set a specific
                    // code for it - presumably it only handles one. It does not have to be treated
                    // in any specific way: anything that is not IME_ACTION_NONE should be sent to
                    // performEditorAction.
                    performEditorAction(imeOptionsActionId)
                } else {
                    // No action label, and the action from imeOptions is NONE: this is a regular
                    // enter key that should input a carriage return.
                    handleNonSpecialCharacterEvent(event, inputTransaction)
                }
            }

            else -> {
                handleNonSpecialCharacterEvent(event, inputTransaction)
            }
        }
    }

    /**
     * Handle inputting a code point to the editor.
     *
     * Non-special keys are those that generate a single code point.
     * This includes all letters, digits, punctuation, separators, emoji. It excludes keys that
     * manage keyboard-related stuff like shift, language switch, settings, layout switch, or
     * any key that results in multiple code points like the ".com" key.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleNonSpecialCharacterEvent(
        event: Event,
        inputTransaction: InputTransaction,
    ) {
        val codePoint = event.mCodePoint
        if (inputTransaction.mSettingsValues!!.isWordSeparator(codePoint) ||
            Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()
        ) {
            handleSeparatorEvent(event, inputTransaction)
        } else {
            handleNonSeparatorEvent(event)
        }
    }

    /**
     * Handle a non-separator.
     * @param event The event to handle.
     */
    private fun handleNonSeparatorEvent(event: Event) {
        sendKeyCodePoint(event.mCodePoint)
    }

    /**
     * Handle input of a separator code point.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleSeparatorEvent(
        event: Event,
        inputTransaction: InputTransaction,
    ) {
        sendKeyCodePoint(event.mCodePoint)

        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
    }

    /**
     * Handle a press on the backspace key.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private fun handleBackspaceEvent(
        event: Event,
        inputTransaction: InputTransaction,
    ) {
        // In many cases after backspace, we need to update the shift state. Normally we need
        // to do this right away to avoid the shift state being out of date in case the user types
        // backspace then some other character very fast. However, in the case of backspace key
        // repeat, this can lead to flashiness when the cursor flies over positions where the
        // shift state should be updated, so if this is a key repeat, we update after a small delay.
        // Then again, even in the case of a key repeat, if the cursor is at start of text, it
        // can't go any further back, so we can update right away even if it's a key repeat.
        val shiftUpdateKind =
            if (event.isKeyRepeat && mConnection.expectedSelectionStart > 0) {
                InputTransaction.SHIFT_UPDATE_LATER
            } else {
                InputTransaction.SHIFT_UPDATE_NOW
            }
        inputTransaction.requireShiftUpdate(shiftUpdateKind)

        if (mConnection.hasSelection()) {
            mConnection.deleteSelectedText()
        } else {
            val codePointBeforeCursor = mConnection.getCodePointBeforeCursor()
            if (codePointBeforeCursor == Constants.NOT_A_CODE) {
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
            } else {
                val numChars =
                    if (Character.isSupplementaryCodePoint(codePointBeforeCursor)) 2 else 1
                mConnection.deleteTextBeforeCursor(numChars)
            }
        }
    }

    /**
     * Handle a press on the language switch key (the "globe key")
     */
    private fun handleLanguageSwitchKey() {
        mLatinIME.switchToNextSubtype()
    }

    /**
     * Performs a recapitalization event.
     */
    private fun performRecapitalization() {
        if (!mConnection.hasSelection() || !mRecapitalizeStatus.mIsEnabled()) {
            return // No selection or recapitalize is disabled for now
        }
        val selectionStart = mConnection.expectedSelectionStart
        val selectionEnd = mConnection.expectedSelectionEnd
        val numCharsSelected = selectionEnd - selectionStart
        if (numCharsSelected > Constants.MAX_CHARACTERS_FOR_RECAPITALIZATION) {
            // We bail out if we have too many characters for performance reasons. We don't want
            // to suck possibly multiple-megabyte data.
            return
        }
        // If we have a recapitalize in progress, use it; otherwise, start a new one.
        if (!mRecapitalizeStatus.isStarted ||
            !mRecapitalizeStatus.isSetAt(selectionStart, selectionEnd)
        ) {
            val selectedText = mConnection.selectedText
            if (TextUtils.isEmpty(selectedText)) return // Race condition with the input connection

            mRecapitalizeStatus.start(
                selectionStart,
                selectionEnd,
                selectedText.toString(),
                mLatinIME.currentLayoutLocale,
            )
            // We trim leading and trailing whitespace.
            mRecapitalizeStatus.trim()
        }
        mConnection.beginBatchEdit()
        mConnection.setSelection(selectionStart, selectionStart)
        mRecapitalizeStatus.rotate()
        mConnection.replaceText(
            selectionStart,
            selectionEnd,
            mRecapitalizeStatus.recapitalizedString,
        )
        mConnection.setSelection(
            mRecapitalizeStatus.newCursorStart,
            mRecapitalizeStatus.newCursorEnd,
        )
        mConnection.endBatchEdit()
    }

    /**
     * Gets the current auto-caps state, factoring in the space state.
     *
     * This method tries its best to do this in the most efficient possible manner. It avoids
     * getting text from the editor if possible at all.
     * This is called from the KeyboardSwitcher (through a trampoline in LatinIME) because it
     * needs to know auto caps state to display the right layout.
     *
     * @param settingsValues the relevant settings values
     * @param layoutSetName the name of the current keyboard layout set
     * @return a caps mode from TextUtils.CAP_MODE_* or Constants.TextUtils.CAP_MODE_OFF.
     */
    fun getCurrentAutoCapsState(
        settingsValues: SettingsValues,
        layoutSetName: String,
    ): Int {
        if (!settingsValues.mAutoCap || !layoutUsesAutoCaps(layoutSetName)) {
            return Constants.TextUtils.CAP_MODE_OFF
        }

        val ei = this.currentInputEditorInfo
        val inputType = ei.inputType
        // Warning: this depends on mSpaceState, which may not be the most current value. If
        // mSpaceState gets updated later, whoever called this may need to be told about it.
        return mConnection.getCursorCapsMode(inputType, settingsValues.mSpacingAndPunctuations)
    }

    private fun layoutUsesAutoCaps(layoutSetName: String): Boolean =
        when (layoutSetName) {
            SubtypeLocaleUtils.LAYOUT_ARABIC, SubtypeLocaleUtils.LAYOUT_BENGALI, SubtypeLocaleUtils.LAYOUT_BENGALI_AKKHOR, SubtypeLocaleUtils.LAYOUT_BENGALI_UNIJOY, SubtypeLocaleUtils.LAYOUT_FARSI, SubtypeLocaleUtils.LAYOUT_GEORGIAN, SubtypeLocaleUtils.LAYOUT_HEBREW, SubtypeLocaleUtils.LAYOUT_HINDI, SubtypeLocaleUtils.LAYOUT_HINDI_COMPACT, SubtypeLocaleUtils.LAYOUT_KANNADA, SubtypeLocaleUtils.LAYOUT_KHMER, SubtypeLocaleUtils.LAYOUT_LAO, SubtypeLocaleUtils.LAYOUT_MALAYALAM, SubtypeLocaleUtils.LAYOUT_MARATHI, SubtypeLocaleUtils.LAYOUT_NEPALI_ROMANIZED, SubtypeLocaleUtils.LAYOUT_NEPALI_TRADITIONAL, SubtypeLocaleUtils.LAYOUT_TAMIL, SubtypeLocaleUtils.LAYOUT_TELUGU, SubtypeLocaleUtils.LAYOUT_THAI, SubtypeLocaleUtils.LAYOUT_URDU -> false
            else -> true
        }

    val currentRecapitalizeState: Int
        get() {
            if (!mRecapitalizeStatus.isStarted ||
                !mRecapitalizeStatus.isSetAt(
                    mConnection.expectedSelectionStart,
                    mConnection.expectedSelectionEnd,
                )
            ) {
                // Not recapitalizing at the moment
                return RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
            }
            return mRecapitalizeStatus.currentMode
        }

    private val currentInputEditorInfo: EditorInfo
        /*
         * return the editor info for the current editor
         */
        get() = mLatinIME.currentInputEditorInfo

    /*
     * @param actionId the action to perform
     */
    private fun performEditorAction(actionId: Int) {
        mConnection.performEditorAction(actionId)
    }

    /*
     * Perform the processing specific to inputting TLDs.
     *
     * Some keys input a TLD (specifically, the ".com" key) and this warrants some specific
     * processing. First, if this is a TLD, we ignore PHANTOM spaces -- this is done by type
     * of character in onCodeInput, but since this gets inputted as a whole string we need to
     * do it here specifically. Then, if the last character before the cursor is a period, then
     * we cut the dot at the start of ".com". This is because humans tend to type "www.google."
     * and then press the ".com" key and instinctively don't expect to get "www.google.com".
     *
     * @param text the raw text supplied to onTextInput
     * @return the text to actually send to the editor
     */
    private fun performSpecificTldProcessingOnTextInput(text: String): String {
        if (text.length <= 1 || text[0].code != Constants.CODE_PERIOD ||
            !Character.isLetter(
                text[1],
            )
        ) {
            // Not a tld: do nothing.
            return text
        }
        val codePointBeforeCursor = mConnection.getCodePointBeforeCursor()
        // If no code point, #getCodePointBeforeCursor returns NOT_A_CODE_POINT.
        if (Constants.CODE_PERIOD == codePointBeforeCursor) {
            return text.substring(1)
        }
        return text
    }

    /*
     * Handle a press on the settings key.
     */
    private fun onSettingsKeyPressed() {
        mLatinIME.launchSettings()
    }

    /*
     * Sends a DOWN key event followed by a UP key event to the editor.
     *
     * If possible at all, avoid using this method. It causes all sorts of race conditions with
     * the text view because it goes through a different, asynchronous binder. Also, batch edits
     * are ignored for key events. Use the normal software input methods instead.
     *
     * keyCode the key code to send inside the key event.
     */
    @JvmOverloads
    fun sendDownUpKeyEvent(
        keyCode: Int,
        metaState: Int = 0,
    ) {
        val eventTime = SystemClock.uptimeMillis()
        mConnection.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
            ),
        )
        mConnection.sendKeyEvent(
            KeyEvent(
                SystemClock.uptimeMillis(),
                eventTime,
                KeyEvent.ACTION_UP,
                keyCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
            ),
        )
    }

    /**
     * Sends a code point to the editor, using the most appropriate method.
     *
     * Normally we send code points with commitText, but there are some cases (where backward
     * compatibility is a concern for example) where we want to use deprecated methods.
     *
     * @param codePoint the code point to send.
     */
    // TODO: replace these two parameters with an InputTransaction
    private fun sendKeyCodePoint(codePoint: Int) {
        // TODO: Remove this special handling of digit letters.
        // For backward compatibility. See {@link InputMethodService#sendKeyChar(char)}.
        if (codePoint >= '0'.code && codePoint <= '9'.code) {
            sendDownUpKeyEvent(codePoint - '0'.code + KeyEvent.KEYCODE_0)
            return
        }

        mConnection.commitText(StringUtils.newSingleCodePointString(codePoint), 1)
    }
}
