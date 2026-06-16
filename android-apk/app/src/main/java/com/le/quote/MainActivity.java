package com.le.quote;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    // Load from GitHub Pages — always gets the latest web version
    // Same source as the web app, so data sync and features stay in sync
    private static final String HOME_URL = "https://qqsteven.github.io/le-quote-system/";
    private static final String CHANNEL_ID = "le_quote_notifications";
    private static final int NOTIF_PERMISSION_REQUEST = 1001;
    private static final int FILE_CHOOSER_REQUEST = 1002;
    private WebView webView;
    private ValueCallback<Uri[]> mFilePathCallback;

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
        // ** Bridge for file downloads (bypasses blob: URL issue in DownloadManager) **
        webView.addJavascriptInterface(new DownloadBridge(), "AndroidDownload");
        // ** Bridge for native printing (bypasses window.print() in WebView) **
        webView.addJavascriptInterface(new PrintBridge(), "AndroidPrint");

        webView.setWebViewClient(new WebViewClient());

        // ** CRITICAL: WebChromeClient with file chooser support **
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimeTypes = fileChooserParams.getAcceptTypes();
                if (mimeTypes != null && mimeTypes.length > 0) {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                }

                try {
                    startActivityForResult(Intent.createChooser(intent, "选择文件"), FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    mFilePathCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl(HOME_URL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (mFilePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        }
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

    /** JavaScript bridge — handles file downloads from WebView */
    public class DownloadBridge {
        @JavascriptInterface
        public void saveFile(String base64Data, String fileName, String mimeType) {
            runOnUiThread(() -> {
                try {
                    // Strip data:xxx;base64, prefix if present
                    String cleanData = base64Data;
                    if (cleanData.contains(",")) {
                        cleanData = cleanData.substring(cleanData.indexOf(",") + 1);
                    }
                    byte[] bytes = android.util.Base64.decode(cleanData, android.util.Base64.DEFAULT);
                    
                    // Android 10+ (API 29): use MediaStore for proper scoped storage
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                        values.put(MediaStore.Downloads.MIME_TYPE, mimeType != null ? mimeType : "application/octet-stream");
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri != null) {
                            OutputStream os = getContentResolver().openOutputStream(uri);
                            if (os != null) {
                                os.write(bytes);
                                os.close();
                                Toast.makeText(MainActivity.this, "📥 已保存到下载文件夹: " + fileName, Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                    }
                    
                    // Legacy fallback (Android 9 and below, or MediaStore failed)
                    java.io.File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null || !dir.canWrite()) dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) dir = getFilesDir(); // ultimate fallback to internal
                    if (!dir.exists()) dir.mkdirs();
                    java.io.File file = new java.io.File(dir, fileName);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                    fos.write(bytes);
                    fos.close();
                    
                    String location = dir.getAbsolutePath().contains("Android/data") ? "应用私有目录/Download" : "下载文件夹";
                    Toast.makeText(MainActivity.this, "📥 已保存: " + fileName + " (" + location + ")", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "❌ 保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /** JavaScript bridge — native print/PDF via PrintManager */
    public class PrintBridge {
        @JavascriptInterface
        public void print(String jobName) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
                        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(
                            jobName != null && !jobName.isEmpty() ? jobName : "LE文档"
                        );
                        printManager.print(
                            jobName != null && !jobName.isEmpty() ? jobName : "LE文档",
                            adapter,
                            new PrintAttributes.Builder().build()
                        );
                    } else {
                        Toast.makeText(MainActivity.this, "打印需要 Android 5.0+", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "打印失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
