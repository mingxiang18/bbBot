package com.bb.bot.common.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 断言 {@link ConfigKeys} 常量值与历史硬编码字面量逐一相等，防止重构引入拼写漂移。
 *
 * @author ren
 */
class ConfigKeysTest {

    @Test
    void constantValuesMatchLegacyLiterals() {
        assertThat(ConfigKeys.NSO_TYPE).isEqualTo("NSO");
        assertThat(ConfigKeys.SESSION_TOKEN).isEqualTo("session_token");
        assertThat(ConfigKeys.AUTO_UPLOAD).isEqualTo("autoUploadRecords");
        assertThat(ConfigKeys.WEB_ACCESS_TOKEN).isEqualTo("webAccessToken");
        assertThat(ConfigKeys.USER_INFO).isEqualTo("userInfo");
        assertThat(ConfigKeys.CORAL_USER_ID).isEqualTo("coralUserId");
        assertThat(ConfigKeys.NSO_APP_VERSION).isEqualTo("nsoAppVersion");
        assertThat(ConfigKeys.DATA_USER).isEqualTo("dataUser");
    }
}
