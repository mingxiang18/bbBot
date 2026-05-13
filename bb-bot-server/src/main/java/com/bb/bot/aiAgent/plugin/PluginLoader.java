package com.bb.bot.aiAgent.plugin;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.aiAgent.core.AiToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 扫描 plugins/ 目录里的 jar，把每个插件的 @AiTool 方法注册进 {@link AiToolRegistry}。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>每个 jar 用独立 URLClassLoader 加载，避免插件间冲突</li>
 *   <li>parent classloader 是当前线程 context classloader，让插件能引用 bbBot 主项目的 @AiTool 注解和共用类型</li>
 *   <li>不与 Spring 容器深度集成 —— 插件类用反射 newInstance，自己处理依赖</li>
 *   <li>reload 时先 unregisterBySource(pluginName) 再重新加载</li>
 * </ul>
 */
@Slf4j
@Component
public class PluginLoader {

    @Autowired
    private AiToolRegistry registry;

    @Value("${aiAgent.pluginDir:./plugins}")
    private String pluginDir;

    /** pluginName → 加载详情（保留 ClassLoader 引用便于 reload）。 */
    private final Map<String, LoadedPlugin> loaded = Collections.synchronizedMap(new HashMap<>());

    @EventListener
    public void onContextRefreshed(ContextRefreshedEvent event) {
        reloadAll();
    }

    /** 全量 reload：清空所有已加载插件，重新扫描目录。 */
    public synchronized List<String> reloadAll() {
        for (String name : new ArrayList<>(loaded.keySet())) {
            unload(name);
        }
        File dir = new File(pluginDir);
        if (!dir.exists() || !dir.isDirectory()) {
            log.info("插件目录 {} 不存在，跳过扫描", dir.getAbsolutePath());
            return Collections.emptyList();
        }
        File[] jars = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        List<String> loadedNames = new ArrayList<>();
        if (jars == null) return loadedNames;
        for (File jar : jars) {
            try {
                String name = loadJar(jar);
                if (name != null) loadedNames.add(name);
            } catch (Exception e) {
                log.warn("插件加载失败 jar={}", jar.getName(), e);
            }
        }
        log.info("插件加载完成，{} 个：{}", loadedNames.size(), loadedNames);
        return loadedNames;
    }

    private String loadJar(File jar) throws Exception {
        PluginManifest manifest = readManifest(jar);
        if (manifest == null || manifest.getName() == null) {
            log.warn("插件 {} 缺少 plugin.json 或 name 字段，跳过", jar.getName());
            return null;
        }
        URL jarUrl = jar.toURI().toURL();
        URLClassLoader cl = new URLClassLoader(new URL[]{jarUrl}, Thread.currentThread().getContextClassLoader());
        List<Object> instances = new ArrayList<>();
        if (manifest.getToolClasses() != null) {
            for (String fqn : manifest.getToolClasses()) {
                try {
                    Class<?> klass = Class.forName(fqn, true, cl);
                    Object inst = klass.getDeclaredConstructor().newInstance();
                    registry.registerToolsFromInstance(inst, manifest.getName());
                    instances.add(inst);
                } catch (Exception e) {
                    log.warn("插件 {} 加载类 {} 失败", manifest.getName(), fqn, e);
                }
            }
        }
        loaded.put(manifest.getName(), new LoadedPlugin(manifest, cl, jar, instances));
        return manifest.getName();
    }

    private PluginManifest readManifest(File jar) {
        try (JarFile jarFile = new JarFile(jar)) {
            JarEntry entry = jarFile.getJarEntry("plugin.json");
            if (entry == null) return null;
            try (InputStream is = jarFile.getInputStream(entry)) {
                byte[] bytes = is.readAllBytes();
                return JSON.parseObject(bytes, PluginManifest.class);
            }
        } catch (Exception e) {
            log.warn("读取 plugin.json 失败 jar={}", jar.getName(), e);
            return null;
        }
    }

    public synchronized void unload(String pluginName) {
        LoadedPlugin lp = loaded.remove(pluginName);
        if (lp == null) return;
        Set<String> removed = registry.unregisterBySource(pluginName);
        log.info("插件 {} 已卸载，移除 {} 个工具：{}", pluginName, removed.size(), removed);
        try {
            lp.classLoader.close();
        } catch (Exception e) {
            log.debug("关闭 plugin classloader 失败", e);
        }
    }

    public List<LoadedPlugin> listLoaded() {
        return new ArrayList<>(loaded.values());
    }

    /** 已加载插件的全部信息。 */
    public static class LoadedPlugin {
        public final PluginManifest manifest;
        public final URLClassLoader classLoader;
        public final File sourceJar;
        public final List<Object> instances;

        LoadedPlugin(PluginManifest manifest, URLClassLoader cl, File sourceJar, List<Object> instances) {
            this.manifest = manifest;
            this.classLoader = cl;
            this.sourceJar = sourceJar;
            this.instances = instances;
        }
    }
}
