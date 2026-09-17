# 04 编排

编排实现位于 `taskx-workflow`。设计原则是：**定义不可变、运行态关系化、Redis 只做可重建索引、节点执行至少一次**。

## 定义与发布

- `tx_workflow_draft` 是唯一可变草稿，`revision` 用于乐观锁。
- 发布时先完整解析和校验，再把整图快照写入 `tx_workflow_version`。
- 已发布的 `definition_json` 不更新；实例启动时固定 `workflow_id + version`。
- `ui_layout_json` 与语义图分开，改画布坐标不会改变定义哈希。
- Task 的目标为 `HANDLER` 或 `WORKFLOW`。WORKFLOW 可固定版本，也可在实例启动时取 `current_published_version`。

Schema v1 示例：

```json
{
  "schemaVersion": 1,
  "nodes": [
    {
      "id": "start",
      "name": "Start",
      "type": "START",
      "configVersion": 1,
      "workerGroup": "default",
      "timeoutSeconds": 0,
      "retryPolicy": {"maxAttempts": 1, "intervalSeconds": 0},
      "config": null,
      "inputMapping": null
    },
    {
      "id": "check",
      "name": "Check score",
      "type": "CONDITION",
      "configVersion": 1,
      "workerGroup": "default",
      "timeoutSeconds": 30,
      "retryPolicy": {"maxAttempts": 2, "intervalSeconds": 5},
      "config": {
        "pointer": "/score",
        "operator": "GTE",
        "value": 80,
        "trueHandle": "pass",
        "falseHandle": "reject"
      },
      "inputMapping": null
    },
    {
      "id": "passed",
      "name": "Passed",
      "type": "END",
      "configVersion": 1,
      "workerGroup": "default",
      "timeoutSeconds": 0,
      "retryPolicy": {"maxAttempts": 1, "intervalSeconds": 0},
      "config": null,
      "inputMapping": null
    },
    {
      "id": "rejected",
      "name": "Rejected",
      "type": "END",
      "configVersion": 1,
      "workerGroup": "default",
      "timeoutSeconds": 0,
      "retryPolicy": {"maxAttempts": 1, "intervalSeconds": 0},
      "config": null,
      "inputMapping": null
    }
  ],
  "edges": [
    {"id": "e1", "sourceNodeId": "start", "sourceHandle": "default", "targetNodeId": "check"},
    {"id": "e2", "sourceNodeId": "check", "sourceHandle": "pass", "targetNodeId": "passed"},
    {"id": "e3", "sourceNodeId": "check", "sourceHandle": "reject", "targetNodeId": "rejected"}
  ]
}
```

发布校验会拒绝：

- START 不是一个、没有 END、节点/边 ID 重复；
- 边引用不存在节点、同一节点出现重复 `sourceHandle`；
- 不可达节点和外层环；
- 没有对应 `type + configVersion` 的 `WorkflowNodeExecutor`；
- schema v1 尚未启用的 `inputMapping`。

## 运行态

- `tx_workflow_instance`：一次流程实例，固定发布版本，并关联入口 `tx_execution`。
- `tx_workflow_node_activation`：节点的一次逻辑激活。循环/扇出以后可增加 `activation_no`，不复用旧行。
- `tx_workflow_node_attempt`：每次真实尝试独立留痕。
- `tx_workflow_token`：`source_activation_id + edge_id` 唯一，保证事务重试不重复推进。
- `tx_workflow_outbox`：与 READY/RETRY_WAIT 状态同事务写入，异步幂等投影到 Redis ZSET。
- `tx_workflow_callback`：WAIT/CALLBACK 预留，schema v1 不启用。

节点完成、写 Token、创建下游 Activation、写 Outbox 在同一个 MySQL 事务中。Redis key 为：

```text
workflow:ready:{workerGroup}:slot:{slotNo}
```

member 是 `activationId`，score 是可执行时间。Redis 不是事实源；脏 member 在 claim 失败时删除，漏 member 可由未发布 Outbox 补回。

## 执行与恢复

1. 入口 Task 到期后照常创建 `tx_execution` 并 CAS 到 RUNNING。
2. WORKFLOW 目标创建实例和首个 Activation；根执行记录暂不结束。
3. 执行者只拉自己拥有的 slot，MySQL claim 成功后才提交到工作线程池。
4. 节点成功按 `routeHandle` 选择唯一出边；到 END 时实例和根执行记录一起变 SUCCESS。
5. 可重试失败进入 RETRY_WAIT，并经 Outbox 重新入队；达到 `maxAttempts` 后实例和根执行记录变 FAILED。
6. RUNNING Activation 使用 lease。执行者崩溃后，过期 lease 会被标记 TIMED_OUT，并按同一重试上限重排或结束实例。
7. WORKFLOW 类型的 FIXED_DELAY 在实例终态后写下一次 trigger score。

节点执行是至少一次。`idempotencyKey = activationId:attemptNo`，HANDLER/HTTP 应把它用于业务幂等。

## Schema v1 节点

- `HANDLER`：配置 `{"handler":"name"}`，调用 `WorkflowHandler`。Starter 中可通过 Bean 名或 `@TaskxHandler` 命名。
- `HTTP`：支持静态 method/url/headers/body；自动发送 `Idempotency-Key`。2xx 成功，429/5xx 可重试。凭证不能直接写进定义，后续由 SecretResolver 提供。
- `CONDITION`：使用 JSON Pointer 和 `EXISTS/EQ/NE/GT/GTE/LT/LTE`，返回 true/false 或自定义 handle。
- `START`、`END`：结构节点，不占工作线程。

schema v1 外层图仅支持单路由 DAG。并行分叉/汇聚、受控 LOOP、子流程、人工节点、回调等待、JSONPath 输入映射和 SecretResolver 仍需后续 schema/执行器版本实现；发布阶段不会把这些能力伪装成可用。
