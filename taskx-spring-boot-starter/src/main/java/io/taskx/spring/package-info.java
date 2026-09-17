/**
 * Spring Boot starter: embed a TaskX executor. Activates when {@code taskx.executor.id} is set.
 * Does not start Admin REST. Uses a private MySQL/Redis pool so the application DataSource is left alone.
 */
package io.taskx.spring;
