package com.sanele.jobtracker.service;

import com.sanele.jobtracker.dto.JobApplicationRequest;
import com.sanele.jobtracker.dto.JobApplicationResponse;
import com.sanele.jobtracker.dto.StatusUpdateRequest;
import com.sanele.jobtracker.entity.ApplicationStatus;
import com.sanele.jobtracker.entity.JobApplication;
import com.sanele.jobtracker.entity.User;
import com.sanele.jobtracker.exception.ResourceNotFoundException;
import com.sanele.jobtracker.exception.UnauthorizedException;
import com.sanele.jobtracker.repository.JobApplicationRepository;
import com.sanele.jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    @Transactional
    public JobApplicationResponse create(String username, JobApplicationRequest request) {
        User user = getUser(username);

        JobApplication application = JobApplication.builder()
                .user(user)
                .company(request.company())
                .role(request.role())
                .location(request.location())
                .jobUrl(request.jobUrl())
                .status(request.status())
                .appliedDate(request.appliedDate())
                .salaryRange(request.salaryRange())
                .notes(request.notes())
                .build();

        return JobApplicationResponse.from(applicationRepository.save(application));
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> findAll(String username, ApplicationStatus status) {
        User user = getUser(username);

        List<JobApplication> applications;
        if (status != null) {
            applications = applicationRepository.findByUserIdAndStatusOrderByAppliedDateDesc(user.getId(), status);
        } else {
            applications = applicationRepository.findByUserIdOrderByAppliedDateDesc(user.getId());
        }

        return applications.stream()
                .map(JobApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse findById(String username, Long id) {
        User user = getUser(username);
        JobApplication application = applicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        return JobApplicationResponse.from(application);
    }

    @Transactional
    public JobApplicationResponse update(String username, Long id, JobApplicationRequest request) {
        User user = getUser(username);
        JobApplication application = applicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));

        application.setCompany(request.company());
        application.setRole(request.role());
        application.setLocation(request.location());
        application.setJobUrl(request.jobUrl());
        application.setStatus(request.status());
        application.setAppliedDate(request.appliedDate());
        application.setSalaryRange(request.salaryRange());
        application.setNotes(request.notes());

        return JobApplicationResponse.from(applicationRepository.save(application));
    }

    @Transactional
    public JobApplicationResponse updateStatus(String username, Long id, StatusUpdateRequest request) {
        User user = getUser(username);
        JobApplication application = applicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));

        application.setStatus(request.status());
        return JobApplicationResponse.from(applicationRepository.save(application));
    }

    @Transactional
    public void delete(String username, Long id) {
        User user = getUser(username);
        JobApplication application = applicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        applicationRepository.delete(application);
    }
}
