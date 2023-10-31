# bbBot

基于OneBot11协议，使用Java语言开发的个人自用misubb机器人(开发中)

### 模块划分

- bb-onebot-sdk sdk模块（提供接入oneBot规范实现端的各种事件和api）
- bb-onebot-server 机器人服务模块

### 环境相关
- 开发语言：Java
- 开发环境：JDK1.8
- 基本框架：SpringBoot、Mybatis
- 数据库：MySQL8
- 机器人协议：OneBot11
- 机器人实现端：go-cqhttp

### 实现功能
- 每日抽签
- Splatoon3地图、活动、祭典获取
- chatGPT聊天回复
- 日语学习相关
- 戳一戳回复
- 聊天消息记录

### 路径规范
- 绝对路径/从根目录定义的路径/URL，最后必须带/，如：/home/app/files/，http://127.0.0.1:8082/
- 相对路径，统一不带/，如：avatar/admin.png