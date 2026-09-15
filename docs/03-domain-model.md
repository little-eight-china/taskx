# 03 领域模型

## 实体关系

```mermaid
flowchart LR
  ExecutorReg[Executor]
  SlotOwner[SlotOwnership]
  Task[Task]
  Trigger[Trigger]
  Execution[Execution]
  WfJson[WorkflowJSON]

  ExecutorReg --> SlotOwner
  Task --> Trigger
  Task -->|hash_taskId_mod_N| SlotOwner
  Task -->|每次触发| Execution
  Execution -->|executor_id| ExecutorReg
  Task -->|可选| WfJson
```

表结构与初始化 SQL 见 [08-schema.md](08-schema.md)、[sql/schema.sql](sql/schema.sql)。

## Executor

- `executor_id`：来自环境变量/配置中心，启动必填，稳定。
- 心跳仅观测，不自动抢 slot。

## SlotOwnership

- `slot_no` 主键，`0 .. N-1`。
- `executor_id`：当前计划归属。

## Task

Handler、参数、启停。槽位由 `CRC32(UTF-8(taskId)) % N` 计算，不写死在行上。停用/删除必须 `ZREM`（写入路径 + 拉取发现双保险）。

## Trigger

| 类型 | 下次 score 何时写 |
|------|-------------------|
| `CRON` / `FIXED_RATE` | 领取时同步 ZADD（从 **现在** 算） |
| `FIXED_DELAY` | 终态再写；领取时先移出到期 |
| `DELAY` / `ONCE` | 一般 ZREM，不再写回 |
| 编排入口 | 同普通 Trigger；内部推进见 [04](04-workflow.md) |

时间单位：**秒**。一律 Redis TIME。Cron 为 6 段 Spring 表达式、UTC。

首次写入 ZSET 用 `firstFire`：CRON / FIXED_RATE / FIXED_DELAY 从现在加间隔或取下一次 Cron；DELAY 为现在 + delay；ONCE 为指定秒（即使已过期，只跑这一次）。

## Execution

字段：`id`、`taskId`、`scheduledFireTime`、`executorId`、`status`。

唯一键 `(taskId, scheduledFireTime)`。

```text
PENDING --> RUNNING --> SUCCESS
                  \--> FAILED
                  \--> CANCELLED     （人工）
                  \--> PENDING       （人工 requeue，给原执行者恢复）
PENDING --> FAILED     （submit 失败）
PENDING --> CANCELLED
FAILED  --> PENDING    （人工 requeue）
```

- `PENDING → RUNNING` CAS 成功才执行。
- `RUNNING` 崩溃保持 RUNNING，不自动恢复；管理接口可 `cancel` 或 `requeue`。
- `PENDING` 仅原 `executorId` 的恢复循环可捞。`requeue` 不改 `executorId`。

不依赖 JVM 任务 ID 缓存作为正确性前提。
