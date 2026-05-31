package com.bb.bot.handler.news.contract;

import java.util.List;
import java.util.Map;

/**
 * 固定分类枚举（键）与展示标签映射。
 *
 * <p>分类「键」由本类统一定义，T3（AI 整理）用于约束 LLM 输出、T4（页面生成）用于
 * 渲染 Tab。两者共用，避免不一致。</p>
 */
public final class NewsCategory {

    private NewsCategory() {
    }

    public static final String AI = "AI";
    public static final String WORLD = "国际";
    public static final String POLITICS = "时政";
    public static final String FINANCE = "财经";
    public static final String APPLE = "苹果";
    public static final String GAME = "游戏";
    public static final String CAR = "汽车";
    public static final String TECH = "科技";

    /** 全部合法分类键，顺序即默认展示顺序。 */
    public static final List<String> ALL = List.of(
            AI, WORLD, POLITICS, FINANCE, APPLE, GAME, CAR, TECH
    );

    /** 分类键 → 展示标签（带 emoji）。 */
    public static final Map<String, String> LABELS = Map.of(
            AI, "🤖 AI",
            WORLD, "🌏 国际",
            POLITICS, "🏛 时政",
            FINANCE, "💰 财经",
            APPLE, "🍎 苹果",
            GAME, "🎮 游戏",
            CAR, "🚗 汽车",
            TECH, "🔬 科技"
    );

    /** 兜底分类：当 AI 给出非法分类或降级时归入此类。 */
    public static final String FALLBACK = TECH;

    /** 校验并归一化分类键，非法值返回 {@link #FALLBACK}。 */
    public static String normalize(String category) {
        return ALL.contains(category) ? category : FALLBACK;
    }
}
