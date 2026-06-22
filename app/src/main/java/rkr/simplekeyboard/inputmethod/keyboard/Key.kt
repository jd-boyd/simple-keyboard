/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2023 Raimondas Rimkus
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
package rkr.simplekeyboard.inputmethod.keyboard

import android.content.res.TypedArray
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyDrawParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeySpecParser
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyStyle
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyVisualAttributes
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardIconsSet
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardRow
import rkr.simplekeyboard.inputmethod.keyboard.internal.MoreKeySpec
import rkr.simplekeyboard.inputmethod.keyboard.internal.MoreKeySpec.LettersOnBaseLayout
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.common.StringUtils
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Class for describing the position and characteristics of a single key in the keyboard.
 */
open class Key : Comparable<Key> {
    // The key code (Unicode or custom code) that this key generates.
    val code: Int

    // Label to display
    val label: String?

    // Hint label to display on the key in conjunction with the label
    val hintLabel: String?

    // Flags of the label
    private val mLabelFlags: Int

    // Icon to display instead of a label. Icon takes precedence over a label
    val iconId: Int

    /*
     * Gets the width of the key in pixels, excluding the padding.
     * @return The width of the key in pixels, excluding the padding.
     */
    val width: Int

    /*
     * Gets the height of the key in pixels, excluding the padding.
     */
    val height: Int

    /*
     * Get the theoretical width of the key in pixels, excluding the padding. This is the exact
     * width that the key was defined to be, but this will likely differ from the drawn width
     * because the normal (drawn/functional) width was determined by rounding the left and right
     * edge to fit evenly in a pixel.
     */
    val definedWidth: Float

    /*
     * Get the theoretical height of the key in pixels, excluding the padding. This is the exact
     * height that the key was defined to be, but this will likely differ from the drawn
     * height because the normal (drawn/functional) width was determined by rounding the top and
     * bottom edge to fit evenly in a pixel.
     */
    val definedHeight: Float

    /*
     * Gets the x-coordinate of the top-left corner of the key in pixels, excluding the padding.
     * @return The x-coordinate of the top-left corner of the key in pixels, excluding the padding.
     */
    val x: Int

    /*
     * Gets the y-coordinate of the top-left corner of the key in pixels, excluding the padding.
     * @return The y-coordinate of the top-left corner of the key in pixels, excluding the padding.
     */
    val y: Int

    // Hit bounding box of the key
    private val mHitbox = Rect()

    // More keys. It is guaranteed that this is null or an array of one or more elements
    val moreKeys: Array<MoreKeySpec?>?

    // More keys column number and flags
    private val mMoreKeysColumnAndFlags: Int

    /** Background type that represents different key background visual than normal one.  */
    private val mBackgroundType: Int
    private val mActionFlags: Int
    val visualAttributes: KeyVisualAttributes?
    private val mOptionalAttributes: OptionalAttributes?

    private class OptionalAttributes(
        /** Text to output when pressed. This can be multiple characters, like ".com"  */
        val mOutputText: String?,
        val mAltCode: Int,
    ) {
        companion object {
            fun newInstance(
                outputText: String?,
                altCode: Int,
            ): OptionalAttributes? {
                if (outputText == null && altCode == Constants.CODE_UNSPECIFIED) {
                    return null
                }
                return OptionalAttributes(outputText, altCode)
            }
        }
    }

    private val mHashCode: Int

    /** The current pressed state of this key  */
    private var mPressed = false

