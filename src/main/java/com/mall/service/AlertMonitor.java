package com.mall.service;

import com.mall.config.MallProperties;
import com.mall.dto.AlertSnapshot;
import com.mall.mapper.OutboxMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AlertMonitor {

    private final OutboxMapper outboxMapper;
    private final MallProperties mallProperties;
    private final AtomicInteger open = new AtomicInteger();
    private volatile boolean wasOpen;

    public AlertMonitor(OutboxMapper outboxMapper, MallProperties mallProperties, MeterRegistry meterRegistry) {
        this.outboxMapper = outboxMapper;
        this.mallProperties = mallProperties;
        Gauge.builder("mall.alert.open", open, AtomicInteger::get)
                .description("1 when outbox has failed or stale pending events")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${mall.alert.scan-ms:15000}")
    public void scheduledScan() {
        refresh();
    }

    public AlertSnapshot refresh() {
        Instant staleBefore = Instant.now().minus(mallProperties.getAlert().getPendingStale());
        int failed = outboxMapper.countByStatus("FAILED");
        int stale = outboxMapper.countStalePending(staleBefore);
        boolean nowOpen = failed > 0 || stale > 0;
        open.set(nowOpen ? 1 : 0);
        if (nowOpen) {
            log.error("ALERT mall outboxFailed={} stalePending={}", failed, stale);
        } else if (wasOpen) {
            log.info("ALERT cleared mall outbox backlog recovered");
        }
        wasOpen = nowOpen;
        return AlertSnapshot.builder()
                .outboxFailed(failed)
                .outboxStalePending(stale)
                .open(nowOpen)
                .build();
    }
}
