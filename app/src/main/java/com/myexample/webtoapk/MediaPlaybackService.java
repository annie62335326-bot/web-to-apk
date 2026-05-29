package com.myexample.webtoapk;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class MediaPlaybackService extends Service {
    
    public static final String ACTION_PLAY_MESSAGE_SOUND = "com.myexample.webtoapk.PLAY_MESSAGE_SOUND";
    public static final String ACTION_PLAY_CALL_INCOMING = "com.myexample.webtoapk.PLAY_CALL_INCOMING";
    public static final String ACTION_PLAY_CALL_OUTGOING = "com.myexample.webtoapk.PLAY_CALL_OUTGOING";
    public static final String ACTION_STOP_CALL_SOUND = "com.myexample.webtoapk.STOP_CALL_SOUND";
    public static final String ACTION_VIBRATE = "com.myexample.webtoapk.VIBRATE";
    public static final String ACTION_VIBRATE_LONG = "com.myexample.webtoapk.VIBRATE_LONG";
    public static final String ACTION_STOP_VIBRATE = "com.myexample.webtoapk.STOP_VIBRATE";

    private MediaPlayer callMediaPlayer;
    private Vibrator vibrator;
    private boolean isRinging = false;

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        Log.d("WebToApk", "MediaPlaybackService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        Log.d("WebToApk", "Action: " + action);

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

    private void playMessageSound() {
        try {
            stopCallSound();
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer mp = MediaPlayer.create(this, soundUri);
            if (mp != null) {
                mp.setOnCompletionListener(m -> m.release());
                mp.start();
                Log.d("WebToApk", "Message sound played");
            }
        } catch (Exception e) {
            Log.e("WebToApk", "播放消息提示音失败", e);
        }
    }

    private void playCallIncomingSound() {
        try {
            if (isRinging) return;
            isRinging = true;
            stopCallSound();
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            callMediaPlayer = MediaPlayer.create(this, ringtoneUri);
            if (callMediaPlayer != null) {
                callMediaPlayer.setLooping(true);
                callMediaPlayer.start();
                Log.d("WebToApk", "Call sound played");
            }
        } catch (Exception e) {
            Log.e("WebToApk", "播放来电铃声失败", e);
        }
    }

    private void playCallOutgoingSound() {
        playCallIncomingSound(); // 使用相同的铃声
    }

    private void stopCallSound() {
        isRinging = false;
        if (callMediaPlayer != null) {
            try {
                if (callMediaPlayer.isPlaying()) callMediaPlayer.stop();
                callMediaPlayer.release();
            } catch (Exception e) {}
            callMediaPlayer = null;
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
            long[] pattern = {0, 500, 300, 500};
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCallSound();
        stopVibrate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
