package com.curio.shared.config;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.SourceLink;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    @Test
    void cachedSummaries_roundTripThroughTheAllowlistedSerializer_includingSourceLinks() {
        GenericJackson2JsonRedisSerializer serializer = new RedisConfig().allowlistedJsonSerializer();
        NewsSummary summary = NewsSummary.builder()
                .headline("H").summary("S").whyItMatters("W")
                .sourceUrl("https://lab.example.com/post").sourceName("Lab Blog").topic("Claude")
                .publishedAt("2026-10-30T14:00:00Z")
                .alsoCoveredBy(new ArrayList<>(List.of(new SourceLink("News Site", "https://news.example.com/story"))))
                .build();
        List<NewsSummary> cached = new ArrayList<>(List.of(summary));

        Object back = serializer.deserialize(serializer.serialize(cached));

        assertThat(back).isEqualTo(cached);
    }
}
