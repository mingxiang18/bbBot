package com.bb.bot.aiAgent.core;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 记录每个会话当前运行中的 agent 工具循环，支撑 steering（中途打断 / 追加）。
 *
 * <p>会话粒度 = {@code platform:groupId:userId}。用户在 agent 干活期间再发消息时，
 * 经 {@link #beginOrSteer} 并入运行中的 {@link RunHandle}，而不是另起一轮独立回复。</p>
 */
@Component
public class AgentRunRegistry {

    private final ConcurrentMap<String, RunHandle> running = new ConcurrentHashMap<>();

    public static String sessionKey(String platform, String groupId, String userId) {
        return (platform == null ? "" : platform) + ":"
                + (groupId == null ? "" : groupId) + ":"
                + (userId == null ? "" : userId);
    }

    /**
     * 原子地「并入已有 run」或「开启新 run」。
     *
     * @return 非 null：本调用方拿到新 run 的句柄，应执行工具循环；
     *         null：消息已并入运行中的 run（steering），本调用方不应另起回复
     */
    public RunHandle beginOrSteer(String key, String steeringMessage) {
        while (true) {
            RunHandle existing = running.get(key);
            if (existing != null) {
                if (existing.offer(steeringMessage)) {
                    return null;
                }
                // 该 run 已结束但尚未从表中摘除：清掉后重试
                running.remove(key, existing);
                continue;
            }
            RunHandle fresh = new RunHandle();
            if (running.putIfAbsent(key, fresh) == null) {
                return fresh;
            }
            // 与并发的 beginOrSteer 撞车：重试，会走到上面的 steering 分支
        }
    }

    /** run 结束后从表中摘除（仅当仍指向同一句柄）。 */
    public void end(String key, RunHandle handle) {
        running.remove(key, handle);
    }
}
