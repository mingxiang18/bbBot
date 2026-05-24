package com.bb.bot.common.util.aiChat.prompt;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptRendererTest {

    @Test
    void replacesSinglePlaceholder() {
        String result = PromptRenderer.render("hi {name}", Map.of("name", "ren"));
        assertEquals("hi ren", result);
    }

    @Test
    void replacesMultiplePlaceholders() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("question", "Q?");
        vars.put("answer", "A!");
        String result = PromptRenderer.render("q={question}, a={answer}", vars);
        assertEquals("q=Q?, a=A!", result);
    }

    @Test
    void leavesUnknownPlaceholderIntact() {
        String result = PromptRenderer.render("hi {name}, {missing}", Map.of("name", "ren"));
        assertEquals("hi ren, {missing}", result);
    }

    @Test
    void nullValueRendersAsEmpty() {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", null);
        String result = PromptRenderer.render("hi {name}!", vars);
        assertEquals("hi !", result);
    }

    @Test
    void nullTemplateReturnsEmpty() {
        assertEquals("", PromptRenderer.render(null, Map.of("a", "1")));
    }

    @Test
    void nullVarsReturnsTemplateUnchanged() {
        assertEquals("hi {name}", PromptRenderer.render("hi {name}", null));
    }

    @Test
    void emptyVarsReturnsTemplateUnchanged() {
        assertEquals("hi {name}", PromptRenderer.render("hi {name}", Collections.emptyMap()));
    }
}
