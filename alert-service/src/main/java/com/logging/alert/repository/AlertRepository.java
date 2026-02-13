package com.logging.alert.repository;

import com.logging.alert.entity.AlertEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<AlertEntity, UUID> {

    Page<AlertEntity> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    @Query("SELECT a FROM AlertEntity a WHERE a.ruleName = :ruleName " +
           "AND a.triggeredAt > :since ORDER BY a.triggeredAt DESC")
    Optional<AlertEntity> findRecentAlertByRuleName(
            @Param("ruleName") String ruleName,
            @Param("since") Instant since);

    Page<AlertEntity> findByRuleNameOrderByTriggeredAtDesc(String ruleName, Pageable pageable);
}
