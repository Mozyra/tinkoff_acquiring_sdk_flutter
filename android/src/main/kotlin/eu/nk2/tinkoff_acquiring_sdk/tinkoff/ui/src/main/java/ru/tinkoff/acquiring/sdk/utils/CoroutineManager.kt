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

package ru.tinkoff.acquiring.sdk.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import ru.tinkoff.acquiring.sdk.requests.AcquiringRequest
import ru.tinkoff.acquiring.sdk.responses.AcquiringResponse

/**
 * @author Mariya Chernyadieva
 */
internal class CoroutineManager(private val exceptionHandler: (Throwable) -> Unit) {

    private val job = SupervisorJob()
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable -> doOnMain { exceptionHandler(throwable) } }
    private val coroutineScope = CoroutineScope(Dispatchers.Main + coroutineExceptionHandler + job)
    private val disposableSet = hashSetOf<Disposable>()

    fun <R : AcquiringResponse> call(request: AcquiringRequest<R>, onSuccess: (R) -> Unit, onFailure: ((Exception) -> Unit)? = null) {
        disposableSet.add(request)

        doOnBackground {
            request.execute(
                    onSuccess = {
                        doOnMain {
                            onSuccess(it)
                        }
                    },
                    onFailure = {
                        doOnMain {
                            if (onFailure == null) {
                                exceptionHandler.invoke(it)
                            } else {
                                onFailure(it)
                            }
                        }
                    })
        }
    }

    fun cancelAll() {
        disposableSet.forEach {
            it.dispose()
        }
        job.cancel()
    }

    fun runWithDelay(timeMills: Long, block: () -> Unit) {
        coroutineScope.launch(Dispatchers.Main) {
            delay(timeMills)
            block.invoke()
        }
    }

    private fun doOnMain(block: () -> Unit) {
        coroutineScope.launch(Dispatchers.Main) {
            block.invoke()
        }
    }

    private fun doOnBackground(block: () -> Unit) {
        coroutineScope.launch(IO) {
            block.invoke()
        }
    }
}