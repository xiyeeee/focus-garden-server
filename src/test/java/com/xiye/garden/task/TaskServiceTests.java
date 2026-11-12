package com.xiye.garden.task;

import com.xiye.garden.common.ResourceNotFoundException;
import com.xiye.garden.task.dto.TaskUpdateDTO;
import com.xiye.garden.task.dto.TaskCreateDTO;
import com.xiye.garden.task.entity.TaskEntity;
import com.xiye.garden.task.entity.TaskStatus;
import com.xiye.garden.task.mapper.TaskMapper;
import com.xiye.garden.task.service.TaskService;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskServiceTests {
    private final TaskMapper mapper = mock(TaskMapper.class);
    private final Instant now = Instant.parse("2026-11-12T02:00:00Z");
    private final com.xiye.garden.auth.security.CurrentUser currentUser = mock(com.xiye.garden.auth.security.CurrentUser.class);
    private final TaskService service = new TaskService(mapper, Clock.fixed(now, ZoneOffset.UTC), currentUser);
    private TaskEntity task;

    @BeforeEach
    void setUp() {
        task = new TaskEntity();
        task.setId(1L);
        task.setUserId(1L);
        when(currentUser.id()).thenReturn(1L);
        task.setTitle("测试");
        task.setDescription("");
        task.setPlannedFocusMinutes(25);
        task.setStatus(TaskStatus.TODO);
        task.setPlantType("fern");
        task.setCreatedAt(LocalDateTime.ofInstant(now.minusSeconds(60), ZoneOffset.UTC));
        task.setUpdatedAt(task.getCreatedAt());
        when(mapper.selectForUpdate(1, 1)).thenReturn(task);
        when(mapper.insert(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity inserted = invocation.getArgument(0);
            inserted.setId(2L);
            return 1;
        });
    }

    @Test
    void completeSetsServerTimeAndIsIdempotent() {
        assertEquals(now, service.complete(1).completedAt());
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals(now, service.complete(1).completedAt());
        verify(mapper, times(1)).updateById(task);
    }

    @Test
    void reopenClearsCompletionAndCompletingAgainSetsNewTime() {
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(task.getCreatedAt());
        var result = service.update(1, new TaskUpdateDTO(" 更新任务 ", null, 30, TaskStatus.IN_PROGRESS, "fern"));
        assertNull(result.completedAt());
        assertEquals("更新任务", result.title());
        assertEquals("", result.description());
        assertEquals(TaskStatus.IN_PROGRESS, result.status());
        assertEquals(now, service.complete(1).completedAt());
    }

    @Test
    void editingCompletedTaskPreservesFirstCompletion() {
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(task.getCreatedAt());
        assertEquals(now.minusSeconds(60), service.update(1,
                new TaskUpdateDTO("完成任务", "说明", 25, TaskStatus.COMPLETED, "fern")).completedAt());
    }

    @Test
    void createCompletedTaskUsesServerCompletionTime() {
        var result = service.create(new TaskCreateDTO("新任务", null, 25, TaskStatus.COMPLETED, "fern"));
        assertEquals(now, result.createdAt());
        assertEquals(now, result.completedAt());
    }

    @Test
    void deleteRetainsPhysicalRowAndUsesServerTime() {
        service.delete(1);
        verify(mapper).softDelete(1, 1, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    void putNullFieldsClearDescriptionAndResetPlantPreference() {
        task.setDescription("原描述");
        task.setPlantType("daisy");
        var result = service.update(1, new TaskUpdateDTO("任务", null, 25, TaskStatus.TODO, null));
        assertEquals("", result.description());
        assertEquals("fern", result.plantType());
    }

    @Test
    void allMissingResourceOperationsFail() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(99));
        assertThrows(ResourceNotFoundException.class, () -> service.complete(99));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(99));
        assertThrows(ResourceNotFoundException.class, () -> service.update(99,
                new TaskUpdateDTO("不存在", "", 25, TaskStatus.TODO, "fern")));
        verify(mapper, never()).deleteById(anyLong());
        verify(mapper, never()).softDelete(anyLong(), anyLong(), any());
    }
}
