package com.bb.bot.dispatcher;

import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbReceiveMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * T10.1：验证 REGEX 规则预编译缓存。
 * <p>覆盖：
 * <ul>
 *   <li>MATCH / FUZZY / REGEX 三类规则匹配结果与重构前一致；</li>
 *   <li>同一正则关键字多次匹配只会编译一次（编译计数 + Pattern 实例复用双重验证）。</li>
 * </ul>
 * <p>由于 {@link BbEventDispatcher} 构造函数依赖 Spring 容器（{@code SpringUtils.getBeansWithAnnotation}），
 * 这里用 Mockito 在不调用构造函数的前提下创建实例，并仅对被测的 {@code messageRuleMatch} 调真实方法。
 */
class BbEventDispatcherRegexCacheTest {

    /**
     * 承载各类型规则注解的样例 handler，方法仅用于通过反射拿到真实的 {@link Rule} 实例。
     */
    @SuppressWarnings("unused")
    static class SampleHandler {
        @Rule(name = "match", ruleType = RuleType.MATCH, keyword = {"菜单", "帮助"})
        void matchRule() {
        }

        @Rule(name = "fuzzy", ruleType = RuleType.FUZZY, keyword = {"天气"})
        void fuzzyRule() {
        }

        @Rule(name = "regex", ruleType = RuleType.REGEX, keyword = {"^打工记录(\\d+)?$"})
        void regexRule() {
        }

        @Rule(name = "noKeyword", ruleType = RuleType.MATCH)
        void noKeywordRule() {
        }
    }

    private static Rule ruleOf(String methodName) {
        for (Method m : SampleHandler.class.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                Rule r = m.getAnnotation(Rule.class);
                if (r != null) {
                    return r;
                }
            }
        }
        throw new IllegalStateException("找不到方法上的 Rule 注解: " + methodName);
    }

    /**
     * 创建一个不走 Spring 构造函数的 dispatcher，并注入一个全新的正则缓存。
     */
    private BbEventDispatcher newDispatcher(BbEventDispatcher.RegexPatternCache cache) {
        BbEventDispatcher dispatcher = mock(BbEventDispatcher.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(dispatcher, "regexPatternCache", cache);
        return dispatcher;
    }

    private BbReceiveMessage privateMessage(String text) {
        BbReceiveMessage msg = new BbReceiveMessage();
        msg.setMessageType(MessageType.PRIVATE);
        msg.setMessage(text);
        return msg;
    }

    @Test
    void match_rule_results_unchanged() {
        BbEventDispatcher dispatcher = newDispatcher(new BbEventDispatcher.RegexPatternCache());
        Rule rule = ruleOf("matchRule");

        assertThat(dispatcher.messageRuleMatch(privateMessage("菜单"), rule)).isTrue();
        assertThat(dispatcher.messageRuleMatch(privateMessage("帮助"), rule)).isTrue();
        // 完全匹配：包含但不等于不命中
        assertThat(dispatcher.messageRuleMatch(privateMessage("菜单一下"), rule)).isFalse();
        assertThat(dispatcher.messageRuleMatch(privateMessage("其它"), rule)).isFalse();
    }

    @Test
    void fuzzy_rule_results_unchanged() {
        BbEventDispatcher dispatcher = newDispatcher(new BbEventDispatcher.RegexPatternCache());
        Rule rule = ruleOf("fuzzyRule");

        assertThat(dispatcher.messageRuleMatch(privateMessage("今天天气怎么样"), rule)).isTrue();
        assertThat(dispatcher.messageRuleMatch(privateMessage("天气"), rule)).isTrue();
        assertThat(dispatcher.messageRuleMatch(privateMessage("吃饭了吗"), rule)).isFalse();
    }

    @Test
    void regex_rule_results_unchanged() {
        BbEventDispatcher dispatcher = newDispatcher(new BbEventDispatcher.RegexPatternCache());
        Rule rule = ruleOf("regexRule");

        assertThat(dispatcher.messageRuleMatch(privateMessage("打工记录"), rule)).isTrue();
        assertThat(dispatcher.messageRuleMatch(privateMessage("打工记录3"), rule)).isTrue();
        assertThat(dispatcher.messageRuleMatch(privateMessage("打工记录abc"), rule)).isFalse();
        assertThat(dispatcher.messageRuleMatch(privateMessage("我要打工记录"), rule)).isFalse();
    }

    @Test
    void no_keyword_rule_matches_all() {
        BbEventDispatcher dispatcher = newDispatcher(new BbEventDispatcher.RegexPatternCache());
        Rule rule = ruleOf("noKeywordRule");

        assertThat(dispatcher.messageRuleMatch(privateMessage("任意内容"), rule)).isTrue();
        assertThat(dispatcher.messageRuleMatch(privateMessage(""), rule)).isTrue();
    }

    @Test
    void null_message_does_not_match_keyword_rule() {
        BbEventDispatcher dispatcher = newDispatcher(new BbEventDispatcher.RegexPatternCache());
        Rule rule = ruleOf("regexRule");

        assertThat(dispatcher.messageRuleMatch(privateMessage(null), rule)).isFalse();
    }

    @Test
    void same_regex_compiled_only_once_across_many_matches() {
        BbEventDispatcher.RegexPatternCache cache = new BbEventDispatcher.RegexPatternCache();
        BbEventDispatcher dispatcher = newDispatcher(cache);
        Rule rule = ruleOf("regexRule");

        for (int i = 0; i < 50; i++) {
            dispatcher.messageRuleMatch(privateMessage("打工记录" + i), rule);
        }

        assertThat(cache.getCompileCount())
                .as("同一正则关键字在多次匹配中只应编译一次")
                .isEqualTo(1);
    }

    @Test
    void cache_returns_same_pattern_instance() {
        BbEventDispatcher.RegexPatternCache cache = new BbEventDispatcher.RegexPatternCache();

        Pattern first = cache.compile("^打工记录(\\d+)?$");
        Pattern second = cache.compile("^打工记录(\\d+)?$");

        assertThat(second).isSameAs(first);
        assertThat(cache.getCompileCount()).isEqualTo(1);
    }

    @Test
    void distinct_keywords_compiled_separately() {
        BbEventDispatcher.RegexPatternCache cache = new BbEventDispatcher.RegexPatternCache();

        cache.compile("a+");
        cache.compile("b+");
        cache.compile("a+");

        assertThat(cache.getCompileCount()).isEqualTo(2);
    }
}
