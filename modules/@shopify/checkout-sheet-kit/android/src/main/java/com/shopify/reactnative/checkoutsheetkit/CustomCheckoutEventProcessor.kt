/*
MIT License

Copyright 2023 - Present, Shopify Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package com.shopify.reactnative.checkoutsheetkit

import android.content.Context
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.shopify.checkoutsheetkit.*
import com.shopify.checkoutsheetkit.lifecycleevents.CheckoutCompletedEvent
import com.shopify.checkoutsheetkit.pixelevents.PixelEvent

class CustomCheckoutEventProcessor(
    context: Context,
    private val reactContext: ReactApplicationContext
) : DefaultCheckoutEventProcessor(context) {

    private val mapper = ObjectMapper()

    override fun onCheckoutCompleted(event: CheckoutCompletedEvent) {
        try {
            val data = mapper.writeValueAsString(event)
            sendEventWithStringData("completed", data)
        } catch (e: IOException) {
            Log.e("ShopifyCheckoutSheetKit", "Error processing completed event", e)
        }
    }

    override fun onWebPixelEvent(event: PixelEvent) {
        try {
            val data = mapper.writeValueAsString(event)
            sendEventWithStringData("pixel", data)
        } catch (e: IOException) {
            Log.e("ShopifyCheckoutSheetKit", "Error processing pixel event", e)
        }
    }

    override fun onCheckoutFailed(checkoutError: CheckoutException) {
        try {
            val data = mapper.writeValueAsString(populateErrorDetails(checkoutError))
            sendEventWithStringData("error", data)
        } catch (e: IOException) {
            Log.e("ShopifyCheckoutSheetKit", "Error processing checkout failed event", e)
        }
    }

    private fun populateErrorDetails(checkoutError: CheckoutException): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put("__typename", getErrorTypeName(checkoutError))
            put("message", checkoutError.errorDescription)
            put("recoverable", checkoutError.isRecoverable)
            put("code", checkoutError.errorCode)

            if (checkoutError is HttpException) {
                put("statusCode", checkoutError.statusCode)
            }
        }
    }

    private fun getErrorTypeName(error: CheckoutException): String = when (error) {
        is CheckoutExpiredException -> "CheckoutExpiredError"
        is ClientException -> "CheckoutClientError"
        is HttpException -> "CheckoutHTTPError"
        is ConfigurationException -> "ConfigurationError"
        is CheckoutSheetKitException -> "InternalError"
        else -> "UnknownError"
    }

    override fun onCheckoutCanceled() {
        sendEvent("close", null)
    }

    private fun sendEvent(eventName: String, params: WritableNativeMap?) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private fun sendEventWithStringData(name: String, data: String) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(name, data)
    }
}
