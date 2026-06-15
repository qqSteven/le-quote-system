package com.le.quote;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://qqsteven.github.io/le-quote-system/";
    private static final String CHANNEL_ID = "le_quote_notifications";
    private static final int NOTIF_PERMISSION_REQUEST = 1001;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create notification channel (required for Android 8+)
        createNotificationChannel();

        // Request notification permission on Android 13+
        requestNotificationPermission();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        // ** Disable aggressive caching — always load fresh from web **
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // ** KEY: Bridge native notifications to JavaScript **
        webView.addJavascriptInterface(new NotificationBridge(), "AndroidNotifications");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        // ** Handle file downloads (CSV/PDF exports) **
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            String fileName = "LE_export_" + System.currentTimeMillis();
            try {
                String cd = contentDisposition;
                if(cd != null && cd.contains("filename=")) {
                    fileName = cd.split("filename=\"?")[1].replace("\"", "");
                }
            } catch(Exception e) {}
            request.setTitle(fileName);
            request.setDescription("LE 报价系统导出文件");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if(dm != null) dm.enqueue(request);
            Toast.makeText(this, "📥 开始下载: " + fileName, Toast.LENGTH_SHORT).show();
        });

        webView.loadUrl(HOME_URL);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIF_PERMISSION_REQUEST
                );
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "LE 报价通知",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("LE 报价审批和报价更新通知");
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /** JavaScript bridge — called from web page to trigger native notification */
    public class NotificationBridge {
        @JavascriptInterface
        public void showNotification(String title, String body) {
            runOnUiThread(() -> {
                // Check permission again before posting (Android 13+)
                if (Build.VERSION.SDK_INT >= 33) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        return; // Permission not granted
                    }
                }

                Intent intent = new Intent(MainActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pending = PendingIntent.getActivity(
                    MainActivity.this, 0, intent,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0
                );

                Notification.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder = new Notification.Builder(MainActivity.this, CHANNEL_ID);
                } else {
                    builder = new Notification.Builder(MainActivity.this);
                }

                builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setVibrate(new long[]{200, 100, 200})
                    .setContentIntent(pending);

                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                int id = (int) System.currentTimeMillis() % 100000;
                try {
                    nm.notify(id, builder.build());
                } catch (SecurityException e) {
                    // Permission denied — silently fail
                }
            });
        }

        /** Check if notifications are enabled */
        @JavascriptInterface
        public boolean isEnabled() {
            if (Build.VERSION.SDK_INT >= 33) {
                return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return true; // Pre-Android 13, always enabled if channel exists
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
