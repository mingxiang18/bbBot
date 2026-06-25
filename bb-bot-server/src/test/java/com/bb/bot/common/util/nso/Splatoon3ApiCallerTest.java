package com.bb.bot.common.util.nso;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class Splatoon3ApiCallerTest {

    @Test
    void genGraphqlBody_withMultipleVariables_keepsPersistedQueryAndVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", "season-1");
        variables.put("first", 10);
        variables.put("cursor", null);

        Map<String, Object> body = Splatoon3ApiCaller.genGraphqlBody("hash-1", variables);

        Map<String, Object> extensions = (Map<String, Object>) body.get("extensions");
        Map<String, Object> persistedQuery = (Map<String, Object>) extensions.get("persistedQuery");
        assertEquals("hash-1", persistedQuery.get("sha256Hash"));
        assertEquals(1, persistedQuery.get("version"));
        assertSame(variables, body.get("variables"));
    }

    @Test
    void genGraphqlBody_withoutVariable_omitsVariables() {
        Map<String, Object> body = Splatoon3ApiCaller.genGraphqlBody("hash-2", null, null);

        assertFalse(body.containsKey("variables"));
    }
}
