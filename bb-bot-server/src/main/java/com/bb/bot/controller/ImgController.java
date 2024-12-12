package com.bb.bot.controller;

import com.bb.bot.common.util.FileUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 获取服务器图片控制器
 * @author ren
 */
@RestController
@Api(tags = "图片Controller")
@RequestMapping("/img")
public class ImgController {

    /**
     * 获取图片
     */
    @GetMapping(value = "/getImage/{imgName}", produces = MediaType.IMAGE_PNG_VALUE)
    @ApiOperation(value="获取图片")
    public byte[] getImage(@PathVariable("imgName") String imgName) throws Exception {
        byte[] imgBytes = FileUtils.getFile(FileUtils.getAbsolutePath("tmp/" + imgName));
        return imgBytes;
    }
}
