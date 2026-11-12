package com.xiye.garden;

import org.springframework.context.annotation.Import;
import com.xiye.garden.config.SecurityConfig;
import com.xiye.garden.auth.security.SecurityJsonResponse;
import com.xiye.garden.user.mapper.UserMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.xiye.garden.common.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, SecurityJsonResponse.class})
@WebMvcTest(HealthController.class)
class GardenApplicationTests {
    @MockitoBean
    private UserMapper users;
    @MockitoBean
    private com.xiye.garden.auth.service.AuthService auth;
    @Autowired
    private MockMvc mvc;

    @Test
    void contextLoads() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
