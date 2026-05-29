package com.bb.bot.common.constant;

/**
 * 用户配置（{@code UserConfigValue}）中使用的 type / keyName 常量。
 *
 * <p>集中管理散落在各 handler / schedule 中的配置键字面量，避免硬编码字符串拼写漂移。
 * 仅覆盖 {@code UserConfigValue} 的 type / keyName，<b>不含</b>任天堂 OAuth 等外部 API 的报文字段
 * （如 {@code NsoApiCaller} 中 token 接口的 {@code session_token} 请求/响应字段，那是外部协议契约，
 * 与内部配置键语义不同，不在此处收敛）。
 *
 * @author ren
 */
public interface ConfigKeys {

    /** UserConfigValue.type：NSO 相关配置统一归此类型。 */
    String NSO_TYPE = "NSO";

    /** keyName：NSO 登录码（session_token）。 */
    String SESSION_TOKEN = "session_token";

    /** keyName：自动上传喷喷记录开关（"1" 开 / "0" 关）。 */
    String AUTO_UPLOAD = "autoUploadRecords";

    /** keyName：webAccessToken。 */
    String WEB_ACCESS_TOKEN = "webAccessToken";

    /** keyName：用户账号信息（userInfo JSON）。 */
    String USER_INFO = "userInfo";

    /** keyName：coral 用户 id。 */
    String CORAL_USER_ID = "coralUserId";

    /** keyName：绑定的 Android NSO 实例(dataUser)，逗号分隔多账号。 */
    String DATA_USER = "dataUser";

    /** keyName：NSO app 版本号（用于覆盖默认版本）。 */
    String NSO_APP_VERSION = "nsoAppVersion";
}
