package com.bb.bot.handler.splatoon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link RecordType} 字段值断言：必须与重构前 {@code BbSplatoonUserHandler} 两个记录方法中的
 * 内联字面量逐一等价，保证后续 T5.2/T5.3 替换为枚举后对外文案不变。
 */
class RecordTypeTest {

    @Test
    void coopLiterals() {
        assertEquals("打工记录", RecordType.COOP.getKeyword());
        assertEquals("格式不正确，参考格式：【打工记录】、【打工记录2-11】",
                RecordType.COOP.getFormatErrorHint());
        assertEquals("还没有打工记录，先发【上传打工记录】或等自动上传跑过一轮",
                RecordType.COOP.getEmptyHint());
    }

    @Test
    void battleLiterals() {
        assertEquals("对战记录", RecordType.BATTLE.getKeyword());
        assertEquals("格式不正确，参考格式：【对战记录】、【对战记录2-11】",
                RecordType.BATTLE.getFormatErrorHint());
        assertEquals("还没有对战记录，先发【上传对战记录】或等自动上传跑过一轮",
                RecordType.BATTLE.getEmptyHint());
    }
}
