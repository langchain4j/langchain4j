package dev.langchain4j.web.search;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import dev.langchain4j.data.document.Metadata;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class WebSearchOrganicResultTest {

    @Test
    void should_build_webSearchOrganicResult_with_default_values() {
        WebSearchOrganicResult webSearchOrganicResult =
                WebSearchOrganicResult.from("title", URI.create("https://google.com"));

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.url()).hasToString("https://google.com");
        assertThat(webSearchOrganicResult.snippet()).isNull();
        assertThat(webSearchOrganicResult.content()).isNull();
        assertThat(webSearchOrganicResult.metadata()).isEmpty();
    }

    @Test
    void should_build_webSearchOrganicResult_with_custom_snippet() {
        WebSearchOrganicResult webSearchOrganicResult =
                WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet", null);

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.url()).hasToString("https://google.com");
        assertThat(webSearchOrganicResult.snippet()).isEqualTo("snippet");
        assertThat(webSearchOrganicResult.content()).isNull();
        assertThat(webSearchOrganicResult.metadata()).isEmpty();

        assertThat(webSearchOrganicResult)
                .hasToString(
                        "WebSearchOrganicResult{title='title', url=https://google.com, snippet='snippet', content='null', metadata={}}");
    }

    @Test
    void should_build_webSearchOrganicResult_with_custom_content() {
        WebSearchOrganicResult webSearchOrganicResult =
                WebSearchOrganicResult.from("title", URI.create("https://google.com"), null, "content");

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.url()).hasToString("https://google.com");
        assertThat(webSearchOrganicResult.snippet()).isNull();
        assertThat(webSearchOrganicResult.content()).isEqualTo("content");
        assertThat(webSearchOrganicResult.metadata()).isEmpty();

        assertThat(webSearchOrganicResult)
                .hasToString(
                        "WebSearchOrganicResult{title='title', url=https://google.com, snippet='null', content='content', metadata={}}");
    }

    @Test
    void should_build_webSearchOrganicResult_with_custom_title_link_and_metadata() {
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from(
                "title",
                URI.create("https://google.com"),
                "snippet",
                null,
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.url()).hasToString("https://google.com");
        assertThat(webSearchOrganicResult.snippet()).isEqualTo("snippet");
        assertThat(webSearchOrganicResult.metadata()).containsExactly(new AbstractMap.SimpleEntry<>("key", "value"));

        assertThat(webSearchOrganicResult)
                .hasToString(
                        "WebSearchOrganicResult{title='title', url=https://google.com, snippet='snippet', content='null', metadata={key=value}}");
    }

    @Test
    void equals_and_hash() {
        WebSearchOrganicResult wsor1 = WebSearchOrganicResult.from(
                "title",
                URI.create("https://google.com"),
                "snippet",
                null,
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        WebSearchOrganicResult wsor2 = WebSearchOrganicResult.from(
                "title",
                URI.create("https://google.com"),
                "snippet",
                null,
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(wsor1)
                .isEqualTo(wsor1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsor2)
                .hasSameHashCodeAs(wsor2);

        assertThat(WebSearchOrganicResult.from(
                        "other title",
                        URI.create("https://google.com"),
                        "snippet",
                        null,
                        Stream.of(new AbstractMap.SimpleEntry<>("key", "value"))
                                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.from(
                        "title",
                        URI.create("https://docs.langchain4j.dev"),
                        "snippet",
                        null,
                        Stream.of(new AbstractMap.SimpleEntry<>("key", "value"))
                                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.from(
                        "title",
                        URI.create("https://google.com"),
                        "other snippet",
                        null,
                        Stream.of(new AbstractMap.SimpleEntry<>("key", "value"))
                                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.from(
                        "title",
                        URI.create("https://google.com"),
                        "snippet",
                        null,
                        Stream.of(new AbstractMap.SimpleEntry<>("other key", "value"))
                                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);
    }

    @Test
    void should_return_textSegment() {
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from(
                "title",
                URI.create("https://google.com"),
                "snippet",
                null,
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.toTextSegment().text()).isEqualTo("title\nsnippet");
        assertThat(webSearchOrganicResult.toTextSegment().metadata())
                .isEqualTo(Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("url", "https://google.com"),
                                new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))));
    }

    @Test
    void should_return_textSegment_when_metadata_is_null_with_all_factories_and_constructors() {
        URI url = URI.create("https://google.com");

        // 1. Using factory method with content and metadata (both null)
        WebSearchOrganicResult fromWithContentAndMetadata =
                WebSearchOrganicResult.from("title", url, "snippet", null, null);

        // 2. Using factory method without content (metadata is null)
        WebSearchOrganicResult fromWithoutContent = WebSearchOrganicResult.from(
                "title", url, "snippet", null // metadata
                );

        // 3. Using constructor directly
        WebSearchOrganicResult usingConstructor = new WebSearchOrganicResult("title", url, "snippet", null, null);

        // Expected text
        String expectedText = "title\nsnippet";

        // Expected metadata
        Metadata expectedMetadata = Metadata.from(Map.of("url", "https://google.com"));

        // Assertions
        assertThat(fromWithContentAndMetadata.toTextSegment().text()).isEqualTo(expectedText);
        assertThat(fromWithContentAndMetadata.toTextSegment().metadata()).isEqualTo(expectedMetadata);

        assertThat(fromWithoutContent.toTextSegment().text()).isEqualTo(expectedText);
        assertThat(fromWithoutContent.toTextSegment().metadata()).isEqualTo(expectedMetadata);

        assertThat(usingConstructor.toTextSegment().text()).isEqualTo(expectedText);
        assertThat(usingConstructor.toTextSegment().metadata()).isEqualTo(expectedMetadata);
    }

    @Test
    void should_return_document() {
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from(
                "title",
                URI.create("https://google.com"),
                "snippet",
                null,
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.toDocument().text()).isEqualTo("title\nsnippet");
        assertThat(webSearchOrganicResult.toDocument().metadata())
                .isEqualTo(Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("url", "https://google.com"),
                                new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))));
    }

    @Test
    void should_throw_illegalArgumentException_without_title() {
        IllegalArgumentException exception = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(
                        () -> WebSearchOrganicResult.from(null, URI.create("https://google.com"), "snippet", "content"))
                .actual();
        assertThat(exception).hasMessage("title cannot be null or blank");
    }
}
