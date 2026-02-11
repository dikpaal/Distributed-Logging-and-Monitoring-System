package com.logging.monitoring.repository;

import com.logging.monitoring.entity.LogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LogRepository extends JpaRepository<LogEntity, UUID>, JpaSpecificationExecutor<LogEntity> {

    List<LogEntity> findByTraceIdOrderByTimestampAsc(String traceId);

    @Query("SELECT l.severity, COUNT(l) FROM LogEntity l GROUP BY l.severity")
    List<Object[]> countBySeverity();

    @Query("SELECT l.serviceName, COUNT(l) FROM LogEntity l GROUP BY l.serviceName ORDER BY COUNT(l) DESC")
    List<Object[]> countByServiceName();

    @Query("SELECT l.severity, COUNT(l) FROM LogEntity l WHERE l.serviceName = :serviceName GROUP BY l.severity")
    List<Object[]> countBySeverityForService(@Param("serviceName") String serviceName);
}
