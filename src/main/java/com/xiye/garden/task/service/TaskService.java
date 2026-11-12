package com.xiye.garden.task.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiye.garden.auth.security.CurrentUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiye.garden.common.PageResponse;
import com.xiye.garden.common.ResourceNotFoundException;
import com.xiye.garden.task.dto.TaskQueryDTO;
import com.xiye.garden.task.dto.TaskUpdateDTO;
import com.xiye.garden.task.dto.TaskCreateDTO;
import com.xiye.garden.task.entity.TaskEntity;
import com.xiye.garden.task.entity.TaskStatus;
import com.xiye.garden.task.mapper.TaskMapper;
import com.xiye.garden.task.vo.TaskVO;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "id", "id", "createdAt", "created_at", "updatedAt", "updated_at", "plannedFocusMinutes", "planned_focus_minutes");
    private final TaskMapper mapper;
    private final Clock clock;
    private final CurrentUser currentUser;

    @Transactional
    public TaskVO create(TaskCreateDTO request) {
        TaskEntity task = new TaskEntity();
        task.setUserId(currentUser.id());
        task.setCreatedAt(utcNow());
        applyRequest(task, new TaskUpdateDTO(request.title(), request.description(),
                request.plannedFocusMinutes(), request.status(), request.plantType()));
        mapper.insert(task);
        return TaskVO.from(task);
    }

    public PageResponse<TaskVO> list(TaskQueryDTO query) {
        var conditions = new QueryWrapper<TaskEntity>().eq("user_id", currentUser.id());
        conditions.eq(query.getStatus() != null, "status", query.getStatus());
        if (query.getTitle() != null && !query.getTitle().isBlank()) {
            // LOCATE treats % and _ as literal characters and still uses bound parameters.
            conditions.apply("LOCATE({0}, title) > 0", query.getTitle().strip());
        }
        String column = SORT_COLUMNS.get(query.getSortBy());
        if (column == null) {
            throw new IllegalArgumentException("不支持的排序字段");
        }
        conditions.orderBy(true, "asc".equals(query.getDirection()), column);
        if (!"id".equals(column)) {
            conditions.orderByDesc("id");
        }
        var result = mapper.selectPage(new Page<TaskEntity>(query.getPage(), query.getSize()), conditions);
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(TaskVO::from).toList());
    }

    public TaskVO get(long id) {
        return TaskVO.from(requireTask(mapper.selectOwned(id, currentUser.id())));
    }

    @Transactional
    public TaskVO update(long id, TaskUpdateDTO request) {
        TaskEntity task = requireTask(mapper.selectForUpdate(id, currentUser.id()));
        applyRequest(task, request);
        mapper.updateById(task);
        return TaskVO.from(task);
    }

    @Transactional
    public TaskVO complete(long id) {
        TaskEntity task = requireTask(mapper.selectForUpdate(id, currentUser.id()));
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return TaskVO.from(task);
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setUpdatedAt(utcNow());
        task.setCompletedAt(task.getUpdatedAt());
        mapper.updateById(task);
        return TaskVO.from(task);
    }

    @Transactional
    public void delete(long id) {
        requireTask(mapper.selectForUpdate(id, currentUser.id()));
        mapper.softDelete(id, currentUser.id(), utcNow());
    }

    private void applyRequest(TaskEntity task, TaskUpdateDTO request) {
        var now = utcNow();
        task.setTitle(request.title().strip());
        task.setDescription(request.description() == null ? "" : request.description());
        task.setPlannedFocusMinutes(request.plannedFocusMinutes());
        task.setPlantType(request.plantType());
        if (request.status() == TaskStatus.COMPLETED) {
            if (task.getCompletedAt() == null) {
                task.setCompletedAt(now);
            }
        } else {
            task.setCompletedAt(null);
        }
        task.setStatus(request.status());
        task.setUpdatedAt(now);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private TaskEntity requireTask(TaskEntity task) {
        if (task == null || !java.util.Objects.equals(task.getUserId(), currentUser.id())) {
            throw new ResourceNotFoundException("任务不存在");
        }
        return task;
    }
}
