package com.bb.bot.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RestClientConfig#isInternalHost(String)} 单测：内网/集群地址直连、外网走代理。
 * 覆盖真实 bug：内网地址 rsshub:1200 被外网代理转发导致 502。
 */
class RestClientConfigTest {

    @Test
    void internalHosts_returnTrue() {
        assertThat(RestClientConfig.isInternalHost("rsshub")).isTrue();              // 集群 service 名
        assertThat(RestClientConfig.isInternalHost("mysql")).isTrue();
        assertThat(RestClientConfig.isInternalHost("bb-sandbox.bb-bot.svc.cluster.local")).isTrue();
        assertThat(RestClientConfig.isInternalHost("mysql-inner.mysql.svc.cluster.local")).isTrue();
        assertThat(RestClientConfig.isInternalHost("localhost")).isTrue();
        assertThat(RestClientConfig.isInternalHost("127.0.0.1")).isTrue();
        assertThat(RestClientConfig.isInternalHost("10.8.0.1")).isTrue();
        assertThat(RestClientConfig.isInternalHost("192.168.50.227")).isTrue();
        assertThat(RestClientConfig.isInternalHost("172.16.0.5")).isTrue();
        assertThat(RestClientConfig.isInternalHost("172.31.255.255")).isTrue();
    }

    @Test
    void externalHosts_returnFalse() {
        assertThat(RestClientConfig.isInternalHost("36kr.com")).isFalse();
        assertThat(RestClientConfig.isInternalHost("api.deepseek.com")).isFalse();
        assertThat(RestClientConfig.isInternalHost("feeds.macrumors.com")).isFalse();
        assertThat(RestClientConfig.isInternalHost("api.telegram.org")).isFalse();
        assertThat(RestClientConfig.isInternalHost("8.138.142.47")).isFalse();       // 公网 IP
        assertThat(RestClientConfig.isInternalHost("172.32.0.1")).isFalse();         // 172.32 不在私有段
        assertThat(RestClientConfig.isInternalHost("172.15.0.1")).isFalse();         // 172.15 不在私有段
    }

    @Test
    void nullOrBlank_returnFalse() {
        assertThat(RestClientConfig.isInternalHost(null)).isFalse();
        assertThat(RestClientConfig.isInternalHost("  ")).isFalse();
    }
}
