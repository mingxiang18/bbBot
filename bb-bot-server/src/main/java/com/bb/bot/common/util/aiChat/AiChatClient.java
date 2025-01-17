package com.bb.bot.common.util.aiChat;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ai聊天工具类
 * @author ren
 */
@Slf4j
@Component
public class AiChatClient {

    @Autowired
    private RestUtils restUtils;

    /**
     * chatGPT的Url
     */
    @Value("${chatGPT.url:https://api.openai.com/v1/chat/completions}")
    private String chatGPTUrl;

    /**
     * chatGPT的apiKey
     */
    @Value("${chatGPT.apiKey:}")
    private String chatGPTApiKey;

    /**
     * 模型
     */
    @Value("${chatGPT.model:gpt-4}")
    private String model;

    /**
     * ai模型视觉开关，如果模型支持图像输入可开启
     */
    @Value("${chatGPT.visionEnable:false}")
    private Boolean visionEnable;

    /**
     * 出现错误时重试次数
     */
    @Value("${chatGPT.retryNum:10}")
    private Integer retryNum;

    /**
     * 是否配置了ai
     */
    public Boolean hasConfigAI() {
        return StringUtils.isNoneBlank(chatGPTApiKey);
    }

    /**
     * 询问chatGPT
     * @Param personality 机器人设定/性格
     * @Param question 问题
     * @Param chatHistoryList 聊天历史
     * @author ren
     */
    public String askChatGPT(List<ChatGPTContent> chatContentList) {
        //如果apiKey为空，不执行
        if (StringUtils.isBlank(chatGPTApiKey)) {
            return null;
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("Authorization", "Bearer " + chatGPTApiKey);

        //图片url二次处理
        Iterator<ChatGPTContent> iterator = chatContentList.iterator(); // 实例化迭代器
        while (iterator.hasNext()) {
            ChatGPTContent chatGPTContent = iterator.next(); // 读取当前集合数据元素
            if (chatGPTContent.getContent() instanceof List<?> contentList) {
                for (Object content : contentList) {
                    if (content instanceof Map imageMap) {
                        if (imageMap.get("type").equals("image_url")) {
                            Map imageUrlMap = (Map) imageMap.get("image_url");
                            String imageUrl = (String) imageUrlMap.get("url");
                            if (StringUtils.isNotBlank(imageUrl)) {
                                boolean isBase64 = imageUrl.startsWith("data:image/");
                                //如果是moonshot模型，且图片不是base64，把网络图片下载后转成base64发送
                                if (model.contains("moonshot") && !isBase64) {
                                    try (InputStream inputStream = restUtils.getFileInputStream(imageUrl);) {
                                        //替换原来的url
                                        imageUrlMap.put("url", "data:image/png;base64," + FileUtils.InputStreamToBase64(inputStream));
                                    } catch (Exception e) {
                                        log.error("下载图片失败", e);
                                    }
                                }else if (!model.contains("moonshot") && isBase64){
                                    //如果不是moonshot模型，去掉所有base64格式的图像
                                    iterator.remove();
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!visionEnable) {
            //如果模型不支持图像输入，要把图像输入去掉，仅保留文本
            chatContentList.forEach(chatGPTContent -> {
                if (chatGPTContent.getContent() instanceof List<?> contentList) {
                    contentList.removeIf(content -> content instanceof Map imageMap && imageMap.get("type").equals("image_url"));
                }
            });
        }

        int nowRetryNum = retryNum;
        while (nowRetryNum > 0) {
            try {
                //发送请求
                JSONObject chatGPTResponse = restUtils.post(chatGPTUrl, httpHeaders, new ChatGPTRequest(model, chatContentList), JSONObject.class);
                //返回chatGPT回复
                return chatGPTResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } catch (Exception e) {
                log.error("chatGPT请求失败，剩余重试次数：" + nowRetryNum, e);
            } finally {
                nowRetryNum--;
            }
        }
        return "";
    }
}
