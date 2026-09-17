package io.taskx.spring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskxPropertiesTest {

    @Test
    void copiesSlotsAndFillsMysqlRedisDefaults() {
        TaskxProperties properties = new TaskxProperties(
                32,
                null,
                null,
                new TaskxProperties.Executor("ex-1", List.of(0, 1), List.of("default"), true, 4, 256)
        );
        assertEquals("ex-1", properties.executor().id());
        assertEquals(List.of(0, 1), properties.executor().slots());
        assertEquals("root", properties.mysql().user());
        assertEquals("redis://127.0.0.1:6379", properties.redis().address());
    }

    @Test
    void rejectsBlankExecutorId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TaskxProperties.Executor(" ", List.of(0), List.of("default"), true, 4, 256)
        );
    }
}
