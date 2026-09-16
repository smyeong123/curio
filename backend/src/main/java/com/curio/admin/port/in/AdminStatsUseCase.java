package com.curio.admin.port.in;

import com.curio.admin.dto.JobStatusResponse;
import com.curio.admin.dto.StatsResponse;
import com.curio.admin.dto.TopicStatusResponse;

import java.util.List;
import java.util.Map;

public interface AdminStatsUseCase {
    StatsResponse getStats();
    Map<String, Long> getTopicDistribution();
    List<TopicStatusResponse> getTopicsStatus();
    List<JobStatusResponse> getJobsStatus();
}
