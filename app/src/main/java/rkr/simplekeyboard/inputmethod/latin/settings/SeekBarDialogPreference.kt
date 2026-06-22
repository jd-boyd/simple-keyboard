/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2022 Raimondas Rimkus
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
package rkr.simplekeyboard.inputmethod.latin.settings

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import rkr.simplekeyboard.inputmethod.R
import kotlin.math.max
import kotlin.math.min

class SeekBarDialogPreference(context: Context, attrs: AttributeSet?) :
    DialogPreference(context, attrs), OnSeekBarChangeListener {
    interface ValueProxy {
        fun readValue(key: String?): Int
        fun readDefaultValue(key: String?): Int
        fun writeValue(value: Int, key: String?)
        fun writeDefaultValue(key: String?)
        fun getValueText(value: Int): String?
        fun feedbackValue(value: Int)
    }

    private val mMaxValue: Int
    private val mMinValue: Int
    private val mStepValue: Int

    private var mValueView: TextView? = null
    private var mSeekBar: SeekBar? = null

    private var mValueProxy: ValueProxy? = null

    init {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.SeekBarDialogPreference, 0, 0
        )
        mMaxValue = a.getInt(R.styleable.SeekBarDialogPreference_maxValue, 0)
        mMinValue = a.getInt(R.styleable.SeekBarDialogPreference_minValue, 0)
        mStepValue = a.getInt(R.styleable.SeekBarDialogPreference_stepValue, 0)
        a.recycle()
        setDialogLayoutResource(R.layout.seek_bar_dialog)
    }

    fun setInterface(proxy: ValueProxy) {
        mValueProxy = proxy
        val value = mValueProxy!!.readValue(getKey())
        setSummary(mValueProxy!!.getValueText(value))
    }

    override fun onCreateDialogView(): View {
        val view = super.onCreateDialogView()
        mSeekBar = view.findViewById<View?>(R.id.seek_bar_dialog_bar) as SeekBar
        mSeekBar!!.setMax(mMaxValue - mMinValue)
        mSeekBar!!.setOnSeekBarChangeListener(this)
        mValueView = view.findViewById<View?>(R.id.seek_bar_dialog_value) as TextView
        return view
    }

    private fun getProgressFromValue(value: Int): Int {
        return value - mMinValue
    }

    private fun getValueFromProgress(progress: Int): Int {
        return progress + mMinValue
    }

    private fun clipValue(value: Int): Int {
        val clippedValue = min(mMaxValue, max(mMinValue, value))
        if (mStepValue <= 1) {
            return clippedValue
        }
        return clippedValue - (clippedValue % mStepValue)
    }

    private fun getClippedValueFromProgress(progress: Int): Int {
        return clipValue(getValueFromProgress(progress))
    }

    override fun onBindDialogView(view: View?) {
        val value = mValueProxy!!.readValue(getKey())
        mValueView!!.setText(mValueProxy!!.getValueText(value))
        mSeekBar!!.setProgress(getProgressFromValue(clipValue(value)))
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        builder.setPositiveButton(android.R.string.ok, this)
            .setNegativeButton(android.R.string.cancel, this)
            .setNeutralButton(R.string.button_default, this)
    }

    override fun onClick(dialog: DialogInterface?, which: Int) {
        super.onClick(dialog, which)
        val key = getKey()
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            val value = mValueProxy!!.readDefaultValue(key)
            setSummary(mValueProxy!!.getValueText(value))
            mValueProxy!!.writeDefaultValue(key)
            return
        }
        if (which == DialogInterface.BUTTON_POSITIVE) {
            val value = getClippedValueFromProgress(mSeekBar!!.getProgress())
            setSummary(mValueProxy!!.getValueText(value))
            mValueProxy!!.writeValue(value, key)
            return
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        val value = getClippedValueFromProgress(progress)
        mValueView!!.setText(mValueProxy!!.getValueText(value))
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        mValueProxy!!.feedbackValue(getClippedValueFromProgress(seekBar.getProgress()))
    }
}
