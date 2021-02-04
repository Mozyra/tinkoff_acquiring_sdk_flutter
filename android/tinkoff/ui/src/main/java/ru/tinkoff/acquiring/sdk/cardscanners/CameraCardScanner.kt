/*
 * Copyright © 2020 Tinkoff Bank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package ru.tinkoff.acquiring.sdk.cardscanners

import android.content.Context
import android.content.Intent
import ru.tinkoff.acquiring.sdk.cardscanners.models.ScannedCardData
import java.io.Serializable

/**
 * Осуществляет взаимодействие сканера карт и SDK
 *
 * @author Mariya Chernyadieva
 */
interface CameraCardScanner : Serializable {

    /**
     * Запуск экрана сканирования карты
     */
    fun startActivityForScanning(context: Context, requestCode: Int)

    /**
     * Определение результата сканирования
     */
    fun hasResult(data: Intent): Boolean

    /**
     * Возвращает результат сканирования (данные карты)
     */
    fun parseIntentData(data: Intent): ScannedCardData

    companion object {

        const val REQUEST_CAMERA_CARD_SCAN = 4123
    }
}