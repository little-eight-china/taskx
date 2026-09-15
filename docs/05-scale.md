# 05 规模、扩容与恢复

## 规模

大量 Task 定义 + 整点同时到期。第一版不承诺具体 TPS。

- 每个 slot 每秒 `ZRANGEBYSCORE` 带 **LIMIT**，一轮处理不完留到下一秒（下次 score 已从现在算走的项不会再因旧分堆积连环补跑）。
- 插库可批量；日志与任务行要能归档/TTL。
- 拉取与业务池隔离。slot 锁看门狗覆盖同步改 ZSET 的批次，不要把 Handler 跑完才解锁。

## misfire

全局：**不补跑**。领取后下次时间从 Redis 现在算。停机一小时回来：本次捞到的过期点仍会跑一次，然后跳到未来，不会 10:00、10:01、10:02 连环打满。

全量重建同样从现在算下一次。

## 水平扩展

Task → slot 为 `hash(taskId) % N`，**日常不改 N**。

- **加执行者**：`PUT /api/slots/{n}` 持锁改归属（或先停旧进程再改）。ZSET 数据不动。
- **执行者上限**：同时持有独占 slot 时，活执行者数 ≤ N。默认 N=32。
- **改 N**：停机 → `POST /api/maintenance/rebuild-triggers`（锁齐全部 slot、清空 ZSET、按启用配置从现在重建）→ 重写归属 → 再启动。改 N 会让几乎所有 Task 换桶，必须搬家而不能只改配置数字。

## 恢复

- 触发：原进程重启后继续拉自己的 slot。
- PENDING：仅原执行者 ID。
- RUNNING：人工接口。
- Redis 丢失：维护窗口全量重建（`POST /api/maintenance/rebuild-triggers`）。

## 时钟与热点

- score、now 都用秒。
- 单 slot 仍可能整点热点：LIMIT、有界线程池、Cron 不要全集中在同一秒。
