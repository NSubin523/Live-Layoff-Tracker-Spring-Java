package com.example.layoff_tracker.controller;
import com.example.layoff_tracker.dto.ToggleFavoriteResponseDto;
import com.example.layoff_tracker.security.FirebaseAuthValidator;
import com.example.layoff_tracker.service.UserFavoriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserFavoriteController.class)
class UserFavoriteControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserFavoriteService service;
    @MockitoBean FirebaseAuthValidator auth;
    @Test void explicitSaveUsesVerifiedIdentity() throws Exception {
        when(auth.extractUserId("Bearer valid")).thenReturn("trusted");
        when(service.setFavorite("trusted", "company", true)).thenReturn(new ToggleFavoriteResponseDto("company", true, "Company saved"));
        mvc.perform(put("/api/v1/favorites/company").header("Authorization", "Bearer valid")
            .contentType("application/json").content("{\"saved\":true,\"userId\":\"spoofed\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.companyId").value("company")).andExpect(jsonPath("$.saved").value(true));
        verify(service).setFavorite("trusted", "company", true);
    }
    @Test void missingDesiredStateIsRejected() throws Exception {
        mvc.perform(put("/api/v1/favorites/company").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectedTokenCannotSave() throws Exception {
        when(auth.extractUserId("Bearer invalid")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(put("/api/v1/favorites/company").header("Authorization", "Bearer invalid")
            .contentType("application/json").content("{\"saved\":false}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void idsEndpointReturnsMembershipWithoutHydratingCompanies() throws Exception {
        when(auth.extractUserId("Bearer valid")).thenReturn("trusted");
        when(service.getFavoriteIds("trusted")).thenReturn(List.of("missing-company"));
        mvc.perform(get("/api/v1/favorites/ids").header("Authorization", "Bearer valid"))
            .andExpect(status().isOk()).andExpect(content().json("[\"missing-company\"]"));
        verify(service, never()).getUserFavorites(anyString());
    }
    @Test void legacyToggleRemainsAvailable() throws Exception {
        when(auth.extractUserId("Bearer valid")).thenReturn("trusted");
        when(service.toggleFavorite("trusted", "company")).thenReturn(new ToggleFavoriteResponseDto("company", false, "unsaved"));
        mvc.perform(post("/api/v1/favorites/toggle").header("Authorization", "Bearer valid")
            .contentType("application/json").content("{\"companyId\":\"company\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(false));
    }
}
