package com.bb.bot.aiAgent.tools;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T8.2：断言 {@link SplatoonRecordTool.SplatoonModes} 常量值 == 原 Base64 字面量,
 * 且 {@link SplatoonRecordTool#modeVsIds} 替换为常量后映射不变。
 */
class SplatoonModesTest {

    @Test
    void constants_equal_original_base64_literals() {
        assertThat(SplatoonRecordTool.SplatoonModes.TURF).isEqualTo("VnNNb2RlLTE=");
        assertThat(SplatoonRecordTool.SplatoonModes.ANARCHY_CHALLENGE).isEqualTo("VnNNb2RlLTI=");
        assertThat(SplatoonRecordTool.SplatoonModes.ANARCHY_OPEN).isEqualTo("VnNNb2RlLTUx");
        assertThat(SplatoonRecordTool.SplatoonModes.X).isEqualTo("VnNNb2RlLTM=");
        assertThat(SplatoonRecordTool.SplatoonModes.EVENT).isEqualTo("VnNNb2RlLTQ=");
        assertThat(SplatoonRecordTool.SplatoonModes.PRIVATE).isEqualTo("VnNNb2RlLTU=");
        assertThat(SplatoonRecordTool.SplatoonModes.FEST_OPEN).isEqualTo("VnNNb2RlLTY=");
        assertThat(SplatoonRecordTool.SplatoonModes.FEST_CHALLENGE).isEqualTo("VnNNb2RlLTg=");
    }

    @Test
    void modeVsIds_mapping_unchanged_after_constant_replacement() {
        // 真格/蛮颓/挑战/开放 → [开放, 挑战]
        assertThat(SplatoonRecordTool.modeVsIds("真格")).containsExactly("VnNNb2RlLTUx", "VnNNb2RlLTI=");
        assertThat(SplatoonRecordTool.modeVsIds("anarchy")).containsExactly("VnNNb2RlLTUx", "VnNNb2RlLTI=");
        // 占地/涂地/turf
        assertThat(SplatoonRecordTool.modeVsIds("占地")).containsExactly("VnNNb2RlLTE=");
        assertThat(SplatoonRecordTool.modeVsIds("turf")).containsExactly("VnNNb2RlLTE=");
        // X
        assertThat(SplatoonRecordTool.modeVsIds("x")).containsExactly("VnNNb2RlLTM=");
        // 活动
        assertThat(SplatoonRecordTool.modeVsIds("活动")).containsExactly("VnNNb2RlLTQ=");
        // 祭典 → [开放, 挑战]
        assertThat(SplatoonRecordTool.modeVsIds("祭典")).containsExactly("VnNNb2RlLTY=", "VnNNb2RlLTg=");
        // 私房
        assertThat(SplatoonRecordTool.modeVsIds("私房")).containsExactly("VnNNb2RlLTU=");
    }

    @Test
    void modeVsIds_blank_or_unknown_returns_empty() {
        List<String> empty = Collections.emptyList();
        assertThat(SplatoonRecordTool.modeVsIds(null)).isEqualTo(empty);
        assertThat(SplatoonRecordTool.modeVsIds("")).isEqualTo(empty);
        assertThat(SplatoonRecordTool.modeVsIds("   ")).isEqualTo(empty);
        assertThat(SplatoonRecordTool.modeVsIds("不存在的模式")).isEqualTo(empty);
    }
}
