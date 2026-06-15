package com.le.quote;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://qqsteven.github.io/le-quote-system/";
    private static final String CHANNEL_ID = "le_quote_notifications";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create notification channel (required for Android 8+)
        createNotificationChannel();

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
        webView.loadUrl(HOME_URL);
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
                } catch (Exception e) {
                    // Notification permission not granted — silently fail
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
