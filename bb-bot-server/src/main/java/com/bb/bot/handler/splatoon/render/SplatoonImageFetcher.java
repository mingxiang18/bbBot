package com.bb.bot.handler.splatoon.render;

import com.bb.bot.common.util.ResourcesUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 把 {@code BbSplatoonHandler.getImageFile} 抽到独立 Bean。
 * 给图片资源算一个 {@code splatoon/{type}/{filename}} 的本地路径，命中本地就用本地，
 * 没有就从 url 下载到本地再返回。
 *
 * @author ren
 */
@Component
public class SplatoonImageFetcher {

    @Autowired
    private ResourcesUtils resourcesUtils;

    public File getImageFile(String url, String type) {
        String fileName;

        if ("festival".equals(type)) {
            fileName = url.substring(url.lastIndexOf("/", url.lastIndexOf("/") - 1)).replace("/", "");
        } else {
            fileName = url.substring(url.lastIndexOf("/"));
        }

        String fileSubPath = "splatoon/" + type + "/" + fileName;
        return resourcesUtils.getOrAddStaticResourceFromNet(fileSubPath, url);
    }
}
