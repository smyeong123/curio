package com.curio.news.service;

import com.curio.shared.config.TopicConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HackerNewsFetcherTest {

    private final HackerNewsFetcher fetcher = new HackerNewsFetcher(new RestTemplateBuilder(), new ObjectMapper());

    @Test
    void parse_linksTheOriginalPage_carriesPointsForTheModel_andSkipsLinklessPosts() throws Exception {
        String body = """
            {"hits":[
              {"title":"Backdoor in Claude Code extension","url":"https://www.phorion.io/blog/x",
               "points":974,"num_comments":547,"created_at":"2026-10-30T07:09:12Z"},
              {"title":"Ask HN: Is Claude worse today?","url":null,"points":300,"num_comments":90,
               "created_at":"2026-10-30T08:00:00Z"},
              {"title":"Bad scheme","url":"javascript:alert(1)","points":100,"num_comments":1,
               "created_at":"2026-10-30T08:00:00Z"}
            ]}""";

        List<Map<String, String>> items = fetcher.parse(body);

        assertThat(items).singleElement().satisfies(m -> {
            assertThat(m).containsEntry("url", "https://www.phorion.io/blog/x")
                    .containsEntry("sourceName", "phorion.io")
                    .containsEntry("publishedAt", "2026-10-30T07:09:12Z")
                    .containsEntry("points", "974");
            assertThat(m.get("description")).isEqualTo("Discussed on Hacker News: 974 points, 547 comments.");
        });
    }

    @Test
    void everyTopicHasSearchTermsForBothKeywordSources() {
        assertThat(TopicQueries.NEWS_API.keySet()).containsExactlyInAnyOrderElementsOf(TopicConstants.ALL_TOPICS);
        assertThat(TopicQueries.HACKER_NEWS.keySet()).containsExactlyInAnyOrderElementsOf(TopicConstants.ALL_TOPICS);
    }
}