    /*
     * Constructor for a key on `MoreKeyKeyboard`.
     */
    constructor(
        label: String?,
        iconId: Int,
        code: Int,
        outputText: String?,
        hintLabel: String?,
        labelFlags: Int,
        backgroundType: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        leftPadding: Float,
        rightPadding: Float,
        topPadding: Float,
        bottomPadding: Float,
    ) {
        mHitbox.set(
            (x - leftPadding).roundToInt(),
            (y - topPadding).roundToInt(),
            (x + width + rightPadding).roundToInt(),
            (y + height + bottomPadding).roundToInt(),
        )
        this.x = x.roundToInt()
        this.y = y.roundToInt()
        this.width = (x + width).roundToInt() - this.x
        this.height = (y + height).roundToInt() - this.y
        this.definedWidth = width
        this.definedHeight = height
        this.hintLabel = hintLabel
        mLabelFlags = labelFlags
        mBackgroundType = backgroundType
        // TODO: Pass keyActionFlags as an argument.
        mActionFlags = ACTION_FLAGS_NO_KEY_PREVIEW
        this.moreKeys = null
        mMoreKeysColumnAndFlags = 0
        this.label = label
        mOptionalAttributes = OptionalAttributes.newInstance(outputText, Constants.CODE_UNSPECIFIED)
        this.code = code
        this.iconId = iconId
        this.visualAttributes = null

        mHashCode = computeHashCode(this)
    }

