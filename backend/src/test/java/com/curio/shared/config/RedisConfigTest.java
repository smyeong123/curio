package com.curio.shared.config;

import com.curio.news.dto.NewsSummary;
import com.curio.news.dto.SourceLink;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;
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
                .importance(5)
                .build();
        List<NewsSummary> cached = new ArrayList<>(List.of(summary));

        Object back = serializer.deserialize(serializer.serialize(cached));

        assertThat(back).isEqualTo(cached);
        assertThat(((List<?>) back).get(0)).extracting("importance").isEqualTo(5);
    }

    @Test
    void summariesCachedBeforeImportanceExisted_stillDeserialize_withNoImportance() {
        GenericJackson2JsonRedisSerializer serializer = new RedisConfig().allowlistedJsonSerializer();
        NewsSummary legacy = NewsSummary.builder().headline("H").summary("S").topic("Claude").build();
        String json = new String(serializer.serialize(new ArrayList<>(List.of(legacy))), StandardCharsets.UTF_8);
        // An entry written by the previous release has no "importance" key at all.
        String withoutKey = json.replace(",\"importance\":null", "");
        assertThat(withoutKey).doesNotContain("importance");

        Object back = serializer.deserialize(withoutKey.getBytes(StandardCharsets.UTF_8));

        assertThat(((List<?>) back).get(0)).isInstanceOfSatisfying(NewsSummary.class,
                s -> assertThat(s.getImportance()).isNull());
    }
}
