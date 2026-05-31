package com.bb.bot.handler.news.fetch;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NewsFetcherImpl#decodeXml(byte[])} 字符集解码单测。
 *
 * <p>覆盖真实 bug：RSS 字节按错误字符集解码导致中文乱码。</p>
 */
class NewsFetcherDecodeTest {

    @Test
    void decodeXml_utf8Declaration_decodesChinese() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss><item>"
                + "<title>俄外交部召回驻亚美尼亚大使</title></item></rss>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);

        String decoded = NewsFetcherImpl.decodeXml(bytes);

        assertThat(decoded).contains("俄外交部召回驻亚美尼亚大使");
    }

    @Test
    void decodeXml_gbkDeclaration_decodesChinese() {
        String xml = "<?xml version=\"1.0\" encoding=\"GBK\"?><rss><item>"
                + "<title>中文标题测试</title></item></rss>";
        byte[] bytes = xml.getBytes(Charset.forName("GBK"));

        String decoded = NewsFetcherImpl.decodeXml(bytes);

        assertThat(decoded).contains("中文标题测试");
    }

    @Test
    void decodeXml_noDeclaration_defaultsUtf8() {
        String xml = "<rss><item><title>无声明的中文</title></item></rss>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);

        assertThat(NewsFetcherImpl.decodeXml(bytes)).contains("无声明的中文");
    }

    @Test
    void decodeXml_invalidCharsetName_fallsBackToUtf8() {
        String xml = "<?xml version=\"1.0\" encoding=\"not-a-charset\"?><rss>中文</rss>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);

        assertThat(NewsFetcherImpl.decodeXml(bytes)).contains("中文");
    }

    @Test
    void decodeXml_emptyOrNull_returnsEmpty() {
        assertThat(NewsFetcherImpl.decodeXml(null)).isEmpty();
        assertThat(NewsFetcherImpl.decodeXml(new byte[0])).isEmpty();
    }
}
