/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.RelativeLayout
import rkr.simplekeyboard.inputmethod.latin.common.CoordinateUtils

class DrawingPreviewPlacerView(context: Context?, attrs: AttributeSet?) :
    RelativeLayout(context, attrs) {
    private val mKeyboardViewOrigin: IntArray = CoordinateUtils.newInstance()

    init {
        setWillNotDraw(false)
    }

    fun setKeyboardViewGeometry(originCoords: IntArray) {
        CoordinateUtils.copy(mKeyboardViewOrigin, originCoords)
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val originX = CoordinateUtils.x(mKeyboardViewOrigin)
        val originY = CoordinateUtils.y(mKeyboardViewOrigin)
        canvas.translate(originX.toFloat(), originY.toFloat())
        canvas.translate(-originX.toFloat(), -originY.toFloat())
    }
}
