package com.curio.shared.scheduler;

import com.curio.shared.digest.DigestEmailBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly at :00 UTC. Each tick sends to the subscribers whose local delivery
 * hour has arrived (the batch does the per-user timezone gating).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailSendJob {

    public static final String JOB_NAME = DigestEmailBatch.JOB_NAME;

    private final DigestEmailBatch emailBatch;

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @SchedulerLock(name = "email-send", lockAtLeastFor = "PT5M", lockAtMostFor = "PT1H")
    public void sendDailyEmails() {
        log.info("Starting daily email send job...");
        emailBatch.sendDue();
    }
}
