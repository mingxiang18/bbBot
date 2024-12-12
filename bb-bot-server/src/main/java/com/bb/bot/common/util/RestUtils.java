package com.bb.bot.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * @author ren
 */
@Slf4j
@Component
public class RestUtils {

    @Autowired
    private RestClient restClient;

    @SneakyThrows
    public <T> T get(String url, Class<T> clazz) {
        return get(url, new HttpHeaders(), clazz);
    }

    @SneakyThrows
    public <T> T get(String url, HttpHeaders httpHeaders, Class<T> clazz) {
        if (!httpHeaders.containsKey(HttpHeaders.USER_AGENT)) {
            httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        }

        log.info("向外部接口发起GET请求，url：{}", url);
        String responseStr = restClient.get()
                .uri(url)
                .headers(h -> httpHeaders.forEach(h::addAll))
                .exchange((request, response) -> {
                    return packageResponse(response);
                });

        log.info("外部接口返回报文:{}", responseStr);

        if (clazz == String.class) {
            return (T) responseStr;
        }else {
            T t = JSON.parseObject(responseStr, clazz);
            return t;
        }
    }

    public <T> T post(String url, HttpHeaders httpHeaders, Object params, Class<T> clazz) {
        String jsonParams = JSON.toJSONString(params);

        log.info("向外部接口发起POST请求，url：{}，请求报文：{}", url, jsonParams);
        String responseStr = restClient.post()
                .uri(url)
                .headers(h -> httpHeaders.forEach(h::addAll))
                .body(jsonParams)
                .exchange((request, response) -> {
                    return packageResponse(response);
                });
        log.info("外部接口返回报文:{}", responseStr);

        T t = JSON.parseObject(responseStr, clazz);

        return t;
    }

