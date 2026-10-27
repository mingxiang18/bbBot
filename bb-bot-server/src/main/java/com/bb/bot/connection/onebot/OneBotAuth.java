package com.bb.bot.connection.onebot;

import org.apache.commons.lang3.StringUtils;
import org.java_websocket.handshake.ClientHandshake;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** OneBot 正向连接的认证头，以及反向连接的握手鉴权。 */
public final class OneBotAuth {

    private static final String BEARER_PREFIX = "Bearer ";

    private OneBotAuth() {}

    /** 未配置 token 时不发送空的 Authorization 头。 */
    static Map<String, String> headers(String token) {
        if (StringUtils.isBlank(token)) {
            return Map.of();
        }
        return Map.of("Authorization", BEARER_PREFIX + token);
    }

    /** 优先校验 Bearer 头，兼容旧网关通过 access_token 查询参数传递认证信息。 */
    static boolean accepts(String expectedToken, ClientHandshake request) {
        if (StringUtils.isBlank(expectedToken)) {
            return true;
        }

        String providedToken = request.getFieldValue("Authorization");
        if (providedToken != null
                && providedToken.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            providedToken = providedToken.substring(BEARER_PREFIX.length());
        } else {
            try {
                providedToken = queryToken(request.getResourceDescriptor());
            } catch (IllegalArgumentException e) {
                // 非法 URL 编码按认证失败处理，不把请求内容或 token 写进日志。
                return false;
            }
        }

        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }

    private static String queryToken(String resource) {
        int queryStart = resource.indexOf('?');
        if (queryStart < 0) {
            return "";
        }

        String token = "";
        for (String parameter : resource.substring(queryStart + 1).split("&")) {
            int separator = parameter.indexOf('=');
            if (separator > 0 && "access_token".equals(parameter.substring(0, separator))) {
                token =
                        URLDecoder.decode(
                                parameter.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return token;
    }

    /** 日志只显示服务端点，省略可能包含凭据的 userinfo、查询参数和 fragment。 */
    static String endpoint(URI uri) {
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + port;
    }
}
