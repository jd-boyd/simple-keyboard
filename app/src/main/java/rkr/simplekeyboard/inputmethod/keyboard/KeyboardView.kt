/*
 * Copyright (C) 2010 The Android Open Source Project
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
package rkr.simplekeyboard.inputmethod.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat.getDeviceSharedPreferences
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyDrawParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyVisualAttributes
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.settings.Settings
import rkr.simplekeyboard.inputmethod.latin.utils.TypefaceUtils
import kotlin.math.max
import kotlin.math.min

/*
 * The Keyboard_Key declare-styleable (from AOSP convention) doesn't match this class name,
 * but it is used by Key, KeyVisualAttributes, and KeyStylesSet across 30+ references.
 * Renaming it to KeyboardView would be invasive with no functional benefit.
 */
@SuppressLint("CustomViewStyleable")
open class KeyboardView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int = R.attr.keyboardViewStyle,
    ) : View(context, attrs, defStyle) {
        // XML attributes
        private val mKeyVisualAttributes: KeyVisualAttributes?

        // Default keyLabelFlags from KeyboardTheme.
        // Currently only "alignHintLabelToBottom" is supported.
        private val mDefaultKeyLabelFlags: Int
        private val mKeyHintLetterPadding: Float
        private val mKeyShiftedLetterHintPadding: Float
        private val mKeyTextShadowRadius: Float
        protected val verticalCorrection: Float
        private val mKeyBackground: Drawable?
        private val mFunctionalKeyBackground: Drawable
        private val mSpacebarBackground: Drawable
        private val mKeyBackgroundPadding = Rect()

        @JvmField
        protected var mCustomColor: Int = 0

        @JvmField
        protected var mTheme: KeyboardTheme? = null

        // Main keyboard
        // TODO: Consider having a dummy keyboard object to make this @NonNull
        private var mKeyboard: Keyboard? = null
        protected val keyDrawParams: KeyDrawParams = KeyDrawParams()

        // True if all keys should be drawn
        private var mInvalidateAllKeys = false

        // The keys that should be drawn
        private val mInvalidatedKeys = HashSet<Key>()

        // The working rectangle for clipping
        private val mClipRect = Rect()

        // The keyboard bitmap buffer for faster updates
        private var mOffscreenBuffer: Bitmap? = null

        // The canvas for the above mutable keyboard bitmap  */
        private val mOffscreenCanvas = Canvas()
        private val mPaint = Paint()
        private val mFontMetrics = Paint.FontMetrics()

        init {
            val keyboardViewAttr =
                context.obtainStyledAttributes(
                    attrs,
                    R.styleable.KeyboardView,
                    defStyle,
                    R.style.KeyboardView,
                )
            mKeyBackground = keyboardViewAttr.getDrawable(R.styleable.KeyboardView_keyBackground)
            mKeyBackground!!.getPadding(mKeyBackgroundPadding)
            val functionalKeyBackground =
                keyboardViewAttr.getDrawable(
                    R.styleable.KeyboardView_functionalKeyBackground,
                )
            mFunctionalKeyBackground = (
                functionalKeyBackground ?: mKeyBackground
            )
            val spacebarBackground =
                keyboardViewAttr.getDrawable(
                    R.styleable.KeyboardView_spacebarBackground,
                )
            mSpacebarBackground =
                (spacebarBackground ?: mKeyBackground)
            mKeyHintLetterPadding =
                keyboardViewAttr.getDimension(
                    R.styleable.KeyboardView_keyHintLetterPadding,
                    0.0f,
                )
            mKeyShiftedLetterHintPadding =
                keyboardViewAttr.getDimension(
                    R.styleable.KeyboardView_keyShiftedLetterHintPadding,
                    0.0f,
                )
            mKeyTextShadowRadius =
                keyboardViewAttr.getFloat(
                    R.styleable.KeyboardView_keyTextShadowRadius,
                    KET_TEXT_SHADOW_RADIUS_DISABLED,
                )
            this.verticalCorrection =
                keyboardViewAttr.getDimension(
                    R.styleable.KeyboardView_verticalCorrection,
                    0.0f,
                )
            keyboardViewAttr.recycle()

            val keyAttr =
                context.obtainStyledAttributes(
                    attrs,
                    R.styleable.Keyboard_Key,
                    defStyle,
                    R.style.KeyboardView,
                )
            mDefaultKeyLabelFlags = keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0)
            mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr)
            keyAttr.recycle()

            mPaint.isAntiAlias = true
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val keyboard = this.keyboard
            if (keyboard == null) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            // The main keyboard expands to the entire this {@link KeyboardView}.
            val width = keyboard.mOccupiedWidth + paddingLeft + getPaddingRight()
            val height = keyboard.mOccupiedHeight + paddingTop + paddingBottom
            setMeasuredDimension(width, height)
        }

        open var keyboard: Keyboard?

            // Returns the current keyboard being displayed by this view.
            get() = mKeyboard

        /*
         * Attaches a keyboard to this view. The keyboard can be switched at any time and the
         * view will re-layout itself to accommodate the keyboard.
         */
            set(keyboard) {
                mKeyboard = keyboard
                val keyHeight = keyboard!!.mMostCommonKeyHeight
                keyDrawParams.updateParams(keyHeight, mKeyVisualAttributes)
                keyDrawParams.updateParams(keyHeight, keyboard.mKeyVisualAttributes)
                val prefs =
                    getDeviceSharedPreferences(context)
                mCustomColor =
                    Settings.readKeyboardColor(
                        prefs!!,
                        context,
                    )
                mTheme =
                    Settings.getKeyboardTheme(context)
                invalidateAllKeys()
                requestLayout()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (canvas.isHardwareAccelerated) {
                onDrawKeyboard(canvas)
                return
            }

            val bufferNeedsUpdates = mInvalidateAllKeys || !mInvalidatedKeys.isEmpty()
            if (bufferNeedsUpdates || mOffscreenBuffer == null) {
                if (maybeAllocateOffscreenBuffer()) {
                    mInvalidateAllKeys = true
                    // TODO: Stop using the offscreen canvas even when in software rendering
                    mOffscreenCanvas.setBitmap(mOffscreenBuffer)
                }
                onDrawKeyboard(mOffscreenCanvas)
            }
            canvas.drawBitmap(mOffscreenBuffer!!, 0.0f, 0.0f, null)
        }

        private fun maybeAllocateOffscreenBuffer(): Boolean {
            val width = getWidth()
            val height = getHeight()
            if (width == 0 || height == 0) {
                return false
            }
            if (mOffscreenBuffer != null && mOffscreenBuffer!!.getWidth() == width && mOffscreenBuffer!!.getHeight() == height) {
                return false
            }
            freeOffscreenBuffer()
            mOffscreenBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            return true
        }

        private fun freeOffscreenBuffer() {
            mOffscreenCanvas.setBitmap(null)
            mOffscreenCanvas.setMatrix(null)
            if (mOffscreenBuffer != null) {
                mOffscreenBuffer!!.recycle()
                mOffscreenBuffer = null
            }
        }

        private fun onDrawKeyboard(canvas: Canvas) {
            val keyboard = this.keyboard ?: return

            val paint = mPaint
            val background = getBackground()
            if (background != null && mTheme!!.mCustomColorSupport) {
                if (keyboard.javaClass == MoreKeysKeyboard::class.java) {
                    background.setColorFilter(mCustomColor, PorterDuff.Mode.OVERLAY)
                } else {
                    setBackgroundColor(mCustomColor)
                }
            }
            // Calculate clip region and set.
            val drawAllKeys = mInvalidateAllKeys || mInvalidatedKeys.isEmpty()
            val isHardwareAccelerated = canvas.isHardwareAccelerated
            // TODO: Confirm if it's really required to draw all keys when hardware acceleration is on.
            if (drawAllKeys || isHardwareAccelerated) {
                if (!isHardwareAccelerated && background != null) {
                    // Need to draw keyboard background on {@link #mOffscreenBuffer}.
                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                    background.draw(canvas)
                }
                // Draw all keys.
                for (key in keyboard.sortedKeys) {
                    onDrawKey(key, canvas, paint)
                }
            } else {
                for (key in mInvalidatedKeys) {
                    if (!keyboard.hasKey(key)) {
                        continue
                    }
                    if (background != null) {
                        // Need to redraw key's background on {@link #mOffscreenBuffer}.
                        val x = key.x + paddingLeft
                        val y = key.y + paddingTop
                        mClipRect.set(x, y, x + key.width, y + key.height)
                        canvas.save()
                        canvas.clipRect(mClipRect)
                        canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                        background.draw(canvas)
                        canvas.restore()
                    }
                    onDrawKey(key, canvas, paint)
                }
            }

            mInvalidatedKeys.clear()
            mInvalidateAllKeys = false
        }

        private fun onDrawKey(
            key: Key,
            canvas: Canvas,
            paint: Paint,
        ) {
            val keyDrawX = key.x + paddingLeft
            val keyDrawY = key.y + paddingTop
            canvas.translate(keyDrawX.toFloat(), keyDrawY.toFloat())

            val attr = key.visualAttributes
            val params = keyDrawParams.mayCloneAndUpdateParams(key.height, attr)
            params.mAnimAlpha = Constants.Color.ALPHA_OPAQUE

            if (!key.isSpacer) {
                val background =
                    key.selectBackgroundDrawable(
                        mKeyBackground!!,
                        mFunctionalKeyBackground,
                        mSpacebarBackground,
                    )
                onDrawKeyBackground(key, canvas, background)
            }
            onDrawKeyTopVisuals(key, canvas, paint, params)

            canvas.translate(-keyDrawX.toFloat(), -keyDrawY.toFloat())
        }

        // Draw key background.
        protected fun onDrawKeyBackground(
            key: Key,
            canvas: Canvas,
            background: Drawable,
        ) {
            val keyWidth = key.width
            val keyHeight = key.height
            val padding = mKeyBackgroundPadding
            val bgWidth = keyWidth + padding.left + padding.right
            val bgHeight = keyHeight + padding.top + padding.bottom
            val bgX = -padding.left
            val bgY = -padding.top
            val bounds = background.getBounds()
            if (bgWidth != bounds.right || bgHeight != bounds.bottom) {
                background.setBounds(0, 0, bgWidth, bgHeight)
            }
            canvas.translate(bgX.toFloat(), bgY.toFloat())
            background.draw(canvas)
            canvas.translate(-bgX.toFloat(), -bgY.toFloat())
        }

        // Draw key top visuals.
        protected open fun onDrawKeyTopVisuals(
            key: Key,
            canvas: Canvas,
            paint: Paint,
            params: KeyDrawParams,
        ) {
            val keyWidth = key.width
            val keyHeight = key.height
            val centerX = keyWidth * 0.5f
            val centerY = keyHeight * 0.5f

            // Draw key label.
            val keyboard = this.keyboard
            val icon =
                if (keyboard == null) {
                    null
                } else {
                    key.getIcon(keyboard.mIconsSet, params.mAnimAlpha)
                }
            var labelX = centerX
            var labelBaseline = centerY
            val label = key.label
            if (label != null) {
                paint.setTypeface(key.selectTypeface(params))
                paint.textSize = key.selectTextSize(params).toFloat()
                val labelCharHeight = TypefaceUtils.getReferenceCharHeight(paint)
                val labelCharWidth = TypefaceUtils.getReferenceCharWidth(paint)

                // Vertical label text alignment.
                labelBaseline = centerY + labelCharHeight / 2.0f

                // Horizontal label text alignment
                if (key.isAlignLabelOffCenter) {
                    // The label is placed off center of the key. Used mainly on "phone number" layout.
                    labelX = centerX + params.mLabelOffCenterRatio * labelCharWidth
                    paint.textAlign = Align.LEFT
                } else {
                    labelX = centerX
                    paint.textAlign = Align.CENTER
                }
                if (key.needsAutoXScale()) {
                    val ratio =
                        min(
                            1.0f,
                            (keyWidth * MAX_LABEL_RATIO) /
                                TypefaceUtils.getStringWidth(label, paint),
                        )
                    if (key.needsAutoScale()) {
                        val autoSize = paint.textSize * ratio
                        paint.textSize = autoSize
                    } else {
                        paint.textScaleX = ratio
                    }
                }

                paint.setColor(key.selectTextColor(params))
                // Set a drop shadow for the text if the shadow radius is positive value.
                if (mKeyTextShadowRadius > 0.0f) {
                    paint.setShadowLayer(mKeyTextShadowRadius, 0.0f, 0.0f, params.mTextShadowColor)
                } else {
                    paint.clearShadowLayer()
                }

                blendAlpha(paint, params.mAnimAlpha)
                canvas.drawText(label, 0, label.length, labelX, labelBaseline, paint)
                // Turn off drop shadow and reset x-scale.
                paint.clearShadowLayer()
                paint.textScaleX = 1.0f
            }

            // Draw hint label.
            val hintLabel = key.hintLabel
            if (hintLabel != null) {
                paint.textSize = key.selectHintTextSize(params).toFloat()
                paint.setColor(key.selectHintTextColor(params))
                // TODO: Should add a way to specify type face for hint letters
                paint.setTypeface(Typeface.DEFAULT_BOLD)
                blendAlpha(paint, params.mAnimAlpha)
                val labelCharHeight = TypefaceUtils.getReferenceCharHeight(paint)
                val labelCharWidth = TypefaceUtils.getReferenceCharWidth(paint)
                val hintX: Float
                val hintBaseline: Float
                if (key.hasHintLabel()) {
                    // The hint label is placed just right of the key label. Used mainly on
                    // "phone number" layout.
                    hintX = labelX + params.mHintLabelOffCenterRatio * labelCharWidth
                    hintBaseline =
                        if (key.isAlignHintLabelToBottom(mDefaultKeyLabelFlags)) {
                            labelBaseline
                        } else {
                            centerY + labelCharHeight / 2.0f
                        }
                    paint.textAlign = Align.LEFT
                } else if (key.hasShiftedLetterHint()) {
                    // The hint label is placed at top-right corner of the key. Used mainly on tablet.
                    hintX = keyWidth - mKeyShiftedLetterHintPadding - labelCharWidth / 2.0f
                    paint.getFontMetrics(mFontMetrics)
                    hintBaseline = -mFontMetrics.top
                    paint.textAlign = Align.CENTER
                } else { // key.hasHintLetter()
                    // The hint letter is placed at top-right corner of the key. Used mainly on phone.
                    val hintDigitWidth = TypefaceUtils.getReferenceDigitWidth(paint)
                    val hintLabelWidth = TypefaceUtils.getStringWidth(hintLabel, paint)
                    hintX = (
                        keyWidth - mKeyHintLetterPadding -
                            max(hintDigitWidth, hintLabelWidth) / 2.0f
                    )
                    hintBaseline = -paint.ascent()
                    paint.textAlign = Align.CENTER
                }
                val adjustmentY = params.mHintLabelVerticalAdjustment * labelCharHeight
                canvas.drawText(
                    hintLabel,
                    0,
                    hintLabel.length,
                    hintX,
                    hintBaseline + adjustmentY,
                    paint,
                )
            }

            // Draw key icon.
            if (label == null && icon != null) {
                val iconWidth = min(icon.intrinsicWidth, keyWidth)
                val iconHeight = icon.intrinsicHeight
                val iconY: Int =
                    if (key.isAlignIconToBottom) {
                        keyHeight - iconHeight
                    } else {
                        (keyHeight - iconHeight) / 2 // Align vertically center.
                    }
                val iconX = (keyWidth - iconWidth) / 2 // Align horizontally center.
                drawIcon(canvas, icon, iconX, iconY, iconWidth, iconHeight)
            }
        }

        fun newLabelPaint(key: Key?): Paint {
            val paint = Paint()
            paint.isAntiAlias = true
            if (key == null) {
                paint.setTypeface(keyDrawParams.mTypeface)
                paint.textSize = keyDrawParams.mLabelSize.toFloat()
            } else {
                paint.setColor(key.selectTextColor(this.keyDrawParams))
                paint.setTypeface(key.selectTypeface(this.keyDrawParams))
                paint.textSize = key.selectTextSize(this.keyDrawParams).toFloat()
            }
            return paint
        }

        /**
         * Requests a redraw of the entire keyboard. Calling [.invalidate] is not sufficient
         * because the keyboard renders the keys to an off-screen buffer and an invalidate() only
         * draws the cached buffer.
         * @see .invalidateKey
         */
        fun invalidateAllKeys() {
            mInvalidatedKeys.clear()
            mInvalidateAllKeys = true
            invalidate()
        }

        /**
         * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
         * one key is changing it's content. Any changes that affect the position or size of the key
         * may not be honored.
         * @param key key in the attached [Keyboard].
         * @see .invalidateAllKeys
         */
        fun invalidateKey(key: Key?) {
            if (mInvalidateAllKeys || key == null) {
                return
            }
            mInvalidatedKeys.add(key)
            val x = key.x + getPaddingLeft()
            val y = key.y + paddingTop
            invalidate(x, y, x + key.width, y + key.height)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            freeOffscreenBuffer()
        }

        fun deallocateMemory() {
            freeOffscreenBuffer()
        }

        companion object {
            private const val KET_TEXT_SHADOW_RADIUS_DISABLED = -1.0f

            // The maximum key label width in the proportion to the key width.
            private const val MAX_LABEL_RATIO = 0.90f

            private fun blendAlpha(
                paint: Paint,
                alpha: Int,
            ) {
                val color = paint.color
                paint.setARGB(
                    (paint.alpha * alpha) / Constants.Color.ALPHA_OPAQUE,
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color),
                )
            }

            protected fun drawIcon(
                canvas: Canvas,
                icon: Drawable,
                x: Int,
                y: Int,
                width: Int,
                height: Int,
            ) {
                canvas.translate(x.toFloat(), y.toFloat())
                icon.setBounds(0, 0, width, height)
                icon.draw(canvas)
                canvas.translate(-x.toFloat(), -y.toFloat())
            }
        }
    }
