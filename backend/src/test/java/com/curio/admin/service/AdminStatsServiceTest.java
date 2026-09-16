package com.curio.admin.service;

import com.curio.admin.dto.JobStatusResponse;
import com.curio.admin.port.in.AdminManualJobUseCase;
import com.curio.news.port.out.DigestPort;
import com.curio.quiz.port.out.QuizAttemptPort;
import com.curio.shared.scheduler.CleanupJob;
import com.curio.shared.scheduler.DigestGenerationJob;
import com.curio.shared.scheduler.EmailSendJob;
import com.curio.shared.scheduler.JobStatusRegistry;
import com.curio.user.port.out.UserPort;
import com.curio.user.port.out.UserPreferencesPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock private UserPort userPort;
    @Mock private UserPreferencesPort userPreferencesPort;
    @Mock private DigestPort digestPort;
    @Mock private QuizAttemptPort quizAttemptPort;
    @Mock private JobStatusRegistry jobStatusRegistry;
    @Mock private AdminManualJobUseCase adminManualJobService;

    @InjectMocks private AdminStatsService service;

    private JobStatusResponse jobNamed(List<JobStatusResponse> jobs, String name) {
        return jobs.stream().filter(j -> j.getJobName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void getJobsStatus_reflectsLiveRunningFlagsFromManualJobService() {
        // registry has no history — running must still come through
        when(jobStatusRegistry.getStatus(DigestGenerationJob.JOB_NAME)).thenReturn(null);
        when(jobStatusRegistry.getStatus(EmailSendJob.JOB_NAME)).thenReturn(null);
        when(jobStatusRegistry.getStatus(CleanupJob.JOB_NAME)).thenReturn(null);
        when(adminManualJobService.isDigestGenerationRunning()).thenReturn(true);
        when(adminManualJobService.isEmailSendRunning()).thenReturn(false);

        List<JobStatusResponse> jobs = service.getJobsStatus();

        assertThat(jobNamed(jobs, DigestGenerationJob.JOB_NAME).isRunning()).isTrue();
        assertThat(jobNamed(jobs, EmailSendJob.JOB_NAME).isRunning()).isFalse();
        // cleanup has no manual trigger, so it is never reported as running
        assertThat(jobNamed(jobs, CleanupJob.JOB_NAME).isRunning()).isFalse();
    }

    @Test
    void getJobsStatus_runningFlagIsIndependentOfLastRunHistory() {
        when(jobStatusRegistry.getStatus(DigestGenerationJob.JOB_NAME)).thenReturn(java.util.Map.of(
                "lastStatus", "SUCCESS",
                "lastRanAt", java.time.LocalDateTime.now().toString(),
                "lastResult", java.util.Map.of("successCount", 3)));
        when(jobStatusRegistry.getStatus(EmailSendJob.JOB_NAME)).thenReturn(null);
        when(jobStatusRegistry.getStatus(CleanupJob.JOB_NAME)).thenReturn(null);
        when(adminManualJobService.isDigestGenerationRunning()).thenReturn(true);
        when(adminManualJobService.isEmailSendRunning()).thenReturn(false);

        List<JobStatusResponse> jobs = service.getJobsStatus();

        JobStatusResponse digest = jobNamed(jobs, DigestGenerationJob.JOB_NAME);
        assertThat(digest.getLastStatus()).isEqualTo("SUCCESS");
        assertThat(digest.isRunning()).isTrue();
    }
}
