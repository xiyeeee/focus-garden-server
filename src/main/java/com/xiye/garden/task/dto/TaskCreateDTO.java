package com.xiye.garden.task.dto;

import com.xiye.garden.task.entity.TaskStatus;
import jakarta.validation.constraints.*;

public record TaskCreateDTO(
        @NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题最多100个字符") String title,
        @Size(max = 2000, message = "描述最多2000个字符") String description,
        @NotNull(message = "计划专注分钟数不能为空")
        @Min(value = 1, message = "计划专注分钟数至少1分钟")
        @Max(value = 1440, message = "计划专注分钟数最多1440分钟") Integer plannedFocusMinutes,
        @NotNull(message = "状态不能为空，支持 TODO、IN_PROGRESS、COMPLETED") TaskStatus status,
        @Pattern(regexp = "fern|daisy|cactus|tulip", message = "植物类型仅支持 fern、daisy、cactus、tulip") String plantType) {
    public TaskCreateDTO {
        plantType = plantType == null ? "fern" : plantType;
    }
}
