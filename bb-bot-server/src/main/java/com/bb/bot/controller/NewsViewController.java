package com.bb.bot.controller;

import com.bb.bot.config.NewsConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 每日资讯日报对外访问控制器（无鉴权）。
 *
 * <p>从 {@code newsConfig.hosting.dir} 读取静态 HTML：latest（index.html）、归档索引
 * （archive.html）、单日页（{date}.html）。日期严格按 yyyy-MM-dd 校验，防目录穿越。</p>
 *
 * @author ren
 */
@RestController
@Api(tags = "每日资讯日报Controller")
@RequestMapping("/news")
public class NewsViewController {

    /** 仅允许 yyyy-MM-dd，杜绝 "/"、".." 等穿越字符。 */
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Autowired
    private NewsConfig newsConfig;

    /**
     * latest 入口：返回 index.html（最新一期）。
     */
    @GetMapping(value = {"", "/", "/index.html"})
    @ApiOperation(value = "最新一期日报")
    public ResponseEntity<byte[]> latest() {
        return read("index.html");
    }

    /**
     * 归档索引页。
     */
    @GetMapping(value = {"/archive", "/archive.html"})
    @ApiOperation(value = "归档索引")
    public ResponseEntity<byte[]> archive() {
        return read("archive.html");
    }

    /**
     * 单日页。{@code date} 必须形如 yyyy-MM-dd。
     */
    @GetMapping(value = "/{date}.html")
    @ApiOperation(value = "指定日期的日报")
    public ResponseEntity<byte[]> daily(@PathVariable("date") String date) {
        if (date == null || !DATE_PATTERN.matcher(date).matches()) {
            return ResponseEntity.notFound().build();
        }
        return read(date + ".html");
    }

    /**
     * 从托管目录读取指定文件名（仅文件名，不含路径分隔符），返回 text/html。
     * 文件不存在返回 404。
     */
    private ResponseEntity<byte[]> read(String fileName) {
        Path dir = Paths.get(newsConfig.getHosting().getDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(fileName).normalize();
        // 双保险：normalize 后必须仍在托管目录内
        if (!target.startsWith(dir) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] body = Files.readAllBytes(target);
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body(body);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