    @SneakyThrows
    public <T> T post(String url, Object params, Class<T> clazz) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        return post(url, httpHeaders, params, clazz);
    }

    public <T> T postForForm(String url, Object params, Class<T> clazz) {
        HttpHeaders httpHeaders = new HttpHeaders();
        //头部类型
        httpHeaders.set("Content-Type", "multipart/form-data");
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        return postForForm(url, httpHeaders, params, clazz);
    }

    @SneakyThrows
    public <T> T postForForm(String url, HttpHeaders httpHeaders, Object params, Class<T> clazz) {
        MultiValueMap<String, Object> map = packageParamMultiValueMap(params);

        log.info("向外部接口发起form-data类型的post请求，url：{}", url);
        String resultString = restClient.post()
                .uri(url)
                .headers(h -> httpHeaders.forEach(h::addAll))
                .body(map)
                .retrieve()
                .body(String.class);
        log.info("外部接口返回报文:{}", resultString);

        T t = JSON.parseObject(resultString, clazz);
        return t;
    }

    @SneakyThrows
    public <T> T put(String url, Object params, Class<T> clazz) {
        String jsonParams = JSON.toJSONString(params);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");

        log.info("向外部接口发起PUT请求，url：{}，请求报文：{}", url, jsonParams);
        String resultString = restClient.put()
                .uri(url)
                .headers(h -> httpHeaders.forEach(h::addAll))
                .body(jsonParams)
                .retrieve()
                .body(String.class);
        log.info("外部接口返回报文:{}", resultString);

        T t = JSON.parseObject(resultString, clazz);
        return t;
    }

    @SneakyThrows
    public <T> T delete(String url, Class<T> clazz) {

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");

        log.info("向外部接口发起DELETE请求，url：{}", url);
        String resultString = restClient.delete()
                .uri(url)
                .headers(h -> httpHeaders.forEach(h::addAll))
                .retrieve()
                .body(String.class);
        log.info("外部接口返回报文:{}", resultString);

        T t = JSON.parseObject(resultString, clazz);
        return t;
    }

    /**
     * 读取网络文件
     */
    public InputStream getFileInputStream(String url) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        return getFileInputStream(url, httpHeaders);
    }

    /**
     * 读取网络文件
     */
    @SneakyThrows
    public InputStream getFileInputStream(String url, HttpHeaders httpHeaders) {
        log.info("向外部接口发起GET请求获取文件，url：{}", url);
        Resource resource = restClient.get()
                .uri(url)
                .headers(h -> httpHeaders.forEach(h::addAll))
                .retrieve()
                .body(Resource.class);
        return resource.getInputStream();
    }

    /**
     * 封装网络请求响应体
     */
    @SneakyThrows
    private String packageResponse(ClientHttpResponse response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("RestClientCallCodeException，code: " + response.getStatusCode().value() + ", message: " + IOUtils.toString(response.getBody(), StandardCharsets.UTF_8));
        }
        // 如果请求头是gzip格式，解压gzip响应体
        if (response.getHeaders().containsKey(HttpHeaders.CONTENT_ENCODING)
                && "gzip".equalsIgnoreCase(response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING))) {
            return new String(unGZip(response.getBody()), StandardCharsets.UTF_8);
        }else {
            return IOUtils.toString(response.getBody(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Gzip解压缩
     * @param inputStream
     * @return
     */
    @SneakyThrows
    public byte[] unGZip(InputStream inputStream) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            byte[] buf = new byte[4096];
            int len = -1;
            while ((len = gzipInputStream.read(buf, 0, buf.length)) != -1) {
                byteArrayOutputStream.write(buf, 0, len);
            }
            return byteArrayOutputStream.toByteArray();
        } finally {
            byteArrayOutputStream.close();
        }
    }

    /**
     * 将实体类参数封装为GET请求的URL后缀
     * @return
     */
    public String packageParamString(Object obj) {
        List<String> paramList = new ArrayList<>();

        if (obj instanceof Map) {
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) obj).entrySet()) {
                paramList.add(entry.getKey() + "=" + entry.getValue());
            }
        }else {
            //获得实体类名
            Class clazz = obj.getClass();

            while (clazz != null) {
                //获得属性
                Field[] fields = clazz.getDeclaredFields();
                //获得Object对象中的所有方法
                for (Field field : fields) {
                    try {
                        field.setAccessible(true);
                        //获取属性上的JsonProperty注解
                        JSONField jsonField = field.getAnnotation(JSONField.class);

                        PropertyDescriptor pd = new PropertyDescriptor(field.getName(), clazz);
                        //获得get方法
                        Method getMethod = pd.getReadMethod();
                        if (getMethod != null) {
                            Object value = getMethod.invoke(obj);
                            if (value != null) {
                                if (jsonField != null && StringUtils.isNoneEmpty(jsonField.name())) {
                                    //如果注解不为空，获取注解上的字段名对应的字段值
                                    paramList.add(jsonField.name() + "=" + value);
                                } else {
                                    paramList.add(field.getName() + "=" + value);
                                }
                            }
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }

                clazz = clazz.getSuperclass();
            }
        }

        if (paramList != null && paramList.size() > 0) {
            return "?" + String.join("&", paramList);
        }else {
            return "";
        }
    }

    /**
     * 将实体类参数封装为MultiValueMap类型的数据
     * @return
     */
    public MultiValueMap<String, Object> packageParamMultiValueMap(Object obj) {
        //获得实体类名
        Class clazz = obj.getClass();

        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();

        if (obj instanceof MultiValueMap) {
            return (MultiValueMap<String, Object>) obj;
        }else if (obj instanceof Map) {
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) obj).entrySet()) {
                paramMap.add((String) entry.getKey(), entry.getValue());
            }
        }else {
            while (clazz != null) {
                //获得属性
                Field[] fields = clazz.getDeclaredFields();
                //获得Object对象中的所有方法
                for (Field field : fields) {
                    try {
                        field.setAccessible(true);
                        //获取属性上的JsonProperty注解
                        JSONField jsonField = field.getAnnotation(JSONField.class);

                        PropertyDescriptor pd = new PropertyDescriptor(field.getName(), clazz);
                        //获得get方法
                        Method getMethod = pd.getReadMethod();
                        if (getMethod != null) {
                            Object value = getMethod.invoke(obj);
                            if (value != null) {
                                if (value instanceof File) {
                                    value = new FileSystemResource((File) value);
                                }
                                if (jsonField != null && StringUtils.isNoneEmpty(jsonField.name())) {
                                    //如果注解不为空，获取注解上的字段名对应的字段值
                                    paramMap.add(jsonField.name(), value);
                                }else {
                                    paramMap.add(field.getName(), value);
                                }
                            }
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }

                clazz = clazz.getSuperclass();
            }
        }

        return paramMap;
    }
}
