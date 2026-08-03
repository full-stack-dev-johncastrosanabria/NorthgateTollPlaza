package com.john.northgate.toll.controllers;

import com.john.northgate.toll.client.AuditClient;
import com.john.northgate.toll.config.JwtService;
import com.john.northgate.toll.dto.*;
import com.john.northgate.toll.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the role rules on the endpoints themselves. The service layer is
 * mocked out — what is under test is the @PreAuthorize wiring, which a typo in
 * a role name would otherwise break silently until someone tried it by hand.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ControllerSecurityTest {

    private static final String PASS_JSON = """
            {"vehicleClassCode":"CAR","plate":"KTR 8891","paymentMethod":"CASH"}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private PassService passService;
    @MockitoBean
    private ShiftService shiftService;
    @MockitoBean
    private LaneExceptionService laneExceptionService;
    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private AuditClient auditClient;

    private String operatorToken;
    private String managerToken;

    @BeforeEach
    void setUp() {
        operatorToken = jwtService.issue("OP-14", "R. Alvarez", "OPERATOR");
        managerToken = jwtService.issue("MG-02", "D. Okafor", "MANAGER");
    }

    @Test
    @DisplayName("only a manager may read the plaza overview")
    void dashboardIsManagerOnly() throws Exception {
        when(dashboardService.overview()).thenReturn(new DashboardResponseDto(
                BigDecimal.ZERO, 0, 0, 6, 0, 0, List.of(), List.of()));

        mockMvc.perform(get("/api/toll/dashboard").header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/toll/dashboard").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/toll/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only an operator may record a pass and open the gate")
    void recordingAPassIsOperatorOnly() throws Exception {
        when(passService.recordPass(anyString(), any(PassRequestDto.class))).thenReturn(
                new PassResponseDto(1L, "KTR 8891", "CAR", "Car", "CASH",
                        new BigDecimal("3.00"), OffsetDateTime.now()));

        mockMvc.perform(post("/api/toll/passes")
                        .header("Authorization", bearer(operatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(PASS_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/toll/passes")
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(PASS_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/toll/passes")
                        .contentType(MediaType.APPLICATION_JSON).content(PASS_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only an operator may override a lane exception")
    void overridingAnExceptionIsOperatorOnly() throws Exception {
        when(laneExceptionService.override(any(), anyString())).thenReturn(
                new ExceptionResponseDto(1L, "TSD 1190", "VIOLATION", "desc", "OVERRIDDEN",
                        OffsetDateTime.now()));

        mockMvc.perform(post("/api/toll/exceptions/1/override")
                        .header("Authorization", bearer(operatorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/toll/exceptions/1/override")
                        .header("Authorization", bearer(managerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("signing in is reachable without a token")
    void loginIsPublic() throws Exception {
        when(authService.login(any(LoginRequestDto.class))).thenReturn(
                new LoginResponseDto("token", "OP-14", "R. Alvarez", "OPERATOR", 3));

        mockMvc.perform(post("/api/toll/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffCode\":\"op-14\",\"pin\":\"1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("lane console reads need a token but not a particular role")
    void consoleReadsNeedAnyAuthenticatedStaff() throws Exception {
        when(passService.recentPasses(anyString())).thenReturn(List.of());
        when(laneExceptionService.forCurrentLane(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/toll/passes")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/toll/passes").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/toll/exceptions").header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token signed with the wrong secret is refused everywhere")
    void refusesTokenFromAnotherSecret() throws Exception {
        String forged = new JwtService("a-totally-different-secret-32-bytes-xx", 60)
                .issue("MG-02", "Not Really", "MANAGER");

        mockMvc.perform(get("/api/toll/dashboard").header("Authorization", bearer(forged)))
                .andExpect(status().isForbidden());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
