package com.bb.bot.connection.qq;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T8.1：断言 QqOpcode / QqIntent 枚举值与重构前的裸字面量逐一相等，
 * 保证常量化为等价重构（opcode 10/0/11/7；intents 1<<30/25/12 及其组合）。
 */
class QqOpcodeIntentTest {

    @Test
    void opcodeValuesMatchLegacyLiterals() {
        assertThat(QqOpcode.HELLO.code()).isEqualTo(10);
        assertThat(QqOpcode.DISPATCH.code()).isEqualTo(0);
        assertThat(QqOpcode.HEARTBEAT_ACK.code()).isEqualTo(11);
        assertThat(QqOpcode.RECONNECT.code()).isEqualTo(7);
    }

    @Test
    void opcodeMatchesReflectsCode() {
        assertThat(QqOpcode.HELLO.matches(10)).isTrue();
        assertThat(QqOpcode.HELLO.matches(0)).isFalse();
        assertThat(QqOpcode.DISPATCH.matches(0)).isTrue();
        assertThat(QqOpcode.HEARTBEAT_ACK.matches(11)).isTrue();
        assertThat(QqOpcode.RECONNECT.matches(7)).isTrue();
    }

    @Test
    void intentValuesMatchLegacyLiterals() {
        assertThat(QqIntent.CHANNEL_AT_MESSAGE.value()).isEqualTo(1 << 30);
        assertThat(QqIntent.GROUP_AND_C2C_EVENT.value()).isEqualTo(1 << 25);
        assertThat(QqIntent.DIRECT_MESSAGE.value()).isEqualTo(1 << 12);
    }

    @Test
    void combinedIntentsEqualLegacyBitwiseOr() {
        int legacy = 1 << 30 | 1 << 25 | 1 << 12;
        int combined = QqIntent.combine(
                QqIntent.CHANNEL_AT_MESSAGE, QqIntent.GROUP_AND_C2C_EVENT, QqIntent.DIRECT_MESSAGE);
        assertThat(combined).isEqualTo(legacy);
    }

    @Test
    void combineWithNoArgsIsZero() {
        assertThat(QqIntent.combine()).isZero();
    }
}
