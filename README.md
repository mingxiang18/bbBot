# bbBot

- 使用Java语言开发的个人自用misu-bb机器人(开发中)
- 目前支持（OneBot11协议、QQNT、QQ官方机器人），初步开发中

### 模块划分

- bb-bot-sdk sdk模块（与不同协议机器人建立连接，提供事件和具体可执行操作的api）
- bb-bot-server 机器人服务模块（机器人具体功能的实现）

### 环境相关
- 开发语言：Java
- 开发环境：JDK17
- 基本框架：SpringBoot3、Mybatis
- 数据库：MySQL8

### 实现功能
- 每日抽签（OneBot11、QQNT、QQ官方机器人）
- Splatoon3地图、活动、祭典获取（OneBot11、QQNT、QQ官方机器人）
- chatGPT聊天回复（OneBot11、QQNT）
- 日语学习相关（OneBot11）
- 戳一戳回复（OneBot11）
- 聊天消息记录（OneBot11、QQNT、QQ官方机器人）
