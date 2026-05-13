package com.bb.bot.common.util.aiChat.provider;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * 默认 {@link HttpExchanger} 实现：包一下项目里已有的 {@link RestClient} 单例 Bean。
 *
 * @author ren
 */
@Component
public class RestClientHttpExchanger implements HttpExchanger {

    @Autowired
    private RestClient restClient;

    @Override
    public HttpResponse post(String url, java.util.Map<String, String> headers, String jsonBody) {
        HttpHeaders h = new HttpHeaders();
        headers.forEach(h::add);

        return restClient.post()
                .uri(url)
                .headers(rh -> h.forEach(rh::addAll))
                .body(jsonBody)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String body = IOUtils.toString(response.getBody(), StandardCharsets.UTF_8);
                    return new HttpResponse(status, body);
                });
    }
}
