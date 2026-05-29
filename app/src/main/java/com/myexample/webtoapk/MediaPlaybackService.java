package com.myexample.webtoapk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.net.wifi.WifiManager;
import android.media.AudioAttributes;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.Base64;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Vibrator;
import android.os.VibrationEffect;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPlaybackService extends Service {
    public static final String NOTIFICATION_CHANNEL_ID = "web_app_notifications";
    public static final String ACTION_UPDATE_METADATA = "com.myexample.webtoapk.UPDATE_METADATA";
    public static final String ACTION_UPDATE_STATE = "com.myexample.webtoapk.UPDATE_STATE";
    public static final String ACTION_SET_HANDLERS = "com.myexample.webtoapk.SET_HANDLERS";
    public static final String ACTION_STOP_SERVICE = "com.myexample.webtoapk.STOP_SERVICE";
    public static final String ACTION_UPDATE_POSITION = "com.myexample.webtoapk.UPDATE_POSITION";

    public static final String ACTION_PLAY = "com.myexample.webtoapk.PLAY";
    public static final String ACTION_PAUSE = "com.myexample.webtoapk.PAUSE";
    public static final String ACTION_NEXT = "com.myexample.webtoapk.NEXT";
    public static final String ACTION_PREVIOUS = "com.myexample.webtoapk.PREVIOUS";

    public static final String ACTION_PLAY_MESSAGE_SOUND = "com.myexample.webtoapk.PLAY_MESSAGE_SOUND";
    public static final String ACTION_PLAY_CALL_INCOMING = "com.myexample.webtoapk.PLAY_CALL_INCOMING";
    public static final String ACTION_PLAY_CALL_OUTGOING = "com.myexample.webtoapk.PLAY_CALL_OUTGOING";
    public static final String ACTION_STOP_CALL_SOUND = "com.myexample.webtoapk.STOP_CALL_SOUND";
    public static final String ACTION_VIBRATE = "com.myexample.webtoapk.VIBRATE";
    public static final String ACTION_VIBRATE_LONG = "com.myexample.webtoapk.VIBRATE_LONG";
    public static final String ACTION_STOP_VIBRATE = "com.myexample.webtoapk.STOP_VIBRATE";

    public static final String BROADCAST_MEDIA_ACTION = "com.myexample.webtoapk.BROADCAST_MEDIA_ACTION";
    public static final String EXTRA_MEDIA_ACTION = "EXTRA_MEDIA_ACTION";

    private static final int NOTIFICATION_ID = 101;
    private MediaSessionCompat mediaSession;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BroadcastReceiver becomingNoisyReceiver;
    
    private MediaPlayer callMediaPlayer;
    private Vibrator vibrator;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;
    private boolean isRinging = false;

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                sendActionToWebView("pause");
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // 申请标准的电源锁与网络锁，防止后台断网断流
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebToApk:WakeLock");
        }
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WebToApk:WifiLock");
        }

        // 创建通知渠道（防止高版本系统无法弹出通知）
        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, "WebToApkMediaSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                              MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        PlaybackStateCompat initialState = new PlaybackStateCompat.Builder()
                .setActions(0)
                .setState(PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0)
                .build();
        mediaSession.setPlaybackState(initialState);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { sendActionToWebView("play"); }
            @Override
            public void onPause() { sendActionToWebView("pause"); }
            @Override
            public void onSkipToNext() { sendActionToWebView("nexttrack"); }
            @Override
            public void onSkipToPrevious() { sendActionToWebView("previoustrack"); }
            @Override
            public void onStop() { sendActionToWebView("stop"); }
        });

        mediaSession.setActive(true);

        becomingNoisyReceiver = new BecomingNoisyReceiver();
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(becomingNoisyReceiver, intentFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(becomingNoisyReceiver, intentFilter);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Web App Notifications",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_UPDATE_METADATA:
                updateMetadata(intent.getStringExtra("title"), intent.getStringExtra("artist"), intent.getStringExtra("album"), intent.getStringExtra("artworkUrl"));
                break;
            case ACTION_UPDATE_STATE:
                updatePlaybackState(intent.getStringExtra("state"));
                break;
            case ACTION_UPDATE_POSITION:
                updatePositionState(intent.getDoubleExtra("duration", 0), intent.getDoubleExtra("playbackRate", 1.0), intent.getDoubleExtra("position", 0));
                break;
            case ACTION_SET_HANDLERS:
                setMediaActionHandlers(intent.getStringArrayExtra("actions"));
                break;
            case ACTION_STOP_SERVICE:
                stopSelf();
                break;
            case ACTION_PLAY: sendActionToWebView("play"); break;
            case ACTION_PAUSE: sendActionToWebView("pause"); break;
            case ACTION_NEXT: sendActionToWebView("nexttrack"); break;
            case ACTION_PREVIOUS: sendActionToWebView("previoustrack"); break;
            case ACTION_PLAY_MESSAGE_SOUND: playMessageSound(); break;
            case ACTION_PLAY_CALL_INCOMING: playCallIncomingSound(); break;
            case ACTION_PLAY_CALL_OUTGOING: playCallOutgoingSound(); break;
            case ACTION_STOP_CALL_SOUND: stopCallSound(); break;
            case ACTION_VIBRATE: vibrate(intent.getIntExtra("duration", 200)); break;
            case ACTION_VIBRATE_LONG: vibrateLong(); break;
            case ACTION_STOP_VIBRATE: stopVibrate(); break;
        }

        return START_STICKY;
    }

    // 🌟 修复核心：使用兼容所有高低版本系统的通用保活通知
    private void showBackgroundActiveNotification(String text) {
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_remainder)
                .setContentTitle("系统后台通讯服务")
                .setContentText(text)
                .setContentIntent(contentPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        // 避开硬编码的 FOREGROUND_SERVICE_TYPE 检查，防止打包报错或运行崩溃
        startForeground(NOTIFICATION_ID, notification);
    }

    private void playMessageSound() {
        try {
            stopCallSound(); 
            showBackgroundActiveNotification("正在处理新消息提示...");

            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(3000);

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(this, soundUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            } else {
                mp.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            }

            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(m -> {
                if (m != null) m.release();
                PlaybackStateCompat s = mediaSession.getController().getPlaybackState();
                if (!isRinging && (s == null || s.getState() != PlaybackStateCompat.STATE_PLAYING)) {
                    stopForeground(true);
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e("WebToApk", "播放消息提示音失败", e);
        }
    }

    private void playCallSoundInternal() {
        try {
            if (isRinging) return;
            isRinging = true;
            
            stopCallSound();
            showBackgroundActiveNotification("正在保持网络通话连接...");

            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
            if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();

            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            callMediaPlayer = new MediaPlayer();
            callMediaPlayer.setDataSource(this, ringtoneUri);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                callMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            } else {
                callMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            }
            
            callMediaPlayer.setLooping(true);
            callMediaPlayer.setOnPreparedListener(MediaPlayer::start);
            callMediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e("WebToApk", "播放通话铃声失败", e);
        }
    }

    private void playCallIncomingSound() { playCallSoundInternal(); }
    private void playCallOutgoingSound() { playCallSoundInternal(); }

    private void stopCallSound() {
        isRinging = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        
        if (callMediaPlayer != null) {
            try {
                if (callMediaPlayer.isPlaying()) callMediaPlayer.stop();
                callMediaPlayer.release();
            } catch (Exception e) {}
            callMediaPlayer = null;
        }

        PlaybackStateCompat s = mediaSession.getController().getPlaybackState();
        if (s == null || s.getState() != PlaybackStateCompat.STATE_PLAYING) {
            stopForeground(true);
        }
    }

    private void vibrate(int duration) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    private void vibrateLong() {
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 500, 300, 500, 300, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopVibrate() {
        if (vibrator != null) vibrator.cancel();
    }

    private void updateMetadata(String title, String artist, String album, @Nullable String artworkUrl) {
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);

        if (artworkUrl != null && !artworkUrl.isEmpty()) {
            try {
                String base64String = artworkUrl.substring(artworkUrl.indexOf(',') + 1);
                byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
                Bitmap artworkBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap);
            } catch (Exception e) {
                Log.e("WebToApk", "Error decoding Base64 artwork", e);
            }
        }
        mediaSession.setMetadata(metadataBuilder.build());
        updateNotification();
    }

    private void updatePositionState(double duration, double playbackRate, double position) {
        PlaybackStateCompat currentState = mediaSession.getController().getPlaybackState();
        if (currentState == null || currentState.getState() == PlaybackStateCompat.STATE_NONE) return;

        long durationMs = (long) (duration * 1000);
        long positionMs = (long) (position * 1000);
        float rate = (float) playbackRate;

        MediaMetadataCompat currentMetadata = mediaSession.getController().getMetadata();
        MediaMetadataCompat.Builder metadataBuilder = currentMetadata == null ? new MediaMetadataCompat.Builder() : new MediaMetadataCompat.Builder(currentMetadata);
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        mediaSession.setMetadata(metadataBuilder.build());

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder(currentState);
        stateBuilder.setState(currentState.getState(), positionMs, rate);
        mediaSession.setPlaybackState(stateBuilder.build());

        updateNotification();
    }

    private void updatePlaybackState(String stateStr) {
        PlaybackStateCompat currentState = mediaSession.getController().getPlaybackState();
        if (currentState == null) {
            currentState = new PlaybackStateCompat.Builder().setActions(0).setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f).build();
        }

        int state;
        switch (stateStr) {
            case "playing": state = PlaybackStateCompat.STATE_PLAYING; break;
            case "paused": state = PlaybackStateCompat.STATE_PAUSED; break;
            default: state = PlaybackStateCompat.STATE_STOPPED; break;
        }

        PlaybackStateCompat.Builder newStateBuilder = new PlaybackStateCompat.Builder(currentState);
        newStateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        mediaSession.setPlaybackState(newStateBuilder.build());

        if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
            Notification n = buildNotification();
            if (n != null) startForeground(NOTIFICATION_ID, n);
        } else {
            if (!isRinging) {
                stopForeground(true);
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
            }
            if (state == PlaybackStateCompat.STATE_STOPPED && !isRinging) {
                 stopSelf();
            }
        }
    }

    private void setMediaActionHandlers(String[] actions) {
        PlaybackStateCompat currentState = mediaSession.getController().getPlaybackState();
        if (currentState == null) return;
        
        long supportedActions = 0;
        if (actions != null) {
            for (String action : actions) {
                switch (action) {
                    case "play": supportedActions |= PlaybackStateCompat.ACTION_PLAY; break;
                    case "pause": supportedActions |= PlaybackStateCompat.ACTION_PAUSE; break;
                    case "previoustrack": supportedActions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS; break;
                    case "nexttrack": supportedActions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT; break;
                }
            }
        }
        if ((supportedActions & PlaybackStateCompat.ACTION_PLAY) != 0 && (supportedActions & PlaybackStateCompat.ACTION_PAUSE) != 0) {
            supportedActions |= PlaybackStateCompat.ACTION_PLAY_PAUSE;
        }

        PlaybackStateCompat.Builder newStateBuilder = new PlaybackStateCompat.Builder(currentState);
        newStateBuilder.setActions(supportedActions);
        mediaSession.setPlaybackState(newStateBuilder.build());

        updateNotification();
    }

    private Notification buildNotification() {
        MediaMetadataCompat metadata = mediaSession.getController().getMetadata();
        PlaybackStateCompat playbackState = mediaSession.getController().getPlaybackState();

        if (playbackState == null || (playbackState.getState() != PlaybackStateCompat.STATE_PLAYING && playbackState.getState() != PlaybackStateCompat.STATE_PAUSED)) {
            return null;
        }

        boolean isPlaying = playbackState.getState() == PlaybackStateCompat.STATE_PLAYING;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        List<Integer> compactActionIndices = new ArrayList<>();

        if ((playbackState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            builder.addAction(android.R.drawable.ic_media_previous, "Previous", createActionIntent(ACTION_PREVIOUS));
            compactActionIndices.add(compactActionIndices.size());
        }

        if ((playbackState.getActions() & PlaybackStateCompat.ACTION_PLAY_PAUSE) != 0) {
            builder.addAction(new Action(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play",
                createActionIntent(isPlaying ? ACTION_PAUSE : ACTION_PLAY)
            ));
            compactActionIndices.add(compactActionIndices.size());
        }

        if ((playbackState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            builder.addAction(android.R.drawable.ic_media_next, "Next", createActionIntent(ACTION_NEXT));
            compactActionIndices.add(compactActionIndices.size());
        }

        int[] compactIndices = new int[compactActionIndices.size()];
        for (int i = 0; i < compactActionIndices.size(); i++) compactIndices[i] = compactActionIndices.get(i);

        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, MediaPlaybackService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent deletePendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metadata != null ? metadata.getDescription().getTitle() : "Radio")
            .setContentText(metadata != null ? metadata.getDescription().getSubtitle() : "...")
            .setLargeIcon(metadata != null ? metadata.getDescription().getIconBitmap() : null)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setStyle(new MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(compactIndices));

        return builder.build();
    }

    private void updateNotification() {
        if (mediaSession.getController().getPlaybackState() == null) return;

        PlaybackStateCompat state = mediaSession.getController().getPlaybackState();
        if (state.getState() == PlaybackStateCompat.STATE_PLAYING || state.getState() == PlaybackStateCompat.STATE_PAUSED) {
            Notification notification = buildNotification();
            if (notification != null) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        } else {
            if (!isRinging) NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        }
    }
    
    private PendingIntent createActionIntent(String action) {
        Intent intent = new Intent(this, MediaPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    
    private void sendActionToWebView(String action) {
        Intent intent = new Intent(BROADCAST_MEDIA_ACTION);
        intent.putExtra(EXTRA_MEDIA_ACTION, action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCallSound();
        stopVibrate();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (mediaSession != null) mediaSession.release();
        executor.shutdown();
        try { unregisterReceiver(becomingNoisyReceiver); } catch (Exception e) {}
    }
    
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