    /**
     * Create a key with the given top-left coordinate and extract its attributes from a key
     * specification string, Key attribute array, key style, etc.
     *
     * @param keySpec the key specification.
     * @param keyAttr the Key XML attributes array.
     * @param style the [KeyStyle] of this key.
     * @param params the keyboard building parameters.
     * @param row the row that this key belongs to. row's x-coordinate will be the right edge of
     * this key.
     */
    constructor(
        keySpec: String?,
        keyAttr: TypedArray,
        style: KeyStyle,
        params: KeyboardParams,
        row: KeyboardRow,
    ) {
        // Update the row to work with the new key
        row.setCurrentKey(keyAttr, this.isSpacer)

        this.definedWidth = row.keyWidth
        this.definedHeight = row.keyHeight

        val keyLeft = row.keyX
        val keyTop = row.keyY
        val keyRight = keyLeft + this.definedWidth
        val keyBottom = keyTop + this.definedHeight

        val leftPadding = row.keyLeftPadding
        val topPadding = row.keyTopPadding
        val rightPadding = row.keyRightPadding
        val bottomPadding = row.keyBottomPadding

        mHitbox.set(
            (keyLeft - leftPadding).roundToInt(),
            (keyTop - topPadding).roundToInt(),
            (keyRight + rightPadding).roundToInt(),
            (keyBottom + bottomPadding).roundToInt(),
        )
        this.x = keyLeft.roundToInt()
        this.y = keyTop.roundToInt()
        this.width = keyRight.roundToInt() - this.x
        this.height = keyBottom.roundToInt() - this.y

        mBackgroundType =
            style.getInt(
                keyAttr,
                R.styleable.Keyboard_Key_backgroundType,
                row.defaultBackgroundType,
            )

        mLabelFlags = (
            style.getFlags(
                keyAttr,
                R.styleable.Keyboard_Key_keyLabelFlags,
            )
                or row.defaultKeyLabelFlags
        )
        val needsToUpCase: Boolean = needsToUpcase(mLabelFlags, params.mId.mElementId)
        val localeForUpcasing = params.mId.locale
        var actionFlags = style.getFlags(keyAttr, R.styleable.Keyboard_Key_keyActionFlags)
        var moreKeys = style.getStringArray(keyAttr, R.styleable.Keyboard_Key_moreKeys)

        // Get maximum column order number and set a relevant mode value.
        var moreKeysColumnAndFlags: Int = (
            MORE_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER
                or
                style.getInt(
                    keyAttr,
                    R.styleable.Keyboard_Key_maxMoreKeysColumn,
                    params.mMaxMoreKeysKeyboardColumn,
                )
        )
        var value: Int
        if ((
                MoreKeySpec
                    .getIntValue(moreKeys, MORE_KEYS_AUTO_COLUMN_ORDER, -1)
                    .also { value = it }
            ) > 0
        ) {
            // Override with fixed column order number and set a relevant mode value.
            moreKeysColumnAndFlags = (
                MORE_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER
                    or (value and MORE_KEYS_COLUMN_NUMBER_MASK)
            )
        }
        if ((
                MoreKeySpec
                    .getIntValue(moreKeys, MORE_KEYS_FIXED_COLUMN_ORDER, -1)
                    .also { value = it }
            ) > 0
        ) {
            // Override with fixed column order number and set a relevant mode value.
            moreKeysColumnAndFlags = (
                MORE_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER
                    or (value and MORE_KEYS_COLUMN_NUMBER_MASK)
            )
        }
        if (MoreKeySpec.getBooleanValue(moreKeys, MORE_KEYS_HAS_LABELS)) {
            moreKeysColumnAndFlags = moreKeysColumnAndFlags or MORE_KEYS_FLAGS_HAS_LABELS
        }
        if (MoreKeySpec.getBooleanValue(moreKeys, MORE_KEYS_NO_PANEL_AUTO_MORE_KEY)) {
            moreKeysColumnAndFlags =
                moreKeysColumnAndFlags or MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY
        }
        mMoreKeysColumnAndFlags = moreKeysColumnAndFlags
        val additionalMoreKeys: Array<String?>? =
            if ((mLabelFlags and LABEL_FLAGS_DISABLE_ADDITIONAL_MORE_KEYS) != 0) {
                null
            } else {
                style.getStringArray(
                    keyAttr,
                    R.styleable.Keyboard_Key_additionalMoreKeys,
                )
            }
        moreKeys = MoreKeySpec.insertAdditionalMoreKeys(moreKeys, additionalMoreKeys)
        if (moreKeys != null) {
            actionFlags = actionFlags or ACTION_FLAGS_ENABLE_LONG_PRESS
            this.moreKeys = arrayOfNulls(moreKeys.size)
            for (i in moreKeys.indices) {
                this.moreKeys[i] = MoreKeySpec(moreKeys[i], needsToUpCase, localeForUpcasing)
            }
        } else {
            this.moreKeys = null
        }
        mActionFlags = actionFlags

        this.iconId = KeySpecParser.getIconId(keySpec)

        val code = KeySpecParser.getCode(keySpec)
        if ((mLabelFlags and LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL) != 0) {
            this.label = params.mId.mCustomActionLabel
        } else if (code >= Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            // This is a workaround to have a key that has a supplementary code point in its label.
            // Because we can put a string in resource neither as an XML entity of a supplementary
            // code point nor as a surrogate pair.
            this.label = StringBuilder().appendCodePoint(code).toString()
        } else {
            val label = KeySpecParser.getLabel(keySpec)
            this.label =
                if (needsToUpCase) {
                    StringUtils.toTitleCaseOfKeyLabel(label, localeForUpcasing)
                } else {
                    label
                }
        }
        if ((mLabelFlags and LABEL_FLAGS_DISABLE_HINT_LABEL) != 0) {
            this.hintLabel = null
        } else {
            val hintLabel =
                style.getString(
                    keyAttr,
                    R.styleable.Keyboard_Key_keyHintLabel,
                )
            this.hintLabel =
                if (needsToUpCase) {
                    StringUtils.toTitleCaseOfKeyLabel(hintLabel, localeForUpcasing)
                } else {
                    hintLabel
                }
        }
        var outputText = KeySpecParser.getOutputText(keySpec)
        if (needsToUpCase) {
            outputText = StringUtils.toTitleCaseOfKeyLabel(outputText, localeForUpcasing)
        }
        // Choose the first letter of the label as primary code if not specified.
        if (code == Constants.CODE_UNSPECIFIED && TextUtils.isEmpty(outputText) &&
            !TextUtils.isEmpty(this.label)
        ) {
            if (StringUtils.codePointCount(this.label) == 1) {
                // Use the first letter of the hint label if shiftedLetterActivated flag is
                // specified.
                if (hasShiftedLetterHint() && this.isShiftedLetterActivated) {
                    this.code = hintLabel!!.codePointAt(0)
                } else {
                    this.code = label!!.codePointAt(0)
                }
            } else {
                // In some locale and case, the character might be represented by multiple code
                // points, such as upper case Eszett of German alphabet.
                outputText = this.label
                this.code = Constants.CODE_OUTPUT_TEXT
            }
        } else if (code == Constants.CODE_UNSPECIFIED && outputText != null) {
            if (StringUtils.codePointCount(outputText) == 1) {
                this.code = outputText.codePointAt(0)
                outputText = null
            } else {
                this.code = Constants.CODE_OUTPUT_TEXT
            }
        } else {
            this.code =
                if (needsToUpCase) {
                    StringUtils.toTitleCaseOfKeyCode(code, localeForUpcasing)
                } else {
                    code
                }
        }
        val altCodeInAttr =
            KeySpecParser.parseCode(
                style.getString(keyAttr, R.styleable.Keyboard_Key_altCode),
                Constants.CODE_UNSPECIFIED,
            )
        val altCode =
            if (needsToUpCase) {
                StringUtils.toTitleCaseOfKeyCode(altCodeInAttr, localeForUpcasing)
            } else {
                altCodeInAttr
            }
        mOptionalAttributes = OptionalAttributes.newInstance(outputText, altCode)
        this.visualAttributes = KeyVisualAttributes.newInstance(keyAttr)
        mHashCode = computeHashCode(this)
    }

