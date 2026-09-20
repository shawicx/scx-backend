-- 收件箱测试跨用例清理：InboxServiceTest 三个用例共享同一 Spring 上下文 / H2 库
-- 且以 Propagation.NOT_SUPPORTED 真实提交，断言均为全局计数（列表 size / 未读分桶 /
-- 管理端 total），任一执行顺序下都会互相污染，故每个用例开始前清空消息表。
-- 渠道表保留（seedChannels 按 code 幂等）；users 由 users-fixture.sql 重建。
DELETE FROM user_notifications;
DELETE FROM notifications;
