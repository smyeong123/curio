package com.curio.news.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSummary {
    private String headline;
    private String summary;
    @JsonProperty("why_it_matters")
    private String whyItMatters;
    @JsonProperty("source_url")
    private String sourceUrl;
    @JsonProperty("source_name")
    private String sourceName;
    private String topic;
}
