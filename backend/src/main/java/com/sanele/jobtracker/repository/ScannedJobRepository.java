package com.sanele.jobtracker.repository;

import com.sanele.jobtracker.entity.ScannedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScannedJobRepository extends JpaRepository<ScannedJob, Long> {

    Optional<ScannedJob> findByUrl(String url);

    /**
     * Openings the user has NOT already applied to (their applied job URLs are
     * excluded), with optional filters. Junior-first, newest-first.
     * <p>
     * Parameters are non-null on purpose: an empty {@code q} matches everything
     * (LIKE '%%'), an empty {@code segment} means "all", and {@code juniorOnly}
     * is a plain boolean. This avoids binding untyped NULLs, which Postgres
     * cannot infer a type for.
     */
    @Query("""
            SELECT j FROM ScannedJob j
            WHERE j.url NOT IN (
                SELECT a.jobUrl FROM JobApplication a
                WHERE a.user.id = :userId AND a.jobUrl IS NOT NULL
            )
            AND j.matchScore >= :minScore
            AND (lower(j.title) LIKE lower(concat('%', :q, '%'))
                 OR lower(j.company) LIKE lower(concat('%', :q, '%'))
                 OR lower(concat(j.stack, '')) LIKE lower(concat('%', :q, '%')))
            AND (:segment = '' OR j.segment = :segment)
            AND (:juniorOnly = FALSE OR j.junior = TRUE)
            ORDER BY j.matchScore DESC, j.junior DESC, j.firstSeenAt DESC
            """)
    List<ScannedJob> findNewForUser(@Param("userId") Long userId,
                                    @Param("q") String q,
                                    @Param("juniorOnly") boolean juniorOnly,
                                    @Param("segment") String segment,
                                    @Param("minScore") int minScore);

    /** Same filters but includes already-applied roles (the "all" view). */
    @Query("""
            SELECT j FROM ScannedJob j
            WHERE j.matchScore >= :minScore
            AND (lower(j.title) LIKE lower(concat('%', :q, '%'))
                   OR lower(j.company) LIKE lower(concat('%', :q, '%'))
                   OR lower(concat(j.stack, '')) LIKE lower(concat('%', :q, '%')))
            AND (:segment = '' OR j.segment = :segment)
            AND (:juniorOnly = FALSE OR j.junior = TRUE)
            ORDER BY j.matchScore DESC, j.junior DESC, j.firstSeenAt DESC
            """)
    List<ScannedJob> findAllMatching(@Param("q") String q,
                                     @Param("juniorOnly") boolean juniorOnly,
                                     @Param("segment") String segment,
                                     @Param("minScore") int minScore);

    @Query("""
            SELECT COUNT(j) FROM ScannedJob j
            WHERE j.url NOT IN (
                SELECT a.jobUrl FROM JobApplication a
                WHERE a.user.id = :userId AND a.jobUrl IS NOT NULL
            )
            """)
    long countNewForUser(@Param("userId") Long userId);
}
