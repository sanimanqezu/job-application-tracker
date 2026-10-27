package com.sanele.jobtracker.scan;

import com.sanele.jobtracker.ats.RawJob;
import com.sanele.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdzunaJobDiscoverer Unit Tests")
class AdzunaJobDiscovererTest {

    @Mock
    private AdzunaClient adzuna;

    @Mock
    private JobScanService scanService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdzunaJobDiscoverer discoverer;

    private JobScanService.SourcedJob sampleJob() {
        RawJob raw = new RawJob("Java Developer", "Cape Town",
                "https://example.com/job/1", null, "Build things.");
        return new JobScanService.SourcedJob("Acme", "Adzuna", "Cape Town", raw);
    }

    @Test
    @DisplayName("When Adzuna is not configured, nothing is searched or stored")
    void notConfigured_skipsEntirely() {
        when(adzuna.isConfigured()).thenReturn(false);

        discoverer.discover();

        verify(adzuna, never()).search(anyString(), anyInt());
        verify(scanService, never()).storeExternalJobs(anyList(), anyString());
    }

    @Test
    @DisplayName("Happy path queries the first term and stores the fetched jobs")
    void configured_searchesAndStores() {
        List<JobScanService.SourcedJob> jobs = List.of(sampleJob());
        when(adzuna.isConfigured()).thenReturn(true);
        when(userRepository.findAll()).thenReturn(List.of());
        when(adzuna.search("software developer", 1)).thenReturn(jobs);
        when(scanService.storeExternalJobs(jobs, "adzuna")).thenReturn(1);

        discoverer.discover();

        verify(adzuna).search("software developer", 1);
        verify(scanService).storeExternalJobs(jobs, "adzuna");
    }

    @Test
    @DisplayName("A downstream Adzuna outage (empty result) stores nothing and does not throw")
    void downstreamUnavailable_storesNothing() {
        when(adzuna.isConfigured()).thenReturn(true);
        when(userRepository.findAll()).thenReturn(List.of());
        when(adzuna.search(anyString(), anyInt())).thenReturn(List.of());
        when(scanService.storeExternalJobs(anyList(), eq("adzuna"))).thenReturn(0);

        assertThatCode(() -> discoverer.discover()).doesNotThrowAnyException();

        verify(scanService).storeExternalJobs(anyList(), eq("adzuna"));
    }
}
