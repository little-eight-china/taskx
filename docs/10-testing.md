# 10 本地测试

按顺序做。接口字段见 [09-api.md](09-api.md)，表结构见 [08-schema.md](08-schema.md)。

需要 **JDK 21**、**Maven 3.9+**、**Docker**。Admin 无鉴权，只在本机或受信网络测。

内置执行者只注册了 handler `demo`。测到期执行时 Task 的 `handler` 必须是 `demo`。id 建议用 `demo`：默认 N=32 时落到 **slot 0**。不要用 `demo-task`（slot **31**），执行者若只占 slot 0 会永远捞不到。

```bash
python3 - <<'PY'
import zlib
for s in ["demo", "demo-task", "hello"]:
    print(s, (zlib.crc32(s.encode()) & 0xffffffff) % 32)
PY
```

| taskId | N=32 的 slot |
|--------|----------------|
| `demo` | 0 |
| `hello` | 6 |
| `demo-task` | 31 |

`FIXED_RATE` / `FIXED_DELAY` 首次 score 是「现在 + 间隔」，要等间隔秒数才会第一次触发。想马上看到执行，用 `ONCE`（已过期的 `fireEpochSecond` 仍会跑一次）或较短的 `DELAY`。

---

## 1. 单元测试

不需要 MySQL / Redis。

```bash
mvn -q test
```

主要覆盖：`SlotHasher`、下次时间、单 slot 拉取 / 停用 ZREM、submit 失败、全量重建、迁 slot。Admin 模块有 Trigger JSON 映射单测，不启 Spring 容器。

---

## 2. 起依赖并建表

```bash
docker compose up -d
# MySQL 第一次起来大约十几秒
docker compose exec mysql mysqladmin ping -uroot -ptaskx --silent && echo mysql-ok
docker compose exec redis redis-cli ping
```

`PONG` 且 mysql-ok 后再灌表。脚本没有 `IF NOT EXISTS`，**库是空的只跑一次**；表已存在会报错，可忽略或 `docker compose down -v` 后重来。

```bash
docker compose exec -T mysql mysql -uroot -ptaskx < docs/sql/schema.sql
```

宿主机装了 mysql 客户端也可以：`mysql -h 127.0.0.1 -uroot -ptaskx < docs/sql/schema.sql`。

---

## 3. 打包

```bash
mvn -q -pl taskx-admin,taskx-executor -am package
```

产物：

- `taskx-admin/target/taskx-admin-0.1.0-SNAPSHOT.jar`
- `taskx-executor/target/taskx-executor-0.1.0-SNAPSHOT.jar`

默认连 `127.0.0.1:3306`（库 `taskx`，用户 `root` / `taskx`）和 `redis://127.0.0.1:6379`。覆盖方式：

| 变量 | 默认 | 谁用 |
|------|------|------|
| `TASKX_MYSQL_URL` | `jdbc:mysql://127.0.0.1:3306/taskx?...` | 两者 |
| `TASKX_MYSQL_USER` | `root` | 两者 |
| `TASKX_MYSQL_PASSWORD` | `taskx` | 两者 |
| `TASKX_REDIS_ADDRESS` | `redis://127.0.0.1:6379` | 两者 |
| `TASKX_SLOT_COUNT` | `32` | 两者，必须一致 |
| `TASKX_ADMIN_PORT` | `8080` | Admin |
| `TASKX_EXECUTOR_ID` | （必填） | 执行者 |
| `TASKX_SLOTS` | `0` | 执行者，逗号分隔 |
| `TASKX_SEED_DEMO` | `false` | 执行者启动时写入 `demo` 任务 |

下面命令假设仓库根目录、两个 jar 已打好。Admin 与执行者用**两个终端**。

---

## 4. 只测 Admin（可不启执行者）

终端 A：

```bash
java -jar taskx-admin/target/taskx-admin-0.1.0-SNAPSHOT.jar
```

探活，期望 `"status":"ok"`，HTTP 200。MySQL/Redis 不通则 503。

```bash
curl -sS localhost:8080/api/health
```

### 4.1 创建 / 读取

```bash
curl -sS -X PUT localhost:8080/api/tasks/demo \
  -H 'Content-Type: application/json' \
  -d '{
    "handler": "demo",
    "enabled": true,
    "trigger": { "type": "ONCE", "fireEpochSecond": 1 }
  }'
```

期望响应含 `"id":"demo"`、`"slot":0`、`"enabled":true`。再查：

```bash
curl -sS localhost:8080/api/tasks/demo
curl -sS localhost:8080/api/tasks
curl -sS localhost:8080/api/slots
curl -sS localhost:8080/api/executors
```

此时还没有执行者，`/api/slots` 里 slot 0 的 `executorId` 多为 `null`。Redis 里应已有到期 member（`ONCE` 的 score=1）：

```bash
docker compose exec redis redis-cli ZRANGE trigger:slot:0 0 -1 WITHSCORES
```

