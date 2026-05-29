package com.myexample.webtoapk;

import android.content.DialogInterface;
import android.net.http.SslError;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.os.Handler;
import android.webkit.WebSettings;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.content.Intent;
import android.net.Uri;
import android.content.res.Configuration;
import android.widget.EditText;
import android.webkit.JsResult;
import android.webkit.JsPromptResult;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.graphics.Bitmap;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.graphics.Color;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceError;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import android.webkit.JavascriptInterface;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.os.Looper;
import android.webkit.GeolocationPermissions;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.TextView;
import android.app.Activity;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.webkit.DownloadListener;
import android.app.DownloadManager;
import android.webkit.URLUtil;
import android.os.Environment;
import static android.content.Context.DOWNLOAD_SERVICE;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.unifiedpush.android.connector.UnifiedPush;
import static org.unifiedpush.android.connector.ConstantsKt.INSTANCE_DEFAULT;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.json.JSONException;
import org.json.JSONObject;
import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.webkit.PermissionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import android.app.AlarmManager;
import android.os.SystemClock;

public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 2;
    private static final int MEDIA_PERMISSION_REQUEST_CODE = 1001;
    private static final String NOTIFICATION_CHANNEL_ID = "web_app_notifications";
    private static final String NOTIFICATION_CHANNEL_NAME = "Web App Notifications";

    private WebView webview;
    private UserScriptManager userScriptManager;
    private ProgressBar spinner;
    private View mainLayout;
    private View errorLayout;
    private ViewGroup parentLayout;
    private boolean errorOccurred = false;
    private ValueCallback<Uri[]> mFilePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private WebAppInterface webAppInterface;
    private BroadcastReceiver unifiedPushEndpointReceiver;
    private BroadcastReceiver mediaActionReceiver;
    private PermissionRequest currentPermissionRequest;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    String mainURL = "https://im8885.akfjcn.shop";
    boolean requireDoubleBackToExit = true;
    boolean allowSubdomains = true;

    boolean enableExternalLinks = true;
    boolean openExternalLinksInBrowser = true;
    boolean confirmOpenInBrowser = true;

    boolean allowOpenMobileApp = false;
    boolean confirmOpenExternalApp = true;

    String cookies = "";
    String basicAuth = "";
    String userAgent = "";
    boolean blockLocalhostRequests = true;
    boolean JSEnabled = true;
    boolean JSCanOpenWindowsAutomatically = true;
    boolean DomStorageEnabled = true;
    boolean DatabaseEnabled = true;
    boolean MediaPlaybackRequiresUserGesture = true;
    boolean SavePassword = true;
    boolean AllowFileAccess = true;
    boolean AllowFileAccessFromFileURLs = true;
    boolean showDetailsOnErrorScreen = false;
    boolean forceLandscapeMode = false;
    boolean edgeToEdge = false;
    boolean forceDarkTheme = false;
    boolean allowMixedContent = false;
    String cacheMode = "default";
    int fadeInDuration = 400;
    boolean DebugWebView = false;

    boolean geolocationEnabled = true;
    boolean cameraEnabled = true;
    boolean microphoneEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (forceDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        if (edgeToEdge) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }

        super.onCreate(savedInstanceState);

        if (edgeToEdge) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, importance);
            channel.setDescription("Channel for web app notifications");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d("WebToApk", "Notification channel created.");
        }

        if (forceLandscapeMode) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        setContentView(R.layout.activity_main);
        mainLayout = findViewById(android.R.id.content);
        parentLayout = (ViewGroup) mainLayout.getParent();
        userScriptManager = new UserScriptManager(this, mainURL);

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        Log.d("WebToApk", "Action: " + action);
        Log.d("WebToApk", "Data: " + data);
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            mainURL = data.toString();
        }

        webview = findViewById(R.id.webView);
        webview.setAlpha(0f);
        spinner = findViewById(R.id.progressBar1);
        webview.setWebViewClient(new CustomWebViewClient());
        webview.setWebChromeClient(new CustomWebChrome());
        webAppInterface = new WebAppInterface(this);
        webview.addJavascriptInterface(webAppInterface, "WebToApk");

        WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(JSEnabled);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(JSCanOpenWindowsAutomatically);
        webSettings.setGeolocationEnabled(geolocationEnabled);
        webSettings.setDomStorageEnabled(DomStorageEnabled);
        webSettings.setDatabaseEnabled(DatabaseEnabled);
        webSettings.setMediaPlaybackRequiresUserGesture(MediaPlaybackRequiresUserGesture);
        webSettings.setSavePassword(SavePassword);
        webSettings.setAllowFileAccess(AllowFileAccess);
        webSettings.setAllowFileAccessFromFileURLs(AllowFileAccessFromFileURLs);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webview.setWebContentsDebuggingEnabled(DebugWebView);

        if (allowMixedContent) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        if (!userAgent.isEmpty()) {
            webSettings.setUserAgentString(userAgent);
        }

        switch (cacheMode) {
            case "aggressive":
                webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                break;
            case "no_cache":
                webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
                webview.clearCache(true);
                break;
            default:
                webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
                break;
        }

        webview.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        CookieManager cookieManager = CookieManager.getInstance();
        CookieManager.getInstance().setAcceptThirdPartyCookies(webview, true);
        cookieManager.setCookie(mainURL, cookies);
        cookieManager.flush();

        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Uri[] results = null;

                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent intentData = result.getData();

                    if (intentData.getClipData() != null) {
                        int count = intentData.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = intentData.getClipData().getItemAt(i).getUri();
                        }
                    } else if (intentData.getData() != null) {
                        results = new Uri[]{intentData.getData()};
                    }
                }

                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(results);
                    mFilePathCallback = null;
                }
            }
        );

        webview.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);

                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);

                request.setDescription(getString(R.string.download_description));
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));

                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));

                try {
                    downloadManager.enqueue(request);
                    Toast.makeText(getApplicationContext(), R.string.download_started, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), R.string.download_failed, Toast.LENGTH_LONG).show();
                    Log.e("WebToApk", "Failed to start download", e);
                }
            }
        });

        unifiedPushEndpointReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String endpoint = intent.getStringExtra("endpoint");
                String p256dh = intent.getStringExtra("p256dh");
                String auth = intent.getStringExtra("auth");

                Log.d("WebToApk", "Received new UnifiedPush data. Endpoint: " + endpoint);

                if (endpoint != null && p256dh != null && auth != null && webview != null) {
                    try {
                        JSONObject keys = new JSONObject();
                        keys.put("p256dh", p256dh);
                        keys.put("auth", auth);

                        JSONObject subscription = new JSONObject();
                        subscription.put("endpoint", endpoint);
                        subscription.put("expirationTime", JSONObject.NULL);
                        subscription.put("keys", keys);

                        String subscriptionJson = subscription.toString();

                        webview.post(() -> {
                            String js = "if (typeof window.__shim_onNewEndpoint === 'function') { window.__shim_onNewEndpoint('" + subscriptionJson.replace("'", "\\'") + "'); }";
                            webview.evaluateJavascript(js, null);
                        });

                    } catch (JSONException e) {
                         Log.e("WebToApk", "Failed to create subscription JSON for shim", e);
                    }
                }
            }
        };
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unifiedPushEndpointReceiver, new IntentFilter("com.myexample.webtoapk.NEW_ENDPOINT"), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(unifiedPushEndpointReceiver, new IntentFilter("com.myexample.webtoapk.NEW_ENDPOINT"));
        }

        if (edgeToEdge) {
            ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                float density = v.getResources().getDisplayMetrics().density;

                float top = insets.top / density;
                float bottom = insets.bottom / density;
                float left = insets.left / density;
                float right = insets.right / density;

                Log.d("WebToApk", String.format(java.util.Locale.US,
                    "Insets (CSS px) -> T:%.2f, B:%.2f, L:%.2f, R:%.2f",
                    top, bottom, left, right
                ));

                String js = String.format(java.util.Locale.US,
                    "document.documentElement.style.setProperty('--safe-area-inset-top', '%.2fpx');" +
                    "document.documentElement.style.setProperty('--safe-area-inset-bottom', '%.2fpx');" +
                    "document.documentElement.style.setProperty('--safe-area-inset-left', '%.2fpx');" +
                    "document.documentElement.style.setProperty('--safe-area-inset-right', '%.2fpx');" +
                    "document.dispatchEvent(new CustomEvent('WebToApkInsetsApplied'));",
                    top, bottom, left, right
                );
                webview.evaluateJavascript(js, null);

                return WindowInsetsCompat.CONSUMED;
            });
        }

        boolean stateRestored = false;
        if (savedInstanceState != null) {
            stateRestored = webview.restoreState(savedInstanceState) != null;
            if (stateRestored) Log.d("WebToApk", "Restored WebView state");
        }

        if (!stateRestored) {
            webview.loadUrl(mainURL);
        }

        mediaActionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && MediaPlaybackService.BROADCAST_MEDIA_ACTION.equals(intent.getAction())) {
                    String action = intent.getStringExtra(MediaPlaybackService.EXTRA_MEDIA_ACTION);
                    if (action != null) {
                        executeMediaActionInWebView(action);
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(mediaActionReceiver, new IntentFilter(MediaPlaybackService.BROADCAST_MEDIA_ACTION));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String newUrl = intent.getData().toString();
            if (webview != null) {
                webview.loadUrl(newUrl);
            }
        }
    }

    private void registerForUnifiedPush(final String vapidPublicKey) {
        if (vapidPublicKey == null || vapidPublicKey.isEmpty()) {
            Log.e("WebToApk", "VAPID public key is null or empty. Cannot register for push.");
            return;
        }

        UnifiedPush.tryUseCurrentOrDefaultDistributor(this, new Function1<Boolean, Unit>() {
            @Override
            public Unit invoke(Boolean success) {
                if (success) {
                    Log.d("WebToApk", "UnifiedPush distributor found, registering...");
                    UnifiedPush.register(
                        MainActivity.this,
                        INSTANCE_DEFAULT,
                        null,
                        vapidPublicKey
                    );
                } else {
                    Log.w("WebToApk", "No UnifiedPush distributor found or user cancelled.");

                    new Handler(Looper.getMainLooper()).post(() -> {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.push_distributor_required_title)
                            .setMessage(R.string.push_distributor_required_message)
                            .setPositiveButton(R.string.learn_more, (dialog, which) -> {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://unifiedpush.org/users/distributors/"));
                                startActivity(browserIntent);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    });
                }
                return Unit.INSTANCE;
            }
        });
    }

    private void executeMediaActionInWebView(String action) {
        Log.d("WebToApk", "Executing JS for media action: " + action);
        if (webview != null) {
            webview.post(() -> {
                String js = "if (typeof window.__runMediaAction === 'function') { window.__runMediaAction('" + action + "'); }";
                webview.evaluateJavascript(js, null);
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unifiedPushEndpointReceiver != null) {
            unregisterReceiver(unifiedPushEndpointReceiver);
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mediaActionReceiver);
        Intent intent = new Intent(this, MediaPlaybackService.class);
        stopService(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webview.saveState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean isGranted = true;
        if (grantResults.length == 0) {
            isGranted = false;
        } else {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    isGranted = false;
                    break;
                }
            }
        }

        if (requestCode == MEDIA_PERMISSION_REQUEST_CODE) {
            if (currentPermissionRequest != null) {
                if (isGranted) {
                    currentPermissionRequest.grant(currentPermissionRequest.getResources());
                } else {
                    currentPermissionRequest.deny();
                }
                currentPermissionRequest = null;
            }
        }
        else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (geoCallback != null) {
                geoCallback.invoke(geoOrigin, isGranted, false);
                geoCallback = null;
                geoOrigin = null;
            }
        }
        else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            final boolean granted = isGranted;
            if (webview != null) {
                webview.post(() -> {
                    String js = "if (typeof window.__onNotificationPermissionResult === 'function') { window.__onNotificationPermissionResult(" + granted + "); }";
                    webview.evaluateJavascript(js, null);
                });
            }
        }
    }

    private class CustomWebChrome extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            String src = consoleMessage.sourceId();
            Integer line = consoleMessage.lineNumber();
            String msg = consoleMessage.message();

            if (src.startsWith("http://") || src.startsWith("https://")) {
                src = src.substring(8);
                Log.e("WebToApk", "[" + src + ":" + line + "] " + msg);
            } else {
                switch (consoleMessage.messageLevel()) {
                    case ERROR:
                        Log.e("WebToApk", "\033[0;31m[" + src + ":" + line  +"] " + msg + "\033[0m");
                        break;
                    case WARNING:
                        Log.w("WebToApk", "\033[1;33m[" + src + ":" +  line +"]\033[0m " + msg);
                        break;
                    case LOG:
                    case DEBUG:
                    case TIP:
                        Log.d("WebToApk", "\033[0;34m[" + src + ":" +  line +"]\033[0m " + msg);
                        break;
                }
            }
            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final android.webkit.JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm();
                    }
                })
                .setCancelable(false)
                .create()
                .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.cancel();
                    }
                })
                .setCancelable(false)
                .create()
                .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
            final EditText input = new EditText(MainActivity.this);
            input.setText(defaultValue);

            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm(input.getText().toString());
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.cancel();
                    }
                })
                .setCancelable(false)
                .create()
                .show();
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (!geolocationEnabled) {
                callback.invoke(origin, false, false);
                return;
            }

            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                geoCallback = callback;
                geoOrigin = origin;
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                callback.invoke(origin, true, false);
            }
        }

        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;

        @Override
        public void onHideCustomView() {
            ((FrameLayout)getWindow().getDecorView()).removeView(mCustomView);
            mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
            setRequestedOrientation(mOriginalOrientation);
            mCustomViewCallback.onCustomViewHidden();
            mCustomViewCallback = null;
        }

        @Override
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            if (mCustomView != null) {
                onHideCustomView();
                return;
            }
            mCustomView = view;
            mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            mOriginalOrientation = getRequestedOrientation();
            mCustomViewCallback = callback;
            ((FrameLayout)getWindow().getDecorView()).addView(mCustomView,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE);
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
            }
            mFilePathCallback = filePathCallback;

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");

            String[] acceptTypes = fileChooserParams.getAcceptTypes();
            if (acceptTypes.length > 0 && acceptTypes[0] != null && !acceptTypes[0].isEmpty()) {
                if (acceptTypes[0].contains("image")) {
                    intent.setType("image/*");
                } else {
                    intent.setType("*/*");
                }
            }

            if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }

            Intent chooserIntent = Intent.createChooser(intent, "Выберите файл");

            try {
                fileChooserLauncher.launch(chooserIntent);
            } catch (ActivityNotFoundException e) {
                mFilePathCallback = null;
                Toast.makeText(MainActivity.this, "Невозможно открыть файловый менеджер", Toast.LENGTH_LONG).show();
                return false;
            }

            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            for (String resource : request.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) && !cameraEnabled) {
                    request.deny();
                    return;
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) && !microphoneEnabled) {
                    request.deny();
                    return;
                }
            }

            List<String> permissionsNeeded = new ArrayList<>();
            for (String resource : request.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.CAMERA);
                    }
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
                    }
                }
            }

            if (permissionsNeeded.isEmpty()) {
                request.grant(request.getResources());
            } else {
                currentPermissionRequest = request;
                ActivityCompat.requestPermissions(MainActivity.this, permissionsNeeded.toArray(new String[0]), MEDIA_PERMISSION_REQUEST_CODE);
            }
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            super.onPermissionRequestCanceled(request);
            currentPermissionRequest = null;
        }
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
            String failingUrl = error.getUrl();
            String currentUrl = view.getUrl();
            boolean isMainPage = (currentUrl != null && failingUrl != null && failingUrl.equals(currentUrl));
            if (!isMainPage) {
                handler.cancel();
                return;
            }

            final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(R.string.notification_error_ssl_cert_invalid);

            builder.setPositiveButton("continue", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.proceed();
                }
            });

            builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.cancel();
                }
            });
            final AlertDialog dialog = builder.create();
            dialog.show();
        }

        @Override
        public void onReceivedHttpAuthRequest(final WebView view, final android.webkit.HttpAuthHandler handler, String host, String realm) {
            if (MainActivity.this.basicAuth != null && !MainActivity.this.basicAuth.isEmpty()) {
                String[] parts = MainActivity.this.basicAuth.split(":", 2);
                if (parts.length == 2) {
                    String login = parts[0];
                    String password = parts[1];

                    String mainDomain = "";
                    try {
                        mainDomain = Uri.parse(MainActivity.this.mainURL).getHost();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    boolean domainIsValid = false;
                    if (mainDomain != null && !mainDomain.isEmpty() && host != null && !host.isEmpty()) {
                        if (MainActivity.this.allowSubdomains) {
                            domainIsValid = host.endsWith(mainDomain) || mainDomain.endsWith(host);
                        } else {
                            domainIsValid = host.equals(mainDomain);
                        }
                    }

                    if (domainIsValid) {
                        handler.proceed(login, password);
                        return;
                    }
                }
            }

            final View dialogView = getLayoutInflater().inflate(R.layout.auth_dialog, null);
            final EditText usernameInput = dialogView.findViewById(R.id.username);
            final EditText passwordInput = dialogView.findViewById(R.id.password);

            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Authentication Required")
                .setView(dialogView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String user = usernameInput.getText().toString();
                        String pass = passwordInput.getText().toString();
                        handler.proceed(user, pass);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handler.cancel();
                    }
                })
                .show();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                if (allowOpenMobileApp) {
                    if (confirmOpenExternalApp) {
                        new AlertDialog.Builder(view.getContext())
                            .setTitle(R.string.external_link)
                            .setMessage(R.string.open_in_external_app)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                        view.getContext().startActivity(intent);
                                    } catch (ActivityNotFoundException e) {
                                        Log.e("WebToApk", "\033[0;31mNo application can handle this URL:\033[0m " + url, e);
                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    } else {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            view.getContext().startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Log.e("WebToApk", "\033[0;31mNo application can handle this URL:\033[0m " + url);
                        }
                    }
                } else {
                    Log.d("WebToApk", "Opening URLs in external app is disabled: " + url);
                }
                return true;
            }

            String urlDomain = request.getUrl().getHost();
            String mainDomain = Uri.parse(mainURL).getHost();

            if (urlDomain == null || mainDomain == null) {
                return handleExternalLink(url, view);
            }

            boolean isInternalLink;
            if (allowSubdomains) {
                isInternalLink = urlDomain.endsWith(mainDomain) || mainDomain.endsWith(urlDomain);
            } else {
                isInternalLink = urlDomain.equals(mainDomain);
            }

            if (isInternalLink) {
                return false;
            }

            return handleExternalLink(url, view);
        }

        private boolean handleExternalLink(String url, WebView view) {
            if (!enableExternalLinks) {
                return true;
            }
            if (openExternalLinksInBrowser) {
                if (confirmOpenInBrowser) {
                    new AlertDialog.Builder(view.getContext())
                        .setTitle(R.string.external_link)
                        .setMessage(R.string.open_in_browser)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d("WebToApk", "\033[1;34mExternal link:\033[0m '" + url);
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                view.getContext().startActivity(intent);
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                    return true;
                } else {
                    Log.d("WebToApk", "\033[1;34mExternal link:\033[0m '" + url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                }
            }
            Log.d("WebToApk", "\033[1;34mExternal link:\033[0m '" + url);
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String host = request.getUrl().getHost();

            if (blockLocalhostRequests && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host))) {
                Log.d("WebToApk", "Blocked access to localhost resource: " + request.getUrl().toString());
                return new WebResourceResponse("text/plain", "UTF-8", null);
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView webview, String url, Bitmap favicon) {
            super.onPageStarted(webview, url, favicon);
            userScriptManager.injectScripts(webview, url);
        }

        @Override
        public void onPageFinished(WebView webview, String url) {
            if (!errorOccurred) {
                Log.d("WebToApk","Current page: " + url);
                spinner.setVisibility(View.GONE);
                if (webview.getAlpha() == 0f) {
                    webview.animate().alpha(1f).setDuration(fadeInDuration).start();
                }
            }
            super.onPageFinished(webview, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            String errorDescription = error.getDescription().toString();
            int errorCode = error.getErrorCode();

            if (request.isForMainFrame()) {
                switch (errorCode) {
                    case ERROR_AUTHENTICATION:
                    case ERROR_BAD_URL:
                    case ERROR_CONNECT:
                    case ERROR_FAILED_SSL_HANDSHAKE:
                    case ERROR_FILE:
                    case ERROR_FILE_NOT_FOUND:
                    case ERROR_HOST_LOOKUP:
                    case ERROR_IO:
                    case ERROR_PROXY_AUTHENTICATION:
                    case ERROR_TIMEOUT:
                    case ERROR_TOO_MANY_REQUESTS:
                    case ERROR_UNKNOWN:
                    case ERROR_UNSUPPORTED_AUTH_SCHEME:
                    case ERROR_UNSUPPORTED_SCHEME:
                        Log.e("WebToApk", "Major error: " + errorCode + " - " + errorDescription + " url: " + request.getUrl());
                        errorOccurred = true;
                        errorLayout = getLayoutInflater().inflate(R.layout.error, parentLayout, false);
                        parentLayout.removeView(mainLayout);
                        parentLayout.addView(errorLayout);
                        if (showDetailsOnErrorScreen) {
                            TextView errorTextView = errorLayout.findViewById(R.id.errorText);
                            if (errorTextView != null) {
                                errorTextView.setText("Error " + errorCode + ":\n" + errorDescription + "\nURL: " + request.getUrl().toString());
                            }
                        }
                        break;
                    default:
                        Log.w("WebToApk", "Minor error: " + errorCode + " - " + errorDescription + " url: " + request.getUrl());
                        break;
                }
            } else {
                Log.d("WebToApk", "Resource error: " + errorCode + " - " + errorDescription + " url: " + request.getUrl());
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            Log.e("WebToApk", "WebView render process gone! Did crash: " + detail.didCrash());
            if (webview != null) {
                ((ViewGroup)webview.getParent()).removeView(webview);
                webview.destroy();
                webview = null;
            }
            Toast.makeText(MainActivity.this, "Recovering from memory kill...", Toast.LENGTH_SHORT).show();
            finish();
            startActivity(getIntent());
            return true;
        }
    }

    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {
        if (webview.canGoBack()) {
            webview.goBack();
        } else {
            if (doubleBackToExitPressedOnce || !requireDoubleBackToExit) {
                finish();
                return;
            }
            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(this, R.string.exit_app, Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }
    }

    public void tryAgain(View v) {
        parentLayout.removeView(errorLayout);
        parentLayout.addView(mainLayout);
        webview.setAlpha(0f);
        spinner.setVisibility(View.VISIBLE);
        errorOccurred = false;
        webview.reload();
    }

    private class WebAppInterface {
        private Context context;

        WebAppInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void showShortToast(String message) {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void showLongToast(String message) {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public boolean hasNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
                        Log.d("WebToApk", "Requesting notification permission.");
                    } else {
                        Log.d("WebToApk", "Notification permission already granted.");
                    }
                });
            }
        }

        @JavascriptInterface
        public void showNotification(String title, String message, String deepLink) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w("WebToApk", "Notification permission not granted.");
                    return;
                }
            }

            Intent intent = new Intent(context, MainActivity.class);
            if (deepLink != null && !deepLink.isEmpty()) {
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(deepLink));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }

        @JavascriptInterface
        public void scheduleNotification(int id, long delayMs, String title, String message, String deepLink) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, ScheduledNotificationReceiver.class);
            intent.putExtra("id", id);
            intent.putExtra("title", title);
            intent.putExtra("message", message);
            intent.putExtra("deepLink", deepLink);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            long triggerTime = SystemClock.elapsedRealtime() + delayMs;

            if (alarmManager != null) {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
                Log.d("WebToApk", "Scheduled notification " + id + " for " + delayMs + "ms");
            }
        }

        @JavascriptInterface
        public void cancelScheduledNotification(int id) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, ScheduledNotificationReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                Log.d("WebToApk", "Cancelled notification " + id);
            }
        }

        @JavascriptInterface
        public void cancelAllScheduledNotifications() {
            Log.w("WebToApk", "cancelAllScheduledNotifications invoked but not robustly supported via explicit Intents without persistent tracking.");
        }

        @JavascriptInterface
        public void share(String title, String text, String url) {
            Log.d("WebToApk", "Share: " + title + " :: " +  text +" " + url);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String shareBody = (text != null ? text : "") + ((url != null && !url.isEmpty()) ? "\n" + url : "");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);

            context.startActivity(
                Intent.createChooser(shareIntent, title == null ? "Share" : title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            );
        }

        @JavascriptInterface
        public void unifiedPushSubscribe(String vapidPublicKey) {
            new Handler(Looper.getMainLooper()).post(() -> {
                Log.d("WebToApk", "JS shim triggered UnifiedPush registration.");
                MainActivity.this.registerForUnifiedPush(vapidPublicKey);
            });
        }

        @JavascriptInterface
        public void unifiedPushUnregister() {
            Log.d("WebToApk", "JS shim triggered UnifiedPush un-registration for default instance.");
            UnifiedPush.unregister(context, INSTANCE_DEFAULT);
        }

        @JavascriptInterface
        public String getUnifiedPushSubscriptionJson() {
            SharedPreferences prefs = context.getSharedPreferences("unifiedpush", Context.MODE_PRIVATE);
            String endpoint = prefs.getString("endpoint_" + INSTANCE_DEFAULT, null);
            String p256dh = prefs.getString("p256dh_" + INSTANCE_DEFAULT, null);
            String auth = prefs.getString("auth_" + INSTANCE_DEFAULT, null);

            if (endpoint == null || endpoint.isEmpty() || p256dh == null || auth == null) {
                return "";
            }

            try {
                JSONObject keys = new JSONObject();
                keys.put("p256dh", p256dh);
                keys.put("auth", auth);

                JSONObject subscription = new JSONObject();
                subscription.put("endpoint", endpoint);
                subscription.put("expirationTime", JSONObject.NULL);
                subscription.put("keys", keys);

                return subscription.toString();
            } catch (JSONException e) {
                Log.e("WebToApk", "Failed to create subscription JSON", e);
                return "";
            }
        }

        @JavascriptInterface
        public String getNotificationPermissionState() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    return "granted";
                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, Manifest.permission.POST_NOTIFICATIONS)) {
                         return "prompt";
                    }
                    return "prompt";
                }
            }
            return "granted";
        }

        @JavascriptInterface
        public void updateMediaMetadata(String title, String artist, String album, @Nullable String artworkUrl) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_UPDATE_METADATA);
            intent.putExtra("title", title);
            intent.putExtra("artist", artist);
            intent.putExtra("album", album);
            intent.putExtra("artworkUrl", artworkUrl);
            context.startService(intent);
        }

        @JavascriptInterface
        public void updateMediaPlaybackState(String state) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_UPDATE_STATE);
            intent.putExtra("state", state);
            context.startService(intent);
        }

        @JavascriptInterface
        public void setMediaActionHandlers(String[] actions) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_SET_HANDLERS);
            intent.putExtra("actions", actions);
            context.startService(intent);
        }

        @JavascriptInterface
        public void updateMediaPositionState(double duration, double playbackRate, double position) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_UPDATE_POSITION);
            intent.putExtra("duration", duration);
            intent.putExtra("playbackRate", playbackRate);
            intent.putExtra("position", position);
            context.startService(intent);
        }

        @JavascriptInterface
        public void clearAppCache() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (webview != null) {
                    webview.clearCache(true);
                    Log.d("WebToApk", "Cache cleared via WebToAPK.clearAppCache() from js");
                }
            });
        }

        // ========== 新增：消息提示音和来电铃声方法 ==========
        
        @JavascriptInterface
        public void playMessageSound() {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_PLAY_MESSAGE_SOUND);
            context.startService(intent);
        }

        @JavascriptInterface
        public void playCallIncomingSound() {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_PLAY_CALL_INCOMING);
            context.startService(intent);
        }

        @JavascriptInterface
        public void playCallOutgoingSound() {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_PLAY_CALL_OUTGOING);
            context.startService(intent);
        }

        @JavascriptInterface
        public void stopCallSound() {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_STOP_CALL_SOUND);
            context.startService(intent);
        }

        @JavascriptInterface
        public void vibrate(int duration) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_VIBRATE);
            intent.putExtra("duration", duration);
            context.startService(intent);
        }

        @JavascriptInterface
        public void vibrateLong() {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_VIBRATE_LONG);
            context.startService(intent);
        }

        @JavascriptInterface
        public void stopVibrate() {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_STOP_VIBRATE);
            context.startService(intent);
        }
    }

    public static class ScheduledNotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra("id", 0);
            String title = intent.getStringExtra("title");
            String message = intent.getStringExtra("message");
            String deepLink = intent.getStringExtra("deepLink");

            Intent activityIntent = new Intent(context, MainActivity.class);
            if (deepLink != null && !deepLink.isEmpty()) {
                activityIntent.setAction(Intent.ACTION_VIEW);
                activityIntent.setData(Uri.parse(deepLink));
            }
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(context, id, activityIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            
            notificationManager.notify(id, builder.build());
        }
    }
}
MainActivity.java

