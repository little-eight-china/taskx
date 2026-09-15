# 09 Admin REST

第一版 **无鉴权**，只适合本机或受信网络。React 管理台尚未接。默认端口 `8080`。

逐步测试（Admin CRUD → 单执行者到期 → 迁 slot → 重建）见 [10-testing.md](10-testing.md)。管理台 UI 见 `taskx-admin-ui/`（开发服务器代理本 API）。

改配置与迁 slot 会抢与执行者相同的 `lock:slot:{n}`。抢不到返回 **409**。全量重建必须先停所有执行者。

## 启动

需要 JDK 21、已初始化的 MySQL、Redis。环境变量与执行者对齐：`TASKX_MYSQL_URL`、`TASKX_MYSQL_USER`、`TASKX_MYSQL_PASSWORD`、`TASKX_REDIS_ADDRESS`、`TASKX_SLOT_COUNT`。额外：`TASKX_ADMIN_PORT`（默认 8080）。

```bash
docker compose up -d
mysql -h 127.0.0.1 -uroot -ptaskx < docs/sql/schema.sql
mvn -q -pl taskx-admin -am package
java -jar taskx-admin/target/taskx-admin-0.1.0-SNAPSHOT.jar
```

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 探活 MySQL / Redis；异常时 503 |
| GET | `/api/tasks` | 列出全部 Task（含计算出的 `slot`） |
| GET | `/api/tasks/{id}` | 单条 |
| PUT | `/api/tasks/{id}` | 创建或更新；持槽锁写 MySQL + Redis |
| DELETE | `/api/tasks/{id}` | 删除并行 `ZREM` |
| POST | `/api/tasks/{id}/disable` | 停用并 `ZREM` |
| POST | `/api/tasks/{id}/enable` | 启用并按现在写入下次 score |
| GET | `/api/executions?taskId=&status=&limit=` | 默认 `limit=50`，最大 200 |
| GET | `/api/executions/{id}` | 单条执行记录 |
| POST | `/api/executions/{id}/requeue` | `RUNNING` / `FAILED` → `PENDING`（给原 `executorId` 的恢复循环捞） |
| POST | `/api/executions/{id}/cancel` | `PENDING` / `RUNNING` → `CANCELLED` |
| GET | `/api/slots` | `0 .. N-1`，未分配时 `executorId` 为 `null` |
| PUT | `/api/slots/{slotNo}` | 持锁改归属。body：`{"executorId":"ex-1"}` |
| GET | `/api/executors` | 心跳观测 |
| POST | `/api/maintenance/rebuild-triggers` | 锁全部 slot → 清空 ZSET → 按启用中的 Task 从现在重建 |

`requeue` / `cancel` 只改库，不改 Redis 触发索引。执行者若仍在跑同一行，可能与 Handler 终态 CAS 冲突；适合执行者已死、行滞留的情况。

## curl 示例

```bash
# 探活
curl -s localhost:8080/api/health

# 创建 demo（id=demo 在 N=32 时落到 slot 0）
curl -s -X PUT localhost:8080/api/tasks/demo \
  -H 'Content-Type: application/json' \
  -d '{
    "handler": "demo",
    "enabled": true,
    "trigger": { "type": "FIXED_RATE", "intervalSeconds": 60 }
  }'

curl -s localhost:8080/api/tasks
curl -s localhost:8080/api/tasks/demo

curl -s -X POST localhost:8080/api/tasks/demo/disable
curl -s -X POST localhost:8080/api/tasks/demo/enable

# 执行记录
curl -s 'localhost:8080/api/executions?taskId=demo&limit=20'
curl -s localhost:8080/api/executions/1
curl -s -X POST localhost:8080/api/executions/1/requeue
curl -s -X POST localhost:8080/api/executions/1/cancel

# 观测与迁 slot（先停占用该 slot 的执行者更稳妥）
curl -s localhost:8080/api/slots
curl -s localhost:8080/api/executors
curl -s -X PUT localhost:8080/api/slots/0 \
  -H 'Content-Type: application/json' \
  -d '{"executorId":"ex-1"}'

# 改 N 或 Redis 丢数据：停全部执行者后再调
curl -s -X POST localhost:8080/api/maintenance/rebuild-triggers
```

## Task JSON

`trigger.type` 为大写枚举：`CRON`、`FIXED_RATE`、`FIXED_DELAY`、`DELAY`、`ONCE`。

```json
{
  "id": "demo",
  "handler": "demo",
  "payload": null,
  "enabled": true,
  "slot": 0,
  "trigger": { "type": "FIXED_RATE", "intervalSeconds": 60 },
  "workflowJson": null
}
```

| type | 需要的字段 |
|------|------------|
| `CRON` | `expression`（6 段 Spring Cron，UTC） |
| `FIXED_RATE` | `intervalSeconds` |
| `FIXED_DELAY` / `DELAY` | `delaySeconds` |
| `ONCE` | `fireEpochSecond` |

响应里的 `slot` 由 `CRC32(UTF-8(id)) % N` 计算，不入库。

## 状态码

| 码 | 情况 |
|----|------|
| 200 | 成功 |
| 204 | `DELETE /api/tasks/{id}` |
| 400 | 参数 / JSON 不合法 |
| 404 | Task 或 Execution 不存在 |
| 409 | 槽锁被占、归属冲突、不允许的状态迁移 |
| 503 | MySQL 或 Redis 探活失败 |
