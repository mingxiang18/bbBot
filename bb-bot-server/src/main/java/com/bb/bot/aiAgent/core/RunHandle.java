package com.bb.bot.aiAgent.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 一次运行中的 agent 工具循环的句柄。承载 steering（中途打断 / 追加）消息队列。
 *
 * <p>用户在 agent 干活期间又发来消息时，不另起一轮，而是 {@link #offer} 进本句柄；
 * {@link ToolLoopExecutor} 在每步开始 / 停止决策点 drain 出来并入对话上下文。</p>
 *
 * <p>所有方法在 {@code this} 上同步：{@link #offer} 与 {@link #drainOrClose} 的
 * 「检查队列 + 置关闭位」原子完成，杜绝停止瞬间塞进来的消息丢失。</p>
 */
public class RunHandle {

    private final Deque<String> queue = new ArrayDeque<>();
    private boolean closed = false;

    /**
     * 把一条 steering 消息塞进运行中的 agent。
     *
     * @return true 表示已入队；false 表示该 run 已结束，调用方应另起新一轮
     */
    public synchronized boolean offer(String message) {
        if (closed) {
            return false;
        }
        queue.add(message);
        return true;
    }

    /** 取出并清空当前排队的 steering 消息（不关闭，运行中途调用）。 */
    public synchronized List<String> drain() {
        if (queue.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>(queue);
        queue.clear();
        return list;
    }

    /**
     * 停止决策点调用：队列空则关闭并返回空列表；非空则返回待处理消息且保持开启
     * （让循环继续，把这些消息当成新一轮用户输入）。
     */
    public synchronized List<String> drainOrClose() {
        if (queue.isEmpty()) {
            closed = true;
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>(queue);
        queue.clear();
        return list;
    }

    /**
     * run 退出时调用：关闭并返回残留消息（调用方负责补派，覆盖 maxSteps 退出等竞态窗口）。
     */
    public synchronized List<String> close() {
        closed = true;
        if (queue.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>(queue);
        queue.clear();
        return list;
    }
}
