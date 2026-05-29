package com.bb.bot.common.util;

import com.alibaba.fastjson2.annotation.JSONField;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RestUtils 公共 header 构造去重的等价性单测。
 * 用 JDK {@link HttpServer} 起本地 stub，真实收发，断言：
 * - User-Agent 不变（默认 UA 字面量）
 * - Content-Type 不变（POST/PUT/DELETE=application/json；postForForm=multipart/form-data）
 * - GET 自定义 header 时不覆盖已有 UA
 * - 参数序列化不变（POST body / GET query string / form-data 字段）
 */
class RestUtilsTest {

    private static final String EXPECTED_UA = "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)";

    private HttpServer server;
    private RestUtils restUtils;
    private String baseUrl;

    /** 记录最近一次收到的请求，供断言。 */
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();

    static class RecordedRequest {
        String method;
        String path;
        String query;
        HttpHeaders headers;
        String body;
    }

    static class SampleParam {
        private String name;
        private Integer age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }

    static class AnnotatedParam {
        @JSONField(name = "user_name")
        private String userName;

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
    }

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        int port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        restUtils = new RestUtils();
        RestClient restClient = RestClient.builder().build();
        Field f = RestUtils.class.getDeclaredField("restClient");
        f.setAccessible(true);
        f.set(restUtils, restClient);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        RecordedRequest req = new RecordedRequest();
        req.method = exchange.getRequestMethod();
        req.path = exchange.getRequestURI().getPath();
        req.query = exchange.getRequestURI().getRawQuery();
        HttpHeaders headers = new HttpHeaders();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, new ArrayList<>(v)));
        req.headers = headers;
        try (InputStream in = exchange.getRequestBody()) {
            req.body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        lastRequest.set(req);

        byte[] resp = "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, resp.length);
        try (var os = exchange.getResponseBody()) {
            os.write(resp);
        }
    }

    private String ua(RecordedRequest req) {
        return req.headers.getFirst("User-Agent");
    }

    private String contentType(RecordedRequest req) {
        return req.headers.getFirst("Content-Type");
    }

    // ---------- header 构造 helper 的纯逻辑断言 ----------

    @Test
    void buildDefaultHeaders_onlyUserAgent() {
        HttpHeaders h = RestUtils.buildDefaultHeaders();
        assertThat(h.getFirst(HttpHeaders.USER_AGENT)).isEqualTo(EXPECTED_UA);
        assertThat(h.getContentType()).isNull();
    }

    @Test
    void buildJsonHeaders_uaPlusJson() {
        HttpHeaders h = RestUtils.buildJsonHeaders();
        assertThat(h.getFirst(HttpHeaders.USER_AGENT)).isEqualTo(EXPECTED_UA);
        assertThat(h.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void buildFormHeaders_uaPlusMultipart() {
        HttpHeaders h = RestUtils.buildFormHeaders();
        assertThat(h.getFirst(HttpHeaders.USER_AGENT)).isEqualTo(EXPECTED_UA);
        // 等价于原先 set("Content-Type", "multipart/form-data")
        assertThat(h.getContentType().toString()).isEqualTo("multipart/form-data");
    }

    // ---------- 真实收发：UA / Content-Type 不变 ----------

    @Test
    void get_setsDefaultUserAgent() {
        restUtils.get(baseUrl + "/g", String.class);
        RecordedRequest req = lastRequest.get();
        assertThat(req.method).isEqualTo("GET");
        assertThat(ua(req)).isEqualTo(EXPECTED_UA);
    }

    @Test
    void get_doesNotOverrideExistingUserAgent() {
        HttpHeaders custom = new HttpHeaders();
        custom.set(HttpHeaders.USER_AGENT, "my-agent/1.0");
        restUtils.get(baseUrl + "/g", custom, String.class);
        assertThat(ua(lastRequest.get())).isEqualTo("my-agent/1.0");
    }

    @Test
    void post_setsUaAndJsonContentTypeAndSerializesBody() {
        SampleParam p = new SampleParam();
        p.setName("ren");
        p.setAge(18);

        restUtils.post(baseUrl + "/p", p, Map.class);

        RecordedRequest req = lastRequest.get();
        assertThat(req.method).isEqualTo("POST");
        assertThat(ua(req)).isEqualTo(EXPECTED_UA);
        assertThat(contentType(req)).startsWith("application/json");
        // 参数序列化为 JSON 不变
        assertThat(req.body).contains("\"name\":\"ren\"").contains("\"age\":18");
    }

    @Test
    void post_withExplicitHeaders_preservesThem() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(HttpHeaders.USER_AGENT, "explicit/2");
        SampleParam p = new SampleParam();
        p.setName("a");

        restUtils.post(baseUrl + "/p", h, p, Map.class);

        RecordedRequest req = lastRequest.get();
        assertThat(ua(req)).isEqualTo("explicit/2");
        assertThat(contentType(req)).startsWith("application/json");
    }

    @Test
    void put_setsUaAndJsonContentType() {
        SampleParam p = new SampleParam();
        p.setName("x");
        p.setAge(5);

        restUtils.put(baseUrl + "/u", p, Map.class);

        RecordedRequest req = lastRequest.get();
        assertThat(req.method).isEqualTo("PUT");
        assertThat(ua(req)).isEqualTo(EXPECTED_UA);
        assertThat(contentType(req)).startsWith("application/json");
        assertThat(req.body).contains("\"name\":\"x\"").contains("\"age\":5");
    }

    @Test
    void delete_setsUaAndJsonContentType() {
        restUtils.delete(baseUrl + "/d", Map.class);

        RecordedRequest req = lastRequest.get();
        assertThat(req.method).isEqualTo("DELETE");
        assertThat(ua(req)).isEqualTo(EXPECTED_UA);
        assertThat(contentType(req)).startsWith("application/json");
    }

    @Test
    void postForForm_setsUaAndMultipartContentTypeAndSerializesFields() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("k1", "v1");
        params.put("k2", "v2");

        restUtils.postForForm(baseUrl + "/f", params, Map.class);

        RecordedRequest req = lastRequest.get();
        assertThat(req.method).isEqualTo("POST");
        assertThat(ua(req)).isEqualTo(EXPECTED_UA);
        assertThat(contentType(req)).startsWith("multipart/form-data");
        // form-data 字段序列化不变
        assertThat(req.body).contains("name=\"k1\"").contains("v1");
        assertThat(req.body).contains("name=\"k2\"").contains("v2");
    }

    @Test
    void getFileInputStream_setsDefaultUserAgent() throws Exception {
        try (InputStream in = restUtils.getFileInputStream(baseUrl + "/file")) {
            assertThat(in.readAllBytes()).isNotEmpty();
        }
        RecordedRequest req = lastRequest.get();
        assertThat(req.method).isEqualTo("GET");
        assertThat(ua(req)).isEqualTo(EXPECTED_UA);
    }

    // ---------- 参数序列化（query string）不变 ----------

    @Test
    void packageParamString_serializesFieldsAndAnnotations() {
        SampleParam p = new SampleParam();
        p.setName("ren");
        p.setAge(18);
        String qs = restUtils.packageParamString(p);
        assertThat(qs).startsWith("?");
        assertThat(qs).contains("name=ren").contains("age=18");

        AnnotatedParam a = new AnnotatedParam();
        a.setUserName("zhang");
        assertThat(restUtils.packageParamString(a)).isEqualTo("?user_name=zhang");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "1");
        map.put("b", "2");
        assertThat(restUtils.packageParamString(map)).isEqualTo("?a=1&b=2");

        SampleParam empty = new SampleParam();
        assertThat(restUtils.packageParamString(empty)).isEqualTo("");
    }
}
