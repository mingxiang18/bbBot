package com.bb.bot.connection.onebot;

import org.apache.commons.lang3.StringUtils;
import org.java_websocket.handshake.ClientHandshake;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

public final class OneBotAuth {
    private OneBotAuth() { }
    static Map<String, String> headers(String token) {
        return StringUtils.isBlank(token) ? Map.of() : Map.of("Authorization", "Bearer " + token);
    }
    static boolean accepts(String expected, ClientHandshake request) {
        if (StringUtils.isBlank(expected)) return true;
        String provided = request.getFieldValue("Authorization");
        if (provided != null && provided.regionMatches(true, 0, "Bearer ", 0, 7)) {
            provided = provided.substring(7);
        } else {
            provided = "";
            String resource = request.getResourceDescriptor();
            int query = resource.indexOf('?');
            if (query >= 0) for (String part : resource.substring(query + 1).split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && "access_token".equals(part.substring(0, eq))) {
                    try { provided = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8); }
                    catch (IllegalArgumentException e) { return false; }
                }
            }
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
    static String endpoint(URI uri) {
        // Omit userinfo, query and fragment, including legacy access_token URLs.
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
    }
}
