package com.sanele.jobtracker.repository;

import com.sanele.jobtracker.entity.CompanyEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {

    boolean existsByName(String name);

    java.util.Optional<CompanyEntity> findByName(String name);

    /** Companies to scan next: never-scanned first (NULLS FIRST), then oldest. */
    @Query("SELECT c FROM CompanyEntity c ORDER BY c.lastScannedAt ASC NULLS FIRST, c.id ASC")
    List<CompanyEntity> nextToScan(Limit limit);

    long countByLastScannedAtIsNotNull();
}