期望 member `demo`，score `1`。

非法 JSON 或缺少 `trigger.type` 期望 **400**。不存在的 id 期望 **404**：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' localhost:8080/api/tasks/missing
```

### 4.2 停用 / 启用 / 删除

```bash
curl -sS -X POST localhost:8080/api/tasks/demo/disable
docker compose exec redis redis-cli ZSCORE trigger:slot:0 demo
```

停用后 `enabled` 为 false，`ZSCORE` 应无值（`(nil)`）。再启用：

```bash
curl -sS -X POST localhost:8080/api/tasks/demo/enable
docker compose exec redis redis-cli ZSCORE trigger:slot:0 demo
```

`ONCE` 再次启用会把 score 写回 `1`，仍会立刻到期。删除：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE localhost:8080/api/tasks/demo
```

期望 **204**，ZSET 中无 `demo`。测执行链路前把 4.1 的 PUT 再做一遍。

---

## 5. 单执行者：创建 → 到期 → 成功

Admin 保持运行。终端 B：

```bash
export TASKX_EXECUTOR_ID=ex-1
export TASKX_SLOTS=0
java -jar taskx-executor/target/taskx-executor-0.1.0-SNAPSHOT.jar
```

不要在这次启动里开 `TASKX_SEED_DEMO`（避免和 Admin 写入打架）。日志应有 `executor ex-1 started slots=[0]`。

立刻看归属和心跳：

```bash
curl -sS localhost:8080/api/slots
curl -sS localhost:8080/api/executors
```

slot 0 的 `executorId` 应为 `ex-1`，`lastHeartbeatAt` 大约每 10 秒更新。

若 4.1 已写入 `ONCE` score=1，数秒内执行者应打日志 `handled demo fireTime=1`。查库：

```bash
curl -sS 'localhost:8080/api/executions?taskId=demo&limit=20'
```

期望一条 `status=SUCCESS`、`executorId=ex-1`、`scheduledFireTime=1`。`ONCE` 跑完会 ZREM，再查 Redis 应无 `demo`。不会补跑第二次。

### 5.1 用 DELAY 看第二次之前的等待

```bash
curl -sS -X PUT localhost:8080/api/tasks/demo \
  -H 'Content-Type: application/json' \
  -d '{
    "handler": "demo",
    "enabled": true,
    "trigger": { "type": "DELAY", "delaySeconds": 5 }
  }'
```

约 5 秒后多一条 SUCCESS。`DELAY` 一般不再写回 ZSET。

### 5.2 停用后不再触发

改成较短周期便于观察：

```bash
curl -sS -X PUT localhost:8080/api/tasks/demo \
  -H 'Content-Type: application/json' \
  -d '{
    "handler": "demo",
    "enabled": true,
    "trigger": { "type": "FIXED_RATE", "intervalSeconds": 10 }
  }'
```

记下当前 executions 条数，`POST .../disable`，再等 15 秒，条数不应再增加。Redis `ZSCORE trigger:slot:0 demo` 为 `(nil)`。`enable` 后约 10 秒应再出现新行。

### 5.3 未知 handler → FAILED

```bash
curl -sS -X PUT localhost:8080/api/tasks/demo \
  -H 'Content-Type: application/json' \
  -d '{
    "handler": "not-registered",
    "enabled": true,
    "trigger": { "type": "ONCE", "fireEpochSecond": 1 }
  }'
```

数秒后该次 `status=FAILED`。改回 `"handler":"demo"` 再测后续步骤。

---

## 6. 人工处理滞留行

`requeue` / `cancel` 只改 MySQL，不改 ZSET。执行者还活着时不要对正在跑的行乱调；下面用「执行者不在」来模拟。

1. 用 `ONCE` 写出一条 SUCCESS（步骤 5）。
2. **停掉终端 B 的执行者**（Ctrl+C）。
3. 把库里最近一条改成滞留态不方便走 API（SUCCESS 不能 requeue）。改为：执行者活着时用未知 handler 造一条 FAILED，或停执行者后对仍是 PENDING/RUNNING 的行操作。

造 FAILED（执行者需在跑）：步骤 5.3。然后：

```bash
# 把返回的 id 换成实际数字
curl -sS -X POST localhost:8080/api/executions/1/requeue
```

期望 `status=PENDING`。原 `executorId` 不变。该执行者的恢复循环约每 5 秒捞 PENDING；handler 仍未注册则会再 FAILED。把 handler 改回 `demo` 再 requeue，应变为 SUCCESS。

对 PENDING / RUNNING：

```bash
curl -sS -X POST localhost:8080/api/executions/1/cancel
```

期望 `CANCELLED`。对 SUCCESS 再 cancel / requeue 期望 **409**。

---

## 7. 迁 slot 与启动冲突

先停占用 slot 0 的执行者，再改归属更干净（执行者活着时 Admin 可能抢不到槽锁，**409**）。

