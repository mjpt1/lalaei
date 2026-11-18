package ir.lalaei.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private PowerManager.WakeLock lock;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#0f1535"));
        getWindow().setNavigationBarColor(Color.parseColor("#141237"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0f1535"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);
        // Android 15 draws apps edge-to-edge; pad the WebView so status/navigation bars don't cover the player
        web.setOnApplyWindowInsetsListener((v, in) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets b = in.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(b.left, b.top, b.right, b.bottom);
            } else {
                v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                        in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
            }
            return in;
        });
        if (state == null) web.loadUrl("file:///android_asset/www/index.html");
        else web.restoreState(state);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lalaei:play");
        lock.setReferenceCounted(false);

        // Android 13+ needs permission to show the playback notification
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
    }

    // Called from the page whenever audio starts or stops.
    class Bridge {
        // Myket intents (required by Myket review): "rate" opens the comment page, "dev" the developer's apps.
        @JavascriptInterface
        public void myket(String what) {
            String pkg = getPackageName();
            String uri = "dev".equals(what) ? "myket://developer/" + pkg : "myket://comment?id=" + pkg;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            try {
                startActivity(i);
            } catch (Exception e) { // Myket not installed: open the web page
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://myket.ir/app/" + pkg)));
            }
        }

        @JavascriptInterface
        public void playing(boolean on, String title) {
            runOnUiThread(() -> {
                Intent svc = new Intent(MainActivity.this, PlaybackService.class);
                if (on) {
                    lock.acquire(3 * 60 * 60 * 1000L);
                    svc.putExtra("title", title);
                    startForegroundService(svc);
                } else {
                    if (lock.isHeld()) lock.release();
                    stopService(svc);
                }
            });
        }
    }

    // "توقف" button in the notification
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if ("toggle".equals(i.getAction())) {
            web.evaluateJavascript("nativeToggle()", null);
            moveTaskToBack(true);
        }
    }

    // WebView is deliberately not paused in onPause, so audio continues with the screen off.

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true); // keep playing instead of closing
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, PlaybackService.class));
        if (lock != null && lock.isHeld()) lock.release();
        web.destroy();
        super.onDestroy();
    }
}
