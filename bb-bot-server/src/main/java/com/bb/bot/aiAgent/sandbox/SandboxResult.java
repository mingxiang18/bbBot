package com.bb.bot.aiAgent.sandbox;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Duration;

@Data
@AllArgsConstructor
public class SandboxResult {
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final Duration duration;
    /** true 表示因 timeout 被沙箱强杀 */
    private final boolean timedOut;
}
