package com.myexample.webtoapk;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;

public class MediaPlaybackService extends Service {
    private static final String TAG = "MediaPlaybackService";
    
    public static final String ACTION_PLAY_MESSAGE_SOUND = "PLAY_MESSAGE_SOUND";
    public static final String ACTION_PLAY_CALL_INCOMING = "PLAY_CALL_INCOMING";
    public static final String ACTION_PLAY_CALL_OUTGOING = "PLAY_CALL_OUTGOING";
    public static final String ACTION_STOP_CALL_SOUND = "STOP_CALL_SOUND";
    public static final String ACTION_VIBRATE = "VIBRATE";
    public static final String ACTION_STOP_VIBRATE = "STOP_VIBRATE";
    
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private boolean isPlaying = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand: " + action);
        
        switch (action) {
            case ACTION_PLAY_MESSAGE_SOUND:
                playMessageSound();
                break;
            case ACTION_PLAY_CALL_INCOMING:
                playCallSound();
                break;
            case ACTION_PLAY_CALL_OUTGOING:
                playCallSound();
                break;
            case ACTION_STOP_CALL_SOUND:
                stopSound();
                break;
            case ACTION_VIBRATE:
                int duration = intent.getIntExtra("duration", 200);
                vibrate(duration);
                break;
            case ACTION_STOP_VIBRATE:
                stopVibrate();
                break;
        }
        
        return START_NOT_STICKY;
    }
    
    private void playMessageSound() {
        try {
            stopSound();
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mediaPlayer = MediaPlayer.create(this, soundUri);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    mediaPlayer = null;
                });
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "播放消息音失败", e);
        }
    }
    
    private void playCallSound() {
        try {
            if (isPlaying) return;
            stopSound();
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mediaPlayer = MediaPlayer.create(this, ringtoneUri);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
                isPlaying = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "播放铃声失败", e);
        }
    }
    
    private void stopSound() {
        isPlaying = false;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {}
            mediaPlayer = null;
        }
    }
    
    private void vibrate(int duration) {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(duration);
        }
    }
    
    private void stopVibrate() {
        if (vibrator != null) {
            vibrator.cancel();
        }
    }
    
    @Override
    public void onDestroy() {
        stopSound();
        stopVibrate();
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
