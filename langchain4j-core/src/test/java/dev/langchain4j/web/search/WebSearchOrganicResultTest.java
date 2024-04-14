package dev.langchain4j.web.search;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSearchOrganicResultTest {

    @Test
    void should_build_webSearchOrganicResult_with_default_values(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("title", URI.create("https://google.com"));

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.url().toString()).isEqualTo("https://google.com");
        assertThat(webSearchOrganicResult.snippet()).isNull();
        assertThat(webSearchOrganicResult.metadata()).isNull();
    }

    @Test
    void should_build_webSearchOrganicResult_with_custom_snippet(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet");

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.url().toString()).isEqualTo("https://google.com");
        assertThat(webSearchOrganicResult.snippet()).isEqualTo("snippet");
        assertThat(webSearchOrganicResult.metadata()).isNull();

        assertThat(webSearchOrganicResult).hasToString("WebSearchOrganicResult{title='title', url=https://google.com, snippet='snippet', metadata=null}");
    }

    @Test
    void should_build_webSearchOrganicResult_with_custom_title_link_and_metadata(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.url().toString()).isEqualTo("https://google.com");
        assertThat(webSearchOrganicResult.snippet()).isEqualTo("snippet");
        assertThat(webSearchOrganicResult.metadata()).containsExactly(new AbstractMap.SimpleEntry<>("key", "value"));

        assertThat(webSearchOrganicResult).hasToString("WebSearchOrganicResult{title='title', url=https://google.com, snippet='snippet', metadata={key=value}}");
    }

    @Test
    void test_equals_and_hash(){
        WebSearchOrganicResult wsor1 = WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        WebSearchOrganicResult wsor2 = WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(wsor1)
                .isEqualTo(wsor1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsor2)
                .hasSameHashCodeAs(wsor2);

        assertThat(WebSearchOrganicResult.from("other title", URI.create("https://google.com"), "snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.from("title", URI.create("https://docs.langchain4j.dev"), "snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.from("title", URI.create("https://google.com"), "other snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("other key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);
    }

    @Test
    void should_return_textSegment(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.toTextSegment().text()).isEqualTo("snippet");
        assertThat(webSearchOrganicResult.toTextSegment().metadata()).isEqualTo(
                Metadata.from(Stream.of(
                        new AbstractMap.SimpleEntry<>("title", "title"),
                        new AbstractMap.SimpleEntry<>("url", "https://google.com"),
                        new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
        );
    }

    @Test
    void should_return_document(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.toDocument().text()).isEqualTo("snippet");
        assertThat(webSearchOrganicResult.toDocument().metadata()).isEqualTo(
                Metadata.from(Stream.of(
                        new AbstractMap.SimpleEntry<>("title", "title"),
                        new AbstractMap.SimpleEntry<>("url", "https://google.com"),
                        new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
        );
    }

    @Test
    void should_throw_illegalArgumentException_without_content(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> WebSearchOrganicResult.from("title", URI.create("https://google.com"), null));
        assertThat(exception).hasMessage("snippet cannot be null or blank");
    }
}
