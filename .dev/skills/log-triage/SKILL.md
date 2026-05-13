---
name: log-triage
description: 当用户让你「分析日志 / 查日志里出错的地方 / log 里有啥异常」时按本指引执行。组合 list_dir + grep_search + file_read 三个原语完成。
---

# 日志分诊 (log-triage)

当用户要分析日志时，按下面流程：

## 步骤

1. **定位日志文件**。如果用户给了具体路径，直接 file_read 它；否则 list_dir 用户指定的目录（默认 `/tmp`），挑后缀 `.log` 的文件。

2. **抓异常行**。对每个候选日志文件用 grep_search，正则 `(ERROR|Exception|FATAL|panic)`，得到匹配的 file:line:text 列表。

3. **取上下文**。如果匹配少于 20 条，用 file_read 把文件读出来给用户看上下文；如果多于 20 条，仅返回 grep 的结果统计 + 前 10 条。

4. **总结**。中文 200 字内总结：
   - 最常出现的异常类型
   - 时间线（如果日志有时间戳）
   - 可能的根因（基于上下文判断）

## 边界

- 不要试图修复 bug，只做诊断
- 不要读 > 1MB 的日志文件，会被工具拒
- 不要请求 root 权限或动用 shell_exec
