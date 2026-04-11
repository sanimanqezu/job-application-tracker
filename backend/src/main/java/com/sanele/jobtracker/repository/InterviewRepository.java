package com.sanele.jobtracker.repository;

import com.sanele.jobtracker.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByApplicationIdOrderByInterviewDateAsc(Long applicationId);

    @Query("SELECT i FROM Interview i WHERE i.id = :id AND i.application.user.id = :userId")
    Optional<Interview> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
