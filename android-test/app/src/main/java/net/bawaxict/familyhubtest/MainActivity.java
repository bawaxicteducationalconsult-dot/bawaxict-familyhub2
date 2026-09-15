package net.bawaxict.familyhubtest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * FamilyHub Test — a single-Activity WebView shell around the existing FamilyHub
 * web app. No UI is rebuilt natively; this Activity exists only to load the site
 * and to behave, towards that site, exactly like a hotspot browser session.
 *
 * What "exactly like a hotspot browser session" means concretely, and where each
 * part is handled:
 *
 *   origin            BuildConfig.START_URL defaults to the URL the production
 *                     MikroTik config redirects authenticated hotspot users to
 *                     (login-page-redirect="https://bawaxict-familyhub2.pages.dev/forum"),
 *                     which is also in that config's walled garden.
 *   cookies/session   the platform CookieManager, accepting cookies and flushing
 *                     them to disk, so a signed-in session survives the app being
 *                     backgrounded or killed — see configureCookies/onPause.
 *   storage           DOM storage on, because the site uses localStorage (36 call
 *                     sites) and sessionStorage.
 *   user agent        WebView markers stripped, see UserAgents.
 *   no extra headers  nothing is injected into requests.
 *   plain HTTP        the captive portal and the on-LAN server are HTTP; allowed
 *                     for those hosts only, in res/xml/network_security_config.xml.
 *   mixed content     compatibility mode, so the HTTPS pages can still follow
 *                     their http://bawaxict.edu.net/status "Back to Hotspot" links.
 *   site features     file inputs (photo posts, marketplace, profile photo),
 *                     camera capture, voice notes via getUserMedia, window.open,
 *                     EventSource streams and downloads all work.
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_RECORD_AUDIO = 1002;

    private WebView webView;
    private View errorPanel;
    private TextView errorBody;
    private ProgressBar progress;

    /** Live only between onShowFileChooser and onActivityResult. */
    private ValueCallback<Uri[]> filePathCallback;
    /** Where the camera app writes a captured photo. */
    private Uri cameraOutputUri;
    /** A page's mic request, waiting on the runtime permission dialog. */
    private PermissionRequest pendingMicRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        errorPanel = findViewById(R.id.error_panel);
        errorBody = findViewById(R.id.error_body);
        progress = findViewById(R.id.progress);

        Button retry = findViewById(R.id.retry_button);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { retry(); }
        });
        Button browser = findViewById(R.id.browser_button);
        browser.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openCurrentInBrowser(); }
        });

        configureCookies();
        configureWebView();

        if (BuildConfig.DEBUG) {
            // Test builds are debuggable on purpose: chrome://inspect from a
            // desktop can inspect the wrapped site exactly as it runs here.
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // restoreState returns null when the bundle carries no WebView state
        // (e.g. the Activity died before the first save); in that case there is
        // nothing to restore and the start URL has to be loaded as usual, or the
        // shell would show a blank page.
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(BuildConfig.START_URL);
        }
    }

    // ------------------------------------------------------------------ setup

    private void configureCookies() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        // The wrapped pages are same-origin, but a browser would also accept
        // cookies set by anything they embed, so the shell does the same.
        cookies.setAcceptThirdPartyCookies(webView, true);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        // The WebView must be able to read the content:// URIs handed back from
        // the file chooser; it must not be able to read file:// URLs.
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);

        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        // window.open()/target=_blank load in this WebView instead of being
        // dropped: there is only one window in a shell.
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);

        s.setMediaPlaybackRequiresUserGesture(false);

        // Pages are served over HTTPS but contain http://bawaxict.edu.net/status
        // links ("Back to Hotspot"). MIXED_CONTENT_NEVER_ALLOW — the setting used
        // by the older CLOUDFLARE_DEPLOY skeleton — blocks exactly those.
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (BuildConfig.BROWSER_USER_AGENT) {
            s.setUserAgentString(UserAgents.asBrowser(s.getUserAgentString()));
        }

        // Safe Browsing matches URLs against a Google-hosted blocklist. On a
        // walled-garden hotspot the phone may have no route to Google at all,
        // and a failed lookup must not be able to block our own portal.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false);
        }

        webView.setBackgroundColor(0xFFFFFFFF);
        webView.setWebViewClient(new ShellWebViewClient());
        webView.setWebChromeClient(new ShellChromeClient());
        webView.setDownloadListener(new android.webkit.DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                // Vouchers/tickets are handed to the platform rather than
                // silently going nowhere, which is what a WebView does by default.
                openExternally(Uri.parse(url));
            }
        });
    }

    // ------------------------------------------------------- WebView callbacks

    private class ShellWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            String scheme = url.getScheme();
            if (scheme == null) return false;
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (scheme.equals("http") || scheme.equals("https")) {
                // Stay in the shell on every host, including the hotspot portal:
                // leaving the app for those would break the session flow.
                return false;
            }
            // tel:, mailto:, sms:, whatsapp:, market:, intent:, geo: ...
            openExternally(url);
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            hideError();
            showProgress(true);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            showProgress(false);
            // Persist the cookie jar so a signed-in session survives the app
            // being killed, the way a browser's does.
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            // Sub-resource failures are normal on a congested hotspot link and
            // must not blank the page; only the main frame counts.
            if (request.isForMainFrame()) {
                int code = (error == null) ? WebViewClient.ERROR_UNKNOWN : error.getErrorCode();
                showError(messageFor(request.getUrl(), code));
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Never proceed past a TLS error, not even in a test build: the
            // session cookies in here are real. Cancel and explain instead.
            handler.cancel();
            showError(getString(R.string.error_tls));
        }
    }

    private class ShellChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            showProgress(newProgress < 100);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            // Only one callback can be answered; drop any stale one first so the
            // page's file input is never left hanging.
            cancelPendingFileChoice();
            filePathCallback = callback;
            try {
                startActivityForResult(buildFileChooserIntent(params), REQ_FILE_CHOOSER);
            } catch (ActivityNotFoundException e) {
                cancelPendingFileChoice();
                toast(getString(R.string.no_browser));
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            handlePermissionRequest(request);
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                                                         GeolocationPermissions.Callback callback) {
            // site/_headers sets Permissions-Policy geolocation=(), so the site
            // never asks. Deny rather than prompt.
            callback.invoke(origin, false, false);
        }
    }

    // ------------------------------------------------------------- file choice

    private Intent buildFileChooserIntent(WebChromeClient.FileChooserParams params) {
        String[] mimeTypes = acceptMimeTypes(params.getAcceptTypes());

        Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        if (mimeTypes.length == 1) {
            picker.setType(mimeTypes[0]);
        } else {
            picker.setType("*/*");
            if (mimeTypes.length > 1) {
                picker.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
        }
        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);

        Intent chooser = Intent.createChooser(picker, getString(R.string.choose_file));

        // <input type="file" accept="image/*" capture="environment"> (profile
        // photo) also offers the camera. No CAMERA permission is declared, so
        // this delegates to the camera app instead of prompting for it here.
        if (params.isCaptureEnabled() && mimeTypes.length == 1 && mimeTypes[0].startsWith("image/")) {
            cameraOutputUri = newUploadUri();
            if (cameraOutputUri != null) {
                Intent capture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                capture.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
                capture.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { capture });
            }
        }
        return chooser;
    }

    private Uri newUploadUri() {
        try {
            File dir = new File(getCacheDir(), "uploads");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File temp = File.createTempFile("capture-", ".jpg", dir);
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", temp);
        } catch (IOException | IllegalArgumentException e) {
            // No camera option then; the gallery picker still works.
            return null;
        }
    }

    /** Normalises the accept="" values a page declares into MIME types. */
    private static String[] acceptMimeTypes(String[] acceptTypes) {
        List<String> out = new ArrayList<>();
        if (acceptTypes != null) {
            for (String raw : acceptTypes) {
                if (raw == null) continue;
                for (String part : raw.split(",")) {
                    String mime = part.trim().toLowerCase(Locale.ROOT);
                    if (mime.isEmpty()) continue;
                    if (mime.charAt(0) == '.') mime = mimeForExtension(mime);
                    if (mime.isEmpty() || out.contains(mime)) continue;
                    out.add(mime);
                }
            }
        }
        if (out.isEmpty()) return new String[] { "*/*" };
        return out.toArray(new String[0]);
    }

    private static String mimeForExtension(String ext) {
        switch (ext) {
            case ".jpg":
            case ".jpeg": return "image/jpeg";
            case ".png":  return "image/png";
            case ".webp": return "image/webp";
            case ".gif":  return "image/gif";
            case ".mp4":  return "video/mp4";
            case ".pdf":  return "application/pdf";
            default:      return "";
        }
    }

    private void cancelPendingFileChoice() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        cameraOutputUri = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_FILE_CHOOSER) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        ValueCallback<Uri[]> callback = filePathCallback;
        Uri camera = cameraOutputUri;
        filePathCallback = null;
        cameraOutputUri = null;
        if (callback == null) return;

        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data != null) {
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            } else if (camera != null) {
                // The camera app returns no data; the photo is at our URI.
                results = new Uri[] { camera };
            }
        }
        callback.onReceiveValue(results);
    }

    // -------------------------------------------------------------- microphone

    /**
     * private-chat.html records voice notes with getUserMedia({audio:true}).
     * A hotspot browser would prompt and could grant it, so the shell does too —
     * but only for the origin the build was pointed at, and only after the
     * runtime permission is granted.
     */
    private void handlePermissionRequest(PermissionRequest request) {
        List<String> wanted = Arrays.asList(request.getResources());
        if (!wanted.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            // Camera and protected-media requests are not used by the site.
            request.deny();
            return;
        }
        if (!Origins.sameOrigin(request.getOrigin(), Uri.parse(BuildConfig.START_URL))) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[] { PermissionRequest.RESOURCE_AUDIO_CAPTURE });
            return;
        }
        pendingMicRequest = request;
        toast(getString(R.string.mic_rationale));
        requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQ_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_RECORD_AUDIO) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        PermissionRequest request = pendingMicRequest;
        pendingMicRequest = null;
        if (request == null) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[] { PermissionRequest.RESOURCE_AUDIO_CAPTURE });
        } else {
            request.deny();
        }
    }

    // ------------------------------------------------------- errors and chrome

    private String messageFor(Uri url, int errorCode) {
        if (!hasNetwork()) {
            return getString(R.string.error_no_network);
        }
        if (errorCode == WebViewClient.ERROR_HOST_LOOKUP) {
            String host = (url == null) ? null : url.getHost();
            if (Origins.isHotspotOnly(host)) {
                return getString(R.string.error_host_only_on_hotspot, host);
            }
        }
        return getString(R.string.error_generic);
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showError(String message) {
        errorBody.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        showProgress(false);
    }

    private void hideError() {
        errorPanel.setVisibility(View.GONE);
    }

    private void showProgress(boolean visible) {
        progress.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void retry() {
        hideError();
        String url = webView.getUrl();
        if (url == null || url.isEmpty() || "about:blank".equals(url)) {
            webView.loadUrl(BuildConfig.START_URL);
        } else {
            webView.reload();
        }
    }

    private void openCurrentInBrowser() {
        String url = webView.getUrl();
        if (url == null || url.isEmpty()) url = BuildConfig.START_URL;
        openExternally(Uri.parse(url));
    }

    private void openExternally(Uri uri) {
        if (uri == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException | SecurityException e) {
            toast(getString(R.string.no_browser));
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Back walks the site's history instead of leaving the app, which is what
        // a browser tab does.
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        // Flush cookies before the app can be killed. webView.onPause() is used
        // rather than pauseTimers(): the chat's EventSource stream keeps up while
        // the app is only briefly backgrounded, as it would in a browser tab.
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            CookieManager.getInstance().flush();
            webView.stopLoading();
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
