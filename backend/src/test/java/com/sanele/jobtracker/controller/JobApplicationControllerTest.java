package com.sanele.jobtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanele.jobtracker.dto.JobApplicationRequest;
import com.sanele.jobtracker.dto.JobApplicationResponse;
import com.sanele.jobtracker.dto.StatusUpdateRequest;
import com.sanele.jobtracker.entity.ApplicationStatus;
import com.sanele.jobtracker.exception.ResourceNotFoundException;
import com.sanele.jobtracker.security.JwtAuthFilter;
import com.sanele.jobtracker.service.JobApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobApplicationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "testuser")
@DisplayName("JobApplicationController Slice Tests")
class JobApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobApplicationService applicationService;

    private JobApplicationResponse sampleResponse() {
        return new JobApplicationResponse(1L, "Acme Corp", "Software Engineer", "Cape Town",
                null, ApplicationStatus.APPLIED, LocalDate.now(), null, null, LocalDateTime.now(), 0);
    }

    @Test
    @DisplayName("POST creates an application and returns 201")
    void create_returnsCreated() throws Exception {
        JobApplicationRequest request = new JobApplicationRequest(
                "Acme Corp", "Software Engineer", "Cape Town", null,
                ApplicationStatus.APPLIED, LocalDate.now(), null, null);
        when(applicationService.create(eq("testuser"), any(JobApplicationRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Acme Corp"));

        verify(applicationService).create(eq("testuser"), any(JobApplicationRequest.class));
    }

    @Test
    @DisplayName("POST with a blank company returns 400 with validation errors")
    void create_blankCompany_returnsBadRequest() throws Exception {
        JobApplicationRequest request = new JobApplicationRequest(
                "", "Software Engineer", "Cape Town", null,
                ApplicationStatus.APPLIED, LocalDate.now(), null, null);

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.company").exists());
    }

    @Test
    @DisplayName("GET lists the user's applications")
    void getAll_returnsOk() throws Exception {
        when(applicationService.findAll("testuser", null))
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("Software Engineer"));
    }

    @Test
    @DisplayName("GET by id returns the application")
    void getById_returnsOk() throws Exception {
        when(applicationService.findById("testuser", 1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/applications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("GET by a missing id is mapped to 404 by the exception handler")
    void getById_notFound_returns404() throws Exception {
        when(applicationService.findById("testuser", 99L))
                .thenThrow(new ResourceNotFoundException("Application not found with id: 99"));

        mockMvc.perform(get("/api/applications/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Application not found with id: 99"));
    }

    @Test
    @DisplayName("PATCH status delegates to the service and returns 200")
    void updateStatus_returnsOk() throws Exception {
        StatusUpdateRequest request = new StatusUpdateRequest(ApplicationStatus.OFFER);
        JobApplicationResponse offered = new JobApplicationResponse(1L, "Acme Corp", "Software Engineer",
                "Cape Town", null, ApplicationStatus.OFFER, LocalDate.now(), null, null, LocalDateTime.now(), 0);
        when(applicationService.updateStatus(eq("testuser"), eq(1L), any(StatusUpdateRequest.class)))
                .thenReturn(offered);

        mockMvc.perform(patch("/api/applications/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFER"));
    }

    @Test
    @DisplayName("PATCH status with a null status returns 400")
    void updateStatus_nullStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/applications/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE returns 204 and delegates to the service")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/applications/1"))
                .andExpect(status().isNoContent());

        verify(applicationService).delete("testuser", 1L);
    }
}
