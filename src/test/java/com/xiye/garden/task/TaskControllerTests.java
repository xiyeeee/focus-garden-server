package com.xiye.garden.task;

import com.xiye.garden.common.ResourceNotFoundException;
import com.xiye.garden.task.controller.TaskController;
import com.xiye.garden.task.service.TaskService;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
class TaskControllerTests {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TaskService service;

    static Stream<String> invalidBodies() {
        return Stream.of(
                "{\"title\": \" \", \"plannedFocusMinutes\": 25, \"status\": \"TODO\"}",
                "{\"title\": \"长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长长\", \"plannedFocusMinutes\": 25, \"status\": \"TODO\"}",
                "{\"title\": \"任务\", \"plannedFocusMinutes\": 0, \"status\": \"TODO\"}",
                "{\"title\": \"任务\", \"plannedFocusMinutes\": 1441, \"status\": \"TODO\"}",
                "{\"title\": \"任务\", \"plannedFocusMinutes\": 25, \"status\": \"UNKNOWN\"}",
                "{\"title\": \"任务\", \"plannedFocusMinutes\": 25}");
    }

    @Test
    void rejectsFractionalMinutesAndNumericStatus() throws Exception {
        String fractional = """
                {"title":"任务","plannedFocusMinutes":25.5,"status":"TODO"}
                """;
        String numericStatus = """
                {"title":"任务","plannedFocusMinutes":25,"status":0}
                """;
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(fractional))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(numericStatus))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void createReturnsLocationAndUtcTime() throws Exception {
        var instant = java.time.Instant.parse("2026-11-12T02:00:00Z");
        when(service.create(any())).thenReturn(new com.xiye.garden.task.vo.TaskVO(
                "9007199254740993", "任务", "", 25, com.xiye.garden.task.entity.TaskStatus.TODO, "fern", instant, instant, null));
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"任务","plannedFocusMinutes":25,"status":"TODO"}
                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tasks/9007199254740993"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-12T02:00:00Z"))
                .andExpect(jsonPath("$.data.id").value("9007199254740993"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidCreateAndUpdate(String body) throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidPaginationSortAndStatus() throws Exception {
        for (String query : new String[]{"page=0", "size=101", "sortBy=title;DROP TABLE tasks", "direction=bad", "status=BAD"}) {
            mvc.perform(get("/api/tasks?" + query)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingTaskReturns404() throws Exception {
        when(service.get(99)).thenThrow(new ResourceNotFoundException("任务不存在"));
        mvc.perform(get("/api/tasks/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    @Test
    void preservesFrameworkHttpStatus() throws Exception {
        mvc.perform(patch("/api/tasks/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("任务"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/tasks/1").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void missingUpdateDeleteAndCompleteReturn404() throws Exception {
        when(service.update(eq(99L), any())).thenThrow(new ResourceNotFoundException("任务不存在"));
        doThrow(new ResourceNotFoundException("任务不存在")).when(service).delete(99L);
        when(service.complete(99L)).thenThrow(new ResourceNotFoundException("任务不存在"));
        mvc.perform(put("/api/tasks/99").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"任务","plannedFocusMinutes":25,"status":"TODO"}
                """))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("任务不存在"));
        mvc.perform(delete("/api/tasks/99")).andExpect(status().isNotFound());
        mvc.perform(post("/api/tasks/99/complete")).andExpect(status().isNotFound());
    }

    @Test
    void invalidPlantTypeHasChineseValidationMessage() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"任务","plannedFocusMinutes":25,"status":"TODO","plantType":"invalid"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("plantType：植物类型仅支持 fern、daisy、cactus、tulip"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonPositiveId() throws Exception {
        mvc.perform(get("/api/tasks/0")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/tasks/-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
