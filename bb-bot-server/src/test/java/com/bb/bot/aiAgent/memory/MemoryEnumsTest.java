package com.bb.bot.aiAgent.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryEnumsTest {

    @Test
    void type_parse_acceptsAliasesAndCase() {
        assertThat(MemoryType.parse("preference")).isEqualTo(MemoryType.PREFERENCE);
        assertThat(MemoryType.parse("feedback")).isEqualTo(MemoryType.PREFERENCE);
        assertThat(MemoryType.parse("USER")).isEqualTo(MemoryType.USER_PROFILE);
        assertThat(MemoryType.parse("project")).isEqualTo(MemoryType.PROJECT_STATE);
        assertThat(MemoryType.parse(" Inside-Joke ")).isEqualTo(MemoryType.INSIDE_JOKE);
        assertThat(MemoryType.parse("ephemeral")).isEqualTo(MemoryType.EPHEMERAL_EVENT);
        assertThat(MemoryType.parse("nonsense")).isNull();
        assertThat(MemoryType.parse(null)).isNull();
    }

    @Test
    void type_code_isLowercase() {
        assertThat(MemoryType.PROJECT_STATE.code()).isEqualTo("project_state");
    }

    @Test
    void scope_parse_and_predicates() {
        assertThat(MemoryScope.parse("user_in_group")).isEqualTo(MemoryScope.USER_IN_GROUP);
        assertThat(MemoryScope.parse("GROUP")).isEqualTo(MemoryScope.GROUP);
        assertThat(MemoryScope.parse("x")).isNull();

        assertThat(MemoryScope.USER.needsUser()).isTrue();
        assertThat(MemoryScope.USER.needsGroup()).isFalse();
        assertThat(MemoryScope.GROUP.needsGroup()).isTrue();
        assertThat(MemoryScope.GROUP.needsUser()).isFalse();
        assertThat(MemoryScope.USER_IN_GROUP.needsUser()).isTrue();
        assertThat(MemoryScope.USER_IN_GROUP.needsGroup()).isTrue();
        assertThat(MemoryScope.GLOBAL.needsUser()).isFalse();
        assertThat(MemoryScope.GLOBAL.needsGroup()).isFalse();
    }
}