    /**
     * Copy constructor for DynamicGridKeyboard.GridKey.
     *
     * @param key the original key.
     */
    protected constructor(key: Key) : this(key, key.moreKeys)

    private constructor(key: Key, moreKeys: Array<MoreKeySpec?>?) {
        // Final attributes.
        this.code = key.code
        this.label = key.label
        this.hintLabel = key.hintLabel
        mLabelFlags = key.mLabelFlags
        this.iconId = key.iconId
        this.width = key.width
        this.height = key.height
        this.definedWidth = key.definedWidth
        this.definedHeight = key.definedHeight
        this.x = key.x
        this.y = key.y
        mHitbox.set(key.mHitbox)
        this.moreKeys = moreKeys
        mMoreKeysColumnAndFlags = key.mMoreKeysColumnAndFlags
        mBackgroundType = key.mBackgroundType
        mActionFlags = key.mActionFlags
        this.visualAttributes = key.visualAttributes
        mOptionalAttributes = key.mOptionalAttributes
        mHashCode = key.mHashCode
        // Key state.
        mPressed = key.mPressed
    }

    private fun equalsInternal(o: Key): Boolean {
        if (this === o) return true
        return o.x == this.x && o.y == this.y && o.width == this.width && o.height == this.height && o.code == this.code &&
            TextUtils.equals(
                o.label,
                this.label,
            ) &&
            TextUtils.equals(o.hintLabel, this.hintLabel) &&
            o.iconId == this.iconId && o.mBackgroundType == mBackgroundType &&
            o.moreKeys.contentEquals(
                this.moreKeys,
            ) && TextUtils.equals(o.outputText, this.outputText) &&
            o.mActionFlags == mActionFlags && o.mLabelFlags == mLabelFlags
    }

    override fun compareTo(other: Key): Int {
        if (equalsInternal(other)) return 0
        if (mHashCode > other.mHashCode) return 1
        return -1
    }

    override fun hashCode(): Int = mHashCode

    override fun equals(other: Any?): Boolean = other is Key && equalsInternal(other)

    override fun toString(): String = toShortString() + " " + this.x + "," + this.y + " " + this.width + "x" + this.height

    fun toShortString(): String? {
        val code = this.code
        if (code == Constants.CODE_OUTPUT_TEXT) {
            return this.outputText
        }
        return Constants.printableCode(code)
    }

    fun setHitboxRightEdge(right: Int) {
        mHitbox.right = right
    }

    val isSpacer: Boolean
        get() = this is Spacer

    val isShift: Boolean
        get() = this.code == Constants.CODE_SHIFT

    val isModifier: Boolean
        get() = this.code == Constants.CODE_SHIFT || this.code == Constants.CODE_SWITCH_ALPHA_SYMBOL

    val isRepeatable: Boolean
        get() = (mActionFlags and ACTION_FLAGS_IS_REPEATABLE) != 0

    fun noKeyPreview(): Boolean = (mActionFlags and ACTION_FLAGS_NO_KEY_PREVIEW) != 0

