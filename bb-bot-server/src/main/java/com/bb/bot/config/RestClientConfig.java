package com.bb.bot.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 *@author Ren yuming
 *@description
 *@since 2023-7-13 10:37
 */
@Configuration
public class RestClientConfig {

    @Value("${rest.readTimeout:50000}")
    private int readTimeout;

    @Value("${rest.connectTimeout:50000}")
    private int connectTimeout;

    @Value("${rest.proxyIp:}")
    private String proxyIp;

    @Value("${rest.proxyPort:}")
    private Integer proxyPort;

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(getClientHttpRequestFactory())
                .build();
    }

    private ClientHttpRequestFactory getClientHttpRequestFactory() {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();

        //如果代理不为空，设置代理（内网/集群地址直连，不走代理）
        if (StringUtils.isNoneBlank(proxyIp) && proxyPort != null) {
            HttpHost proxy = new HttpHost(proxyIp, proxyPort);
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy) {
                @Override
                protected HttpHost determineProxy(HttpHost target, HttpContext context) throws HttpException {
                    // 集群内部地址（如 http://rsshub:1200、*.svc.cluster.local、私有 IP）必须直连，
                    // 否则会被转给外网 clash 代理 → 代理解析不了内网名字 → 502 Bad Gateway。
                    if (isInternalHost(target.getHostName())) {
                        return null;
                    }
                    return super.determineProxy(target, context);
                }
            };
            httpClientBuilder.setRoutePlanner(routePlanner);
        }
        CloseableHttpClient httpClient = httpClientBuilder.build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        requestFactory.setConnectionRequestTimeout(readTimeout);
        requestFactory.setConnectTimeout(connectTimeout);
        return requestFactory;
    }

    /**
     * 判断是否为集群/内网地址（这些地址直连，不走外网代理）：
     * 单段主机名（无点，如 rsshub）、*.svc.cluster.local / *.cluster.local / *.local、
     * localhost / 回环、以及私有网段 10/192.168/172.16-31。
     */
    static boolean isInternalHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase().trim();
        if (h.equals("localhost") || h.equals("::1")) {
            return true;
        }
        if (h.endsWith(".svc.cluster.local") || h.endsWith(".cluster.local") || h.endsWith(".local")) {
            return true;
        }
        // 无点的单段主机名 = 集群内 service 名（如 rsshub、mysql 等）
        if (!h.contains(".")) {
            return true;
        }
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignore) {
                    // 非数字，按外网处理
                }
            }
        }
        return false;
    }
}
