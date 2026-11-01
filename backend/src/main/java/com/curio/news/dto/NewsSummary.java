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
    /**
     * How much the story matters to someone following AI models, 1 (minor update) to 5
     * (major new model, capability, pricing or availability change). Null on summaries
     * cached or stored before it existed; read it through {@link #importanceOf(Integer)}.
     */
    private Integer importance;

    public static final int MIN_IMPORTANCE = 1;
    public static final int MAX_IMPORTANCE = 5;
    /** What a story without a rating counts as: the middle of the scale. */
    public static final int DEFAULT_IMPORTANCE = 3;

    /** The rating clamped into 1-5, or {@link #DEFAULT_IMPORTANCE} when there is none. */
    public static int importanceOf(Integer importance) {
        if (importance == null) return DEFAULT_IMPORTANCE;
        return Math.max(MIN_IMPORTANCE, Math.min(MAX_IMPORTANCE, importance));
    }
}
