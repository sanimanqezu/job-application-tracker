package com.sanele.jobtracker.repository;

import com.sanele.jobtracker.entity.ApplicationStatus;
import com.sanele.jobtracker.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByUserIdOrderByAppliedDateDesc(Long userId);

    List<JobApplication> findByUserIdAndStatusOrderByAppliedDateDesc(Long userId, ApplicationStatus status);

    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, ApplicationStatus status);

    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.user.id = :userId AND a.appliedDate >= :startDate AND a.appliedDate <= :endDate")
    long countByUserIdAndAppliedDateBetween(@Param("userId") Long userId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.user.id = :userId AND a.status NOT IN ('REJECTED', 'WITHDRAWN', 'OFFER')")
    long countActiveByUserId(@Param("userId") Long userId);
}
