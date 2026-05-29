package com.myexample.webtoapk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPlaybackService extends Service {
    public static final String NOTIFICATION_CHANNEL_ID = "web_app_notifications";
    public static final String MEDIA_SERVICE_CHANNEL_ID = "media_service_channel";
    
    public static final String ACTION_UPDATE_METADATA = "com.myexample.webtoapk.UPDATE_METADATA";
    public static final String ACTION_UPDATE_STATE = "com.myexample.webtoapk.UPDATE_STATE";
    public static final String ACTION_SET_HANDLERS = "com.myexample.webtoapk.SET_HANDLERS";
    public static final String ACTION_STOP_SERVICE = "com.myexample.webtoapk.STOP_SERVICE";
    public static final String ACTION_UPDATE_POSITION = "com.myexample.webtoapk.UPDATE_POSITION";

    // Actions from notification buttons
    public static final String ACTION_PLAY = "com.myexample.webtoapk.PLAY";
    public static final String ACTION_PAUSE = "com.myexample.webtoapk.PAUSE";
    public static final String ACTION_NEXT = "com.myexample.webtoapk.NEXT";
    public static final String ACTION_PREVIOUS = "com.myexample.webtoapk.PREVIOUS";

    // 消息和铃声相关的 Action
    public static final String ACTION_PLAY_MESSAGE_SOUND = "com.myexample.webtoapk.PLAY_MESSAGE_SOUND";
    public static final String ACTION_PLAY_CALL_INCOMING = "com.myexample.webtoapk.PLAY_CALL_INCOMING";
    public static final String ACTION_PLAY_CALL_OUTGOING = "com.myexample.webtoapk.PLAY_CALL_OUTGOING";
    public static final String ACTION_STOP_CALL_SOUND = "com.myexample.webtoapk.STOP_CALL_SOUND";
    public static final String ACTION_VIBRATE = "com.myexample.webtoapk.VIBRATE";
    public static final String ACTION_VIBRATE_LONG = "com.myexample.webtoapk.VIBRATE_LONG";
    public static final String ACTION_STOP_VIBRATE = "com.myexample.webtoapk.STOP_VIBRATE";

    // Action for broadcasting to MainActivity
    public static final String BROADCAST_MEDIA_ACTION = "com.myexample.webtoapk.BROADCAST_MEDIA_ACTION";
    public static final String EXTRA_MEDIA_ACTION = "EXTRA_MEDIA_ACTION";

    private static final int NOTIFICATION_ID = 101;
    private static final int FOREGROUND_SERVICE_ID = 1001;
    
    private MediaSessionCompat mediaSession;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BroadcastReceiver becomingNoisyReceiver;
    
    // 声音播放相关变量
    private MediaPlayer callMediaPlayer;
    private Vibrator vibrator;
    private boolean isRinging = false;

    // Inner class to handle the BECOMING_NOISY event
    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                Log.d("WebToApk", "Audio becoming noisy, sending 'pause' action.");
                sendActionToWebView("pause");
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化振动器
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        // 创建前台服务通知渠道（Android 8.0+ 需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                MEDIA_SERVICE_CHANNEL_ID,
                "媒体服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于播放声音和铃声");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            
            // 启动前台服务
            Notification notification = new NotificationCompat.Builder(this, MEDIA_SERVICE_CHANNEL_ID)
                .setContentTitle("通讯服务")
                .setContentText("正在运行中...")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
            
            startForeground(FOREGROUND_SERVICE_ID, notification);
        }

        // 初始化 MediaSession
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
            public void onPlay() {
                sendActionToWebView("play");
            }

            @Override
            public void onPause() {
                sendActionToWebView("pause");
            }

            @Override
            public void onSkipToNext() {
                sendActionToWebView("nexttrack");
            }

            @Override
            public void onSkipToPrevious() {
                sendActionToWebView("previoustrack");
            }

            @Override
            public void onStop() {
                sendActionToWebView("stop");
            }
        });

        mediaSession.setActive(true);

        // 注册音频插拔监听
        becomingNoisyReceiver = new BecomingNoisyReceiver();
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
        
        Log.d("WebToApk", "MediaPlaybackService onCreate completed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        Log.d("WebToApk", "onStartCommand action: " + action);

        switch (action) {
            case ACTION_UPDATE_METADATA:
                updateMetadata(
                    intent.getStringExtra("title"),
                    intent.getStringExtra("artist"),
                    intent.getStringExtra("album"),
                    intent.getStringExtra("artworkUrl")
                );
                break;
            case ACTION_UPDATE_STATE:
                updatePlaybackState(intent.getStringExtra("state"));
                break;
            case ACTION_UPDATE_POSITION:
                updatePositionState(
                    intent.getDoubleExtra("duration", 0),
                    intent.getDoubleExtra("playbackRate", 1.0),
                    intent.getDoubleExtra("position", 0)
                );
                break;
            case ACTION_SET_HANDLERS:
                setMediaActionHandlers(intent.getStringArrayExtra("actions"));
                break;
            case ACTION_STOP_SERVICE:
                stopSelf();
                break;
            case ACTION_PLAY:
                sendActionToWebView("play");
                break;
            case ACTION_PAUSE:
                sendActionToWebView("pause");
                break;
            case ACTION_NEXT:
                sendActionToWebView("nexttrack");
                break;
            case ACTION_PREVIOUS:
                sendActionToWebView("previoustrack");
                break;
                
            // 消息和铃声处理
            case ACTION_PLAY_MESSAGE_SOUND:
                playMessageSound();
                break;
            case ACTION_PLAY_CALL_INCOMING:
                playCallIncomingSound();
                break;
            case ACTION_PLAY_CALL_OUTGOING:
                playCallOutgoingSound();
                break;
            case ACTION_STOP_CALL_SOUND:
                stopCallSound();
                break;
            case ACTION_VIBRATE:
                vibrate(intent.getIntExtra("duration", 200));
                break;
            case ACTION_VIBRATE_LONG:
                vibrateLong();
                break;
            case ACTION_STOP_VIBRATE:
                stopVibrate();
                break;
        }

        return START_STICKY;
    }

    // ========== 消息提示音方法 ==========
    private void playMessageSound() {
        Log.d("WebToApk", "playMessageSound called");
        try {
            stopCallSound(); // 先停止可能正在播放的铃声
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer mp = MediaPlayer.create(this, soundUri);
            if (mp != null) {
                mp.setOnCompletionListener(m -> {
                    if (m != null) m.release();
                    Log.d("WebToApk", "Message sound completed");
                });
                mp.start();
                Log.d("WebToApk", "Message sound played successfully");
            } else {
                Log.e("WebToApk", "MediaPlayer.create returned null for notification sound");
            }
        } catch (Exception e) {
            Log.e("WebToApk", "播放消息提示音失败", e);
        }
    }

    // ========== 来电铃声方法 ==========
    private void playCallIncomingSound() {
        Log.d("WebToApk", "playCallIncomingSound called, isRinging=" + isRinging);
        try {
            if (isRinging) return;
            isRinging = true;
            
            stopCallSound();
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            callMediaPlayer = MediaPlayer.create(this, ringtoneUri);
            if (callMediaPlayer != null) {
                callMediaPlayer.setLooping(true);
                callMediaPlayer.start();
                Log.d("WebToApk", "Incoming call sound played successfully");
            } else {
                Log.e("WebToApk", "MediaPlayer.create returned null for ringtone");
            }
        } catch (Exception e) {
            Log.e("WebToApk", "播放来电铃声失败", e);
        }
    }

    // ========== 去电铃声方法 ==========
    private void playCallOutgoingSound() {
        Log.d("WebToApk", "playCallOutgoingSound called, isRinging=" + isRinging);
        try {
            if (isRinging) return;
            isRinging = true;
            
            stopCallSound();
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            callMediaPlayer = MediaPlayer.create(this, ringtoneUri);
            if (callMediaPlayer != null) {
                callMediaPlayer.setLooping(true);
                callMediaPlayer.start();
                Log.d("WebToApk", "Outgoing call sound played successfully");
            } else {
                Log.e("WebToApk", "MediaPlayer.create returned null for ringtone");
            }
        } catch (Exception e) {
            Log.e("WebToApk", "播放去电铃声失败", e);
        }
    }

    // ========== 停止铃声方法 ==========
    private void stopCallSound() {
        Log.d("WebToApk", "stopCallSound called");
        isRinging = false;
        if (callMediaPlayer != null) {
            try {
                if (callMediaPlayer.isPlaying()) {
                    callMediaPlayer.stop();
                }
                callMediaPlayer.release();
            } catch (Exception e) {
                Log.e("WebToApk", "Error stopping call sound", e);
            }
            callMediaPlayer = null;
        }
    }

    // ========== 短振动方法 ==========
    private void vibrate(int duration) {
        Log.d("WebToApk", "vibrate called with duration=" + duration);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
            Log.d("WebToApk", "Vibration started");
        } else {
            Log.w("WebToApk", "Vibrator not available");
        }
    }

    // ========== 长振动方法（来电用） ==========
    private void vibrateLong() {
        Log.d("WebToApk", "vibrateLong called");
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 500, 300, 500, 300, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
            Log.d("WebToApk", "Long vibration started");
        } else {
            Log.w("WebToApk", "Vibrator not available");
        }
    }

    // ========== 停止振动方法 ==========
    private void stopVibrate() {
        Log.d("WebToApk", "stopVibrate called");
        if (vibrator != null) {
            vibrator.cancel();
            Log.d("WebToApk", "Vibration stopped");
        }
    }

    private void updateMetadata(String title, String artist, String album, @Nullable String artworkUrl) {
        android.support.v4.media.MediaMetadataCompat.Builder metadataBuilder = 
            new android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, album);

        if (artworkUrl != null && !artworkUrl.isEmpty()) {
            try {
                String base64String = artworkUrl.substring(artworkUrl.indexOf(',') + 1);
                byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
                Bitmap artworkBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap);
                mediaSession.setMetadata(metadataBuilder.build());
                updateNotification();
            } catch (Exception e) {
                Log.e("WebToApk", "Error decoding Base64 artwork", e);
                mediaSession.setMetadata(metadataBuilder.build());
                updateNotification();
            }
        } else {
            mediaSession.setMetadata(metadataBuilder.build());
            updateNotification();
        }
    }

    private void updatePositionState(double duration, double playbackRate, double position) {
        android.support.v4.media.session.PlaybackStateCompat currentState = 
            mediaSession.getController().getPlaybackState();
        if (currentState == null || currentState.getState() == android.support.v4.media.session.PlaybackStateCompat.STATE_NONE) {
            return;
        }

        long durationMs = (long) (duration * 1000);
        long positionMs = (long) (position * 1000);
        float rate = (float) playbackRate;

        android.support.v4.media.MediaMetadataCompat currentMetadata = mediaSession.getController().getMetadata();
        android.support.v4.media.MediaMetadataCompat.Builder metadataBuilder;
        if (currentMetadata == null) {
            metadataBuilder = new android.support.v4.media.MediaMetadataCompat.Builder();
        } else {
            metadataBuilder = new android.support.v4.media.MediaMetadataCompat.Builder(currentMetadata);
        }
        metadataBuilder.putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        mediaSession.setMetadata(metadataBuilder.build());

        android.support.v4.media.session.PlaybackStateCompat.Builder stateBuilder = 
            new android.support.v4.media.session.PlaybackStateCompat.Builder(currentState);
        stateBuilder.setState(currentState.getState(), positionMs, rate);
        mediaSession.setPlaybackState(stateBuilder.build());

        updateNotification();
    }

    private void updatePlaybackState(String stateStr) {
        android.support.v4.media.session.PlaybackStateCompat currentState = 
            mediaSession.getController().getPlaybackState();
        if (currentState == null) {
            currentState = new android.support.v4.media.session.PlaybackStateCompat.Builder()
                .setActions(0)
                .setState(android.support.v4.media.session.PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .build();
        }

        int state;
        switch (stateStr) {
            case "playing":
                state = android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
                break;
            case "paused":
                state = android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
                break;
            default:
                state = android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
                break;
        }

        android.support.v4.media.session.PlaybackStateCompat.Builder newStateBuilder = 
            new android.support.v4.media.session.PlaybackStateCompat.Builder(currentState);
        newStateBuilder.setState(state, android.support.v4.media.session.PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        mediaSession.setPlaybackState(newStateBuilder.build());

        if (state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING || 
            state == android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED) {
            startForeground(FOREGROUND_SERVICE_ID, buildNotification());
        } else {
            stopForeground(false);
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
            if (state == android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED) {
                stopSelf();
            }
        }
    }

    private void setMediaActionHandlers(String[] actions) {
        android.support.v4.media.session.PlaybackStateCompat currentState = 
            mediaSession.getController().getPlaybackState();
        if (currentState == null) return;
        
        long supportedActions = 0;
        if (actions != null) {
            for (String action : actions) {
                switch (action) {
                    case "play":
                        supportedActions |= android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
                        break;
                    case "pause":
                        supportedActions |= android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
                        break;
                    case "previoustrack":
                        supportedActions |= android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
                        break;
                    case "nexttrack":
                        supportedActions |= android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
                        break;
                }
            }
        }
        if ((supportedActions & android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY) != 0 && 
            (supportedActions & android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE) != 0) {
            supportedActions |= android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
        }

        android.support.v4.media.session.PlaybackStateCompat.Builder newStateBuilder = 
            new android.support.v4.media.session.PlaybackStateCompat.Builder(currentState);
        newStateBuilder.setActions(supportedActions);
        mediaSession.setPlaybackState(newStateBuilder.build());

        updateNotification();
    }

    private Notification buildNotification() {
        android.support.v4.media.MediaMetadataCompat metadata = mediaSession.getController().getMetadata();
        android.support.v4.media.session.PlaybackStateCompat playbackState = mediaSession.getController().getPlaybackState();

        if (playbackState == null || (playbackState.getState() != android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING && 
            playbackState.getState() != android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED)) {
            return null;
        }

        boolean isPlaying = playbackState.getState() == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        List<Integer> compactActionIndices = new ArrayList<>();

        if ((playbackState.getActions() & android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            builder.addAction(
                android.R.drawable.ic_media_previous, "Previous",
                createActionIntent(ACTION_PREVIOUS)
            );
            compactActionIndices.add(compactActionIndices.size());
        }

        if ((playbackState.getActions() & android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE) != 0) {
            builder.addAction(new androidx.core.app.NotificationCompat.Action(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play",
                createActionIntent(isPlaying ? ACTION_PAUSE : ACTION_PLAY)
            ));
            compactActionIndices.add(compactActionIndices.size());
        }

        if ((playbackState.getActions() & android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            builder.addAction(
                android.R.drawable.ic_media_next, "Next",
                createActionIntent(ACTION_NEXT)
            );
            compactActionIndices.add(compactActionIndices.size());
        }

        int[] compactIndices = new int[compactActionIndices.size()];
        for (int i = 0; i < compactActionIndices.size(); i++) {
            compactIndices[i] = compactActionIndices.get(i);
        }

        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, MediaPlaybackService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent deletePendingIntent = PendingIntent.getService(this, 0, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(metadata != null ? metadata.getDescription().getTitle() : "Radio")
            .setContentText(metadata != null ? metadata.getDescription().getSubtitle() : "...")
            .setLargeIcon(metadata != null ? metadata.getDescription().getIconBitmap() : null)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(compactIndices)
            );

        return builder.build();
    }

    private void updateNotification() {
        if (mediaSession.getController().getPlaybackState() == null) {
            return;
        }

        android.support.v4.media.session.PlaybackStateCompat state = mediaSession.getController().getPlaybackState();
        if (state.getState() == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING || 
            state.getState() == android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED) {
            Notification notification = buildNotification();
            if (notification != null) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
            }
        } else {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        }
    }
    
    private PendingIntent createActionIntent(String action) {
        Intent intent = new Intent(this, MediaPlaybackService.class);
        intent.setAction(action);
        int requestCode = action.hashCode();
        return PendingIntent.getService(this, requestCode, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    
    private void sendActionToWebView(String action) {
        Log.d("WebToApk", "sendActionToWebView: " + action);
        Intent intent = new Intent(BROADCAST_MEDIA_ACTION);
        intent.putExtra(EXTRA_MEDIA_ACTION, action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    @Override
    public void onDestroy() {
        Log.d("WebToApk", "MediaPlaybackService onDestroy");
        super.onDestroy();
        stopCallSound();
        stopVibrate();
        if (mediaSession != null) {
            mediaSession.release();
        }
        executor.shutdown();
        try {
            unregisterReceiver(becomingNoisyReceiver);
        } catch (Exception e) {
            Log.e("WebToApk", "Error unregistering receiver", e);
        }
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
