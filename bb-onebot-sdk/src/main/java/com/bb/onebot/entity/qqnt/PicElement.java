package com.bb.onebot.entity.qqnt;

import lombok.Data;

/**
 * 文本元素
 * @author ren
 */
@Data
public class PicElement {
    //"fileName": "e7503679ccc3dff69491b3f9eb95bc40.jpg",
    //              "fileSize": "306791",
    //              "picWidth": 1170,
    //              "picHeight": 2532,
    //              "sourcePath": "C:\\Users\\ren\\Documents\\Tencent Files\\2068468537\\nt_qq\\nt_data\\Pic\\2023-12\\Ori\\e7503679ccc3dff69491b3f9eb95bc40.jpg"

    private String fileName;

    private String fileSize;

    private String picWidth;

    private String picHeight;

    private String sourcePath;
}
