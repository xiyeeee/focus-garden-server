package com.xiye.garden.task.vo;

import com.xiye.garden.task.entity.TaskEntity;
import com.xiye.garden.task.entity.TaskStatus;
import java.time.Instant;
import java.time.ZoneOffset;

public record TaskVO(String id, String title, String description, Integer plannedFocusMinutes,
                           TaskStatus status, String plantType, Instant createdAt, Instant updatedAt, Instant completedAt) {
    public static TaskVO from(TaskEntity task) {
        return new TaskVO(task.getId().toString(), task.getTitle(), task.getDescription(), task.getPlannedFocusMinutes(),
                task.getStatus(), task.getPlantType(), task.getCreatedAt().toInstant(ZoneOffset.UTC),
                task.getUpdatedAt().toInstant(ZoneOffset.UTC),
                task.getCompletedAt() == null ? null : task.getCompletedAt().toInstant(ZoneOffset.UTC));
    }
}
