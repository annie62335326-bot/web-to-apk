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
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class MediaPlaybackService extends Service {
    private static final String TAG = "MediaPlaybackService";
    private static final String CHANNEL_ID = "media_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Actions
    public static final String ACTION_PLAY_MESSAGE_SOUND = "PLAY_MESSAGE_SOUND";
    public static final String ACTION_PLAY_CALL_INCOMING = "PLAY_CALL_INCOMING";
    public static final String ACTION_PLAY_CALL_OUTGOING = "PLAY_CALL_OUTGOING";
    public static final String ACTION_STOP_CALL_SOUND = "STOP_CALL_SOUND";
    public static final String ACTION_VIBRATE = "VIBRATE";
    public static final String ACTION_VIBRATE_LONG = "VIBRATE_LONG";
    public static final String ACTION_STOP_VIBRATE = "STOP_VIBRATE";

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private boolean isRinging = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MediaPlaybackService onCreate");
        
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        // 获取唤醒锁，确保后台能播放声音
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebToApk:MediaWakeLock");
        wakeLock.setReferenceCounted(false);
        
        // 创建通知渠道
        createNotificationChannel();
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, getForegroundNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "媒体服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于播放消息提示音和铃声");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification getForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("聊天应用")
            .setContentText("正在运行，新消息会及时通知您")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand action: " + action);
        
        switch (action) {
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
                int duration = intent.getIntExtra("duration", 200);
                vibrate(duration);
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

    private void playMessageSound() {
        try {
            // 获取唤醒锁
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(5000);
            }
            
            // 停止当前播放
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                } catch (Exception e) {}
                mediaPlayer = null;
            }
            
            // 创建新的 MediaPlayer
            mediaPlayer = new MediaPlayer();
            
            // 设置音频属性 - 使用通知流
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            }
            
            // 获取默认通知音
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mediaPlayer.setDataSource(this, soundUri);
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> {
                try {
                    mp.release();
                } catch (Exception e) {}
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
                mediaPlayer = null;
            });
            mediaPlayer.start();
            
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

    private void playCallIncomingSound() {
        try {
            if (isRinging) {
                Log.d(TAG, "Already ringing");
                return;
            }
            isRinging = true;
            
            // 获取唤醒锁
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(30000);
            }
            
            // 停止当前播放
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.release();
                } catch (Exception e) {}
                mediaPlayer = null;
            }
            
            // 创建新的 MediaPlayer
            mediaPlayer = new MediaPlayer();
            
            // 设置音频属性 - 使用铃声流
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            }
            
            // 获取默认铃声
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mediaPlayer.setDataSource(this, ringtoneUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            
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

    private void playCallOutgoingSound() {
        try {
            if (isRinging) return;
            isRinging = true;
            
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(30000);
            }
            
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                    mediaPlayer.release();
                } catch (Exception e) {}
                mediaPlayer = null;
            }
            
            mediaPlayer = new MediaPlayer();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            }
            
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mediaPlayer.setDataSource(this, ringtoneUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            Log.d(TAG, "Call outgoing sound played");
            
        } catch (Exception e) {
            Log.e(TAG, "播放去电铃声失败", e);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            isRinging = false;
        }
    }

    private void stopCallSound() {
        Log.d(TAG, "stopCallSound, isRinging: " + isRinging);
        isRinging = false;
        
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {}
            mediaPlayer = null;
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void vibrate(int duration) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        } catch (Exception e) {
            Log.e(TAG, "振动失败", e);
        }
    }

    private void vibrateLong() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            long[] pattern = {0, 500, 300, 500, 300, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "长振动失败", e);
        }
    }

    private void stopVibrate() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception e) {}
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MediaPlaybackService onDestroy");
        stopCallSound();
        stopVibrate();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
