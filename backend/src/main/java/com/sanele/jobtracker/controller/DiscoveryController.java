package com.sanele.jobtracker.controller;

import com.sanele.jobtracker.catalog.Company;
import com.sanele.jobtracker.catalog.CompanyCatalog;
import com.sanele.jobtracker.dto.DiscoverStats;
import com.sanele.jobtracker.dto.JobApplicationRequest;
import com.sanele.jobtracker.dto.JobApplicationResponse;
import com.sanele.jobtracker.dto.ScannedJobResponse;
import com.sanele.jobtracker.entity.ApplicationStatus;
import com.sanele.jobtracker.entity.ScannedJob;
import com.sanele.jobtracker.entity.User;
import com.sanele.jobtracker.exception.ResourceNotFoundException;
import com.sanele.jobtracker.repository.CompanyRepository;
import com.sanele.jobtracker.repository.JobApplicationRepository;
import com.sanele.jobtracker.repository.ScannedJobRepository;
import com.sanele.jobtracker.repository.UserRepository;
import com.sanele.jobtracker.scan.CompanyHarvester;
import com.sanele.jobtracker.scan.JobScanService;
import com.sanele.jobtracker.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Live job discovery: scan company ATS boards, then surface only the openings
 * the signed-in user has NOT already applied to. Applying records a
 * {@link com.sanele.jobtracker.entity.JobApplication}, so the row drops out of
 * "new" and appears on the board.
 */
@RestController
@RequestMapping("/api/discover")
@RequiredArgsConstructor
public class DiscoveryController {

    private final JobScanService scanService;
    private final ScannedJobRepository scannedJobRepository;
    private final UserRepository userRepository;
    private final CompanyCatalog catalog;
    private final JobApplicationService applicationService;
    private final CompanyRepository companyRepository;
    private final CompanyHarvester harvester;
    private final JobApplicationRepository jobApplicationRepository;

    private User user(UserDetails principal) {
        return userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal.getUsername()));
    }

    /** New openings for this user (or all, if onlyNew=false), with optional filters. */
    @GetMapping
    public ResponseEntity<List<ScannedJobResponse>> list(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "true") boolean onlyNew,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean junior,
            @RequestParam(required = false) String segment,
            @RequestParam(defaultValue = "0") int minScore) {
        String query = q == null ? "" : q.trim();
        String seg = segment == null ? "" : segment.trim();
        boolean juniorOnly = Boolean.TRUE.equals(junior);
        int floor = Math.max(16, minScore);   // never return matches of 15% or lower
        List<ScannedJob> jobs = onlyNew
                ? scannedJobRepository.findNewForUser(user(principal).getId(), query, juniorOnly, seg, floor)
                : scannedJobRepository.findAllMatching(query, juniorOnly, seg, floor);
        return ResponseEntity.ok(jobs.stream().map(ScannedJobResponse::from).toList());
    }

    @GetMapping("/stats")
    public ResponseEntity<DiscoverStats> stats(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(new DiscoverStats(
                scannedJobRepository.count(),
                scannedJobRepository.countNewForUser(user(principal).getId()),
                companyRepository.count()));   // live DB count (grows), not the fixed seed catalog
    }

    /** The bundled company/recruiter directory (for the directory tab). */
    @GetMapping("/companies")
    public ResponseEntity<List<Company>> companies() {
        return ResponseEntity.ok(catalog.all());
    }

    /** Re-run the live scan. probe=true is the thorough (slower) sweep. */
    @PostMapping("/scan")
    public ResponseEntity<JobScanService.ScanSummary> scan(@RequestParam(defaultValue = "false") boolean probe) {
        return ResponseEntity.ok(scanService.scan(probe));
    }

    /** Delete jobs already stored with a non-South-African location (backlog cleanup). */
    @PostMapping("/cleanup-location")
    public ResponseEntity<Map<String, Object>> cleanupLocation() {
        int removed = scanService.cleanupNonSouthAfrican();
        return ResponseEntity.ok(Map.of("removed", removed, "remaining", scannedJobRepository.count()));
    }

    /** Background harvester status: how much of the company universe is scanned. */
    @GetMapping("/harvest")
    public ResponseEntity<Map<String, Object>> harvestStatus() {
        long total = companyRepository.count();
        long scanned = companyRepository.countByLastScannedAtIsNotNull();
        return ResponseEntity.ok(Map.of(
                "enabled", harvester.isEnabled(),
                "companiesTotal", total,
                "companiesScanned", scanned,
                "companiesPending", total - scanned,
                "jobsTotal", scannedJobRepository.count()));
    }

    /** Pause or resume the background harvester. */
    @PostMapping("/harvest")
    public ResponseEntity<Map<String, Object>> setHarvest(@RequestParam boolean enabled) {
        harvester.setEnabled(enabled);
        return ResponseEntity.ok(Map.of("enabled", harvester.isEnabled()));
    }

    /** Record an application for a scanned job (drops it from "new", adds it to the board). */
    @PostMapping("/{id}/apply")
    public ResponseEntity<JobApplicationResponse> apply(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id) {
        ScannedJob job = scannedJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scanned job not found: " + id));
        // Don't apply to the same link twice — return the existing application if there is one.
        var already = jobApplicationRepository.findFirstByUserIdAndJobUrl(user(principal).getId(), job.getUrl());
        if (already.isPresent()) {
            return ResponseEntity.ok(JobApplicationResponse.from(already.get()));
        }
        JobApplicationRequest req = new JobApplicationRequest(
                job.getCompany(),
                job.getTitle(),
                job.getLocation(),
                job.getUrl(),
                ApplicationStatus.APPLIED,
                LocalDate.now(),
                null,
                "Added from Discover · " + job.getSource());
        return ResponseEntity.ok(applicationService.create(principal.getUsername(), req));
    }
}
