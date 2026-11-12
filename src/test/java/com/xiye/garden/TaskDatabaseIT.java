package com.xiye.garden;

import com.xiye.garden.task.dto.TaskQueryDTO;
import com.xiye.garden.task.dto.TaskUpdateDTO;
import com.xiye.garden.task.dto.TaskCreateDTO;
import com.xiye.garden.task.entity.TaskStatus;
import com.xiye.garden.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class TaskDatabaseIT {
    @Autowired
    private TaskService service;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationCrudPaginationAndReopeningPersistCorrectly() {
        var first = service.create(new TaskCreateDTO("集成测试🌱_100%", null, 25, TaskStatus.TODO, "fern"));
        var second = service.create(new TaskCreateDTO("集成测试第二条", "描述", 30, TaskStatus.IN_PROGRESS, "fern"));
        assertEquals(first, service.get(Long.parseLong(first.id())));
        assertEquals("fern", jdbc.queryForObject("SELECT plant_type FROM tasks WHERE id = ?", String.class, first.id()));
        var query = new TaskQueryDTO();
        query.setTitle("集成测试");
        query.setSortBy("id");
        query.setDirection("asc");
        query.setSize(1);
        var page = service.list(query);
        assertEquals(2, page.total());
        assertEquals(1, page.records().size());
        assertEquals(first.id(), page.records().getFirst().id());
        query.setPage(2);
        assertEquals(second.id(), service.list(query).records().getFirst().id());
        query.setPage(1);
        query.setTitle("_100%");
        assertEquals(1, service.list(query).total());
        query.setTitle("集成测试");
        query.setStatus(TaskStatus.IN_PROGRESS);
        assertEquals(1, service.list(query).total());
        var completed = service.complete(Long.parseLong(first.id()));
        assertNotNull(completed.completedAt());
        assertEquals(completed.completedAt(), service.complete(Long.parseLong(first.id())).completedAt());
        service.update(Long.parseLong(first.id()), new TaskUpdateDTO("重新开始", "", 40, TaskStatus.TODO, "fern"));
        assertNull(service.get(Long.parseLong(first.id())).completedAt());
        assertEquals(TaskStatus.TODO, service.get(Long.parseLong(first.id())).status());
        service.delete(Long.parseLong(first.id()));
        assertThrows(com.xiye.garden.common.ResourceNotFoundException.class, () -> service.get(Long.parseLong(first.id())));
        assertThrows(com.xiye.garden.common.ResourceNotFoundException.class, () -> service.complete(Long.parseLong(first.id())));
        assertThrows(com.xiye.garden.common.ResourceNotFoundException.class, () -> service.update(Long.parseLong(first.id()),
                new TaskUpdateDTO("已删除", "", 25, TaskStatus.TODO, "fern")));
        assertThrows(com.xiye.garden.common.ResourceNotFoundException.class, () -> service.delete(Long.parseLong(first.id())));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM tasks WHERE id = ?", Integer.class, first.id()));
        assertNotNull(jdbc.queryForObject("SELECT title FROM tasks WHERE id = ?", String.class, first.id()));
        var all = new TaskQueryDTO();
        assertTrue(service.list(all).records().stream().noneMatch(task -> task.id().equals(first.id())));
    }
}
