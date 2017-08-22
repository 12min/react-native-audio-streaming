package com.audioStreaming;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.facebook.infer.annotation.Assertions;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.metadata.id3.CommentFrame;
import com.google.android.exoplayer2.metadata.id3.GeobFrame;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.metadata.id3.UrlLinkFrame;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Signal extends Service implements Player.EventListener, MetadataRenderer.Output , ExtractorMediaSource.EventListener {
    private static final String TAG = "ReactNative";
    private static final String SIGNALTAG = "SIGNAL tag";

    // Notification
    private Class<?> clsActivity;
    private static final int NOTIFY_ME_ID = 696969;
    private NotificationCompat.Builder notifyBuilder;
    private NotificationManager notifyManager = null;
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    public static RemoteViews remoteViews;

    // Player
    private SimpleExoPlayer player = null;

    public static final String BROADCAST_PLAYBACK_STOP = "stop",
            BROADCAST_PLAYBACK_PLAY = "pause",
            BROADCAST_EXIT = "exit";

    private final IBinder binder = new RadioBinder();
    private final SignalReceiver receiver = new SignalReceiver(this);
    private Context context;
    private String streamingURL;
    private EventsReceiver eventsReceiver;
    private ReactNativeAudioStreamingModule module;
    private MappingTrackSelector trackSelector;
    private TelephonyManager phoneManager;
    private PhoneListener phoneStateListener;

    private String streamTitle;
    private String appTitle;
    private String imageUrl;
    private  boolean playing;
    private Timer tickTimer;
    private class tickTask extends TimerTask {
        public void run() {
          tick();
        }
    }

    public Signal() {
      tickTimer = new Timer();
      tickTimer.schedule(new tickTask(), 0, 500);
    }

    @Override
    public void onCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_PLAYBACK_STOP);
        intentFilter.addAction(BROADCAST_PLAYBACK_PLAY);
        intentFilter.addAction(BROADCAST_EXIT);
        registerReceiver(this.receiver, intentFilter);
    }

    public void setData(Context context, ReactNativeAudioStreamingModule module) {
        this.context = context;
        this.clsActivity = module.getClassActivity();
        this.module = module;

        this.eventsReceiver = new EventsReceiver(this.module);

        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CREATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.IDLE));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.DESTROYED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STARTED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CONNECTING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PLAYING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.READY));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STOPPED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PAUSED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.COMPLETED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ERROR));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_START));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_END));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.METADATA_UPDATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ALBUM_UPDATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STREAMING));

        this.phoneStateListener = new PhoneListener(this.module);
        this.phoneManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (this.phoneManager != null) {
            this.phoneManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    public void tick() {
      if (player == null || !isPlaying() || this.getDuration() < 0) {
        return;
      }

      Intent playingIntent = new Intent(Mode.STREAMING);

      playingIntent.putExtra("progress", String.valueOf((double) this.getCurrentPosition() / 1000));
      playingIntent.putExtra("duration", String.valueOf((double) this.getDuration() / 1000));
      playingIntent.putExtra("url", this.streamingURL);

      sendBroadcast(playingIntent);
        if(playing!=isPlaying()){
            playing=isPlaying();
            toggleNotificationIcon(!isPlaying());
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d("onPlayerStateChanged", ""+playbackState);

        switch (playbackState) {
            case ExoPlayer.STATE_IDLE:
                sendBroadcast(new Intent(Mode.IDLE));
                break;
            case ExoPlayer.STATE_BUFFERING:
                sendBroadcast(new Intent(Mode.BUFFERING_START));
                break;
            case ExoPlayer.STATE_READY:
                if (this.player != null && this.player.getPlayWhenReady()) {
                    sendBroadcast(new Intent(Mode.PLAYING));
                } else {
                    sendBroadcast(new Intent(Mode.READY));
                }
                break;
            case ExoPlayer.STATE_ENDED:
                sendBroadcast(new Intent(Mode.BUFFERING_START));
                break;
        }
        toggleNotificationIcon(!isPlaying());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) {
            Log.d(TAG, "Tracks []");
            return;
        }
        Log.d(TAG, "Tracks [");
        // Log tracks associated to renderers.
        for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.length; rendererIndex++) {
            TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
            TrackSelection trackSelection = trackSelections.get(rendererIndex);
            if (rendererTrackGroups.length > 0) {
                // Log metadata for at most one of the tracks selected for the renderer.
                if (trackSelection != null) {
                    for (int selectionIndex = 0; selectionIndex < trackSelection.length(); selectionIndex++) {
                        Metadata metadata = trackSelection.getFormat(selectionIndex).metadata;
                        if (metadata != null) {
                            Log.d(TAG, "    Metadata [");
                            printMetadata(metadata, "      ");
                            Log.d(TAG, "    ]");
                            break;
                        }else{
                            updateTitle(this.streamTitle,"",this.imageUrl);
                        }
                    }
                }
                Log.d(TAG, "  ]");
            }
        }
    }


    @Override
    public void onPlayerError(ExoPlaybackException error) {
        String msg = error.getMessage();
        Log.d(TAG, msg != null ? msg : "Player error");
        sendBroadcast(new Intent(Mode.ERROR));
    }
    @Override
    public void onPositionDiscontinuity() {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    private static String getDefaultUserAgent() {
        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version")); // such as 1.1.0
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
        result.append(version.length() > 0 ? version : "1.0");

        // add the model for the release build
        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }
        String id = Build.ID; // "MASTER" or "M4-rc20"
        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }
        result.append(")");
        return result.toString();
    }

    /**
     *  Player controls
     */

    public synchronized void play(String url, HashMap<String, String> streamingOptions) {
        this.streamTitle = streamingOptions.get("streamTitle");
        this.appTitle = streamingOptions.get("appTitle");
        this.imageUrl = streamingOptions.get("imageUrl");

        //create new player only if the streaming is different
        if (player != null && this.streamingURL!=null) {
            if(this.streamingURL.equals(url)) {
                player.setPlayWhenReady(true);
                return;
            }
            player.setPlayWhenReady(false);
            player.seekTo(0);
            player = null;
        }



        boolean playWhenReady = true; // TODO Allow user to customize this
        this.streamingURL = url;

        // Create player
        // DefaultTrackSelector trackSelector = new DefaultTrackSelector();
        Handler mainHandler = new Handler();
        TrackSelection.Factory adaptiveTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
        trackSelector = new DefaultTrackSelector(adaptiveTrackSelectionFactory);
        LoadControl loadControl = new DefaultLoadControl();
        this.player = ExoPlayerFactory.newSimpleInstance(this.getApplicationContext(), trackSelector, loadControl);

        // Create source
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this.getApplication(), getDefaultUserAgent(), bandwidthMeter);
        MediaSource audioSource = null;

        //try to open url
        try {
            audioSource = new ExtractorMediaSource(Uri.parse(this.streamingURL), dataSourceFactory, extractorsFactory, mainHandler, this);
            //Prepare debugger
            EventLogger eventLogger = new EventLogger(trackSelector);

            // Start preparing audio
            player.prepare(audioSource);
            player.addListener(this);
            player.setPlayWhenReady(playWhenReady);
            player.addListener(eventLogger);
            player.setAudioDebugListener(eventLogger);
            player.setMetadataOutput(this);
        }catch (Exception ex){
            String msg = ex.getMessage();
            Log.d(SIGNALTAG, msg != null ? msg : "the url cant be processed");
        }
    }

    public void start() {
        if(player!=null) {
          player.setPlayWhenReady(true);
        }
    }

    public void pause() {
        if(player!=null) { 
            player.setPlayWhenReady(false);
        }
        sendBroadcast(new Intent(Mode.PAUSED));
    }

    public void resume() {
        if(player !=null) { 
            player.setPlayWhenReady(true);
        }
    }

    public void stop() {
        if(player !=null) { 
            player.setPlayWhenReady(false);
            player = null;
        }

        sendBroadcast(new Intent(Mode.STOPPED));
    }

    public boolean isPlaying() {
        if (player == null) {
          return false;
        } 
        return player.getPlayWhenReady();
    }

    public long getDuration() { 
        if(player !=null) { 
            return player.getDuration();
        }
        return 0;
    }

    public long getCurrentPosition() { 
        if (player!=null){
         return player.getCurrentPosition();
        }
        return 0;
    }

    public int getBufferPercentage() { 
        if (player!=null){
            return player.getBufferedPercentage();
        }
        return 0;
    }

    public void seekTo(long timeMillis) { 
        if(player!=null)
            player.seekTo(timeMillis);
    }

    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }


    @Override
    public void onMetadata(Metadata metadata) {
        Log.d(SIGNALTAG, "onMetadata [");
        printMetadata(metadata, "  ");
        Log.d(SIGNALTAG, "]");
    }

    private void printMetadata(Metadata metadata, String prefix) {
        String title="";
        String author="";
        String urlImage="";
        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);
            if (entry instanceof TextInformationFrame) {
                TextInformationFrame textInformationFrame = (TextInformationFrame) entry;
                Log.d(SIGNALTAG, prefix + String.format("%s: value=%s", textInformationFrame.id,
                        textInformationFrame.value));
                if(textInformationFrame.id.equals("TIT2")){
                    title=textInformationFrame.value;
                }
                if(textInformationFrame.id.equals("TPE1")){
                    author=", "+textInformationFrame.value;
                }
            } else if (entry instanceof UrlLinkFrame) {
                UrlLinkFrame urlLinkFrame = (UrlLinkFrame) entry;
                Log.d(SIGNALTAG, prefix + String.format("%s: url=%s", urlLinkFrame.id, urlLinkFrame.url));
            } else if (entry instanceof PrivFrame) {
                PrivFrame privFrame = (PrivFrame) entry;
                Log.d(SIGNALTAG, prefix + String.format("%s: owner=%s", privFrame.id, privFrame.owner));
            } else if (entry instanceof GeobFrame) {
                GeobFrame geobFrame = (GeobFrame) entry;
                Log.d(SIGNALTAG, prefix + String.format("%s: mimeType=%s, filename=%s, description=%s",
                        geobFrame.id, geobFrame.mimeType, geobFrame.filename, geobFrame.description));
                title=geobFrame.filename;
            } else if (entry instanceof ApicFrame) {
                ApicFrame apicFrame = (ApicFrame) entry;
                Log.d(SIGNALTAG, prefix + String.format("%s: mimeType=%s, description=%s",
                        apicFrame.id, apicFrame.mimeType, apicFrame.description));
            } else if (entry instanceof CommentFrame) {
                CommentFrame commentFrame = (CommentFrame) entry;
                Log.d(SIGNALTAG, prefix + String.format("%s: language=%s, description=%s", commentFrame.id,
                        commentFrame.language, commentFrame.description));
            } else if (entry instanceof Id3Frame) {
                Id3Frame id3Frame = (Id3Frame) entry;
                Log.d(SIGNALTAG, prefix + String.format("%s", id3Frame.id));
            } else if (entry instanceof EventMessage) {
                EventMessage eventMessage = (EventMessage) entry;
                Log.d(SIGNALTAG, prefix + String.format("EMSG: scheme=%s, id=%d, value=%s",
                        eventMessage.schemeIdUri, eventMessage.id, eventMessage.value));
            }
        }
        updateTitle(title,author,urlImage);
    }

    private void updateTitle (String title, String author, String urlImage){

        if(!title.equals(""))
            this.streamTitle = title+""+author;
        if(!urlImage.equals(""))
            this.imageUrl =urlImage;
        Intent intent = new Intent(Mode.METADATA_UPDATED);
        intent.putExtra("StreamTitle",this.streamTitle);
        intent.putExtra("imageUrl",   this.imageUrl);
        sendBroadcast(intent);
        try{
            remoteViews.setTextViewText(R.id.song_name_notification, this.streamTitle);
            remoteViews.setImageViewBitmap(R.id.album_image_notification, BitmapUtils.loadBitmap(this.imageUrl));
        }catch (Exception ex){
            String msg = ex.getMessage();
            Log.d(SIGNALTAG, msg != null ? msg : "error updating title");
        }
    }

    /**
     *  Notification control
     */

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    // Notification
    private PendingIntent makePendingIntent(String broadcast) {
        Intent intent = new Intent(broadcast);
        return PendingIntent.getBroadcast(this.context, 0, intent, 0);
    }

    public NotificationManager getNotifyManager() {
        return notifyManager;
    }

    public class RadioBinder extends Binder {
        public Signal getService() {
            return Signal.this;
        }
    }

    public void showNotification() {
        remoteViews = new RemoteViews(context.getPackageName(), R.layout.streaming_notification_player);
        notifyBuilder = new NotificationCompat.Builder(this.context)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off) // TODO Use app icon instead
                .setContentText("")
                .setContent(remoteViews);

        Intent resultIntent = new Intent(this.context, this.clsActivity);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this.context);
        stackBuilder.addParentStack(this.clsActivity);
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT);

        notifyBuilder.setContentIntent(resultPendingIntent);

        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_play, makePendingIntent(BROADCAST_PLAYBACK_PLAY));
        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_pause, makePendingIntent(BROADCAST_PLAYBACK_PLAY));
        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_stop, makePendingIntent(BROADCAST_EXIT));
        remoteViews.setTextViewText(R.id.song_name_notification, this.streamTitle);
        remoteViews.setTextViewText(R.id.album_name_notification, this.appTitle);
        remoteViews.setImageViewBitmap(R.id.album_image_notification, BitmapUtils.loadBitmap(this.imageUrl));

        notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        startForeground(NOTIFY_ME_ID,notifyBuilder.build());
        //notifyManager.notify(NOTIFY_ME_ID, notifyBuilder.build());
    }

    public void toggleNotificationIcon(boolean isPlaying) {
      if (remoteViews == null || notifyBuilder == null) {
        return;
      }

      if (isPlaying) {
          Log.d(SIGNALTAG,  "playing");
        setNotificationPlayIcon();
      } else {
          Log.d(SIGNALTAG,  "No playing");
        setNotificationPauseIcon();
      }
        startForeground(NOTIFY_ME_ID,notifyBuilder.build());
     // notifyManager.notify(NOTIFY_ME_ID, notifyBuilder.build());
    }

    public void setNotificationPlayIcon() {
      remoteViews.setInt(R.id.btn_streaming_notification_play, "setVisibility", View.VISIBLE);
      remoteViews.setInt(R.id.btn_streaming_notification_pause, "setVisibility", View.GONE);
    }

    public void setNotificationPauseIcon() {
      remoteViews.setInt(R.id.btn_streaming_notification_play, "setVisibility", View.GONE);
      remoteViews.setInt(R.id.btn_streaming_notification_pause, "setVisibility", View.VISIBLE);
    }

    public void clearNotification() {
        if (notifyManager != null) {
            notifyManager.cancel(NOTIFY_ME_ID);
        }
    }

    public void exitNotification() {
        try {
            if (notifyManager == null)
                notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notifyManager.cancelAll();
            clearNotification();
            notifyBuilder = null;
            notifyManager = null;
            stopForeground(true);
        }catch (Exception ex){
            String msg = ex.getMessage();
            Log.d(TAG, msg != null ? msg : "Exit notification error");
        }
    }

    @Override
    public void onLoadError(IOException error) {
        String msg = error.getMessage();
        Log.d(TAG, msg != null ? msg : "Audio load error");
    }
}
