package com.bb.bot.handler.splatoon;

import com.bb.bot.common.util.BbReplies;
import com.bb.bot.common.util.nso.SplatoonTokenManager;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.service.ISplatoonBattleRecordService;
import com.bb.bot.database.splatoon.service.ISplatoonBattleUserDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopRecordsService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopUserDetailService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.handler.splatoon.render.SplatoonHtmlRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link BbSplatoonUserHandler} 合并后的「打工记录 / 对战记录」主流程（T5.2）单测。
 *
 * <p>纯逻辑分支走 mock：区间解析与回复分支（无区间默认、显式区间、超上限、格式错误）、
 * 查无记录提示、成功渲染并以本地图回复。打工/对战两类型对称覆盖，文案与重构前逐一等价。
 *
 * <p>真实 NSO 账号拉取 + 渲染图比对（golden image）无法在无人环境跑，列入 manualVerify。
 */
class BbSplatoonUserHandlerRecordTest {

    private BbSplatoonUserHandler handler;
    private BbReplies bbReplies;
    private SplatoonTokenManager tokenManager;
    private ISplatoonCoopRecordsService coopRecordService;
    private ISplatoonCoopUserDetailService coopUserDetailService;
    private ISplatoonBattleRecordService battleRecordService;
    private ISplatoonBattleUserDetailService battleUserDetailService;
    private SplatoonHtmlRenderer htmlRenderer;

    @BeforeEach
    void setUp() throws Exception {
        handler = new BbSplatoonUserHandler();
        bbReplies = mock(BbReplies.class);
        tokenManager = mock(SplatoonTokenManager.class);
        coopRecordService = mock(ISplatoonCoopRecordsService.class);
        coopUserDetailService = mock(ISplatoonCoopUserDetailService.class);
        battleRecordService = mock(ISplatoonBattleRecordService.class);
        battleUserDetailService = mock(ISplatoonBattleUserDetailService.class);
        htmlRenderer = mock(SplatoonHtmlRenderer.class);

        inject("bbReplies", bbReplies);
        inject("splatoonTokenManager", tokenManager);
        inject("coopRecordService", coopRecordService);
        inject("coopUserDetailService", coopUserDetailService);
        inject("battleRecordService", battleRecordService);
        inject("battleUserDetailService", battleUserDetailService);
        inject("splatoonHtmlRenderer", htmlRenderer);
    }

    private void inject(String field, Object value) throws Exception {
        Field f = BbSplatoonUserHandler.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(handler, value);
    }

    private BbReceiveMessage msg(String text) {
        BbReceiveMessage m = new BbReceiveMessage();
        m.setUserId("u1");
        m.setMessage(text);
        return m;
    }

    private void bound() {
        when(tokenManager.isBound("u1")).thenReturn(true);
        when(tokenManager.getDataUsers("u1")).thenReturn(Collections.singletonList("0"));
    }

    // ---------- 绑定校验 ----------

    @Test
    void unboundUserGetsHintAndNoQuery() {
        when(tokenManager.isBound("u1")).thenReturn(false);
        handler.getCoopRecords(msg("打工记录"));
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(bbReplies).atText(any(), cap.capture());
        assertTrue(cap.getValue().contains("还没绑定喷喷账号"));
        verifyNoInteractions(coopRecordService);
    }

    // ---------- 格式错误分支 ----------

    @Test
    void coopFormatErrorReturnsHint() {
        bound();
        // 尾部多余文字使整体正则不匹配
        handler.getCoopRecords(msg("打工记录abc"));
        verify(bbReplies).atText(any(), eqText(RecordType.COOP.getFormatErrorHint()));
        verify(coopRecordService, never()).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    void battleFormatErrorReturnsHint() {
        bound();
        handler.getBattleRecords(msg("对战记录xyz"));
        verify(bbReplies).atText(any(), eqText(RecordType.BATTLE.getFormatErrorHint()));
        verify(battleRecordService, never()).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    // ---------- 超上限分支 ----------

    @Test
    void coopSpanExceededReturnsHint() {
        bound();
        handler.getCoopRecords(msg("打工记录1-21"));
        verify(bbReplies).atText(any(), eqText("查询记录超过20条了，太多啦"));
        verify(coopRecordService, never()).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    void battleSpanExceededReturnsHint() {
        bound();
        handler.getBattleRecords(msg("对战记录1-30"));
        verify(bbReplies).atText(any(), eqText("查询记录超过20条了，太多啦"));
        verify(battleRecordService, never()).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    // ---------- 查无记录分支 ----------

    @Test
    void coopEmptyRecordReturnsHint() {
        bound();
        when(coopRecordService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        handler.getCoopRecords(msg("打工记录"));
        verify(bbReplies).atText(any(), eqText(RecordType.COOP.getEmptyHint()));
        verifyNoInteractions(coopUserDetailService);
        verifyNoInteractions(htmlRenderer);
    }

    @Test
    void battleEmptyRecordReturnsHint() {
        bound();
        when(battleRecordService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        handler.getBattleRecords(msg("对战记录"));
        verify(bbReplies).atText(any(), eqText(RecordType.BATTLE.getEmptyHint()));
        verifyNoInteractions(battleUserDetailService);
        verifyNoInteractions(htmlRenderer);
    }

    // ---------- 成功：无区间默认 ----------

    @Test
    void coopSuccessSendsLocalImage() throws Exception {
        bound();
        SplatoonCoopRecord rec = new SplatoonCoopRecord();
        rec.setId(7L);
        when(coopRecordService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(rec));
        when(coopUserDetailService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        File img = File.createTempFile("coop", ".png");
        img.deleteOnExit();
        when(htmlRenderer.renderCoopList(any(), any())).thenReturn(img);

        handler.getCoopRecords(msg("打工记录"));

        verify(htmlRenderer).renderCoopList(any(), any());
        assertImageReply(img);
        // 成功路径不应触发任何文本提示
        verify(bbReplies, never()).atText(any(), anyString());
    }

    // ---------- 成功：显式区间（对战） ----------

    @Test
    void battleSuccessWithExplicitRangeSendsLocalImage() throws Exception {
        bound();
        SplatoonBattleRecord rec = new SplatoonBattleRecord();
        rec.setId(9L);
        when(battleRecordService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(rec));
        when(battleUserDetailService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        File img = File.createTempFile("battle", ".png");
        img.deleteOnExit();
        when(htmlRenderer.renderBattleList(any(), any())).thenReturn(img);

        handler.getBattleRecords(msg("对战记录2-4"));

        verify(htmlRenderer).renderBattleList(any(), any());
        assertImageReply(img);
        verify(bbReplies, never()).atText(any(), anyString());
    }

    // ---------- helpers ----------

    /** 断言 send 发出了 [@用户, 本地图(file)] 两段内容。 */
    @SuppressWarnings("unchecked")
    private void assertImageReply(File expectedFile) {
        ArgumentCaptor<List<BbMessageContent>> cap = ArgumentCaptor.forClass(List.class);
        verify(bbReplies).send(any(), cap.capture());
        List<BbMessageContent> contents = cap.getValue();
        assertEquals(2, contents.size());
        assertEquals("at", contents.get(0).getType());
        assertEquals("localImage", contents.get(1).getType());
        assertEquals(expectedFile, contents.get(1).getData());
    }

    /** 精确文本匹配器。 */
    private static String eqText(String expected) {
        return org.mockito.ArgumentMatchers.eq(expected);
    }
}
