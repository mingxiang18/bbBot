package com.bb.bot.common.util.aiChat.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次 LLM 主动发起的工具调用（function calling 协议）。
 *
 * <p>对应 OpenAI Chat Completion 的 tool_calls 数组单项：</p>
 * <pre>
 * {
 *   "id": "call_xxx",
 *   "type": "function",
 *   "function": { "name": "...", "arguments": "{\"k\":\"v\"}" }
 * }
 * </pre>
 *
 * <p>同时支持流式聚合：SSE 增量到来时，name / argumentsJson 用 append 方式拼接，
 * id / index 在第一个分片里给出。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** provider 给的调用 id，工具结果回灌时用 toolCallId 关联。 */
    private String id;

    /** 工具名（@AiTool.name）。 */
    private String name;

    /** 参数 JSON 字符串（function calling 协议要求字符串，不是嵌套 object）。 */
    private String argumentsJson;

    /** 在 tool_calls 数组里的位置，流式聚合时按 index 拼接。 */
    private Integer index;
}
