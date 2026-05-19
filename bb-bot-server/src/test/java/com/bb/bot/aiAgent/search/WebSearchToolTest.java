package com.bb.bot.aiAgent.search;

import com.bb.bot.aiAgent.tools.WebSearchTool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSearchTool 的编排逻辑测试：优先级排序 + 失败/空结果降级。
 * 用 fake provider，不触网。
 */
class WebSearchToolTest {

    /** 可配置行为的假 provider。 */
    static class FakeProvider implements WebSearchProvider {
        final String name;
        final int prio;
        final boolean available;
        final List<WebSearchResult> results;
        final boolean throwOnSearch;
        int calls = 0;

        FakeProvider(String name, int prio, boolean available,
                     List<WebSearchResult> results, boolean throwOnSearch) {
            this.name = name;
            this.prio = prio;
            this.available = available;
            this.results = results;
            this.throwOnSearch = throwOnSearch;
        }

        @Override public String name() { return name; }
        @Override public int priority() { return prio; }
        @Override public boolean isAvailable() { return available; }

        @Override
        public List<WebSearchResult> search(String query, int maxResults) throws Exception {
            calls++;
            if (throwOnSearch) {
                throw new RuntimeException("boom from " + name);
            }
            return results;
        }
    }

    private static FakeProvider ok(String name, int prio, String... titles) {
        List<WebSearchResult> rs = Arrays.stream(titles)
                .map(t -> WebSearchResult.of(t, "http://x/" + t, "snippet"))
                .toList();
        return new FakeProvider(name, prio, true, rs, false);
    }

    private static FakeProvider empty(String name, int prio) {
        return new FakeProvider(name, prio, true, new ArrayList<>(), false);
    }

    private static FakeProvider throwing(String name, int prio) {
        return new FakeProvider(name, prio, true, new ArrayList<>(), true);
    }

    private static FakeProvider unavailable(String name, int prio) {
        return new FakeProvider(name, prio, false, new ArrayList<>(), false);
    }

    private static WebSearchTool toolWith(WebSearchProvider... providers) {
        WebSearchTool tool = new WebSearchTool();
        ReflectionTestUtils.setField(tool, "providers", Arrays.asList(providers));
        return tool;
    }

    @Test
    void blankQuery_returnsError() {
        Map<String, Object> r = toolWith(ok("brave", 10, "a")).search("  ");
        assertEquals("empty_query", r.get("error"));
    }

    @Test
    void picksLowestPriorityNumberFirst() {
        // 注入顺序故意打乱，应按 priority 排序后取 brave(10)
        Map<String, Object> r = toolWith(ok("ddg", 100, "d"), ok("brave", 10, "b")).search("foo");
        assertEquals("brave", r.get("backend"));
        assertEquals(1, r.get("count"));
    }

    @Test
    void skipsUnavailableProvider() {
        // brave 优先级更高但未配置 key → 跳过，落到 tavily
        Map<String, Object> r = toolWith(unavailable("brave", 10), ok("tavily", 20, "t")).search("foo");
        assertEquals("tavily", r.get("backend"));
    }

    @Test
    void fallsThroughWhenHigherPriorityReturnsEmpty() {
        FakeProvider brave = empty("brave", 10);
        FakeProvider ddg = ok("ddg", 100, "result1");
        Map<String, Object> r = toolWith(brave, ddg).search("foo");
        assertEquals("ddg", r.get("backend"));
        assertEquals(1, r.get("count"));
        assertEquals(1, brave.calls, "brave 仍应被尝试过");
    }

    @Test
    void fallsThroughWhenHigherPriorityThrows() {
        FakeProvider brave = throwing("brave", 10);
        FakeProvider tavily = ok("tavily", 20, "ok");
        Map<String, Object> r = toolWith(brave, tavily).search("foo");
        assertEquals("tavily", r.get("backend"));
        assertEquals(1, brave.calls);
    }

    @Test
    void allBackendsEmpty_returnsZeroCountWithNote() {
        Map<String, Object> r = toolWith(empty("brave", 10), empty("ddg", 100)).search("foo");
        assertEquals(0, r.get("count"));
        assertEquals("all_backends_empty", r.get("note"));
        // lastBackend 为按优先级排序后的最后一个
        assertEquals("ddg", r.get("backend"));
    }

    @Test
    void noAvailableProvider_returnsError() {
        Map<String, Object> r = toolWith(unavailable("brave", 10), unavailable("tavily", 20)).search("foo");
        assertEquals("no_provider", r.get("error"));
    }

    @Test
    void tavilyContentFieldIsPassedThrough() {
        WebSearchResult withContent = new WebSearchResult("t", "http://t", "snip", "full body text");
        FakeProvider tavily = new FakeProvider("tavily", 20, true, List.of(withContent), false);
        Map<String, Object> r = toolWith(tavily).search("foo");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertEquals("full body text", results.get(0).get("content"));
    }

    @Test
    void plainResultHasNoContentField() {
        Map<String, Object> r = toolWith(ok("brave", 10, "a")).search("foo");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) r.get("results");
        assertNull(results.get(0).get("content"), "无正文的结果不应带 content 字段");
        assertTrue(results.get(0).containsKey("snippet"));
    }
}
