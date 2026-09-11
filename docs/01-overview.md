# 01 概述

## 一句话定位

**TaskX** 是开源分布式调度平台：配置写入 MySQL 与 Redis slot ZSET，执行者独占若干 slot 拉取到期任务并执行，管理台（React）做治理。不是 `Spring @Scheduled` / Quartz 的薄封装。

## 目标

- **统一触发**：`CRON`、`FIXED_RATE`、`FIXED_DELAY`、`DELAY`、`ONCE` 共用配置 → slot ZSET → 任务行。
- **固定分片**：`hash(jobId) % N` 进入 N 个 ZSET（默认 32）。加机器只改 slot 归属，不搬 member。
- **海量定义 + 高触发**：约束见 [05-scale.md](05-scale.md)，第一版不承诺具体 TPS。
- **可测核心**：`taskx-core` 不依赖 Spring。Java 21。

## 非目标（第一版）

- 不以 ZooKeeper、K8s Operator、多租户为前提。
- 不与 XXL-JOB / PowerJob 做功能对等竞赛。
- 不把取出→业务执行→写回做成跨存储事务。
- 不自动把挂掉的 slot 抢到其它活进程（等重启或人工改归属）。
- 编排只做 Start→任务→End，复杂编排后议。
- 亚秒级触发不做。

## 与竞品的对比维度

| 维度 | XXL-JOB / PowerJob 常见做法 | TaskX |
|------|------------------------------|--------|
| 触发 | 中心推送或中心计算下发 | 执行者拉 **自己拥有的 slot ZSET** |
| 触发类型 | Cron 为主，延时/工作流常拆开 | 统一 Trigger，同一套 ZSET |
| 故障转移 | 调度中心/执行器可切换 | slot 粘滞，挂了就停，人工改归属 |
| 编排 | 子任务或独立工作流 | 第一版仅 Start→任务→End |
| 接入 | 执行器 + 管理台 | core 纯 Java + Starter；Admin REST + React |

## 一致性语义

- **至少一次** 为主；ZADD 成功但插库前崩溃则 **该次可能漏跑**。
- Handler 应尽量幂等。允许同一 Job 重叠执行。
- 跨进程互斥靠 **slot Redis 锁 + 归属表**，不是 JVM 锁。
