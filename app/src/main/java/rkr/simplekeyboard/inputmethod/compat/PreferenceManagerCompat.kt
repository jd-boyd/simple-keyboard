/*
 * Copyright (C) 2020 Raimondas Rimkus
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
package rkr.simplekeyboard.inputmethod.compat

import android.content.Context
import android.content.SharedPreferences

object PreferenceManagerCompat {
    @JvmStatic
    fun getDeviceSharedPreferences(context: Context): SharedPreferences? {
        val deviceContext = context.createDeviceProtectedStorageContext() ?: return null
        return deviceContext.getSharedPreferences(
            deviceContext.packageName + "_preferences",
            Context.MODE_PRIVATE,
        )
    }
}
