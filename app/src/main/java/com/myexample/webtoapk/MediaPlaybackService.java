@android.webkit.JavascriptInterface
public void playMessageSound() {
    Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
    intent.setAction(MediaPlaybackService.ACTION_PLAY_MESSAGE_SOUND);
    startService(intent);
}

@android.webkit.JavascriptInterface
public void playCallIncomingSound() {
    Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
    intent.setAction(MediaPlaybackService.ACTION_PLAY_CALL_INCOMING);
    startService(intent);
}

@android.webkit.JavascriptInterface
public void playCallOutgoingSound() {
    Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
    intent.setAction(MediaPlaybackService.ACTION_PLAY_CALL_OUTGOING);
    startService(intent);
}

@android.webkit.JavascriptInterface
public void stopCallSound() {
    Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
    intent.setAction(MediaPlaybackService.ACTION_STOP_CALL_SOUND);
    startService(intent);
}

@android.webkit.JavascriptInterface
public void vibrate(int duration) {
    Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
    intent.setAction(MediaPlaybackService.ACTION_VIBRATE);
    intent.putExtra("duration", duration);
    startService(intent);
}

@android.webkit.JavascriptInterface
public void vibrateLong() {
    Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
    intent.setAction(MediaPlaybackService.ACTION_VIBRATE_LONG);
    startService(intent);
}

@android.webkit.JavascriptInterface
public void stopVibrate() {
    Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
    intent.setAction(MediaPlaybackService.ACTION_STOP_VIBRATE);
    startService(intent);
}