    fun altCodeWhileTyping(): Boolean = (mActionFlags and ACTION_FLAGS_ALT_CODE_WHILE_TYPING) != 0

    val isLongPressEnabled: Boolean
        get() = // We need not start long press timer on the key which has activated shifted letter.
            (mActionFlags and ACTION_FLAGS_ENABLE_LONG_PRESS) != 0 &&
                (mLabelFlags and LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) == 0

    fun selectTypeface(params: KeyDrawParams): Typeface? {
        when (mLabelFlags and LABEL_FLAGS_FONT_MASK) {
            LABEL_FLAGS_FONT_NORMAL -> {
                return Typeface.DEFAULT
            }

            LABEL_FLAGS_FONT_MONO_SPACE -> {
                return Typeface.MONOSPACE
            }

            LABEL_FLAGS_FONT_DEFAULT -> {
                // The type-face is specified by keyTypeface attribute.
                return params.mTypeface
            }

            else -> {
                return params.mTypeface
            }
        }
    }

    fun selectTextSize(params: KeyDrawParams): Int {
        when (mLabelFlags and LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK) {
            LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO -> return params.mLetterSize

            LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO -> return params.mLargeLetterSize

            LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO -> return params.mLabelSize

            LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO -> return params.mHintLabelSize

            else -> return if (StringUtils.codePointCount(
                    this.label,
                ) == 1
            ) {
                params.mLetterSize
            } else {
                params.mLabelSize
            }
        }
    }

    fun selectTextColor(params: KeyDrawParams): Int {
        if ((mLabelFlags and LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR) != 0) {
            return params.mFunctionalTextColor
        }
        return if (this.isShiftedLetterActivated) params.mTextInactivatedColor else params.mTextColor
    }

    fun selectHintTextSize(params: KeyDrawParams): Int {
        if (hasHintLabel()) {
            return params.mHintLabelSize
        }
        if (hasShiftedLetterHint()) {
            return params.mShiftedLetterHintSize
        }
        return params.mHintLetterSize
    }

    fun selectHintTextColor(params: KeyDrawParams): Int {
        if (hasHintLabel()) {
            return params.mHintLabelColor
        }
        if (hasShiftedLetterHint()) {
            return if (this.isShiftedLetterActivated) {
                params.mShiftedLetterHintActivatedColor
            } else {
                params.mShiftedLetterHintInactivatedColor
            }
        }
        return params.mHintLetterColor
    }

    val previewLabel: String?
        get() = if (this.isShiftedLetterActivated) this.hintLabel else this.label

    private fun previewHasLetterSize(): Boolean =
        (
            mLabelFlags and LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO
        ) != 0 ||
            StringUtils.codePointCount(this.previewLabel) == 1

    fun selectPreviewTextSize(params: KeyDrawParams): Int {
        if (previewHasLetterSize()) {
            return params.mPreviewTextSize
        }
        return params.mLetterSize
    }

    fun selectPreviewTypeface(params: KeyDrawParams): Typeface? {
        if (previewHasLetterSize()) {
            return selectTypeface(params)
        }
        return Typeface.DEFAULT_BOLD
    }

    fun isAlignHintLabelToBottom(defaultFlags: Int): Boolean =
        ((mLabelFlags or defaultFlags) and LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM) != 0

    val isAlignIconToBottom: Boolean
        get() = (mLabelFlags and LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM) != 0

    val isAlignLabelOffCenter: Boolean
        get() = (mLabelFlags and LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER) != 0

    fun hasShiftedLetterHint(): Boolean =
        (mLabelFlags and LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT) != 0 &&
            !TextUtils.isEmpty(this.hintLabel)

    fun hasHintLabel(): Boolean = (mLabelFlags and LABEL_FLAGS_HAS_HINT_LABEL) != 0

    fun needsAutoXScale(): Boolean = (mLabelFlags and LABEL_FLAGS_AUTO_X_SCALE) != 0

    fun needsAutoScale(): Boolean = (mLabelFlags and LABEL_FLAGS_AUTO_SCALE) == LABEL_FLAGS_AUTO_SCALE

