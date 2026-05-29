package com.bb.bot.aiAgent.tools;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bb.bot.common.util.nso.SplatoonTokenManager;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.service.ISplatoonBattleRecordService;
import com.bb.bot.database.splatoon.service.ISplatoonBattleUserDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopEnemyDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopRecordsService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopUserDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopWaveDetailService;
import com.bb.bot.handler.splatoon.BbSplatoonUserHandler;
import com.bb.bot.handler.splatoon.render.SplatoonHtmlRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SplatoonRecordTool} 合并后的 recordList / recordDetail 主流程（T5.3）单测。
 *
 * <p>纯逻辑分支走 mock：权限分支（未绑定 not_bound / 无会话 no_active_conversation /
 * 客户端不支持图片）、刷新分支（refresh=true 时按 type 调对应同步、失败降级 refreshWarn）、
 * type=COOP/BATTLE 下查询条件构造（捕获 {@link LambdaQueryWrapper} 断言绑定的筛选值：
 * judgement / vsModeId / resultWave / *Scale / bossName）。打工/对战对称覆盖，
 * 行为与重构前逐一等价。
 *
 * <p>真实 NSO 账号拉取 + 渲染图比对（golden image）无法在无人环境跑，列入 manualVerify。
 */
class SplatoonRecordToolMainFlowTest {

    private SplatoonRecordTool tool;
    private SplatoonTokenManager tokenManager;
    private ISplatoonCoopRecordsService coopRecordService;
    private ISplatoonCoopUserDetailService coopUserDetailService;
    private ISplatoonBattleRecordService battleRecordService;
    private ISplatoonBattleUserDetailService battleUserDetailService;
    private ISplatoonCoopWaveDetailService coopWaveDetailService;
    private ISplatoonCoopEnemyDetailService coopEnemyDetailService;
    private SplatoonHtmlRenderer htmlRenderer;
    private BbSplatoonUserHandler bbSplatoonUserHandler;
    private AgentReplySink sink;

