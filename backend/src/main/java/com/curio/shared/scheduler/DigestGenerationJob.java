package com.curio.shared.scheduler;

import com.curio.shared.digest.DigestBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The 06:00 UTC edition: today's digest (+ quiz) for every subscriber. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DigestGenerationJob {

    public static final String JOB_NAME = DigestBatch.JOB_NAME;

    private final DigestBatch digestBatch;

    @Scheduled(cron = "0 0 6 * * *", zone = "UTC")
    @SchedulerLock(name = "digest-generation", lockAtLeastFor = "PT5M", lockAtMostFor = "PT2H")
    public void generateDailyDigests() {
        log.info("Starting daily digest generation...");
        digestBatch.runForAll();
    }
}