    private val isShiftedLetterActivated: Boolean
        get() =
            (
                mLabelFlags and LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED
            ) != 0 &&
                !TextUtils.isEmpty(this.hintLabel)

    val moreKeysColumnNumber: Int
        get() = mMoreKeysColumnAndFlags and MORE_KEYS_COLUMN_NUMBER_MASK

    val isMoreKeysFixedColumn: Boolean
        get() = (mMoreKeysColumnAndFlags and MORE_KEYS_FLAGS_FIXED_COLUMN) != 0

    val isMoreKeysFixedOrder: Boolean
        get() = (mMoreKeysColumnAndFlags and MORE_KEYS_FLAGS_FIXED_ORDER) != 0

    fun hasLabelsInMoreKeys(): Boolean = (mMoreKeysColumnAndFlags and MORE_KEYS_FLAGS_HAS_LABELS) != 0

    val moreKeyLabelFlags: Int
        get() {
            val labelSizeFlag: Int =
                if (hasLabelsInMoreKeys()) {
                    LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO
                } else {
                    LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO
                }
            return labelSizeFlag or LABEL_FLAGS_AUTO_X_SCALE
        }

    fun hasNoPanelAutoMoreKey(): Boolean = (mMoreKeysColumnAndFlags and MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY) != 0

    val outputText: String?
        get() {
            val attrs = mOptionalAttributes
            return attrs?.mOutputText
        }

    val altCode: Int
        get() {
            val attrs = mOptionalAttributes
            return attrs?.mAltCode ?: Constants.CODE_UNSPECIFIED
        }

    fun getIcon(
        iconSet: KeyboardIconsSet,
        alpha: Int,
    ): Drawable? {
        val icon = iconSet.getIconDrawable(this.iconId)
        if (icon != null) {
            icon.alpha = alpha
        }
        return icon
    }

    fun getPreviewIcon(iconSet: KeyboardIconsSet): Drawable? = iconSet.getIconDrawable(this.iconId)

    val topPadding: Int
        /*
         * Gets the amount of padding for the hitbox above the key's visible position.
         * return The hitbox padding above the key.
         */
        get() = this.y - mHitbox.top

    val bottomPadding: Int
        /*
         * Gets the amount of padding for the hitbox below the key's visible position.
         * return The hitbox padding below the key.
         */
        get() = mHitbox.bottom - this.y - this.height

    val leftPadding: Int
        /*
         * Gets the amount of padding for the hitbox to the left of the key's visible position.
         * return The hitbox padding to the left of the key.
         */
        get() = this.x - mHitbox.left

    val rightPadding: Int
        /*
         * Gets the amount of padding for the hitbox to the right of the key's visible position.
         * return The hitbox padding to the right of the key.
         */
        get() = mHitbox.right - this.x - this.width

    /*
     * Informs the key that it has been pressed, in case it needs to change its appearance or
     * state.
     */
    fun onPressed() {
        mPressed = true
    }

    /**
     * Informs the key that it has been released, in case it needs to change its appearance or
     * state.
     * @see .onPressed
     */
    fun onReleased() {
        mPressed = false
    }

    /**
     * Detects if a point falls on this key.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether the point falls on the key. This generally includes all points
     * between the key and the keyboard edge for keys attached to an edge and all points between
     * the key and halfway to adjacent keys.
     */
    fun isOnKey(
        x: Int,
        y: Int,
    ): Boolean = mHitbox.contains(x, y)

    /**
     * Returns the square of the distance to the nearest clickable edge of the key and the given
     * point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the square of the distance of the point from the nearest edge of the key
     */
    fun squaredDistanceToHitboxEdge(
        x: Int,
        y: Int,
    ): Int {
        val left = mHitbox.left
        // The hit box right is exclusive
        val right = mHitbox.right - 1
        val top = mHitbox.top
        // The hit box bottom is exclusive
        val bottom = mHitbox.bottom - 1
        val edgeX = if (x < left) left else min(x, right)
        val edgeY = if (y < top) top else min(y, bottom)
        val dx = x - edgeX
        val dy = y - edgeY
        return dx * dx + dy * dy
    }

