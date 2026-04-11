package com.sanele.jobtracker.service;

import com.sanele.jobtracker.dto.DashboardResponse;
import com.sanele.jobtracker.entity.ApplicationStatus;
import com.sanele.jobtracker.entity.User;
import com.sanele.jobtracker.exception.ResourceNotFoundException;
import com.sanele.jobtracker.repository.JobApplicationRepository;
import com.sanele.jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Long userId = user.getId();

        long total = applicationRepository.countByUserId(userId);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status.name(), applicationRepository.countByUserIdAndStatus(userId, status));
        }

        LocalDate now = LocalDate.now();
        LocalDate firstOfMonth = now.withDayOfMonth(1);
        long thisMonth = applicationRepository.countByUserIdAndAppliedDateBetween(userId, firstOfMonth, now);

        long active = applicationRepository.countActiveByUserId(userId);

        return new DashboardResponse(total, byStatus, thisMonth, active);
    }
}
