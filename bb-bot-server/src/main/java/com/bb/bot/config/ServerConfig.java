package com.bb.bot.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 服务器相关配置
 */
@Slf4j
@Data
@Configuration
public class ServerConfig {

    @Value("${server.ip}")
    private String ip;

    @Value("${server.port}")
    private String port;
}