```bash
curl -sS -X PUT localhost:8080/api/slots/0 \
  -H 'Content-Type: application/json' \
  -d '{"executorId":"ex-2"}'
curl -sS localhost:8080/api/slots
```

期望 slot 0 为 `ex-2`。再把 `TASKX_EXECUTOR_ID=ex-1 TASKX_SLOTS=0` 的进程拉起来：应因「slot 已被他人占用」失败退出。

用 `ex-2` 启动才能继续拉 slot 0：

```bash
export TASKX_EXECUTOR_ID=ex-2
export TASKX_SLOTS=0
java -jar taskx-executor/target/taskx-executor-0.1.0-SNAPSHOT.jar
```

测完可再 `PUT /api/slots/0` 改回 `ex-1`，或清库重来。

---

## 8. 全量重建

**先停所有执行者。** Admin 可继续开着。

```bash
curl -sS -X PUT localhost:8080/api/tasks/demo \
  -H 'Content-Type: application/json' \
  -d '{
    "handler": "demo",
    "enabled": true,
    "trigger": { "type": "FIXED_RATE", "intervalSeconds": 60 }
  }'

docker compose exec redis redis-cli DEL trigger:slot:0
curl -sS -X POST localhost:8080/api/maintenance/rebuild-triggers
docker compose exec redis redis-cli ZRANGE trigger:slot:0 0 -1 WITHSCORES
```

期望响应 `"enabledTasks"` ≥ 1，ZSET 重新出现 `demo`，score 约为当前 Unix 秒 + 60。若执行者仍持锁，重建返回 **409**。

改 N 的完整停机步骤见 [05-scale.md](05-scale.md)；本仓库执行者与 Admin 的 `TASKX_SLOT_COUNT` 必须一起改，改完再 rebuild。

---

## 9. 对照库表

```bash
docker compose exec mysql mysql -uroot -ptaskx taskx -e "
SELECT task_id, target_type, target_ref, target_version, enabled, trigger_type, trigger_spec FROM tx_task;
SELECT slot_no, executor_id FROM tx_slot_ownership;
SELECT executor_id, last_heartbeat_at FROM tx_executor;
SELECT id, task_id, scheduled_fire_time, executor_id, status FROM tx_execution ORDER BY id DESC LIMIT 20;
"
```

---

## 10. 管理台 UI

Admin REST 起来之后（compose 不要先 down）：

```bash
cd taskx-admin-ui
npm install
npm run dev
```

打开 http://localhost:5173（Vite 把 `/api` 转到 8080）。

建议点一遍：

1. 总览：左侧 health 为绿；MySQL/Redis 显示 ok。
2. 任务 → 新建：id=`demo`，目标类型=`HANDLER`，目标=`demo`，触发类型 `ONCE`，fireEpochSecond=`1`，保存。列表应出现 slot 0。
3. 停用 / 启用，再打开该任务编辑页确认字段回填。
4. 启动占 slot 0 的执行者后，到「执行记录」应看到 SUCCESS；从任务行「记录」应带上 `?taskId=demo`。
5. 集群：格子上能看到归属；点一个 slot 可改 executorId；**重建触发索引**在执行者活着时应失败提示 409，停执行者后再建应成功。

`npm run build` 用于确认 TypeScript 能通过。页面说明见 `taskx-admin-ui/README.md`。

---

## 11. 嵌入执行者（Starter）

业务应用依赖 `taskx-spring-boot-starter`，配置 `taskx.executor.id`。至少声明一个 `TaskHandler` Bean，名字与 Task 的 `handler` 一致（`@TaskxHandler("demo")` 或 Bean 名 `demo`）。

未配置 `taskx.executor.id` 时自动配置不生效，应用可以只当普通 Spring Boot 服务。没有 Handler 却配了 id，启动失败。

Admin 仍是独立进程；用 UI 建 Task 后，嵌入的执行者按自己的 slot 去拉。

---

## 常见问题

| 现象 | 原因 |
|------|------|
| 执行者启动即退出，slot owned by … | 归属表已被别人占，改 `TASKX_EXECUTOR_ID` 或 `PUT /api/slots/{n}` |
| Task 已启用，一直没有 execution | id 不在执行者负责的 slot；或 `FIXED_RATE` 还没到首次间隔；或已停用 |
| `handled` 日志没有，status=FAILED | `handler` 不是 `demo` |
| PUT Task / 重建 409 | 执行者正持槽锁，停执行者或稍后重试 |
| health 503 | compose 没起来，或 schema 未建、密码不对 |
| 第二次跑 schema.sql 报 Table exists | 正常，不要重复建表 |
| 用了 `demo-task` 当 id | slot 31，`TASKX_SLOTS=0` 的执行者不会拉 |
| UI 总览全失败、侧栏 API 异常 | Admin 没起，或没走 Vite 代理（应用不是 5173） |

测完：

```bash
# 停 Java 进程后
docker compose down
```

清数据（含 MySQL 卷）用 `docker compose down -v`。
