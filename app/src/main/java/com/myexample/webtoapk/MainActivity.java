package com.myexample.webtoapk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.unifiedpush.android.connector.UnifiedPush;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.unifiedpush.android.connector.ConstantsKt.INSTANCE_DEFAULT;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WebToApk";
    
    // 权限请求码
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 2;
    private static final int MEDIA_PERMISSION_REQUEST_CODE = 1001;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 1002;
    
    // 配置
    private String mainURL = "https://im8885.akfjcn.shop";
    private boolean requireDoubleBackToExit = true;
    private boolean allowSubdomains = true;
    private boolean enableExternalLinks = true;
    private boolean openExternalLinksInBrowser = true;
    private boolean confirmOpenInBrowser = true;
    private boolean allowOpenMobileApp = false;
    private boolean confirmOpenExternalApp = true;
    private String cookies = "";
    private String basicAuth = "";
    private String userAgent = "";
    private boolean blockLocalhostRequests = true;
    private boolean JSEnabled = true;
    private boolean JSCanOpenWindowsAutomatically = true;
    private boolean DomStorageEnabled = true;
    private boolean DatabaseEnabled = true;
    private boolean MediaPlaybackRequiresUserGesture = true;
    private boolean SavePassword = true;
    private boolean AllowFileAccess = true;
    private boolean AllowFileAccessFromFileURLs = true;
    private boolean showDetailsOnErrorScreen = false;
    private boolean forceLandscapeMode = false;
    private boolean edgeToEdge = false;
    private boolean forceDarkTheme = false;
    private boolean allowMixedContent = false;
    private String cacheMode = "default";
    private int fadeInDuration = 400;
    private boolean DebugWebView = false;
    private boolean geolocationEnabled = true;
    private boolean cameraEnabled = true;
    private boolean microphoneEnabled = true;
    
    // UI组件
    private WebView webview;
    private ProgressBar spinner;
    private View mainLayout;
    private View errorLayout;
    private ViewGroup parentLayout;
    private boolean errorOccurred = false;
    
    // 文件选择器
    private ValueCallback<Uri[]> mFilePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    
    // 权限请求
    private PermissionRequest currentPermissionRequest;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    
    // 广播接收器
    private BroadcastReceiver unifiedPushEndpointReceiver;
    private BroadcastReceiver mediaActionReceiver;
    
    // 退出相关
    private boolean doubleBackToExitPressedOnce = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题设置
        if (forceDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        if (edgeToEdge) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }
        
        super.onCreate(savedInstanceState);
        
        // 设置边缘到边缘
        if (edgeToEdge) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        
        // 设置强制横屏
        if (forceLandscapeMode) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        
        setContentView(R.layout.activity_main);
        
        // 初始化UI
        mainLayout = findViewById(android.R.id.content);
        parentLayout = (ViewGroup) mainLayout.getParent();
        spinner = findViewById(R.id.progressBar1);
        webview = findViewById(R.id.webView);
        webview.setAlpha(0f);
        
        // 处理深度链接
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            mainURL = data.toString();
        }
        
        // 设置WebView
        setupWebView();
        
        // 设置文件选择器
        setupFileChooser();
        
        // 设置下载监听
        setupDownloadListener();
        
        // 设置统一推送接收器
        setupUnifiedPushReceiver();
        
        // 设置媒体动作接收器
        setupMediaActionReceiver();
        
        // 设置边缘到边缘监听
        if (edgeToEdge) {
            setupEdgeToEdge();
        }
        
        // 恢复或加载URL
        boolean stateRestored = false;
        if (savedInstanceState != null) {
            stateRestored = webview.saveState(savedInstanceState) != null;
            if (stateRestored) Log.d(TAG, "Restored WebView state");
        }
        
        if (!stateRestored) {
            webview.loadUrl(mainURL);
        }
        
        Log.d(TAG, "MainActivity onCreate completed");
    }
    
    private void setupWebView() {
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
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        
        // 允许WebView在后台继续运行
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        }
        
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
        
        // 设置Cookie
        CookieManager cookieManager = CookieManager.getInstance();
        CookieManager.getInstance().setAcceptThirdPartyCookies(webview, true);
        cookieManager.setCookie(mainURL, cookies);
        cookieManager.flush();
        
        // 设置WebViewClient和WebChromeClient
        webview.setWebViewClient(new CustomWebViewClient());
        webview.setWebChromeClient(new CustomWebChrome());
        
        // 添加JavaScript接口
        webview.addJavascriptInterface(new WebAppInterface(this), "WebToApk");
    }
    
    private void setupFileChooser() {
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
    }
    
    private void setupDownloadListener() {
        webview.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            
            String cookies = CookieManager.getInstance().getCookie(url);
            request.addRequestHeader("cookie", cookies);
            request.addRequestHeader("User-Agent", userAgent);
            
            request.setDescription(getString(R.string.download_description));
            request.setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, 
                android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype));
            
            try {
                downloadManager.enqueue(request);
                Toast.makeText(getApplicationContext(), R.string.download_started, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), R.string.download_failed, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Failed to start download", e);
            }
        });
    }
    
    private void setupUnifiedPushReceiver() {
        unifiedPushEndpointReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String endpoint = intent.getStringExtra("endpoint");
                String p256dh = intent.getStringExtra("p256dh");
                String auth = intent.getStringExtra("auth");
                
                Log.d(TAG, "Received UnifiedPush data. Endpoint: " + endpoint);
                
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
                            String js = "if (typeof window.__shim_onNewEndpoint === 'function') { " +
                                "window.__shim_onNewEndpoint('" + subscriptionJson.replace("'", "\\'") + "'); }";
                            webview.evaluateJavascript(js, null);
                        });
                        
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to create subscription JSON", e);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter("com.myexample.webtoapk.NEW_ENDPOINT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unifiedPushEndpointReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(unifiedPushEndpointReceiver, filter);
        }
    }
    
    private void setupMediaActionReceiver() {
        mediaActionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && MediaPlaybackService.BROADCAST_MEDIA_ACTION.equals(intent.getAction())) {
                    String action = intent.getStringExtra(MediaPlaybackService.EXTRA_MEDIA_ACTION);
                    if (action != null) {
                        Log.d(TAG, "Media action received: " + action);
                        webview.post(() -> {
                            String js = "if (typeof window.__runMediaAction === 'function') { " +
                                "window.__runMediaAction('" + action + "'); }";
                            webview.evaluateJavascript(js, null);
                        });
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(mediaActionReceiver, 
            new IntentFilter(MediaPlaybackService.BROADCAST_MEDIA_ACTION));
    }
    
    private void setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            float density = v.getResources().getDisplayMetrics().density;
            
            float top = insets.top / density;
            float bottom = insets.bottom / density;
            float left = insets.left / density;
            float right = insets.right / density;
            
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
    
    private void registerForUnifiedPush(final String vapidPublicKey) {
        if (vapidPublicKey == null || vapidPublicKey.isEmpty()) {
            Log.e(TAG, "VAPID public key is null or empty");
            return;
        }
        
        UnifiedPush.tryUseCurrentOrDefaultDistributor(this, success -> {
            if (success) {
                Log.d(TAG, "UnifiedPush distributor found, registering...");
                UnifiedPush.register(this, INSTANCE_DEFAULT, null, vapidPublicKey);
            } else {
                Log.w(TAG, "No UnifiedPush distributor found");
                new Handler(Looper.getMainLooper()).post(() -> {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.push_distributor_required_title)
                        .setMessage(R.string.push_distributor_required_message)
                        .setPositiveButton(R.string.learn_more, (dialog, which) -> {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, 
                                Uri.parse("https://unifiedpush.org/users/distributors/"));
                            startActivity(browserIntent);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                });
            }
            return null;
        });
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
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webview.saveState(outState);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 不暂停WebView，保持消息轮询
        Log.d(TAG, "onPause - WebView continues running");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unifiedPushEndpointReceiver != null) {
            try {
                unregisterReceiver(unifiedPushEndpointReceiver);
            } catch (Exception e) {}
        }
        if (mediaActionReceiver != null) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(mediaActionReceiver);
            } catch (Exception e) {}
        }
        Intent intent = new Intent(this, MediaPlaybackService.class);
        stopService(intent);
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
        } else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (geoCallback != null) {
                geoCallback.invoke(geoOrigin, isGranted, false);
                geoCallback = null;
                geoOrigin = null;
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            final boolean granted = isGranted;
            if (webview != null) {
                webview.post(() -> {
                    String js = "if (typeof window.__onNotificationPermissionResult === 'function') { " +
                        "window.__onNotificationPermissionResult(" + granted + "); }";
                    webview.evaluateJavascript(js, null);
                });
            }
        }
    }
    
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
            
            new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
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
    
    // ========== WebViewClient ==========
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
            
            builder.setPositiveButton("continue", (dialog, which) -> handler.proceed());
            builder.setNegativeButton("cancel", (dialog, which) -> handler.cancel());
            final AlertDialog dialog = builder.create();
            dialog.show();
        }
        
        @Override
        public void onReceivedHttpAuthRequest(final WebView view, final android.webkit.HttpAuthHandler handler, 
                String host, String realm) {
            if (basicAuth != null && !basicAuth.isEmpty()) {
                String[] parts = basicAuth.split(":", 2);
                if (parts.length == 2) {
                    String login = parts[0];
                    String password = parts[1];
                    
                    String mainDomain = "";
                    try {
                        mainDomain = Uri.parse(mainURL).getHost();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    boolean domainIsValid = false;
                    if (mainDomain != null && !mainDomain.isEmpty() && host != null && !host.isEmpty()) {
                        if (allowSubdomains) {
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
                .setPositiveButton("OK", (dialog, which) -> {
                    String user = usernameInput.getText().toString();
                    String pass = passwordInput.getText().toString();
                    handler.proceed(user, pass);
                })
                .setNegativeButton("Cancel", (dialog, which) -> handler.cancel())
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
                            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                    view.getContext().startActivity(intent);
                                } catch (Exception e) {
                                    Log.e(TAG, "No application can handle this URL: " + url, e);
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    } else {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            view.getContext().startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "No application can handle this URL: " + url);
                        }
                    }
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
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            view.getContext().startActivity(intent);
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                    return true;
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String host = request.getUrl().getHost();
            
            if (blockLocalhostRequests && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || 
                    "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host))) {
                Log.d(TAG, "Blocked access to localhost resource: " + request.getUrl());
                return new WebResourceResponse("text/plain", "UTF-8", null);
            }
            
            return super.shouldInterceptRequest(view, request);
        }
        
        @Override
        public void onPageStarted(WebView webview, String url, Bitmap favicon) {
            super.onPageStarted(webview, url, favicon);
        }
        
        @Override
        public void onPageFinished(WebView webview, String url) {
            if (!errorOccurred) {
                Log.d(TAG, "Current page: " + url);
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
                Log.e(TAG, "Major error: " + errorCode + " - " + errorDescription);
                errorOccurred = true;
                errorLayout = getLayoutInflater().inflate(R.layout.error, parentLayout, false);
                parentLayout.removeView(mainLayout);
                parentLayout.addView(errorLayout);
                if (showDetailsOnErrorScreen) {
                    TextView errorTextView = errorLayout.findViewById(R.id.errorText);
                    if (errorTextView != null) {
                        errorTextView.setText("Error " + errorCode + ":\n" + errorDescription);
                    }
                }
            } else {
                Log.d(TAG, "Resource error: " + errorCode + " - " + errorDescription);
            }
        }
        
        @Override
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            Log.e(TAG, "WebView render process gone! Did crash: " + detail.didCrash());
            Toast.makeText(MainActivity.this, "Recovering from memory kill...", Toast.LENGTH_SHORT).show();
            finish();
            startActivity(getIntent());
            return true;
        }
    }
    
    // ========== WebChromeClient ==========
    private class CustomWebChrome extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            String src = consoleMessage.sourceId();
            Integer line = consoleMessage.lineNumber();
            String msg = consoleMessage.message();
            
            if (src != null && (src.startsWith("http://") || src.startsWith("https://"))) {
                src = src.substring(8);
            }
            
            switch (consoleMessage.messageLevel()) {
                case ERROR:
                    Log.e(TAG, "[" + src + ":" + line + "] " + msg);
                    break;
                case WARNING:
                    Log.w(TAG, "[" + src + ":" + line + "] " + msg);
                    break;
                default:
                    Log.d(TAG, "[" + src + ":" + line + "] " + msg);
                    break;
            }
            return true;
        }
        
        @Override
        public boolean onJsAlert(WebView view, String url, String message, final android.webkit.JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                .setCancelable(false)
                .create()
                .show();
            return true;
        }
        
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final android.webkit.JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                .setCancelable(false)
                .create()
                .show();
            return true;
        }
        
        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, 
                final android.webkit.JsPromptResult result) {
            final EditText input = new EditText(MainActivity.this);
            input.setText(defaultValue);
            
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
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
            
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                geoCallback = callback;
                geoOrigin = origin;
                ActivityCompat.requestPermissions(MainActivity.this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
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
            if (mCustomView == null) return;
            
            ((FrameLayout) getWindow().getDecorView()).removeView(mCustomView);
            mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
            setRequestedOrientation(mOriginalOrientation);
            if (mCustomViewCallback != null) {
                mCustomViewCallback.onCustomViewHidden();
            }
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
            
            ((FrameLayout) getWindow().getDecorView()).addView(mCustomView,
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
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, 
                FileChooserParams fileChooserParams) {
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
            
            Intent chooserIntent = Intent.createChooser(intent, "Choose file");
            
            try {
                fileChooserLauncher.launch(chooserIntent);
            } catch (Exception e) {
                mFilePathCallback = null;
                Toast.makeText(MainActivity.this, "Cannot open file chooser", Toast.LENGTH_LONG).show();
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
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) 
                            != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.CAMERA);
                    }
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) 
                            != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
                    }
                }
            }
            
            if (permissionsNeeded.isEmpty()) {
                request.grant(request.getResources());
            } else {
                currentPermissionRequest = request;
                ActivityCompat.requestPermissions(MainActivity.this, 
                    permissionsNeeded.toArray(new String[0]), MEDIA_PERMISSION_REQUEST_CODE);
            }
        }
        
        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            super.onPermissionRequestCanceled(request);
            currentPermissionRequest = null;
        }
    }
    
    // ========== JavaScript接口 ==========
    private class WebAppInterface {
        private Context context;
        
        WebAppInterface(Context context) {
            this.context = context;
        }
        
        @JavascriptInterface
        public void showShortToast(String message) {
            new Handler(Looper.getMainLooper()).post(() -> 
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }
        
        @JavascriptInterface
        public void showLongToast(String message) {
            new Handler(Looper.getMainLooper()).post(() -> 
                Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        }
        
        @JavascriptInterface
        public boolean hasNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }
        
        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions((Activity) context, 
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
                    }
                });
            }
        }
        
        @JavascriptInterface
        public String getNotificationPermissionState() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                        == PackageManager.PERMISSION_GRANTED) {
                    return "granted";
                }
                return "prompt";
            }
            return "granted";
        }
        
        @JavascriptInterface
        public void unifiedPushSubscribe(String vapidPublicKey) {
            new Handler(Looper.getMainLooper()).post(() -> 
                MainActivity.this.registerForUnifiedPush(vapidPublicKey));
        }
        
        @JavascriptInterface
        public void unifiedPushUnregister() {
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
                Log.e(TAG, "Failed to create subscription JSON", e);
                return "";
            }
        }
        
        @JavascriptInterface
        public void clearAppCache() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (webview != null) {
                    webview.clearCache(true);
                }
            });
        }
        
        // ========== 声音和振动方法 ==========
        
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
    
    // 内部类用于调度通知
    public static class ScheduledNotificationReceiver extends BroadcastReceiver {
        private static final String NOTIFICATION_CHANNEL_ID = "web_app_notifications";
        
        @Override
        public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra("id", 0);
            String title = intent.getStringExtra("title");
            String message = intent.getStringExtra("message");
            String deepLink = intent.getStringExtra("deepLink");
            
            // 创建通知渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Web App Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
            
            Intent activityIntent = new Intent(context, MainActivity.class);
            if (deepLink != null && !deepLink.isEmpty()) {
                activityIntent.setAction(Intent.ACTION_VIEW);
                activityIntent.setData(Uri.parse(deepLink));
            }
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(context, id, activityIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
            
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            
            notificationManager.notify(id, builder.build());
        }
    }
}
