package com.bb.bot.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
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

        //如果代理不为空，设置代理
        if (StringUtils.isNoneBlank(proxyIp) && proxyPort != null) {
            HttpHost proxy = new HttpHost(proxyIp, proxyPort);
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
            httpClientBuilder.setRoutePlanner(routePlanner);
        }
        CloseableHttpClient httpClient = httpClientBuilder.build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        requestFactory.setConnectionRequestTimeout(readTimeout);
        requestFactory.setConnectTimeout(connectTimeout);
        return requestFactory;
    }
}
