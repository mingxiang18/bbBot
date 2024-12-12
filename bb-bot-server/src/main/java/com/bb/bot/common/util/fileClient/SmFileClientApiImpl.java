//package com.bb.bot.util.imageUpload;
//
//import com.alibaba.fastjson2.JSONArray;
//import com.alibaba.fastjson2.JSONObject;
//import com.bb.bot.common.util.RestUtils;
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.core.io.InputStreamResource;
//import org.springframework.core.io.Resource;
//import org.springframework.http.HttpHeaders;
//import org.springframework.stereotype.Component;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//
//import java.io.ByteArrayInputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * sm图床的图片上传工具
// */
//@Slf4j
//@Component
//@ConditionalOnProperty(prefix="imageUpload",name = "type", havingValue = "sm", matchIfMissing = false)
//public class SmFileClientApiImpl implements FileClientApi {
//
//    @Autowired
//    private RestUtils restUtils;
//
//    @Value("${imageUpload.url:https://sm.ms/api/v2}")
//    private String webUrl;
//
//    @Value("${imageUpload.token:}")
//    private String token;
//
//    @Override
//    @SneakyThrows
//    public String uploadFile(File file) {
//        HttpHeaders httpHeaders = new HttpHeaders();
//        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
//        httpHeaders.set("Authorization", token);
//
//        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
//        try (
//                final FileInputStream in = new FileInputStream(file);){
//            //新建流资源，必须重写contentLength()和getFilename()
//            Resource resource = new InputStreamResource(in){
//                //文件长度,单位字节
//                @Override
//                public long contentLength() throws IOException {
//                    long size = in.available();
//                    return size;
//                }
//                //文件名
//                @Override
//                public String getFilename(){
//                    return file.getName();
//                }
//            };
//            paramMap.add("smfile", resource);
//
//            //调用上传接口
//            JSONObject response = restUtils.postForForm(webUrl + "/upload", httpHeaders, paramMap, JSONObject.class);
//
//            //返回图片url
//            return response.containsKey("images") ? response.getString("images") : response.getJSONObject("data").getString("url");
//        }catch (Exception e) {
//            log.error("上传文件出错", e);
//        }
//        return null;
//    }
//
//    /**
//     * 删除所有上传的图片
//     * 图片仅是临时保存，用于中转给qq接收，定时清理图片，防止图床占用满了
//     */
//    @Override
//    public void deleteAllImage() {
//        List<String> deleteImageHash = new ArrayList<>();
//
//        HttpHeaders httpHeaders = new HttpHeaders();
//        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
//        httpHeaders.set("Authorization", token);
//
//        //调用上传历史记录接口
//        JSONObject response = restUtils.get(webUrl + "/upload_history", httpHeaders, JSONObject.class);
//        JSONArray data = response.getJSONArray("data");
//        if (data != null && data.size() > 0) {
//            for (int i = 0; i < data.size(); i++) {
//                JSONObject jsonObject = data.getJSONObject(i);
//                deleteImageHash.add(jsonObject.getString("hash"));
//            }
//        }
//
//        //调用删除接口全部删除
//        for (String imageHash : deleteImageHash) {
//            //调用删除接口
//            restUtils.get(webUrl + "/delete/" + imageHash, httpHeaders, JSONObject.class);
//        }
//    }
//
//    @Override
//    public String uploadFile(byte[] localFile, String remotePath) {
//        HttpHeaders httpHeaders = new HttpHeaders();
//        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
//        httpHeaders.set("Authorization", token);
//
//        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
//        try (
//                final ByteArrayInputStream in = new ByteArrayInputStream(localFile);){
//            //新建流资源，必须重写contentLength()和getFilename()
//            Resource resource = new InputStreamResource(in){
//                //文件长度,单位字节
//                @Override
//                public long contentLength() throws IOException {
//                    long size = in.available();
//                    return size;
//                }
//                //文件名
//                @Override
//                public String getFilename(){
//                    return file.getName();
//                }
//            };
//            paramMap.add("smfile", resource);
//
//            //调用上传接口
//            JSONObject response = restUtils.postForForm(webUrl + "/upload", httpHeaders, paramMap, JSONObject.class);
//
//            //返回图片url
//            return response.containsKey("images") ? response.getString("images") : response.getJSONObject("data").getString("url");
//        }catch (Exception e) {
//            log.error("上传文件出错", e);
//        }
//        return null;
//    }
//
//    @Override
//    public byte[] downloadFile(String remotePath) {
//        return new byte[0];
//    }
//
//    @Override
//    public void deleteFile(String remotePath) {
//
//    }
//}
