package com.logging.monitoring.service;

import com.logging.monitoring.dto.LogResponse;
import com.logging.monitoring.dto.PagedResponse;
import com.logging.monitoring.dto.ServiceLogCount;
import com.logging.monitoring.dto.SeverityCount;
import com.logging.monitoring.entity.LogEntity;
import com.logging.monitoring.repository.LogRepository;
import com.logging.monitoring.repository.LogSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final LogRepository logRepository;

    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public PagedResponse<LogResponse> searchLogs(
            String serviceName,
            String severity,
            String traceId,
            Instant startTime,
            Instant endTime,
            int page,
            int size
    ) {
        log.debug("Searching logs: service={}, severity={}, traceId={}, start={}, end={}, page={}, size={}",
                serviceName, severity, traceId, startTime, endTime, page, size);

        Specification<LogEntity> spec = Specification.where(LogSpecification.hasServiceName(serviceName))
                .and(LogSpecification.hasSeverity(severity))
                .and(LogSpecification.hasTraceId(traceId))
                .and(LogSpecification.timestampAfter(startTime))
                .and(LogSpecification.timestampBefore(endTime));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<LogEntity> logPage = logRepository.findAll(spec, pageable);

        Page<LogResponse> responsePage = logPage.map(LogResponse::from);
        return PagedResponse.from(responsePage);
    }

    public Optional<LogResponse> getLogById(UUID id) {
        log.debug("Getting log by id: {}", id);
        return logRepository.findById(id).map(LogResponse::from);
    }

    public List<LogResponse> getLogsByTraceId(String traceId) {
        log.debug("Getting logs by traceId: {}", traceId);
        return logRepository.findByTraceIdOrderByTimestampAsc(traceId)
                .stream()
                .map(LogResponse::from)
                .toList();
    }

    public List<SeverityCount> getCountsBySeverity() {
        log.debug("Getting counts by severity");
        return logRepository.countBySeverity()
                .stream()
                .map(row -> new SeverityCount((String) row[0], (Long) row[1]))
                .toList();
    }

    public List<ServiceLogCount> getCountsByService() {
        log.debug("Getting counts by service");
        return logRepository.countByServiceName()
                .stream()
                .map(row -> new ServiceLogCount((String) row[0], (Long) row[1]))
                .toList();
    }

    public List<SeverityCount> getCountsBySeverityForService(String serviceName) {
        log.debug("Getting counts by severity for service: {}", serviceName);
        return logRepository.countBySeverityForService(serviceName)
                .stream()
                .map(row -> new SeverityCount((String) row[0], (Long) row[1]))
                .toList();
    }
}
