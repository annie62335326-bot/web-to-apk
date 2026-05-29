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
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;

public class MediaPlaybackService extends Service {
    private static final String TAG = "MediaPlaybackService";
    
    // 通知渠道ID
    private static final String NOTIFICATION_CHANNEL_ID = "web_app_media";
    private static final String BACKGROUND_CHANNEL_ID = "web_app_background";
    private static final int NOTIFICATION_ID = 101;
    private static final int FOREGROUND_ID = 102;
    
    // 动作常量
    public static final String ACTION_UPDATE_METADATA = "com.myexample.webtoapk.UPDATE_METADATA";
    public static final String ACTION_UPDATE_STATE = "com.myexample.webtoapk.UPDATE_STATE";
    public static final String ACTION_SET_HANDLERS = "com.myexample.webtoapk.SET_HANDLERS";
    public static final String ACTION_STOP_SERVICE = "com.myexample.webtoapk.STOP_SERVICE";
    public static final String ACTION_UPDATE_POSITION = "com.myexample.webtoapk.UPDATE_POSITION";
    
    // 通知按钮动作
    public static final String ACTION_PLAY = "com.myexample.webtoapk.PLAY";
    public static final String ACTION_PAUSE = "com.myexample.webtoapk.PAUSE";
    public static final String ACTION_NEXT = "com.myexample.webtoapk.NEXT";
    public static final String ACTION_PREVIOUS = "com.myexample.webtoapk.PREVIOUS";
    
    // 声音和振动动作
    public static final String ACTION_PLAY_MESSAGE_SOUND = "com.myexample.webtoapk.PLAY_MESSAGE_SOUND";
    public static final String ACTION_PLAY_CALL_INCOMING = "com.myexample.webtoapk.PLAY_CALL_INCOMING";
    public static final String ACTION_PLAY_CALL_OUTGOING = "com.myexample.webtoapk.PLAY_CALL_OUTGOING";
    public static final String ACTION_STOP_CALL_SOUND = "com.myexample.webtoapk.STOP_CALL_SOUND";
    public static final String ACTION_VIBRATE = "com.myexample.webtoapk.VIBRATE";
    public static final String ACTION_VIBRATE_LONG = "com.myexample.webtoapk.VIBRATE_LONG";
    public static final String ACTION_STOP_VIBRATE = "com.myexample.webtoapk.STOP_VIBRATE";
    
    // 广播动作
    public static final String BROADCAST_MEDIA_ACTION = "com.myexample.webtoapk.BROADCAST_MEDIA_ACTION";
    public static final String EXTRA_MEDIA_ACTION = "EXTRA_MEDIA_ACTION";
    
    // 媒体播放器
    private MediaPlayer callMediaPlayer;
    private MediaPlayer messageMediaPlayer;
    
    // 振动器
    private Vibrator vibrator;
    
    // 唤醒锁
    private PowerManager.WakeLock wakeLock;
    
    // 状态
    private boolean isRinging = false;
    private boolean isForeground = false;
    
    // 音频管理器
    private AudioManager audioManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MediaPlaybackService onCreate");
        
        // 初始化振动器
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        // 初始化音频管理器
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        // 初始化唤醒锁
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebToApk:MediaWakeLock");
        wakeLock.setReferenceCounted(false);
        
        // 创建通知渠道
        createNotificationChannels();
        
