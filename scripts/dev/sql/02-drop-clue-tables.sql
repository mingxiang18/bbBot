-- 线索机制下线：手动在生产库执行一次，清除三张线索表。
--
-- 背景：群聊随机回复已不再依赖人工导入的「线索」，改为只靠概率门控 + 自动注入的
-- 长期记忆(ai_memory_item) + 短期上下文(ai_memory_event)。相关 Java 代码与 mapper 已删除，
-- 这三张表不再被任何代码读写。自动部署链路不含 DDL，故需手动执行本脚本（数据不可恢复）。
--
-- 按外键无关、先关联表后主表的顺序删除（本身无外键约束，顺序仅为习惯）。

DROP TABLE IF EXISTS ai_keyword_clue;
DROP TABLE IF EXISTS ai_keyword;
DROP TABLE IF EXISTS ai_clue;
