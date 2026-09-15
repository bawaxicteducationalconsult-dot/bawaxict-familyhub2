package net.bawaxict.familyhubtest;

import android.net.Uri;

import java.util.Locale;

/**
 * Origin helpers.
 *
 * The hotspot side of this deployment is plain HTTP on the LAN and is reached
 * through router-side DNS entries (see
 * site/mikrotik/BAWAXICT_MikroTik_FamilyHub_PRODUCTION_V2_FIXED.rsc), so those
 * host names do not resolve anywhere else. Recognising them lets the error
 * screen say "join the BAWAXICT WiFi" instead of "page failed to load", which
 * is the single most likely confusion in a test build handed to hotspot users.
 */
final class Origins {

    private Origins() {}

    /** True for hosts that only resolve on the BAWAXICT hotspot LAN, or are private. */
    static boolean isHotspotOnly(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost")) return true;
        // /ip hotspot dns-name=bawaxict.edu.net, and the static DNS entries for
        // the on-LAN FamilyHub server.
        if (h.equals("bawaxict.edu.net") || h.endsWith(".bawaxict.edu.net")) return true;
        if (h.equals("familyhub.chat") || h.endsWith(".familyhub.chat")) return true;
        return isPrivateAddress(h);
    }

    /** True for an IPv4 literal in a private/loopback range. */
    static boolean isPrivateAddress(String host) {
        if (host == null || !host.matches("[0-9.]+")) return false;
        return host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("127.");
    }

    /** True when both URLs have the same scheme, host and port. */
    static boolean sameOrigin(Uri a, Uri b) {
        if (a == null || b == null) return false;
        return equalsIgnoreCase(a.getScheme(), b.getScheme())
                && equalsIgnoreCase(a.getHost(), b.getHost())
                && a.getPort() == b.getPort();
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
