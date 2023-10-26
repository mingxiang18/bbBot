package com.bb.onebot.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 文件路径配置
 * @author ren
 */
@Data
@Configuration
public class FilePathConfig {

    @Value("${file.path}")
    private String filePath;
}