    internal class KeyBackgroundState private constructor(
        vararg attrs: Int,
    ) {
        private val mReleasedState: IntArray = attrs
        private val mPressedState: IntArray = attrs.copyOf(attrs.size + 1)

        fun getState(pressed: Boolean): IntArray = (if (pressed) mPressedState else mReleasedState)

        init {
            mPressedState[attrs.size] = android.R.attr.state_pressed
        }

        companion object {
            val STATES: Array<KeyBackgroundState?>
                get() =
                    arrayOf(
                        // 0: BACKGROUND_TYPE_EMPTY
                        KeyBackgroundState(android.R.attr.state_empty), // 1: BACKGROUND_TYPE_NORMAL
                        KeyBackgroundState(), // 2: BACKGROUND_TYPE_FUNCTIONAL
                        KeyBackgroundState(), // 3: BACKGROUND_TYPE_STICKY_OFF
                        KeyBackgroundState(android.R.attr.state_checkable), // 4: BACKGROUND_TYPE_STICKY_ON
                        KeyBackgroundState(
                            android.R.attr.state_checkable,
                            android.R.attr.state_checked,
                        ), // 5: BACKGROUND_TYPE_ACTION
                        KeyBackgroundState(android.R.attr.state_active), // 6: BACKGROUND_TYPE_SPACEBAR
                        KeyBackgroundState(),
                    )
        }
    }

    /**
     * Returns the background drawable for the key, based on the current state and type of the key.
     * @return the background drawable of the key.
     * @see android.graphics.drawable.StateListDrawable.setState
     */
    fun selectBackgroundDrawable(
        keyBackground: Drawable,
        functionalKeyBackground: Drawable,
        spacebarBackground: Drawable,
    ): Drawable {
        val background: Drawable =
            when (mBackgroundType) {
                BACKGROUND_TYPE_FUNCTIONAL -> {
                    functionalKeyBackground
                }

                BACKGROUND_TYPE_SPACEBAR -> {
                    spacebarBackground
                }

                else -> {
                    keyBackground
                }
            }
        val state = KeyBackgroundState.STATES[mBackgroundType]!!.getState(mPressed)
        background.setState(state)
        return background
    }

    class Spacer(
        keyAttr: TypedArray,
        keyStyle: KeyStyle,
        params: KeyboardParams,
        row: KeyboardRow,
    ) : Key(null, keyAttr, keyStyle, params, row)

