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

import android.app.Activity
import androidx.activity.ComponentActivity
import com.facebook.react.bridge.*
import com.shopify.checkoutsheetkit.*

class ShopifyCheckoutSheetKitModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val MODULE_NAME = "ShopifyCheckoutSheetKit"
        var checkoutConfig: Configuration = Configuration()
    }

    private var checkoutSheet: CheckoutSheetKitDialog? = null

    init {
        ShopifyCheckoutSheetKit.configure { configuration ->
            configuration.setPlatform(Platform.REACT_NATIVE)
            checkoutConfig = configuration
        }
    }

    override fun getName(): String = MODULE_NAME

    override fun getConstants(): Map<String, Any> = mapOf(
        "version" to ShopifyCheckoutSheetKit.version
    )

    @ReactMethod
    fun addListener(eventName: String) {
        // No-op but required for RN to register module
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // No-op but required for RN to register module
    }

    @ReactMethod
    fun present(checkoutURL: String) {
        val currentActivity = currentActivity
        if (currentActivity is ComponentActivity) {
            val checkoutEventProcessor = CustomCheckoutEventProcessor(currentActivity, reactContext)
            currentActivity.runOnUiThread {
                checkoutSheet = ShopifyCheckoutSheetKit.present(
                    checkoutURL,
                    currentActivity,
                    checkoutEventProcessor
                )
            }
        }
    }

    @ReactMethod
    fun dismiss() {
        checkoutSheet?.dismiss()
        checkoutSheet = null
    }

    @ReactMethod
    fun preload(checkoutURL: String) {
        val currentActivity = currentActivity
        if (currentActivity is ComponentActivity) {
            ShopifyCheckoutSheetKit.preload(checkoutURL, currentActivity)
        }
    }

    @ReactMethod
    fun invalidateCache() {
        ShopifyCheckoutSheetKit.invalidate()
    }

    private fun getColorScheme(colorScheme: String): ColorScheme = when (colorScheme) {
        "web_default" -> ColorScheme.Web()
        "light" -> ColorScheme.Light()
        "dark" -> ColorScheme.Dark()
        else -> ColorScheme.Automatic()
    }

    private fun colorSchemeToString(colorScheme: ColorScheme): String = colorScheme.id

    private fun isValidColorConfig(config: ReadableMap?): Boolean {
        if (config == null) return false

        val colorKeys = arrayOf("backgroundColor", "progressIndicator", "headerTextColor", "headerBackgroundColor")
        return colorKeys.all { key ->
            config.hasKey(key) && config.getString(key)?.let { parseColor(it) } != null
        }
    }

    private fun isValidColorScheme(colorScheme: ColorScheme, colorConfig: ReadableMap?): Boolean {
        if (colorConfig == null) return false

        return when (colorScheme) {
            is ColorScheme.Automatic -> {
                if (!colorConfig.hasKey("light") || !colorConfig.hasKey("dark")) return false
                isValidColorConfig(colorConfig.getMap("light")) && isValidColorConfig(colorConfig.getMap("dark"))
            }
            else -> isValidColorConfig(colorConfig)
        }
    }

    private fun parseColorFromConfig(config: ReadableMap, colorKey: String): Color? =
        if (config.hasKey(colorKey)) {
            config.getString(colorKey)?.let { parseColor(it) }
        } else null

    private fun createColorsFromConfig(config: ReadableMap?): Colors? {
        if (config == null) return null

        val webViewBackground = parseColorFromConfig(config, "backgroundColor")
        val headerBackground = parseColorFromConfig(config, "headerBackgroundColor")
        val headerFont = parseColorFromConfig(config, "headerTextColor")
        val progressIndicator = parseColorFromConfig(config, "progressIndicator")

        return if (webViewBackground != null && progressIndicator != null &&
            headerFont != null && headerBackground != null
        ) {
            Colors(
                webViewBackground,
                headerBackground,
                headerFont,
                progressIndicator
            )
        } else null
    }

    private fun getColors(colorScheme: ColorScheme, config: ReadableMap): ColorScheme? {
        if (!isValidColorScheme(colorScheme, config)) return null

        when (colorScheme) {
            is ColorScheme.Automatic -> {
                if (isValidColorScheme(colorScheme, config)) {
                    val lightColors = createColorsFromConfig(config.getMap("light"))
                    val darkColors = createColorsFromConfig(config.getMap("dark"))
                    if (lightColors != null && darkColors != null) {
                        colorScheme.setLightColors(lightColors)
                        colorScheme.setDarkColors(darkColors)
                        return colorScheme
                    }
                }
            }
            else -> {
                createColorsFromConfig(config)?.let { colors ->
                    when (colorScheme) {
                        is ColorScheme.Light -> colorScheme.setColors(colors)
                        is ColorScheme.Dark -> colorScheme.setColors(colors)
                        is ColorScheme.Web -> colorScheme.setColors(colors)
                    }
                    return colorScheme
                }
            }
        }
        return null
    }

    @ReactMethod
    fun setConfig(config: ReadableMap) {
        ShopifyCheckoutSheetKit.configure { configuration ->
            if (config.hasKey("preloading")) {
                configuration.setPreloading(Preloading(config.getBoolean("preloading")))
            }

            if (config.hasKey("colorScheme")) {
                val colorScheme = config.getString("colorScheme")?.let { getColorScheme(it) }
                val colorsConfig = if (config.hasKey("colors")) config.getMap("colors") else null
                val androidConfig = colorsConfig?.getMap("android")

                if (colorScheme != null) {
                    if (isValidColorConfig(androidConfig)) {
                        getColors(colorScheme, androidConfig!!)?.let {
                            configuration.setColorScheme(it)
                            checkoutConfig = configuration
                            return@configure
                        }
                    }
                    configuration.setColorScheme(colorScheme)
                }
            }
            checkoutConfig = configuration
        }
    }

    @ReactMethod
    fun getConfig(promise: Promise) {
        val resultConfig = WritableNativeMap()
        resultConfig.putBoolean("preloading", checkoutConfig.preloading.enabled)
        resultConfig.putString("colorScheme", colorSchemeToString(checkoutConfig.colorScheme))
        promise.resolve(resultConfig)
    }

    private fun parseColor(colorStr: String): Color? {
        return try {
            val cleanColorStr = colorStr.replace("#", "")
            var color = cleanColorStr.toLong(16)

            if (cleanColorStr.length == 6) {
                // If alpha is not included, assume full opacity
                color = color or 0xFF000000
            }

            Color.SRGB(color.toInt())
        } catch (e: NumberFormatException) {
            println("Warning: Invalid color string. Default color will be used.")
            null
        }
    }
}
