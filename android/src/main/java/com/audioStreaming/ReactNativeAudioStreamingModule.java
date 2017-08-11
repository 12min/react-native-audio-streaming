package com.audioStreaming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import javax.annotation.Nullable;
import android.app.Activity;

import java.util.HashMap;

public class ReactNativeAudioStreamingModule extends ReactContextBaseJavaModule
    implements ServiceConnection {

  public static final String SHOULD_SHOW_NOTIFICATION = "showInAndroidNotifications";
  private ReactApplicationContext context;

  private Class<?> clsActivity;
  private static Signal signal;
  private Intent bindIntent;
  private boolean shouldShowNotification;


  public ReactNativeAudioStreamingModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
  }

  public ReactApplicationContext getReactApplicationContextModule() {
    return this.context;
  }

  public Class<?> getClassActivity() {
    Activity activity = getCurrentActivity();
    if (this.clsActivity == null && activity != null) {
      this.clsActivity = activity.getClass();
    }
    return this.clsActivity;
  }

  public void stopOncall() {
    this.signal.stop();
  }

  public Signal getSignal() {
    return signal;
  }

  public void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  @Override public String getName() {
    return "ReactNativeAudioStreaming";
  }

  @Override public void initialize() {
    super.initialize();

    try {
      bindIntent = new Intent(this.context, Signal.class);
      this.context.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    } catch (Exception e) {
      Log.e("ERROR", e.getMessage());
    }
  }

  @Override public void onServiceConnected(ComponentName className, IBinder service) {
    signal = ((Signal.RadioBinder) service).getService();
    signal.setData(this.context, this);
    WritableMap params = Arguments.createMap();
    sendEvent(this.getReactApplicationContextModule(), "streamingOpen", params);
  }

  @Override public void onServiceDisconnected(ComponentName className) {
    signal = null;
  }

  @ReactMethod public void play(String streamingURL, ReadableMap options) {
    this.shouldShowNotification = options.hasKey(SHOULD_SHOW_NOTIFICATION) && options.getBoolean(SHOULD_SHOW_NOTIFICATION);
    HashMap<String, String> streamingOptions = new HashMap<>();

    streamingOptions.put("streamTitle", options.hasKey("streamTitle")?options.getString("streamTitle"):"");
    streamingOptions.put("appTitle", options.hasKey("appTitle")?options.getString("appTitle"):"");
    streamingOptions.put("imageUrl", options.hasKey("imageUrl")?options.getString("imageUrl"):"");

    playInternal(streamingURL, streamingOptions);
  }

  private void playInternal(String streamingURL, HashMap<String, String> streamingOptions) {
    signal.play(streamingURL, streamingOptions);

    if (shouldShowNotification) {
      signal.showNotification();
    }
  }

  @ReactMethod public void stop() {
    signal.stop();
  }

  @ReactMethod public void pause() {
    // Not implemented on aac
    signal.pause();
  }

  @ReactMethod public void resume() {
    // Not implemented on aac
    signal.resume();
  }

  @ReactMethod public void destroyNotification() {
    signal.exitNotification();
  }

  @ReactMethod public void seekToTime(int seconds) {
    long mili = seconds * 1000;

    signal.seekTo(mili);
  }

  @ReactMethod public void goBack(int seconds) {
    long mili = signal.getCurrentPosition() - (seconds * 1000);

    signal.seekTo(mili);
  }

  @ReactMethod public void goForward(int seconds) {
    long mili = signal.getCurrentPosition() + (seconds * 1000);

    signal.seekTo(mili);
  }

  @ReactMethod public void getStatus(Callback callback) {
    WritableMap state = Arguments.createMap();
    state.putString("status", signal != null && signal.isPlaying() ? Mode.PLAYING : Mode.STOPPED);
    callback.invoke(null, state);
  }
}
