package com.bb.onebot.util;

import com.alibaba.fastjson2.JSON;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;

/**
 * @author ren
 */
@Slf4j
@Component
public class RestClient {
    @Autowired
    private RestTemplate restTemplate;

    @SneakyThrows
    public <T> T get(String url, Class<T> clazz) {
        log.info("向外部接口发起GET请求，url：{}", url);
        String resultString = restTemplate.getForObject(url, String.class);
        log.info("外部接口返回报文:{}", resultString);

        T t = JSON.parseObject(resultString, clazz);
        return t;
    }

    @SneakyThrows
    public <T> T get(String url, HttpHeaders httpHeaders, Class<T> clazz) {
        HttpEntity httpEntity = new HttpEntity(httpHeaders);

        log.info("向外部接口发起GET请求，url：{}", url);
        ResponseEntity<String> resultEntity = restTemplate.exchange(url, HttpMethod.GET, httpEntity, String.class);
        log.info("外部接口返回报文:{}", resultEntity.getBody());

        T t = JSON.parseObject(resultEntity.getBody(), clazz);
        return t;
    }

    public <T> T post(String url, HttpHeaders httpHeaders, Object params, Class<T> clazz) {
        String jsonParams = JSON.toJSONString(params);
        HttpEntity httpEntity = new HttpEntity(jsonParams, httpHeaders);

        log.info("向外部接口发起POST请求，url：{}，请求报文：{}", url, jsonParams);
        String resultString = restTemplate.postForObject(url, httpEntity, String.class);
        log.info("外部接口返回报文:{}", resultString);

        T t = JSON.parseObject(resultString, clazz);

        return t;
    }

    @SneakyThrows
    public <T> T post(String url, Object params, Class<T> clazz) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        return post(url, httpHeaders, params, clazz);
    }

    @SneakyThrows
    public <T> T put(String url, Object params, Class<T> clazz) {
        String jsonParams = JSON.toJSONString(params);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity httpEntity = new HttpEntity(jsonParams, httpHeaders);

        log.info("向外部接口发起PUT请求，url：{}，请求报文：{}", url, jsonParams);
        ResponseEntity<String> resultEntity = restTemplate.exchange(url, HttpMethod.PUT, httpEntity, String.class);
        log.info("外部接口返回报文:{}", resultEntity.getBody());

        T t = JSON.parseObject(resultEntity.getBody(), clazz);
        return t;
    }

    @SneakyThrows
    public <T> T delete(String url, Object params, Class<T> clazz) {
        String jsonParams = JSON.toJSONString(params);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity httpEntity = new HttpEntity(jsonParams, httpHeaders);

        log.info("向外部接口发起DELETE请求，url：{}，请求报文：{}", url, jsonParams);
        ResponseEntity<String> resultEntity = restTemplate.exchange(url, HttpMethod.DELETE, httpEntity, String.class);
        log.info("外部接口返回报文:{}", resultEntity.getBody());

        T t = JSON.parseObject(resultEntity.getBody(), clazz);
        return t;
    }

    /*读取网络文件*/
    @SneakyThrows
    public InputStream getFileInputStream(String url) {
        log.info("向外部接口发起GET请求获取文件，url：{}", url);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        ResponseEntity<Resource> resultEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(httpHeaders), Resource.class);
        return resultEntity.getBody().getInputStream();
    }
}
