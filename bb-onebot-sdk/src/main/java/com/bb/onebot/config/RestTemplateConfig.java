package com.bb.onebot.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 *@author Ren yuming
 *@description
 *@date 2023/7/13 10:37
 */

@Configuration
public class RestTemplateConfig {

    @Value("${rest.readTimeout:50000}")
    private int readTimeout;

    @Value("${rest.connectTimeout:50000}")
    private int connectTimeout;

    @Value("${rest.proxyIp:}")
    private String proxyIp;

    @Value("${rest.proxyPort:}")
    private Integer proxyPort;

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory) {
        return new RestTemplate(factory);
    }

    /**
     * 设置连接超时时间
     * @return
     */
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(readTimeout);
        factory.setConnectTimeout(connectTimeout);
        //如果代理不为空，设置代理
        if (StringUtils.isNoneBlank(proxyIp) && proxyPort != null) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort)));
        }
        return factory;
    }

}
