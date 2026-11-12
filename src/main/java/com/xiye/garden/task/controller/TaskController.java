package com.xiye.garden.task.controller;

import com.xiye.garden.common.ApiResponse;
import com.xiye.garden.common.PageResponse;
import com.xiye.garden.task.dto.TaskQueryDTO;
import com.xiye.garden.task.dto.TaskUpdateDTO;
import com.xiye.garden.task.dto.TaskCreateDTO;
import com.xiye.garden.task.service.TaskService;
import com.xiye.garden.task.vo.TaskVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService service;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskVO>> create(@Valid @RequestBody TaskCreateDTO request) {
        var task = service.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(ApiResponse.success(task));
    }

    @GetMapping
    public ApiResponse<PageResponse<TaskVO>> list(@Valid @ModelAttribute TaskQueryDTO query) {
        return ApiResponse.success(service.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskVO> get(@PathVariable @Positive(message = "任务ID必须为正整数") long id) {
        return ApiResponse.success(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskVO> update(@PathVariable @Positive long id, @Valid @RequestBody TaskUpdateDTO request) {
        return ApiResponse.success(service.update(id, request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TaskVO> complete(@PathVariable @Positive long id) {
        return ApiResponse.success(service.complete(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
