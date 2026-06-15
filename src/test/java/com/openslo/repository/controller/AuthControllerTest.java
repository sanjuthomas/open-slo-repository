package com.openslo.repository.controller;

import com.openslo.repository.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "openslo.security.username=test",
    "openslo.security.password=test"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic("test", "test")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("test"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }
}
