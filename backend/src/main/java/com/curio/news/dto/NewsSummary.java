package com.curio.news.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    /** When the cited article was published (ISO-8601 instant, UTC); null when the source gave no date. */
    @JsonProperty("published_at")
    private String publishedAt;
    /** Other articles in the same batch that report this story; null on digests made before it existed. */
    @JsonProperty("also_covered_by")
    private List<SourceLink> alsoCoveredBy;
}
