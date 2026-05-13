package com.bb.bot.aiAgent.plugin;

import lombok.Data;

import java.util.List;

/**
 * 一个插件 jar 根目录 plugin.json 的内容。
 *
 * <pre>
 * {
 *   "name": "my-tool-pack",
 *   "version": "0.1.0",
 *   "description": "...",
 *   "toolClasses": ["com.example.MyTool", "com.example.AnotherTool"]
 * }
 * </pre>
 *
 * <p>toolClasses 显式列出来比扫描整个 jar 快、可控。</p>
 */
@Data
public class PluginManifest {
    private String name;
    private String version;
    private String description;
    private List<String> toolClasses;
}