    /**
     * 无 Spring 容器下手动初始化 mybatis-plus 的 TableInfo / lambda 列缓存，
     * 否则 {@link LambdaQueryWrapper#getParamNameValuePairs()} 取不到绑定值、
     * {@code getCustomSqlSegment()} 会抛 "can not find lambda cache"。
     */
    @BeforeAll
    static void initLambdaCache() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SplatoonBattleRecord.class);
        TableInfoHelper.initTableInfo(assistant, SplatoonCoopRecord.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        tool = new SplatoonRecordTool();
        tokenManager = mock(SplatoonTokenManager.class);
        coopRecordService = mock(ISplatoonCoopRecordsService.class);
        coopUserDetailService = mock(ISplatoonCoopUserDetailService.class);
        battleRecordService = mock(ISplatoonBattleRecordService.class);
        battleUserDetailService = mock(ISplatoonBattleUserDetailService.class);
        coopWaveDetailService = mock(ISplatoonCoopWaveDetailService.class);
        coopEnemyDetailService = mock(ISplatoonCoopEnemyDetailService.class);
        htmlRenderer = mock(SplatoonHtmlRenderer.class);
        bbSplatoonUserHandler = mock(BbSplatoonUserHandler.class);

        inject("splatoonTokenManager", tokenManager);
        inject("coopRecordService", coopRecordService);
        inject("coopUserDetailService", coopUserDetailService);
        inject("battleRecordService", battleRecordService);
        inject("battleUserDetailService", battleUserDetailService);
        inject("coopWaveDetailService", coopWaveDetailService);
        inject("coopEnemyDetailService", coopEnemyDetailService);
        inject("splatoonHtmlRenderer", htmlRenderer);
        inject("bbSplatoonUserHandler", bbSplatoonUserHandler);

        sink = mock(AgentReplySink.class);
        when(sink.imageSupported()).thenReturn(true);

        MemoryToolContext.setUserId("u1");
        AgentReplyContext.set(sink);
    }

    @AfterEach
    void tearDown() {
        MemoryToolContext.clear();
        AgentReplyContext.clear();
    }

    private void inject(String field, Object value) throws Exception {
        Field f = SplatoonRecordTool.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(tool, value);
    }

    private void bound() {
        when(tokenManager.isBound("u1")).thenReturn(true);
        when(tokenManager.getDataUsers("u1")).thenReturn(Collections.singletonList("0"));
        // accountId 静态 → accountIds = [nso-0]
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<SplatoonBattleRecord> captureBattleWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<SplatoonBattleRecord>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(battleRecordService).list(cap.capture());
        return cap.getValue();
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<SplatoonCoopRecord> captureCoopWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<SplatoonCoopRecord>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(coopRecordService).list(cap.capture());
        return cap.getValue();
    }

    /**
     * wrapper 绑定的全部参数值。mybatis-plus 仅在生成 SQL 段时才把 .eq/.gt/.in 的值
     * 写入 paramNameValuePairs，故先触发 getCustomSqlSegment() 再读取。
     */
    private Collection<Object> boundValues(LambdaQueryWrapper<?> wrapper) {
        wrapper.getCustomSqlSegment();
        return wrapper.getParamNameValuePairs().values();
    }

    // ===================== 权限分支 =====================

    @Test
    void unbound_returns_not_bound_and_no_query() {
        when(tokenManager.isBound("u1")).thenReturn(false);
        Map<String, Object> r = tool.recordList("battle", null, null, null, null, null, null);
        assertThat(r).containsEntry("error", "not_bound");
        verifyNoInteractions(battleRecordService);
    }

    @Test
    void noActiveConversation_returns_error() {
        AgentReplyContext.clear();
        Map<String, Object> r = tool.recordList("battle", null, null, null, null, null, null);
        assertThat(r).containsEntry("error", "no_active_conversation");
        verifyNoInteractions(battleRecordService);
    }

    @Test
    void clientNoImageCapability_returns_error() {
        when(sink.imageSupported()).thenReturn(false);
        Map<String, Object> r = tool.recordList("coop", null, null, null, null, null, null);
        assertThat(r).containsEntry("error", "client_no_image_capability");
        verifyNoInteractions(coopRecordService);
    }

    // ===================== 刷新分支 =====================

    @Test
    void refresh_battle_calls_syncBattleRecords() {
        bound();
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        Map<String, Object> r = tool.recordList("battle", null, null, null, null, null, Boolean.TRUE);
        verify(bbSplatoonUserHandler).syncBattleRecords("u1");
        verify(bbSplatoonUserHandler, never()).syncCoopRecords(anyString());
        assertThat(r).containsEntry("refreshed", true);
    }

    @Test
    void refresh_coop_calls_syncCoopRecords() {
        bound();
        when(coopRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        Map<String, Object> r = tool.recordList("coop", null, null, null, null, null, Boolean.TRUE);
        verify(bbSplatoonUserHandler).syncCoopRecords("u1");
        verify(bbSplatoonUserHandler, never()).syncBattleRecords(anyString());
        assertThat(r).containsEntry("refreshed", true);
    }

    @Test
    void refresh_failure_degrades_to_refreshWarn() {
        bound();
        org.mockito.Mockito.doThrow(new RuntimeException("nso down"))
                .when(bbSplatoonUserHandler).syncBattleRecords("u1");
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        Map<String, Object> r = tool.recordList("battle", null, null, null, null, null, Boolean.TRUE);
        assertThat(r).doesNotContainKey("refreshed");
        assertThat((String) r.get("refreshWarn")).contains("nso down");
    }

    @Test
    void noRefresh_does_not_sync() {
        bound();
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordList("battle", null, null, null, null, null, null);
        verifyNoInteractions(bbSplatoonUserHandler);
    }

    // ===================== 查询条件构造: 对战 =====================

    @Test
    void recordList_battle_noFilter_only_accountIds_and_limit() {
        bound();
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordList("battle", null, null, null, null, null, null);
        LambdaQueryWrapper<SplatoonBattleRecord> w = captureBattleWrapper();
        // 仅 in(userId, [nso-0]) 绑定一个参数值；无 mode/result 过滤
        assertThat(boundValues(w)).containsExactly("nso-0");
        assertThat(w.getSqlSelect()).isNull();
    }

    @Test
    void recordList_battle_with_mode_and_result_binds_modeIds_and_judgement() {
        bound();
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordList("battle", null, "真格", "胜", null, null, null);
        LambdaQueryWrapper<SplatoonBattleRecord> w = captureBattleWrapper();
        Collection<Object> vals = boundValues(w);
        // accountId + 两个 modeId(真格=open+challenge) + judgement
        assertThat(vals).contains("nso-0",
                SplatoonRecordTool.SplatoonModes.ANARCHY_OPEN,
                SplatoonRecordTool.SplatoonModes.ANARCHY_CHALLENGE,
                "WIN");
    }

    @Test
    void recordList_battle_count_clamped_into_limit_segment() {
        bound();
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordList("battle", 50, null, null, null, null, null); // count>10 → clamp 10
        LambdaQueryWrapper<SplatoonBattleRecord> w = captureBattleWrapper();
        assertThat(w.getCustomSqlSegment()).contains("limit 10");
    }

    // ===================== 查询条件构造: 打工 =====================

    @Test
    void recordList_coop_noFilter_only_accountIds() {
        bound();
        when(coopRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordList("coop", null, null, null, null, null, null);
        LambdaQueryWrapper<SplatoonCoopRecord> w = captureCoopWrapper();
        assertThat(boundValues(w)).containsExactly("nso-0");
    }

    @Test
    void recordList_coop_clear_binds_resultWave_zero() {
        bound();
        when(coopRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordList("coop", null, null, "通关", null, null, null);
        LambdaQueryWrapper<SplatoonCoopRecord> w = captureCoopWrapper();
        // clear=TRUE → eq(resultWave, 0)
        assertThat(boundValues(w)).contains("nso-0", 0);
    }

    @Test
    void recordList_coop_scale_gold_binds_threshold() {
        bound();
        when(coopRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordList("coop", null, null, null, null, "金", null);
        LambdaQueryWrapper<SplatoonCoopRecord> w = captureCoopWrapper();
        // gold → gt(goldScale, 0)
        assertThat(boundValues(w)).contains("nso-0", 0);
        assertThat(w.getCustomSqlSegment()).contains("gold_scale");
    }

    @Test
    void recordList_coop_bossOnly_adds_isNotNull_bossName_without_param() {
        bound();
        when(coopRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordList("coop", null, null, null, Boolean.TRUE, null, null);
        LambdaQueryWrapper<SplatoonCoopRecord> w = captureCoopWrapper();
        // isNotNull 不绑定参数值,仅出现在 SQL 段
        assertThat(boundValues(w)).containsExactly("nso-0");
        assertThat(w.getCustomSqlSegment()).contains("boss_name IS NOT NULL");
    }

    // ===================== 详情: limit 200 + index/time =====================

    @Test
    void recordDetail_battle_uses_limit_200() {
        bound();
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        tool.recordDetail("battle", null, null, null, null, null, null, null);
        LambdaQueryWrapper<SplatoonBattleRecord> w = captureBattleWrapper();
        assertThat(w.getCustomSqlSegment()).contains("limit 200");
    }

    @Test
    void recordDetail_coop_success_renders_and_returns_recordId() throws Exception {
        bound();
        SplatoonCoopRecord rec = new SplatoonCoopRecord();
        rec.setId(42L);
        when(coopRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(rec));
        when(coopUserDetailService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(coopWaveDetailService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(coopEnemyDetailService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        File img = File.createTempFile("coop", ".png");
        img.deleteOnExit();
        when(htmlRenderer.renderCoopDetail(any(), any(), any(), any())).thenReturn(img);

        Map<String, Object> r = tool.recordDetail("coop", 1, null, null, null, null, null, null);

        verify(sink).sendImage(img);
        assertThat(r).containsEntry("ok", true).containsEntry("recordId", 42L);
    }

    // ===================== 成功路径: 列表 =====================

    @Test
    void recordList_battle_success_sends_image_and_ok() throws Exception {
        bound();
        SplatoonBattleRecord rec = new SplatoonBattleRecord();
        rec.setId(9L);
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(rec));
        when(battleUserDetailService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        File img = File.createTempFile("battle", ".png");
        img.deleteOnExit();
        when(htmlRenderer.renderBattleList(any(), any())).thenReturn(img);

        Map<String, Object> r = tool.recordList("battle", null, null, null, null, null, null);

        verify(sink).sendImage(img);
        assertThat(r).containsEntry("ok", true).containsEntry("count", 1);
    }

    @Test
    void recordList_emptyResult_returns_not_found() {
        bound();
        when(battleRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        Map<String, Object> r = tool.recordList("battle", null, null, null, null, null, null);
        assertThat(r).containsEntry("error", "not_found");
        verify(sink, never()).sendImage(any());
    }
}
