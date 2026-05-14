package com.bb.bot.common.util.aiChat.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 给 LLM 看的工具描述（function calling 协议 tools 数组单项）。
 *
 * <p>对应 OpenAI Chat Completion 的：</p>
 * <pre>
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "...",
 *     "description": "...",
 *     "parameters": { JSON Schema object }
 *   }
 * }
 * </pre>
 *
 * <p>parameters 通常长这样：</p>
 * <pre>
 * { "type": "object",
 *   "properties": { "city": { "type": "string", "description": "..." } },
 *   "required": ["city"] }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    private String name;
    private String description;
    private Map<String, Object> parametersSchema;
}
