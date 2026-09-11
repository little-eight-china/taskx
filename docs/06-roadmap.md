# 06 路线图

## 阶段 0 — 文档

主路径、slot、领取顺序已对齐 [00-decisions.md](00-decisions.md)。编排细节后议。

## 阶段 1 — core 可单测

- Java 21。Job / Trigger / 下次时间（Redis TIME 秒，从现在算）。
- slot 锁接口（可测假 Redis）。
- 唯一键与 CAS 仓储接口。

## 阶段 2 — 单执行者

- 表：执行者、slot 归属、配置、任务行。
- 一个 slot、每秒拉取、同步 ZADD、异步插库+线程池。
- PENDING 恢复、submit 失败 → FAILED。
- 停用 ZREM。
- 配置写入：未提交 MySQL + Redis + 抢 slot 锁。

## 阶段 3 — 多执行者

- 多 slot 归属、启动冲突退出。
- 人工迁 slot。
- 全量重建接口（停机清空再灌入）。
- 改 N 的维护步骤。

## 阶段 4 — 管理台

- Admin REST + React。
- 人工执行滞留任务。
- 观测心跳、slot 占用。

## 阶段 5 — 编排与扩展（后议）

- Start → 任务节点 → End。
- 之后再加分支等。HTTP/脚本 Handler、更细 misfire 策略等。
