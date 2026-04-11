package com.sanele.jobtracker;

import com.sanele.jobtracker.dto.JobApplicationRequest;
import com.sanele.jobtracker.dto.JobApplicationResponse;
import com.sanele.jobtracker.dto.StatusUpdateRequest;
import com.sanele.jobtracker.entity.ApplicationStatus;
import com.sanele.jobtracker.entity.JobApplication;
import com.sanele.jobtracker.entity.User;
import com.sanele.jobtracker.exception.ResourceNotFoundException;
import com.sanele.jobtracker.repository.JobApplicationRepository;
import com.sanele.jobtracker.repository.UserRepository;
import com.sanele.jobtracker.service.JobApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock
    private JobApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JobApplicationService applicationService;

    private User testUser;
    private JobApplication testApplication;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashed")
                .build();

        testApplication = JobApplication.builder()
                .id(1L)
                .user(testUser)
                .company("Acme Corp")
                .role("Software Engineer")
                .location("Cape Town")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now())
                .updatedAt(LocalDateTime.now())
                .interviews(new ArrayList<>())
                .build();
    }

    @Test
    void create_shouldReturnNewApplication() {
        JobApplicationRequest request = new JobApplicationRequest(
                "Acme Corp", "Software Engineer", "Cape Town", null,
                ApplicationStatus.APPLIED, LocalDate.now(), null, null);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.save(any(JobApplication.class))).thenReturn(testApplication);

        JobApplicationResponse response = applicationService.create("testuser", request);

        assertThat(response.company()).isEqualTo("Acme Corp");
        assertThat(response.role()).isEqualTo("Software Engineer");
        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
        verify(applicationRepository, times(1)).save(any(JobApplication.class));
    }

    @Test
    void findAll_withoutStatusFilter_shouldReturnAllUserApplications() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.findByUserIdOrderByAppliedDateDesc(1L))
                .thenReturn(List.of(testApplication));

        List<JobApplicationResponse> result = applicationService.findAll("testuser", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).company()).isEqualTo("Acme Corp");
    }

    @Test
    void findAll_withStatusFilter_shouldReturnFilteredApplications() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.findByUserIdAndStatusOrderByAppliedDateDesc(1L, ApplicationStatus.APPLIED))
                .thenReturn(List.of(testApplication));

        List<JobApplicationResponse> result = applicationService.findAll("testuser", ApplicationStatus.APPLIED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    void findById_shouldReturnApplication_whenOwned() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testApplication));

        JobApplicationResponse response = applicationService.findById("testuser", 1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.company()).isEqualTo("Acme Corp");
    }

    @Test
    void findById_shouldThrow_whenNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.findById("testuser", 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_shouldChangeApplicationFields() {
        JobApplicationRequest request = new JobApplicationRequest(
                "New Corp", "Senior Engineer", "Johannesburg", "https://jobs.newcorp.com",
                ApplicationStatus.INTERVIEW, LocalDate.now(), "R50k-R70k", "Updated notes");

        JobApplication updated = JobApplication.builder()
                .id(1L).user(testUser).company("New Corp").role("Senior Engineer")
                .location("Johannesburg").status(ApplicationStatus.INTERVIEW)
                .appliedDate(LocalDate.now()).updatedAt(LocalDateTime.now())
                .interviews(new ArrayList<>()).build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testApplication));
        when(applicationRepository.save(any(JobApplication.class))).thenReturn(updated);

        JobApplicationResponse response = applicationService.update("testuser", 1L, request);

        assertThat(response.company()).isEqualTo("New Corp");
        assertThat(response.status()).isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    void updateStatus_shouldChangeStatusOnly() {
        StatusUpdateRequest request = new StatusUpdateRequest(ApplicationStatus.OFFER);

        JobApplication withOffer = JobApplication.builder()
                .id(1L).user(testUser).company("Acme Corp").role("Software Engineer")
                .status(ApplicationStatus.OFFER).appliedDate(LocalDate.now())
                .updatedAt(LocalDateTime.now()).interviews(new ArrayList<>()).build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testApplication));
        when(applicationRepository.save(any(JobApplication.class))).thenReturn(withOffer);

        JobApplicationResponse response = applicationService.updateStatus("testuser", 1L, request);

        assertThat(response.status()).isEqualTo(ApplicationStatus.OFFER);
    }

    @Test
    void delete_shouldRemoveApplication() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(testApplication));
        doNothing().when(applicationRepository).delete(testApplication);

        assertThatCode(() -> applicationService.delete("testuser", 1L))
                .doesNotThrowAnyException();

        verify(applicationRepository, times(1)).delete(testApplication);
    }

    @Test
    void delete_shouldThrow_whenApplicationNotFound() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(applicationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.delete("testuser", 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(applicationRepository, never()).delete(any());
    }
}
