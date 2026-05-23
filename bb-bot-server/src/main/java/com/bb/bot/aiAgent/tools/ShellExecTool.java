package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.sandbox.SandboxResult;
import com.bb.bot.aiAgent.sandbox.SandboxRunner;
import com.bb.bot.aiAgent.sandbox.SandboxRunnerFactory;
import com.bb.bot.aiAgent.sandbox.SandboxSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 高危工具：在沙箱里跑一个 shell 命令。
 *
 * <p>设计选择：</p>
 * <ul>
 *   <li>已对所有用户开放（{@code requiresOwner=false} + ai_tool_policy 给 user 角色放行）；
 *       安全依赖沙箱本身：禁网 + 超时 + 隔离文件系统。若当前无可用沙箱后端（noop），
 *       工具直接返回 sandbox_unavailable 拒绝执行</li>
 *   <li>requires_sandbox=true 给 future-AiToolExecutor 拒绝裸机调用</li>
 *   <li>固定走 bash -c，避免参数注入路径分歧</li>
 *   <li>默认禁网络（spec.networkEnabled=false），AI 要走网络应用 http_fetch 工具</li>
 *   <li>15s 硬超时（沙箱再加一层）</li>
 *   <li>stdout 截断 8KB 回给 LLM</li>
 * </ul>
 */
@Slf4j
@Component
public class ShellExecTool {

    @Autowired
    private SandboxRunnerFactory factory;

    @AiTool(
            name = "shell_exec",
            description = "在隔离沙箱里执行一个 bash 命令。" +
                    "默认无网络、15s 超时、stdout 上限 8KB。" +
                    "工作目录就是你的文件空间：用户上传的文件、你写的产物都在这里（cwd 下可直接读写，" +
                    "产物写到当前目录即可被 file_read 读到 / 用 send_file 回传给用户）。" +
                    "已预装 python3 及大量库，直接 import 即可（按 import 名）：" +
                    "Office=openpyxl/docx(python-docx)/pptx(python-pptx)/xlsxwriter/xlrd/xlwt，" +
                    "PDF=pypdf/pdfplumber/reportlab/fitz(PyMuPDF)，" +
                    "数据=pandas/numpy/scipy/matplotlib，图片=PIL(Pillow)/qrcode，" +
                    "解析=bs4/lxml/markdown/html2text/yaml(PyYAML)/tabulate/chardet/charset_normalizer/jinja2，" +
                    "其它=cryptography/orjson/regex/sympy/dateutil/pytz。" +
                    "沙箱禁网，不要 pip install 或联网下载（会失败）；缺的库就用已装的实现。" +
                    "如果生成了图片，先写成当前目录下的 png/jpg/webp/gif 文件，再调用 send_image 发送；" +
                    "不要把沙箱文件路径当网络图片 URL 回复给用户。" +
                    "用于：跑脚本、处理文件、运行小工具。绝不要用于持续运行的服务。",
            requiresOwner = false,
            requiresSandbox = true
    )
    public Map<String, Object> exec(
            @AiToolParam(name = "command", description = "要执行的 bash 命令（单条，参数空格分隔）")
            String command
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        SandboxRunner runner = factory.current();
        result.put("backend", runner.backendName());
        if ("noop".equals(runner.backendName())) {
            result.put("error", "sandbox_unavailable");
            return result;
        }
        SandboxSpec spec = SandboxSpec.builder()
                .networkEnabled(false)
                .timeout(Duration.ofSeconds(15))
                .build();
        SandboxResult sr = runner.run(spec, new String[]{"bash", "-c", command}, Path.of("/tmp"));
        result.put("exitCode", sr.getExitCode());
        result.put("durationMs", sr.getDuration().toMillis());
        result.put("timedOut", sr.isTimedOut());
        result.put("stdout", cap(sr.getStdout(), 8192));
        result.put("stderr", cap(sr.getStderr(), 4096));
        return result;
    }

    private String cap(String s, int limit) {
        if (s == null) return "";
        if (s.length() <= limit) return s;
        return s.substring(0, limit) + "...[truncated]";
    }
}
