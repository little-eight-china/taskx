# 08 存储结构

初始化 SQL：[schema.sql](sql/schema.sql)（与 `taskx-meta/src/main/resources/db/schema.sql` 相同）。MySQL **8.0+**。

当前仍是 0.x 开发期，初始化脚本不承担增量迁移。旧库中的 `tx_task.handler/workflow_json` 已被 `target_type/target_ref/target_version` 取代；升级本次编排结构前请备份数据并重建开发库。

```bash
mysql -h 127.0.0.1 -uroot -ptaskx < docs/sql/schema.sql
# 或：docker compose exec -T mysql mysql -uroot -ptaskx < docs/sql/schema.sql
```

本地联调步骤见 [10-testing.md](10-testing.md)。

Redis **没有 DDL**：ZSET 与锁在运行时按 key 创建。改 N 或 Redis 丢数据时走全量重建（见 [05-scale.md](05-scale.md)）。

## MySQL 表

| 表 | 作用 |
|----|------|
| `tx_executor` | 稳定 `executor_id`，心跳仅观测 |
| `tx_slot_ownership` | `slot_no` 主键 → `executor_id` |
| `tx_task` | 调度配置；槽位不落列，用 `CRC32(UTF-8(task_id)) % N` |
| `tx_execution` | 执行记录；唯一键 `(task_id, scheduled_fire_time)` |
| `tx_workflow` / `tx_workflow_draft` / `tx_workflow_version` | 工作流身份、可变草稿、不可变发布快照 |
| `tx_workflow_instance` | 固定版本的一次工作流实例，关联入口执行记录 |
| `tx_workflow_node_activation` / `tx_workflow_node_attempt` | 节点逻辑激活与每次真实尝试 |
| `tx_workflow_token` | 唯一边推进 Token |
| `tx_workflow_outbox` | MySQL READY 状态到 Redis 的事务 Outbox |
| `tx_workflow_callback` | 异步回调预留；schema v1 不启用 |

`task_id` / `executor_id` 使用 `utf8mb4_bin`，大小写敏感。`tx_execution.executor_id` 不建外键，便于保留历史行。

`tx_task.target_type` 为 `HANDLER` 或 `WORKFLOW`，`target_ref` 分别保存 handler 名或 workflowId；WORKFLOW 的 `target_version` 为空时在实例启动时解析当前发布版本。

### `tx_task.trigger_spec`

与 `trigger_type` 配对：

| trigger_type | trigger_spec |
|--------------|----------------|
| `CRON` | `{"expression":"0 0 * * * *"}`（6 段 Spring Cron，UTC） |
| `FIXED_RATE` | `{"intervalSeconds":60}` |
| `FIXED_DELAY` | `{"delaySeconds":10}` |
| `DELAY` | `{"delaySeconds":30}` |
| `ONCE` | `{"fireEpochSecond":1768471200}` |

## Redis key

| key | 类型 | 内容 |
|-----|------|------|
| `trigger:slot:{slotNo}` | ZSET | member = `taskId`，score = Unix **秒** |
| `workflow:ready:{workerGroup}:slot:{slotNo}` | ZSET | member = `activationId`，score = 可执行 Unix 秒；可由 Outbox 重建 |
| `lock:slot:{slotNo}` | 字符串锁 | 与归属表独立的运行时互斥；TTL 默认 10s，看门狗 3s |

到期判断：`TIME` 转秒后 `ZRANGEBYSCORE key 0 now`。

## 与 core 的对应

- 下次时间：`NextFireCalculator`（领取时 / 终态 / 首次写入）
- 分槽：`SlotHasher`；Redis key：`SlotRedisKeys`
- 执行记录唯一键与 CAS：`ExecutionRepository`
- 锁：`taskx-common` 的 `DistributedLock`（测试用 `InMemoryLock`，生产用 Redisson）；slot 锁 key 为 `lock:slot:{n}`
