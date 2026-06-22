/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2019 Raimondas Rimkus
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
package rkr.simplekeyboard.inputmethod.event

import rkr.simplekeyboard.inputmethod.latin.common.StringUtils

/*
 * Class representing a generic input event as handled by Latin IME.
 *
 * This contains information about the origin of the event, but it is generalized and should
 * represent a software keypress, hardware keypress, or d-pad move alike.
 * Very importantly, this does not necessarily result in inputting one character, or even anything
 * at all - it may be a dead key, it may be a partial input, it may be a special key on the
 * keyboard, it may be a cancellation of a keypress (e.g. in a soft keyboard the finger of the
 * user has slid out of the key), etc. It may also be a batch input from a gesture or handwriting
 * for example.
 * The combiner should figure out what to do with this.
 */
class Event private constructor( // The type of event - one of the constants above
    private val mEventType: Int,
    val mText: CharSequence?, // The code point associated with the event, if relevant. This is a Unicode code point, and
    // has nothing to do with other representations of the key. It is only relevant if this event
    // is of KEYPRESS type, but for a mode key like hankaku/zenkaku or ctrl, there is no code point
    // associated so this should be NOT_A_CODE_POINT to avoid unintentional use of its value when
    // it's not relevant.
    var mCodePoint: Int,
    keyCode: Int,
    flags: Int,
    next: Event?,
) {
    // The key code associated with the event, if relevant. This is relevant whenever this event
    // has been triggered by a key press, but not for a gesture for example. This has conceptually
    // no link to the code point, although keys that enter a straight code point may often set
    // this to be equal to mCodePoint for convenience. If this is not a key, this must contain
    // NOT_A_KEY_CODE.
    @JvmField
    val mKeyCode: Int

    // Some flags that can't go into the key code. It's a bit field of FLAG_*
    private val mFlags: Int

    // The next event, if any. Null if there is no next event yet.
    @JvmField
    val mNextEvent: Event?

    // This method is private - to create a new event, use one of the create* utility methods.
    init {
        mCodePoint = mCodePoint
        mKeyCode = keyCode
        mFlags = flags
        mNextEvent = next
    }

    val isFunctionalKeyEvent: Boolean
        // Returns whether this is a function key like backspace, ctrl, settings... as opposed to keys
        get() = // This logic may need to be refined in the future
            NOT_A_CODE_POINT == mCodePoint

    val isKeyRepeat: Boolean
        get() = 0 != (FLAG_REPEAT and mFlags)

    val isConsumed: Boolean
        get() = 0 != (FLAG_CONSUMED and mFlags)

    val textToCommit: CharSequence?
        get() {
            if (this.isConsumed) {
                return "" // A consumed event should input no text.
            }
            when (mEventType) {
                EVENT_TYPE_MODE_KEY, EVENT_TYPE_NOT_HANDLED, EVENT_TYPE_TOGGLE, EVENT_TYPE_CURSOR_MOVE -> return ""

                EVENT_TYPE_INPUT_KEYPRESS -> return StringUtils.newSingleCodePointString(
                    mCodePoint,
                )

                EVENT_TYPE_SOFTWARE_GENERATED_STRING -> return mText
            }
            throw RuntimeException("Unknown event type: $mEventType")
        }

    companion object {
        // Should the types below be represented by separate classes instead? It would be cleaner
        // but probably a bit too much
        // An event we don't handle in Latin IME, for example pressing Ctrl on a hardware keyboard.
        const val EVENT_TYPE_NOT_HANDLED: Int = 0

        // A key press that is part of input, for example pressing an alphabetic character on a
        // hardware qwerty keyboard. It may be part of a sequence that will be re-interpreted later
        // through combination.
        const val EVENT_TYPE_INPUT_KEYPRESS: Int = 1

        // A toggle event is triggered by a key that affects the previous character. An example would
        // be a numeric key on a 10-key keyboard, which would toggle between 1 - a - b - c with
        // repeated presses.
        const val EVENT_TYPE_TOGGLE: Int = 2

        // A mode event instructs the combiner to change modes. The canonical example would be the
        // hankaku/zenkaku key on a Japanese keyboard, or even the caps lock key on a qwerty keyboard
        // if handled at the combiner level.
        const val EVENT_TYPE_MODE_KEY: Int = 3

        // An event corresponding to a string generated by some software process.
        const val EVENT_TYPE_SOFTWARE_GENERATED_STRING: Int = 6

        // An event corresponding to a cursor move
        const val EVENT_TYPE_CURSOR_MOVE: Int = 7

        // 0 is a valid code point, so we use -1 here.
        const val NOT_A_CODE_POINT: Int = -1

        // -1 is a valid key code, so we use 0 here.
        const val NOT_A_KEY_CODE: Int = 0

        private const val FLAG_NONE = 0

        // This event is coming from a key repeat, software or hardware.
        private const val FLAG_REPEAT = 0x2

        // This event has already been consumed.
        private const val FLAG_CONSUMED = 0x4

        @JvmStatic
        fun createSoftwareKeypressEvent(
            codePoint: Int,
            keyCode: Int,
            isKeyRepeat: Boolean,
        ): Event =
            Event(
                EVENT_TYPE_INPUT_KEYPRESS,
                null,
                codePoint,
                keyCode,
                if (isKeyRepeat) FLAG_REPEAT else FLAG_NONE,
                null,
            )

        /**
         * Creates an input event with a CharSequence. This is used by some software processes whose
         * output is a string, possibly with styling. Examples include press on a multi-character key,
         * or combination that outputs a string.
         * @param text the CharSequence associated with this event.
         * @param keyCode the key code, or NOT_A_KEYCODE if not applicable.
         * @return an event for this text.
         */
        @JvmStatic
        fun createSoftwareTextEvent(
            text: CharSequence?,
            keyCode: Int,
        ): Event =
            Event(
                EVENT_TYPE_SOFTWARE_GENERATED_STRING,
                text,
                NOT_A_CODE_POINT,
                keyCode,
                FLAG_NONE,
                null, // next
            )
    }
}
