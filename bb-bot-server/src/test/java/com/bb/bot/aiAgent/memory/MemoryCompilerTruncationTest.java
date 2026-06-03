package com.bb.bot.aiAgent.memory;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCompilerTruncationTest {

    private MemoryCompiler compiler(int maxChars, int maxBytes) {
        MemoryCompiler c = new MemoryCompiler();
        ReflectionTestUtils.setField(c, "memoryMdMaxChars", maxChars);
        ReflectionTestUtils.setField(c, "memoryMdMaxBytes", maxBytes);
        return c;
    }

    @Test
    void underBothLimits_unchanged() {
        MemoryCompiler c = compiler(6000, 25000);
        String s = "短文本";
        assertThat(c.truncateByCharsAndBytes(s)).isEqualTo(s);
    }

    @Test
    void overCharLimit_truncatedWithNotice() {
        MemoryCompiler c = compiler(10, 25000);
        String out = c.truncateByCharsAndBytes("0123456789ABCDEFG");
        assertThat(out).startsWith("0123456789");
        assertThat(out).contains("截断");
    }

    @Test
    void overByteLimit_truncatesOnCharBoundary_validUtf8() {
        // 字符数没超(maxChars 大)，但 CJK 字节超(每个汉字 3 字节)
        MemoryCompiler c = compiler(10000, 30);
        String cjk = "记忆机制重构验证字节截断不切坏多字节字符";
        String out = c.truncateByCharsAndBytes(cjk);
        assertThat(out).contains("截断");
        // 去掉提醒后的正文字节数应 <= 30，且能无损 UTF-8 往返（没切坏字符）
        String body = out.substring(0, out.indexOf("\n\n...("));
        assertThat(body.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(30);
        assertThat(new String(body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(body);
    }
}
