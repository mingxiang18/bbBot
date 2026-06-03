package com.bb.bot.handler.news.contract;

/**
 * 候选条目评估状态（Phase 2：候选生命周期）。
 *
 * <p>把"见过"与"评估过"分开，避免一旦入库就被永久视为已处理：</p>
 * <ul>
 *   <li>{@link #RAW}：已采集，仅表示见过；在时间窗内仍可被反复纳入候选评估。</li>
 *   <li>{@link #SELECTED}：被某天日报选中并展示。</li>
 *   <li>{@link #REJECTED}：明确拒绝（带 reject_reason），不再进入候选。</li>
 * </ul>
 */
public final class NewsReviewState {

    public static final String RAW = "RAW";
    public static final String SELECTED = "SELECTED";
    public static final String REJECTED = "REJECTED";

    private NewsReviewState() {
    }
}
