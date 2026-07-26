package com.sanele.jobtracker.scan;

import com.sanele.jobtracker.catalog.Company;
import com.sanele.jobtracker.entity.CompanyEntity;
import com.sanele.jobtracker.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Continuously scans the company universe for jobs — one small batch every tick
 * (default 1 company / 6s ≈ 10 companies per minute), oldest-scanned first, so
 * the jobs database keeps filling and refreshing on its own. Each opening is
 * CV-scored as it is stored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyHarvester {

    private final CompanyRepository companyRepo;
    private final JobScanService scanService;

    @Value("${scanner.enabled:true}")
    private boolean enabledInitial;
    @Value("${scanner.batch:1}")
    private int batch;

    private volatile Boolean enabledOverride;

    public boolean isEnabled() {
        return enabledOverride != null ? enabledOverride : enabledInitial;
    }

    public void setEnabled(boolean enabled) {
        this.enabledOverride = enabled;
    }

    @Scheduled(fixedDelayString = "${scanner.tick-ms:6000}", initialDelay = 15000)
    public void tick() {
        if (!isEnabled()) return;
        List<CompanyEntity> next = companyRepo.nextToScan(Limit.of(Math.max(1, batch)));
        if (next.isEmpty()) return;

        List<Company> batchCompanies = next.stream().map(JobScanService::toDto).toList();
        try {
            JobScanService.ScanSummary s = scanService.scanBatch(batchCompanies, true);
            if (next.size() == 1) {
                next.get(0).setJobsFound(next.get(0).getJobsFound() + s.newlyAdded());
                if (s.newlyAdded() > 0) log.info("Harvest {} → +{} new jobs", batchCompanies.get(0).name(), s.newlyAdded());
            }
        } catch (Exception e) {
            log.warn("Harvest tick failed for {}: {}", batchCompanies.get(0).name(), e.toString());
        } finally {
            LocalDateTime now = LocalDateTime.now();
            next.forEach(c -> c.setLastScannedAt(now));
            companyRepo.saveAll(next);   // always advance, so we never get stuck on one company
        }
    }
}
