package net.bawaxict.familyhubtest;

/**
 * A WebView identifies itself as
 * {@code Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/...; wv) AppleWebKit/537.36
 * (KHTML, like Gecko) Version/4.0 Chrome/127.0.0.0 Mobile Safari/537.36}.
 *
 * The {@code ; wv} marker and the {@code Version/4.0} token are exactly what
 * distinguishes that string from the Chrome Mobile User-Agent a hotspot user's
 * own browser sends. This build's requirement is that the wrapped app behaves
 * like a hotspot browser session, so any server-side or in-page UA sniffing
 * must see what it sees in Chrome.
 *
 * Nothing else about the request is altered on purpose: no extra headers are
 * injected (an injected {@code X-Hotspot-*} header would make the app a
 * <em>different</em> client than a browser, and would silently stop matching
 * whatever the hotspot-origin detection ends up keying on), cookies come from
 * the platform cookie jar, and the origin loaded is the real hotspot-facing
 * origin.
 */
final class UserAgents {

    private UserAgents() {}

    /** Returns the WebView's default User-Agent with the WebView-only tokens removed. */
    static String asBrowser(String webViewUserAgent) {
        if (webViewUserAgent == null || webViewUserAgent.isEmpty()) {
            return webViewUserAgent;
        }
        String ua = webViewUserAgent.replace("; wv", "");
        ua = ua.replace("Version/4.0 ", "");
        return ua.trim();
    }
}