    companion object {
        private const val LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM = 0x02
        private const val LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM = 0x04
        private const val LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER = 0x08

        // Font typeface specification.
        private const val LABEL_FLAGS_FONT_MASK = 0x30
        private const val LABEL_FLAGS_FONT_NORMAL = 0x10
        private const val LABEL_FLAGS_FONT_MONO_SPACE = 0x20
        private const val LABEL_FLAGS_FONT_DEFAULT = 0x30

        // Start of key text ratio enum values
        private const val LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK = 0x1C0
        private const val LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO = 0x40
        private const val LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO = 0x80
        private const val LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO = 0xC0
        private const val LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO = 0x140

        // End of key text ratio mask enum values
        private const val LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT = 0x400
        private const val LABEL_FLAGS_HAS_HINT_LABEL = 0x800

        // The bit to calculate the ratio of key label width against key width. If autoXScale bit is on
        // and autoYScale bit is off, the key label may be shrunk only for X-direction.
        // If both autoXScale and autoYScale bits are on, the key label text size may be auto-scaled.
        private const val LABEL_FLAGS_AUTO_X_SCALE = 0x4000
        private const val LABEL_FLAGS_AUTO_Y_SCALE = 0x8000
        private const val LABEL_FLAGS_AUTO_SCALE: Int = (
            LABEL_FLAGS_AUTO_X_SCALE
                or LABEL_FLAGS_AUTO_Y_SCALE
        )
        private const val LABEL_FLAGS_PRESERVE_CASE = 0x10000
        private const val LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED = 0x20000
        private const val LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL = 0x40000
        private const val LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR = 0x80000
        private const val LABEL_FLAGS_DISABLE_HINT_LABEL = 0x40000000
        private const val LABEL_FLAGS_DISABLE_ADDITIONAL_MORE_KEYS = -0x80000000

        private const val MORE_KEYS_COLUMN_NUMBER_MASK = 0x000000ff

        // If this flag is specified, more keys keyboard should have the specified number of columns.
        // Otherwise, more keys keyboard should have less than or equal to the specified maximum number
        // of columns.
        private const val MORE_KEYS_FLAGS_FIXED_COLUMN = 0x00000100

        // If this flag is specified, the order of more keys is determined by the order in the more
        // keys' specification. Otherwise, the order of more keys is automatically determined.
        private const val MORE_KEYS_FLAGS_FIXED_ORDER = 0x00000200
        private const val MORE_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER = 0
        private const val MORE_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER: Int = MORE_KEYS_FLAGS_FIXED_COLUMN
        private const val MORE_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER: Int =
            (MORE_KEYS_FLAGS_FIXED_COLUMN or MORE_KEYS_FLAGS_FIXED_ORDER)
        private const val MORE_KEYS_FLAGS_HAS_LABELS = 0x40000000
        private const val MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY = 0x10000000

        // TODO: Rename these specifiers to !autoOrder! and !fixedOrder! respectively.
        private const val MORE_KEYS_AUTO_COLUMN_ORDER = "!autoColumnOrder!"
        private const val MORE_KEYS_FIXED_COLUMN_ORDER = "!fixedColumnOrder!"
        private const val MORE_KEYS_HAS_LABELS = "!hasLabels!"
        private const val MORE_KEYS_NO_PANEL_AUTO_MORE_KEY = "!noPanelAutoMoreKey!"

        const val BACKGROUND_TYPE_NORMAL: Int = 1
        const val BACKGROUND_TYPE_FUNCTIONAL: Int = 2
        const val BACKGROUND_TYPE_SPACEBAR: Int = 6

        private const val ACTION_FLAGS_IS_REPEATABLE = 0x01
        private const val ACTION_FLAGS_NO_KEY_PREVIEW = 0x02
        private const val ACTION_FLAGS_ALT_CODE_WHILE_TYPING = 0x04
        private const val ACTION_FLAGS_ENABLE_LONG_PRESS = 0x08

        @JvmStatic
        fun removeRedundantMoreKeys(
            key: Key,
            lettersOnBaseLayout: LettersOnBaseLayout?,
        ): Key {
            val moreKeys = key.moreKeys
            val filteredMoreKeys =
                MoreKeySpec.removeRedundantMoreKeys(
                    moreKeys,
                    lettersOnBaseLayout,
                )
            return if (filteredMoreKeys.contentEquals(moreKeys)) key else Key(key, filteredMoreKeys)
        }

        private fun needsToUpcase(
            labelFlags: Int,
            keyboardElementId: Int,
        ): Boolean {
            if ((labelFlags and LABEL_FLAGS_PRESERVE_CASE) != 0) return false
            return when (keyboardElementId) {
                KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED,
                KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED,
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED,
                -> {
                    true
                }

                else -> {
                    false
                }
            }
        }

        private fun computeHashCode(key: Key): Int =
            arrayOf<Any?>(
                key.x,
                key.y,
                key.width,
                key.height,
                key.code,
                key.label,
                key.hintLabel,
                key.iconId,
                key.mBackgroundType,
                key.moreKeys.contentHashCode(),
                key.outputText,
                key.mActionFlags,
                key.mLabelFlags, // Key can be distinguishable without the following members.
                // key.mOptionalAttributes.mAltCode,
                // key.mOptionalAttributes.mDisabledIconId,
                // key.mOptionalAttributes.mPreviewIconId,
                // key.mMaxMoreKeysColumn,
                // key.mDefinedHeight,
                // key.mDefinedWidth,
            ).contentHashCode()
    }
}
