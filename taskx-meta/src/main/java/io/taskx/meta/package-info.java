/**
 * Persistence for TaskX: MySQL (config, executions, slot ownership) and Redis (slot ZSET, slot locks, TIME).
 * Schema: {@code db/schema.sql} (same as {@code docs/sql/schema.sql}).
 */
package io.taskx.meta;
