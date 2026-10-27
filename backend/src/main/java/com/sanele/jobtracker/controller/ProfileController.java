package com.sanele.jobtracker.controller;

import com.sanele.jobtracker.dto.ProfileResponse;
import com.sanele.jobtracker.dto.ProfileUpdateRequest;
import com.sanele.jobtracker.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's career profile (CV, desired job, years) — viewed and edited later. */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(profileService.get(principal.getUsername()));
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> update(@AuthenticationPrincipal UserDetails principal,
                                                  @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(profileService.update(principal.getUsername(), request));
    }
}
