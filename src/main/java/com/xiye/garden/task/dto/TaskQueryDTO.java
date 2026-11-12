package com.xiye.garden.task.dto;

import com.xiye.garden.task.entity.TaskStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TaskQueryDTO {
    @Min(value = 1, message = "页码至少为1")
    @Max(value = 1000000, message = "页码不能超过1000000")
    private int page = 1;
    @Min(value = 1, message = "每页至少1条")
    @Max(value = 100, message = "每页最多100条")
    private int size = 20;
    private TaskStatus status;
    @Size(max = 100, message = "标题筛选最多100个字符")
    private String title;
    @NotNull(message = "排序字段不能为空")
    @Pattern(regexp = "id|createdAt|updatedAt|plannedFocusMinutes", message = "排序字段仅支持 id、createdAt、updatedAt、plannedFocusMinutes")
    private String sortBy = "createdAt";
    @NotNull(message = "排序方向不能为空")
    @Pattern(regexp = "asc|desc", message = "排序方向仅支持 asc、desc")
    private String direction = "desc";
}
