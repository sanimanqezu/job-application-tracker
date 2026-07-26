package com.sanele.jobtracker.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/** Loads the bundled SA company/recruiter directory (companies.json) once at startup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyCatalog {

    private final ObjectMapper objectMapper;
    private List<Company> companies = List.of();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("companies.json").getInputStream()) {
            companies = List.of(objectMapper.readValue(in, Company[].class));
            log.info("Loaded {} companies into the catalog", companies.size());
        } catch (Exception e) {
            log.error("Could not load companies.json", e);
        }
    }

    public List<Company> all() {
        return companies;
    }
}
