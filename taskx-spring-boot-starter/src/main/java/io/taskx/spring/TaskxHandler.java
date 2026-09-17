package io.taskx.spring;

import io.taskx.core.handler.TaskHandler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a {@link TaskHandler} bean to {@code Task.handler}. If absent, the Spring bean name is used.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskxHandler {

    String value();
}
