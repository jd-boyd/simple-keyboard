/*
 * Copyright (C) 2011 The Android Open Source Project
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
package rkr.simplekeyboard.inputmethod.keyboard

import android.animation.AnimatorInflater
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.keyboard.internal.DrawingPreviewPlacerView
import rkr.simplekeyboard.inputmethod.keyboard.internal.DrawingProxy
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyDrawParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyPreviewChoreographer
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyPreviewDrawParams
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyPreviewView
import rkr.simplekeyboard.inputmethod.keyboard.internal.NonDistinctMultitouchHelper
import rkr.simplekeyboard.inputmethod.keyboard.internal.TimerHandler
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager
import rkr.simplekeyboard.inputmethod.latin.Subtype
import rkr.simplekeyboard.inputmethod.latin.common.Constants
import rkr.simplekeyboard.inputmethod.latin.common.CoordinateUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LanguageOnSpacebarUtils
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils
import rkr.simplekeyboard.inputmethod.latin.utils.TypefaceUtils
import java.util.WeakHashMap
import kotlin.math.roundToInt

/*
 * A view that is responsible for detecting key presses and touch movements.
 */
class MainKeyboardView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int = R.attr.mainKeyboardViewStyle,
    ) : KeyboardView(context, attrs, defStyle),
        MoreKeysPanel.Controller,
        DrawingProxy {
        // Listener for [KeyboardActionListener].
        private var mKeyboardActionListener: KeyboardActionListener?

        // Space key and its icon and background.
        private var mSpaceKey: Key? = null

        // Stuff to draw language name on spacebar.
        private val mLanguageOnSpacebarFinalAlpha: Int
        private var mLanguageOnSpacebarFormatType = 0
        private val mLanguageOnSpacebarTextRatio: Float
        private var mLanguageOnSpacebarTextSize = 0f
        private val mLanguageOnSpacebarTextColor: Int

        // Stuff to draw altCodeWhileTyping keys.
        private val mAltCodeKeyWhileTypingFadeOutAnimator: ObjectAnimator?
        private val mAltCodeKeyWhileTypingFadeInAnimator: ObjectAnimator?
        private val mAltCodeKeyWhileTypingAnimAlpha = Constants.Color.ALPHA_OPAQUE

        // Drawing preview placer view
        private val mDrawingPreviewPlacerView: DrawingPreviewPlacerView
        private val mOriginCoords: IntArray = CoordinateUtils.newInstance()

        // Key preview
        private val mKeyPreviewDrawParams: KeyPreviewDrawParams
        private val mKeyPreviewChoreographer: KeyPreviewChoreographer

        // More keys keyboard
        private val mBackgroundDimAlphaPaint = Paint()
        private val mMoreKeysKeyboardContainer: View
        private val mMoreKeysKeyboardCache = WeakHashMap<Key?, Keyboard?>()
        private val mConfigShowMoreKeysKeyboardAtTouchedPoint: Boolean

        // More keys panel (used by both more keys keyboard and more suggestions view)
        // TODO: Consider extending to support multiple more keys panels
        private var mMoreKeysPanel: MoreKeysPanel? = null

        private val mKeyDetector: KeyDetector
        private val mNonDistinctMultitouchHelper: NonDistinctMultitouchHelper?

        private val mTimerHandler: TimerHandler
        private val mLanguageOnSpacebarHorizontalMargin: Int

        init {
            val drawingPreviewPlacerView =
                DrawingPreviewPlacerView(context, attrs)

            val mainKeyboardViewAttr =
                context.obtainStyledAttributes(
                    attrs,
                    R.styleable.MainKeyboardView,
                    defStyle,
                    R.style.MainKeyboardView,
                )
            val ignoreAltCodeKeyTimeout =
                mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout,
                    0,
                )
            mTimerHandler = TimerHandler(this, ignoreAltCodeKeyTimeout)

            val keyHysteresisDistance =
                mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_keyHysteresisDistance,
                    0.0f,
                )
            val keyHysteresisDistanceForSlidingModifier =
                mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier,
                    0.0f,
                )
            mKeyDetector =
                KeyDetector(
                    keyHysteresisDistance,
                    keyHysteresisDistanceForSlidingModifier,
                )

            PointerTracker.init(
                mainKeyboardViewAttr,
                mTimerHandler,
                this,
            )

            val hasDistinctMultitouch =
                context
                    .packageManager
                    .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
            mNonDistinctMultitouchHelper =
                if (hasDistinctMultitouch) {
                    null
                } else {
                    NonDistinctMultitouchHelper()
                }

            val backgroundDimAlpha =
                mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_backgroundDimAlpha,
                    0,
                )
            mBackgroundDimAlphaPaint.setColor(Color.BLACK)
            mBackgroundDimAlphaPaint.setAlpha(backgroundDimAlpha)
            mLanguageOnSpacebarTextRatio =
                mainKeyboardViewAttr.getFraction(
                    R.styleable.MainKeyboardView_languageOnSpacebarTextRatio,
                    1,
                    1,
                    1.0f,
                )
            mLanguageOnSpacebarTextColor =
                mainKeyboardViewAttr.getColor(
                    R.styleable.MainKeyboardView_languageOnSpacebarTextColor,
                    0,
                )
            mLanguageOnSpacebarFinalAlpha =
                mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_languageOnSpacebarFinalAlpha,
                    Constants.Color.ALPHA_OPAQUE,
                )
            val altCodeKeyWhileTypingFadeoutAnimatorResId =
                mainKeyboardViewAttr.getResourceId(
                    R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator,
                    0,
                )
            val altCodeKeyWhileTypingFadeInAnimatorResId =
                mainKeyboardViewAttr.getResourceId(
                    R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator,
                    0,
                )

            mKeyPreviewDrawParams = KeyPreviewDrawParams(mainKeyboardViewAttr)
            mKeyPreviewChoreographer = KeyPreviewChoreographer(mKeyPreviewDrawParams)

            val moreKeysKeyboardLayoutId =
                mainKeyboardViewAttr.getResourceId(
                    R.styleable.MainKeyboardView_moreKeysKeyboardLayout,
                    0,
                )
            mConfigShowMoreKeysKeyboardAtTouchedPoint =
                mainKeyboardViewAttr.getBoolean(
                    R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint,
                    false,
                )

            mainKeyboardViewAttr.recycle()

            mDrawingPreviewPlacerView = drawingPreviewPlacerView

            val inflater = LayoutInflater.from(getContext())
            mMoreKeysKeyboardContainer = inflater.inflate(moreKeysKeyboardLayoutId, null)
            mAltCodeKeyWhileTypingFadeOutAnimator =
                loadObjectAnimator(
                    altCodeKeyWhileTypingFadeoutAnimatorResId,
                    this,
                )
            mAltCodeKeyWhileTypingFadeInAnimator =
                loadObjectAnimator(
                    altCodeKeyWhileTypingFadeInAnimatorResId,
                    this,
                )

            mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER

            mLanguageOnSpacebarHorizontalMargin =
                resources
                    .getDimension(
                        R.dimen.config_language_on_spacebar_horizontal_margin,
                    ).toInt()
        }

        private fun loadObjectAnimator(
            resId: Int,
            target: Any?,
        ): ObjectAnimator? {
            if (resId == 0) {
                // TODO: Stop returning null.
                return null
            }
            val animator =
                AnimatorInflater.loadAnimator(
                    context,
                    resId,
                ) as ObjectAnimator?
            animator?.setTarget(target)
            return animator
        }

    /*
     * Implements {@link DrawingProxy#startWhileTypingAnimation(int)}.
     * Called when a while-typing-animation should be started.
     * @param fadeInOrOut [DrawingProxy.FADE_IN] starts while-typing-fade-in animation.
     * DrawingProxy.FADE_OUT starts while-typing-fade-out animation.
     */
        override fun startWhileTypingAnimation(fadeInOrOut: Int) {
            when (fadeInOrOut) {
                DrawingProxy.FADE_IN -> {
                    cancelAndStartAnimators(
                        mAltCodeKeyWhileTypingFadeOutAnimator,
                        mAltCodeKeyWhileTypingFadeInAnimator,
                    )
                }

                DrawingProxy.FADE_OUT -> {
                    cancelAndStartAnimators(
                        mAltCodeKeyWhileTypingFadeInAnimator,
                        mAltCodeKeyWhileTypingFadeOutAnimator,
                    )
                }
            }
        }

        fun setKeyboardActionListener(listener: KeyboardActionListener?) {
            mKeyboardActionListener = listener
            PointerTracker.setKeyboardActionListener(listener)
        }

        // TODO: We should reconsider which coordinate system should be used to represent keyboard
        // event.
        fun getKeyX(x: Int): Int = if (Constants.isValidCoordinate(x)) mKeyDetector.getTouchX(x) else x

        // TODO: We should reconsider which coordinate system should be used to represent keyboard
        // event.
        fun getKeyY(y: Int): Int = if (Constants.isValidCoordinate(y)) mKeyDetector.getTouchY(y) else y

    /*
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     *
     * @see .getKeyboard
     * @param keyboard the keyboard to display in this view
     */
        override var keyboard: Keyboard?
            get() = super.keyboard
            set(value) {
                // Remove any pending messages, except dismissing preview and key repeat.
                mTimerHandler.cancelLongPressTimers()
                super.keyboard = value
                mKeyDetector.setKeyboard(
                    value,
                    -getPaddingLeft().toFloat(),
                    -paddingTop + verticalCorrection,
                )
                PointerTracker.setKeyDetector(mKeyDetector)
                mMoreKeysKeyboardCache.clear()

                mSpaceKey = value?.getKey(Constants.CODE_SPACE)
                val keyHeight = value?.mMostCommonKeyHeight ?: return
                mLanguageOnSpacebarTextSize = keyHeight * mLanguageOnSpacebarTextRatio
            }

        /*
         * Enables or disables the key preview popup. This is a popup that shows a magnified
         * version of the depressed key. By default, the preview is enabled.
         * param previewEnabled whether to enable the key feedback preview
         * param delay the delay after which the preview is dismissed
         */
        fun setKeyPreviewPopupEnabled(
            previewEnabled: Boolean,
            delay: Int,
        ) {
            mKeyPreviewDrawParams.setPopupEnabled(previewEnabled, delay)
        }

        private fun locatePreviewPlacerView() {
            getLocationInWindow(mOriginCoords)
            mDrawingPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords)
        }

        private fun installPreviewPlacerView() {
            val rootView = getRootView()
            if (rootView == null) {
                Log.w(TAG, "Cannot find root view")
                return
            }
            val windowContentView = rootView.findViewById<ViewGroup?>(android.R.id.content)
            // Note: It'd be very weird if we get null by android.R.id.content.
            if (windowContentView == null) {
                Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView")
                return
            }
            windowContentView.addView(mDrawingPreviewPlacerView)
        }

        // Implements {@link DrawingProxy#onKeyPressed(Key,boolean)}.
        override fun onKeyPressed(
            key: Key,
            withPreview: Boolean,
        ) {
            key.onPressed()
            invalidateKey(key)
            if (withPreview && !key.noKeyPreview()) {
                showKeyPreview(key)
            }
        }

        private fun showKeyPreview(key: Key?) {
            val keyboard = keyboard ?: return
            val previewParams = mKeyPreviewDrawParams
            if (!previewParams.isPopupEnabled) {
                previewParams.visibleOffset = -keyboard.mVerticalGap.roundToInt()
                return
            }

            locatePreviewPlacerView()
            getLocationInWindow(mOriginCoords)
            val backgroundColor = if (mTheme!!.mCustomColorSupport) mCustomColor else Color.TRANSPARENT
            mKeyPreviewChoreographer.placeAndShowKeyPreview(
                key,
                keyboard.mIconsSet,
                keyDrawParams,
                mOriginCoords,
                mDrawingPreviewPlacerView,
                isHardwareAccelerated,
                backgroundColor,
            )
        }

        private fun dismissKeyPreviewWithoutDelay(key: Key?) {
            mKeyPreviewChoreographer.dismissKeyPreview(key, false)
            invalidateKey(key)
        }

        // Implements {@link DrawingProxy#onKeyReleased(Key,boolean)}.
        override fun onKeyReleased(
            key: Key,
            withAnimation: Boolean,
        ) {
            key.onReleased()
            invalidateKey(key)
            if (!key.noKeyPreview()) {
                if (withAnimation) {
                    dismissKeyPreview(key)
                } else {
                    dismissKeyPreviewWithoutDelay(key)
                }
            }
        }

        private fun dismissKeyPreview(key: Key?) {
            if (isHardwareAccelerated) {
                mKeyPreviewChoreographer.dismissKeyPreview(
                    key,
                    true,
                    // withAnimation
                )
                return
            }
            // TODO: Implement preference option to control key preview method and duration.
            mTimerHandler.postDismissKeyPreview(key, mKeyPreviewDrawParams.lingerTimeout.toLong())
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            installPreviewPlacerView()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            mDrawingPreviewPlacerView.removeAllViews()
        }

        // Implements {@link DrawingProxy@showMoreKeysKeyboard(Key,PointerTracker)}.
        // @Override
        override fun showMoreKeysKeyboard(
            key: Key,
            tracker: PointerTracker,
        ): MoreKeysPanel? {
            val moreKeys = key.moreKeys ?: return null
            var moreKeysKeyboard = mMoreKeysKeyboardCache[key]
            if (moreKeysKeyboard == null) {
                // {@link KeyPreviewDrawParams#mPreviewVisibleWidth} should have been set at
                // {@link KeyPreviewChoreographer#placeKeyPreview(Key,TextView,KeyboardIconsSet,KeyDrawParams,int,int[]},
                // though there may be some chances that the value is zero. <code>width == 0</code>
                // will cause zero-division error at
                // {@link MoreKeysKeyboardParams#setParameters(int,int,int,int,int,int,boolean,int)}.
                val isSingleMoreKeyWithPreview =
                    mKeyPreviewDrawParams.isPopupEnabled &&
                        !key.noKeyPreview() && moreKeys.size == 1 && mKeyPreviewDrawParams.visibleWidth > 0
                val builder =
                    MoreKeysKeyboard.Builder(
                        context,
                        key,
                        keyboard,
                        isSingleMoreKeyWithPreview,
                        mKeyPreviewDrawParams.visibleWidth,
                        mKeyPreviewDrawParams.visibleHeight,
                        newLabelPaint(key),
                    )
                moreKeysKeyboard = builder.build()
                mMoreKeysKeyboardCache[key] = moreKeysKeyboard
            }

            val moreKeysKeyboardView =
                mMoreKeysKeyboardContainer.findViewById<MoreKeysKeyboardView>(R.id.more_keys_keyboard_view)
            moreKeysKeyboardView.keyboard = moreKeysKeyboard
            mMoreKeysKeyboardContainer.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            val lastCoords = CoordinateUtils.newInstance()
            tracker.getLastCoordinates(lastCoords)
            val keyPreviewEnabled =
                mKeyPreviewDrawParams.isPopupEnabled &&
                    !key.noKeyPreview()
            // The more keys keyboard is usually horizontally aligned with the center of the parent key.
            // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
            // keys keyboard is placed at the touch point of the parent key.
            val pointX =
                if (mConfigShowMoreKeysKeyboardAtTouchedPoint && !keyPreviewEnabled) {
                    CoordinateUtils.x(lastCoords)
                } else {
                    key.x + key.width / 2
                }
            // The more keys keyboard is usually vertically aligned with the top edge of the parent key
            // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
            // aligned with the bottom edge of the visible part of the key preview.
            // {@code mPreviewVisibleOffset} has been set appropriately in
            // {@link KeyboardView#showKeyPreview(PointerTracker)}.
            val pointY = (
                key.y + mKeyPreviewDrawParams.visibleOffset +
                    moreKeysKeyboard.mBottomPadding.roundToInt()
            )
            moreKeysKeyboardView.showMoreKeysPanel(this, this, pointX, pointY, mKeyboardActionListener)
            return moreKeysKeyboardView
        }

        val isInDraggingFinger: Boolean
            get() {
                if (this.isShowingMoreKeysPanel) {
                    return true
                }
                return PointerTracker.isAnyInDraggingFinger()
            }

        val isInCursorMove: Boolean
            get() = PointerTracker.isAnyInCursorMove()

        override fun onShowMoreKeysPanel(panel: MoreKeysPanel) {
            locatePreviewPlacerView()
            // Dismiss another {@link MoreKeysPanel} that may be being shown.
            onDismissMoreKeysPanel()
            // Dismiss all key previews that may be being shown.
            PointerTracker.setReleasedKeyGraphicsToAllKeys()
            // Dismiss sliding key input preview that may be being shown.
            panel.showInParent(mDrawingPreviewPlacerView)
            mMoreKeysPanel = panel
        }

        val isShowingMoreKeysPanel: Boolean
            get() = mMoreKeysPanel != null && mMoreKeysPanel!!.isShowingInParent()

        override fun onCancelMoreKeysPanel() {
            PointerTracker.dismissAllMoreKeysPanels()
        }

        override fun onDismissMoreKeysPanel() {
            if (this.isShowingMoreKeysPanel) {
                mMoreKeysPanel!!.removeFromParent()
                mMoreKeysPanel = null
            }
        }

        fun startDoubleTapShiftKeyTimer() {
            mTimerHandler.startDoubleTapShiftKeyTimer()
        }

        fun cancelDoubleTapShiftKeyTimer() {
            mTimerHandler.cancelDoubleTapShiftKeyTimer()
        }

        val isInDoubleTapShiftKeyTimeout: Boolean
            get() = mTimerHandler.isInDoubleTapShiftKeyTimeout

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (keyboard == null) {
                return false
            }
            if (mNonDistinctMultitouchHelper != null) {
                if (event.pointerCount > 1 && mTimerHandler.isInKeyRepeat) {
                    // Key repeating timer will be canceled if 2 or more keys are in action.
                    mTimerHandler.cancelKeyRepeatTimers()
                }
                // Non-distinct multitouch screen support
                mNonDistinctMultitouchHelper.processMotionEvent(event, mKeyDetector)
                return true
            }
            return processMotionEvent(event)
        }

        fun processMotionEvent(event: MotionEvent): Boolean {
            val index = event.actionIndex
            val id = event.getPointerId(index)
            val tracker = PointerTracker.getPointerTracker(id)
            // When a more keys panel is showing, we should ignore other fingers' single touch events
            // other than the finger that is showing the more keys panel.
            if (this.isShowingMoreKeysPanel && !tracker.isShowingMoreKeysPanel && PointerTracker.getActivePointerTrackerCount() == 1) {
                return true
            }
            tracker.processMotionEvent(event, mKeyDetector)
            return true
        }

        fun cancelAllOngoingEvents() {
            mTimerHandler.cancelAllMessages()
            PointerTracker.setReleasedKeyGraphicsToAllKeys()
            PointerTracker.dismissAllMoreKeysPanels()
            PointerTracker.cancelAllPointerTrackers()
        }

        fun closing() {
            cancelAllOngoingEvents()
            mMoreKeysKeyboardCache.clear()
        }

        fun onHideWindow() {
            onDismissMoreKeysPanel()
        }

        fun startDisplayLanguageOnSpacebar(
            subtypeChanged: Boolean,
            languageOnSpacebarFormatType: Int,
        ) {
            if (subtypeChanged) {
                KeyPreviewView.clearTextCache()
            }
            mLanguageOnSpacebarFormatType = languageOnSpacebarFormatType
            invalidateKey(mSpaceKey)
        }

        override fun onDrawKeyTopVisuals(
            key: Key,
            canvas: Canvas,
            paint: Paint,
            params: KeyDrawParams,
        ) {
            if (key.altCodeWhileTyping()) {
                params.mAnimAlpha = mAltCodeKeyWhileTypingAnimAlpha
            }
            super.onDrawKeyTopVisuals(key, canvas, paint, params)
            val code = key.code
            if (code == Constants.CODE_SPACE) {
                // If more than one language is enabled in current input method
                val imm = RichInputMethodManager.getInstance()
                if (imm.hasMultipleEnabledSubtypes()) {
                    drawLanguageOnSpacebar(key, canvas, paint)
                }
            }
        }

        private fun fitsTextIntoWidth(
            width: Int,
            text: String?,
            paint: Paint,
        ): Boolean {
            val maxTextWidth = width - mLanguageOnSpacebarHorizontalMargin * 2
            paint.textScaleX = 1.0f
            val textWidth = TypefaceUtils.getStringWidth(text, paint)
            if (textWidth < width) {
                return true
            }

            val scaleX = maxTextWidth / textWidth
            if (scaleX < MINIMUM_X_SCALE_OF_LANGUAGE_NAME) {
                return false
            }

            paint.textScaleX = scaleX
            return TypefaceUtils.getStringWidth(text, paint) < maxTextWidth
        }

        // Layout language name on spacebar.
        private fun layoutLanguageOnSpacebar(
            paint: Paint,
            subtype: Subtype,
            width: Int,
        ): String {
            // Choose appropriate language name to fit into the width.
            if (mLanguageOnSpacebarFormatType == LanguageOnSpacebarUtils.FORMAT_TYPE_FULL_LOCALE) {
                val fullText =
                    LocaleResourceUtils.getLocaleDisplayNameInLocale(subtype.locale)
                if (fitsTextIntoWidth(width, fullText, paint)) {
                    return fullText
                }
            }

            val middleText =
                LocaleResourceUtils.getLanguageDisplayNameInLocale(subtype.locale)
            if (fitsTextIntoWidth(width, middleText, paint)) {
                return middleText
            }

            return ""
        }

        private fun drawLanguageOnSpacebar(
            key: Key,
            canvas: Canvas,
            paint: Paint,
        ) {
            val keyboard = keyboard ?: return
            val width = key.width
            val height = key.height
            paint.textAlign = Align.CENTER
            paint.setTypeface(Typeface.DEFAULT)
            paint.textSize = mLanguageOnSpacebarTextSize
            val language = layoutLanguageOnSpacebar(paint, keyboard.mId.mSubtype, width)
            // Draw language text with shadow
            val descent = paint.descent()
            val textHeight = -paint.ascent() + descent
            val baseline = height / 2 + textHeight / 2
            paint.setColor(mLanguageOnSpacebarTextColor)
            paint.setAlpha(mLanguageOnSpacebarFinalAlpha)
            canvas.drawText(language, (width / 2).toFloat(), baseline - descent, paint)
            paint.clearShadowLayer()
            paint.textScaleX = 1.0f
        }

        companion object {
            private val TAG: String = MainKeyboardView::class.java.getSimpleName()

            // The minimum x-scale to fit the language name on spacebar.
            private const val MINIMUM_X_SCALE_OF_LANGUAGE_NAME = 0.8f

            private fun cancelAndStartAnimators(
                animatorToCancel: ObjectAnimator?,
                animatorToStart: ObjectAnimator?,
            ) {
                if (animatorToCancel == null || animatorToStart == null) {
                    // TODO: Stop using null as a no-operation animator.
                    return
                }
                var startFraction = 0.0f
                if (animatorToCancel.isStarted) {
                    animatorToCancel.cancel()
                    startFraction = 1.0f - animatorToCancel.animatedFraction
                }
                val startTime = (animatorToStart.duration * startFraction).toLong()
                animatorToStart.start()
                animatorToStart.setCurrentPlayTime(startTime)
            }
        }
    }
