package com.sanele.jobtracker.service;

import com.sanele.jobtracker.dto.ProfileResponse;
import com.sanele.jobtracker.dto.ProfileUpdateRequest;
import com.sanele.jobtracker.entity.User;
import com.sanele.jobtracker.exception.ResourceNotFoundException;
import com.sanele.jobtracker.repository.UserRepository;
import com.sanele.jobtracker.scan.JobCategoryEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final JobCategoryEstimator categoryEstimator;

    public ProfileResponse get(String username) {
        return ProfileResponse.from(load(username));
    }

    /** Update the CV / desired job / years and re-estimate the occupation category. */
    @Transactional
    public ProfileResponse update(String username, ProfileUpdateRequest req) {
        User user = load(username);
        user.setCv(req.cv());
        user.setCvFileName(req.cvFileName());
        user.setDesiredJob(req.desiredJob());
        user.setYearsExperience(req.yearsExperience());
        user.setJobCategory(categoryEstimator.estimate(req.desiredJob(), req.cv()));
        userRepository.save(user);
        return ProfileResponse.from(user);
    }

    private User load(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
