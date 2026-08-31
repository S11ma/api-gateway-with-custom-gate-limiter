package com.gateway.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserStore userStore;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        when(userStore.isValid("seema", "password123")).thenReturn(true);
        when(userStore.rolesOf("seema")).thenReturn(List.of("USER", "ADMIN"));
        when(jwtUtil.generateToken("seema", List.of("USER", "ADMIN"))).thenReturn("fake-jwt-token");

        LoginRequest request = new LoginRequest();
        request.setUsername("seema");
        request.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt-token"))
                .andExpect(jsonPath("$.username").value("seema"));
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        when(userStore.isValid("seema", "wrong-password")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("seema");
        request.setPassword("wrong-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }
}