package com.audioStreaming;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;


public class EventsReceiver extends BroadcastReceiver {
    private ReactNativeAudioStreamingModule module;

    public EventsReceiver(ReactNativeAudioStreamingModule module) {
        this.module = module;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        WritableMap params = Arguments.createMap();
        params.putString("status", intent.getAction());

        switch(intent.getAction()) {
          case "METADATA_UPDATED":
            params.putString("key", intent.getStringExtra("key"));
            params.putString("value", intent.getStringExtra("value"));
          break;
          case "STREAMING":
            params.putDouble("duration", Double.parseDouble(intent.getStringExtra("duration")));
            params.putDouble("progress", Double.parseDouble(intent.getStringExtra("progress")));
            params.putString("url", intent.getStringExtra("url"));
          break;
        }

        this.module.sendEvent(this.module.getReactApplicationContextModule(), "AudioBridgeEvent", params);
    }
}