        // 启动前台服务
        startForegroundService();
    }
    
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 媒体通知渠道
            NotificationChannel mediaChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "媒体播放",
                NotificationManager.IMPORTANCE_LOW
            );
            mediaChannel.setDescription("媒体播放控制通知");
            mediaChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            // 后台服务渠道
            NotificationChannel backgroundChannel = new NotificationChannel(
                BACKGROUND_CHANNEL_ID,
                "后台服务",
                NotificationManager.IMPORTANCE_LOW
            );
            backgroundChannel.setDescription("保持应用在后台运行");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(mediaChannel);
                manager.createNotificationChannel(backgroundChannel);
            }
        }
    }
    
    private void startForegroundService() {
        if (isForeground) return;
        
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Notification notification = new NotificationCompat.Builder(this, BACKGROUND_CHANNEL_ID)
            .setContentTitle("聊天应用")
            .setContentText("正在运行，新消息会及时通知您")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
        
        startForeground(FOREGROUND_ID, notification);
        isForeground = true;
        Log.d(TAG, "Foreground service started");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand action: " + action);
        
        switch (action) {
            // 媒体控制
            case ACTION_UPDATE_METADATA:
                break;
            case ACTION_UPDATE_STATE:
                break;
            case ACTION_UPDATE_POSITION:
                break;
            case ACTION_SET_HANDLERS:
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
                
            // 声音和振动
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
    
    // ========== 消息提示音 ==========
    private void playMessageSound() {
        try {
            stopCallSound();
            
            // 获取唤醒锁
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(5000);
            }
            
            // 释放之前的播放器
            if (messageMediaPlayer != null) {
                try {
                    if (messageMediaPlayer.isPlaying()) {
                        messageMediaPlayer.stop();
                    }
                    messageMediaPlayer.release();
                } catch (Exception e) {}
                messageMediaPlayer = null;
            }
            
            messageMediaPlayer = new MediaPlayer();
            
            // 设置音频属性
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                messageMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            } else {
                messageMediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            }
            
            // 获取默认通知音
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            messageMediaPlayer.setDataSource(this, soundUri);
            messageMediaPlayer.prepare();
            messageMediaPlayer.setOnCompletionListener(mp -> {
                try {
                    mp.release();
                } catch (Exception e) {}
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                messageMediaPlayer = null;
            });
            messageMediaPlayer.start();
            
            Log.d(TAG, "Message sound played");
            
        } catch (IOException e) {
            Log.e(TAG, "播放消息提示音失败", e);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "播放消息提示音异常", e);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }
    
    // ========== 来电铃声 ==========
    private void playCallIncomingSound() {
        try {
            if (isRinging) {
                Log.d(TAG, "Already ringing, skip");
                return;
            }
            isRinging = true;
            
            // 获取唤醒锁（保持CPU运行）
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(30000);
            }
            
            // 释放之前的播放器
            if (callMediaPlayer != null) {
                try {
                    if (callMediaPlayer.isPlaying()) {
                        callMediaPlayer.stop();
                    }
                    callMediaPlayer.release();
                } catch (Exception e) {}
                callMediaPlayer = null;
            }
            
            callMediaPlayer = new MediaPlayer();
            
            // 设置音频属性 - 使用铃声流，确保在后台也能播放
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                callMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            } else {
                callMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            }
            
            // 获取默认铃声
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            callMediaPlayer.setDataSource(this, ringtoneUri);
            callMediaPlayer.setLooping(true);
            callMediaPlayer.prepare();
            callMediaPlayer.start();
            
            // 长振动
            vibrateLong();
            
            Log.d(TAG, "Call incoming sound played");
            
        } catch (IOException e) {
            Log.e(TAG, "播放来电铃声失败", e);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            isRinging = false;
        } catch (Exception e) {
            Log.e(TAG, "播放来电铃声异常", e);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            isRinging = false;
        }
    }
    
    // ========== 去电铃声 ==========
    private void playCallOutgoingSound() {
        try {
            if (isRinging) {
                Log.d(TAG, "Already ringing, skip");
                return;
            }
            isRinging = true;
            
            // 获取唤醒锁
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(30000);
            }
            
            // 释放之前的播放器
            if (callMediaPlayer != null) {
                try {
                    if (callMediaPlayer.isPlaying()) {
                        callMediaPlayer.stop();
                    }
                    callMediaPlayer.release();
                } catch (Exception e) {}
                callMediaPlayer = null;
            }
            
            callMediaPlayer = new MediaPlayer();
            
            // 设置音频属性
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                callMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            } else {
                callMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            }
            
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            callMediaPlayer.setDataSource(this, ringtoneUri);
            callMediaPlayer.setLooping(true);
            callMediaPlayer.prepare();
            callMediaPlayer.start();
            
            Log.d(TAG, "Call outgoing sound played");
            
        } catch (IOException e) {
            Log.e(TAG, "播放去电铃声失败", e);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            isRinging = false;
        } catch (Exception e) {
            Log.e(TAG, "播放去电铃声异常", e);
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            isRinging = false;
        }
    }
    
    // ========== 停止铃声 ==========
    private void stopCallSound() {
        Log.d(TAG, "stopCallSound called, isRinging: " + isRinging);
        isRinging = false;
        
        if (callMediaPlayer != null) {
            try {
                if (callMediaPlayer.isPlaying()) {
                    callMediaPlayer.stop();
                }
                callMediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "释放MediaPlayer异常", e);
            }
            callMediaPlayer = null;
        }
        
        // 释放唤醒锁
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
    
    // ========== 振动方法 ==========
    private void vibrate(int duration) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.d(TAG, "Vibrator not available");
            return;
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
            Log.d(TAG, "Vibrate for " + duration + "ms");
        } catch (Exception e) {
            Log.e(TAG, "振动失败", e);
        }
    }
    
    private void vibrateLong() {
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.d(TAG, "Vibrator not available");
            return;
        }
        
        try {
            // 来电振动模式：振500ms 停300ms 振500ms 停300ms 振500ms
            long[] pattern = {0, 500, 300, 500, 300, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
            Log.d(TAG, "Long vibrate started");
        } catch (Exception e) {
            Log.e(TAG, "长振动失败", e);
        }
    }
    
    private void stopVibrate() {
        if (vibrator == null) return;
        
        try {
            vibrator.cancel();
            Log.d(TAG, "Vibrate stopped");
        } catch (Exception e) {
            Log.e(TAG, "停止振动失败", e);
        }
    }
    
    // ========== 辅助方法 ==========
    private void sendActionToWebView(String action) {
        Intent intent = new Intent(BROADCAST_MEDIA_ACTION);
        intent.putExtra(EXTRA_MEDIA_ACTION, action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Action sent to WebView: " + action);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MediaPlaybackService onDestroy");
        
        // 停止所有声音
        stopCallSound();
        stopVibrate();
        
        // 释放消息提示音播放器
        if (messageMediaPlayer != null) {
            try {
                if (messageMediaPlayer.isPlaying()) {
                    messageMediaPlayer.stop();
                }
                messageMediaPlayer.release();
            } catch (Exception e) {}
            messageMediaPlayer = null;
        }
        
        // 释放唤醒锁
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        isForeground = false;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
