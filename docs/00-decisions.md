# 00 讨论结论对照

本文是已拍板设计的单一事实来源。[02-architecture.md](02-architecture.md) 已按此改。编排实现细节后议。

## 产品

- 名称 **TaskX**（artifact `taskx`）。开源分布式调度平台。
- Java **21**，许可证 **MIT**。core 纯 Java。管理台 **React** + Admin REST。
- MySQL + Redis，合在模块 **`taskx-meta`**（不再拆 `taskx-storage-mysql` / `taskx-storage-redis`）。不引入 ZK。表结构见 [08-schema.md](08-schema.md)。

## 触发与任务行

- ZSET：score = 下次时间（**秒**），member = **调度配置 ID**。到期比较用 **Redis TIME**（换算成秒）。
- 约 **每秒** 拉一次。亚秒级第一版不做。
- fireTime = 领取时的 score；配置从 MySQL 读。下次时间 **从 Redis 当前时间** 往后算（本次仍执行，避免停机连环补跑）。
- `(taskId, fireTime)` 唯一。
- 执行权：`PENDING → RUNNING` CAS。允许同一 Task **重叠执行**。
- `RUNNING` 崩溃不自动重跑。`PENDING` 仅 **产生该行的执行者 ID** 可自动捞；执行者没了走人工接口。
- submit 失败：该次记 `FAILED`，下次 score 若已写过不必再写。
- 停用/删除：处理时 **ZREM**，不写下次。
- misfire：不补跑。全量重建也从现在算下一次。

## 领取顺序

持 **slot 锁**（TTL + 看门狗， Redission实现）：ZRANGE 到期项 → 可并发处理每条：停用则 ZREM，否则记下 fireTime、**同步 ZADD 下次** → **再异步** 插库 + submit → 本轮 ZADD 结束后解锁。

ZADD 已成功、插库前崩溃：**接受漏跑**。

`FIXED_DELAY`：领取时先让 member 不再到期（ZREM 或远离的 score），终态再按现在计算写入。`ONCE`/`DELAY`：跑完不再写回或 ZREM。

## 分片与执行者

- **N 个 slot**（默认 32，可配置）。`hash(taskId) % N`，其中 hash = **CRC-32/IEEE**（`java.util.zip.CRC32` / Python `zlib.crc32`），对 **UTF-8** 字节求无符号 32 位再取模。加机器只改归属，不改 N。
- slot 锁第一版默认：**TTL 10s**，看门狗 **3s**（可配置，看门狗必须短于 TTL）。锁 SPI 在 **`taskx-common`**；生产用 Redisson，key 为 `lock:slot:{n}`。
- Cron：6 段 Spring 表达式（`秒 分 时 日 月 周`），时区 **UTC**。
- 改 N：停执行者 → 清空 ZSET → **全量重建接口** → 改归属表 → 启动。
- 归属表 `slot_no` 主键。启动：被别人占用则失败；自己的则重启。
- 宕机：这些 slot 停触发，等重启或人工改归属。
- 执行者必须配置 **稳定 ID**（写在任务行上，不分桶）。无 ID 启动失败。
- 管理端写 Redis 前抢 **同一把 slot 锁**。配置：MySQL 未提交时写 Redis，失败则 ROLLBACK；COMMIT 失败的脏 member 靠拉取 ZREM。

## 编排（后议细节）

JSON 存整图。第一版引擎只跑 Start → 一个任务节点 → End。
