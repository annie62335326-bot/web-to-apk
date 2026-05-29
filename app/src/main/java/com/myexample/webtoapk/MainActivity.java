package com.myexample.webtoapk;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WebToApk";
    
    // 在这里修改你的网址！！！
    private String mainURL = "https://im8885.akfjcn.shop";
    
    private WebView webview;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> mFilePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private boolean doubleBackToExitPressedOnce = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        webview = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar1);
        
        setupWebView();
        setupFileChooser();
        
        // 处理深度链接
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            mainURL = intent.getData().toString();
        }
        
        // 加载网页
        if (savedInstanceState != null) {
            webview.restoreState(savedInstanceState);
        } else {
            webview.loadUrl(mainURL);
        }
        
        // 请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }
    
    private void setupWebView() {
        WebSettings settings = webview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false); // 允许自动播放
        
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                } else {
                    // 外部链接用浏览器打开
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
        });
        
        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
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
                intent.setType("*/*");
                
                Intent chooser = Intent.createChooser(intent, "选择文件");
                fileChooserLauncher.launch(chooser);
                return true;
            }
        });
        
        // 添加JavaScript接口
        webview.addJavascriptInterface(new WebAppInterface(), "WebToApk");
    }
    
    private void setupFileChooser() {
        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (mFilePathCallback != null) {
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri data = result.getData().getData();
                        if (data != null) {
                            results = new Uri[]{data};
                        }
                    }
                    mFilePathCallback.onReceiveValue(results);
                    mFilePathCallback = null;
                }
            }
        );
    }
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webview.saveState(outState);
    }
    
    @Override
    public void onBackPressed() {
        if (webview.canGoBack()) {
            webview.goBack();
        } else {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed();
                return;
            }
            doubleBackToExitPressedOnce = true;
            Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
        }
    }
    
    // JavaScript接口
    private class WebAppInterface {
        @JavascriptInterface
        public void playMessageSound() {
            Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_PLAY_MESSAGE_SOUND);
            startService(intent);
        }
        
        @JavascriptInterface
        public void playCallIncomingSound() {
            Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_PLAY_CALL_INCOMING);
            startService(intent);
        }
        
        @JavascriptInterface
        public void playCallOutgoingSound() {
            Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_PLAY_CALL_OUTGOING);
            startService(intent);
        }
        
        @JavascriptInterface
        public void stopCallSound() {
            Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_STOP_CALL_SOUND);
            startService(intent);
        }
        
        @JavascriptInterface
        public void vibrate(int duration) {
            Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_VIBRATE);
            intent.putExtra("duration", duration);
            startService(intent);
        }
        
        @JavascriptInterface
        public void stopVibrate() {
            Intent intent = new Intent(MainActivity.this, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_STOP_VIBRATE);
            startService(intent);
        }
        
        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }
}
