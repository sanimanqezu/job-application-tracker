package com.sanele.jobtracker.controller;

import com.sanele.jobtracker.dto.InterviewRequest;
import com.sanele.jobtracker.dto.InterviewResponse;
import com.sanele.jobtracker.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/api/applications/{applicationId}/interviews")
    public ResponseEntity<InterviewResponse> addInterview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long applicationId,
            @Valid @RequestBody InterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewService.addInterview(userDetails.getUsername(), applicationId, request));
    }

    @GetMapping("/api/applications/{applicationId}/interviews")
    public ResponseEntity<List<InterviewResponse>> getInterviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(interviewService.getInterviews(userDetails.getUsername(), applicationId));
    }

    @DeleteMapping("/api/interviews/{id}")
    public ResponseEntity<Void> deleteInterview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        interviewService.deleteInterview(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
