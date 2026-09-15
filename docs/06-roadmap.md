# 06 路线图

## 阶段 0 — 文档

主路径、slot、领取顺序已对齐 [00-decisions.md](00-decisions.md)。编排细节后议。

## 阶段 1 — core 可单测

已落地：`taskx-common` 分布式锁 SPI（`DistributedLock` + `InMemoryLock`）、`taskx-core` 域模型、`NextFireCalculator`、`SlotHasher`、唯一键/CAS 仓储接口。初始化 SQL 见 [08-schema.md](08-schema.md)。

- Java 21。Task / Trigger / 下次时间（Redis TIME 秒，从现在算）。
- 通用锁接口在 `taskx-common`（可测假实现；生产 Redisson 放 `taskx-meta`）。
- 唯一键与 CAS 仓储接口。

## 阶段 2 — 单执行者

已落地：单 slot 拉取循环（core 可单测）、`taskx-meta` JDBC + Redisson、`taskx-executor` 启动抢归属。

- 表：执行者、slot 归属、Task 配置、执行记录。
- 一个 slot、每秒拉取、同步 ZADD、异步插库+线程池。
- PENDING 恢复、submit 失败 → FAILED。
- 停用 ZREM。
- 配置写入：未提交 MySQL + Redis + 抢 slot 锁。

## 阶段 3 — 多执行者

启动时他人占用 slot 会失败（`claim`）。人工迁 slot、全量重建已通过 Admin REST 提供（见 [09-api.md](09-api.md)）。仍待：多进程联调、改 N 的完整维护演练。

## 阶段 4 — 管理台

- Admin REST 已落地（无鉴权）。curl 见 [09-api.md](09-api.md)，本地联调步骤见 [10-testing.md](10-testing.md)。
- React 管理台：`taskx-admin-ui`（Vite，开发时代理 `/api`）。
- 人工处理滞留任务：`requeue` / `cancel`。

## 阶段 5 — 编排与扩展（后议）

- Start → 任务节点 → End。
- 之后再加分支等。HTTP/脚本 Handler、更细 misfire 策略等。
