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

package com.shopify.reactnative.checkoutsheetkit;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.shopify.checkoutsheetkit.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.shopify.checkoutsheetkit.pixelevents.PixelEvent;
import com.shopify.checkoutsheetkit.lifecycleevents.CheckoutCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CustomCheckoutEventProcessor extends DefaultCheckoutEventProcessor {
  private final ReactApplicationContext reactContext;

  private final ObjectMapper mapper = new ObjectMapper();

  private enum CheckoutEvent {
    COMPLETED("completed"),
    PIXEL("pixel"),
    CLOSE("close"),
    ERROR("error");

    private final String value;

    EventType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /**
   * Constructor for CustomCheckoutEventProcessor
   * @param context Android application context
   * @param reactContext React Native application context for event emission
   */
  public CustomCheckoutEventProcessor(Context context, ReactApplicationContext reactContext) {
    super(context);

    this.reactContext = reactContext;
  }

  // Lifecycle events

  /**
   * Handles checkout completion events
   * Serializes the event data to JSON and emits a 'completed' event to React Native
   * @param event The checkout completed event containing completion details
   */
  @Override
  public void onCheckoutCompleted(@NonNull CheckoutCompletedEvent event) {
    try {
      String data = mapper.writeValueAsString(event);
      sendEventWithStringData(CheckoutEvent.COMPLETED.getValue(), data);
    } catch (IOException e) {
      Log.e("ShopifyCheckoutSheetKit", "Error processing completed event", e);
    }
  }

  /**
   * Handles web pixel tracking events
   * Serializes the pixel event data to JSON and emits a 'pixel' event to React Native
   * @param event The pixel event containing tracking information
   */
  @Override
  public void onWebPixelEvent(@NonNull PixelEvent event) {
    try {
      String data = mapper.writeValueAsString(event);
      sendEventWithStringData(CheckoutEvent.PIXEL.getValue(), data);
    } catch (IOException e) {
      Log.e("ShopifyCheckoutSheetKit", "Error processing pixel event", e);
    }
  }

  /**
   * Handles checkout failure events
   * Converts the error to a map, serializes it to JSON, and emits an 'error' event to React Native
   * @param checkoutError The exception containing checkout error details
   */
  @Override
  public void onCheckoutFailed(CheckoutException checkoutError) {
    try {
      String data = mapper.writeValueAsString(populateErrorDetails(checkoutError));
      sendEventWithStringData(CheckoutEvent.ERROR.getValue(), data);
    } catch (IOException e) {
      Log.e("ShopifyCheckoutSheetKit", "Error processing checkout failed event", e);
    }
  }

  /**
   * Handles checkout cancellation events
   * Emits a 'close' event to React Native with no additional data
   */
  @Override
  public void onCheckoutCanceled() {
    sendEvent(CheckoutEvent.CLOSE.getValue(), null);
  }

  /**
   * Handles geolocation permission requests from the webview
   * Always grants permission and remembers the decision
   * @param origin The origin requesting geolocation permissions
   * @param callback Callback to respond to the permission request
   */
  @Override
  public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
    // Whether to grant the webview permission to access device location
    Boolean grant = true;
    // Whether to retain/remember this permission decision for future requests
    Boolean retain = true;

    callback.invoke(origin, grant, retain);
  }

  // Private

  /**
   * Creates a map of error details from a CheckoutException
   * @param checkoutError The checkout exception to process
   * @return Map containing error details including type, message, recovery status, and code
   */
  private Map<String, Object> populateErrorDetails(CheckoutException checkoutError) {
    Map<String, Object> errorMap = new HashMap();
    errorMap.put("__typename", getErrorTypeName(checkoutError));
    errorMap.put("message", checkoutError.getErrorDescription());
    errorMap.put("recoverable", checkoutError.isRecoverable());
    errorMap.put("code", checkoutError.getErrorCode());

    if (checkoutError instanceof HttpException) {
      errorMap.put("statusCode", ((HttpException) checkoutError).getStatusCode());
    }

    return errorMap;
  }

  /**
   * Determines the error type name based on the exception class
   * @param error The checkout exception to classify
   * @return String representing the error type name
   */
  private String getErrorTypeName(CheckoutException error) {
    if (error instanceof CheckoutExpiredException) {
      return "CheckoutExpiredError";
    } else if (error instanceof ClientException) {
      return "CheckoutClientError";
    } else if (error instanceof HttpException) {
      return "CheckoutHTTPError";
    } else if (error instanceof ConfigurationException) {
      return "ConfigurationError";
    } else if (error instanceof CheckoutSheetKitException) {
      return "InternalError";
    } else {
      return "UnknownError";
    }
  }

  /**
   * Emits an event to React Native with a WritableNativeMap payload
   * @param eventName The name of the event to emit
   * @param params Optional map of parameters to pass with the event
   */
  private void sendEvent(String eventName, @Nullable WritableNativeMap params) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  /**
   * Emits an event to React Native with a String payload
   * @param name The name of the event to emit
   * @param data The string data to pass with the event
   */
  private void sendEventWithStringData(String name, String data) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(name, data);
  }
}
