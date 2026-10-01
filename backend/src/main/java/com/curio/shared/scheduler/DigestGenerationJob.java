package com.curio.shared.scheduler;

import com.curio.shared.digest.DigestBatch;
import com.curio.shared.time.DigestDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The 05:00 KST run: the day's digest (+ quiz) for every subscriber,
 * worldwide. Readers get it at their own local delivery hour (see DigestEmailBatch).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DigestGenerationJob {

    public static final String JOB_NAME = DigestBatch.JOB_NAME;

    private final DigestBatch digestBatch;

    @Scheduled(cron = DigestDay.GENERATION_CRON, zone = DigestDay.ZONE)
    @SchedulerLock(name = "digest-generation", lockAtLeastFor = "PT5M", lockAtMostFor = "PT2H")
    public void generateDailyDigests() {
        log.info("Starting daily digest generation...");
        digestBatch.runForAll();
    }
}