package com.myexample.webtoapk;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.Base64;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    // Actions from notification buttons
    public static final String ACTION_PLAY = "com.myexample.webtoapk.PLAY";
    public static final String ACTION_PAUSE = "com.myexample.webtoapk.PAUSE";
    public static final String ACTION_NEXT = "com.myexample.webtoapk.NEXT";
    public static final String ACTION_PREVIOUS = "com.myexample.webtoapk.PREVIOUS";

    // 新增：消息和铃声相关的 Action
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
    private MediaSessionCompat mediaSession;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BroadcastReceiver becomingNoisyReceiver;
    
    // 新增：声音播放相关变量
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

        becomingNoisyReceiver = new BecomingNoisyReceiver();
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }
    

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();

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
                
            // ========== 新增：消息和铃声处理 ==========
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

    // ========== 新增：消息提示音方法 ==========
    private void playMessageSound() {
        try {
            stopCallSound(); // 先停止可能正在播放的铃声
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer mp = MediaPlayer.create(this, soundUri);
            if (mp != null) {
                mp.setOnCompletionListener(m -> {
                    if (m != null) m.release();
                });
                mp.start();
            }
        } catch (Exception e) {
            Log.e("WebToApk", "播放消息提示音失败", e);
        }
    }

    // ========== 新增：来电铃声方法 ==========
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
            }
        } catch (Exception e) {
            Log.e("WebToApk", "播放来电铃声失败", e);
        }
    }

    // ========== 新增：去电铃声方法 ==========
    private void playCallOutgoingSound() {
        try {
            if (isRinging) return;
            isRinging = true;
            
            stopCallSound();
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            callMediaPlayer = MediaPlayer.create(this, ringtoneUri);
            if (callMediaPlayer != null) {
                callMediaPlayer.setLooping(true);
                callMediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e("WebToApk", "播放去电铃声失败", e);
        }
    }

    // ========== 新增：停止铃声方法 ==========
    private void stopCallSound() {
        isRinging = false;
        if (callMediaPlayer != null) {
            try {
                if (callMediaPlayer.isPlaying()) {
                    callMediaPlayer.stop();
                }
                callMediaPlayer.release();
            } catch (Exception e) {}
            callMediaPlayer = null;
        }
    }

    // ========== 新增：短振动方法 ==========
    private void vibrate(int duration) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    // ========== 新增：长振动方法（来电用） ==========
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

    // ========== 新增：停止振动方法 ==========
    private void stopVibrate() {
        if (vibrator != null) {
            vibrator.cancel();
        }
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
        PlaybackStateCompat currentState = mediaSession.getController().getPlaybackState();
        if (currentState == null || currentState.getState() == PlaybackStateCompat.STATE_NONE) {
            return;
        }

        long durationMs = (long) (duration * 1000);
        long positionMs = (long) (position * 1000);
        float rate = (float) playbackRate;

        MediaMetadataCompat currentMetadata = mediaSession.getController().getMetadata();
        MediaMetadataCompat.Builder metadataBuilder;
        if (currentMetadata == null) {
            metadataBuilder = new MediaMetadataCompat.Builder();
        } else {
            metadataBuilder = new MediaMetadataCompat.Builder(currentMetadata);
        }
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
            currentState = new PlaybackStateCompat.Builder()
                .setActions(0)
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .build();
        }

        int state;
        switch (stateStr) {
            case "playing":
                state = PlaybackStateCompat.STATE_PLAYING;
                break;
            case "paused":
                state = PlaybackStateCompat.STATE_PAUSED;
                break;
            default:
                state = PlaybackStateCompat.STATE_STOPPED;
                break;
        }

        PlaybackStateCompat.Builder newStateBuilder = new PlaybackStateCompat.Builder(currentState);
        newStateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        mediaSession.setPlaybackState(newStateBuilder.build());

        if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
            startForeground(NOTIFICATION_ID, buildNotification());
        } else {
            stopForeground(false);
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
            if (state == PlaybackStateCompat.STATE_STOPPED) {
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
                    case "play":
                        supportedActions |= PlaybackStateCompat.ACTION_PLAY;
                        break;
                    case "pause":
                        supportedActions |= PlaybackStateCompat.ACTION_PAUSE;
                        break;
                    case "previoustrack":
                        supportedActions |= PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
                        break;
                    case "nexttrack":
                        supportedActions |= PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
                        break;
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
            builder.addAction(
                android.R.drawable.ic_media_previous, "Previous",
                createActionIntent(ACTION_PREVIOUS)
            );
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

        PlaybackStateCompat state = mediaSession.getController().getPlaybackState();
        if (state.getState() == PlaybackStateCompat.STATE_PLAYING || state.getState() == PlaybackStateCompat.STATE_PAUSED) {
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
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
        if (mediaSession != null) {
            mediaSession.release();
        }
        executor.shutdown();
        try {
            unregisterReceiver(becomingNoisyReceiver);
        } catch (Exception e) {}
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
