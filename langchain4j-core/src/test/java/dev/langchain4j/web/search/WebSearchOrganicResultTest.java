package dev.langchain4j.web.search;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSearchOrganicResultTest {

    @Test
    void should_build_webSearchOrganicResult_with_default_values(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.webSearchOrganicResult("content");

        assertThat(webSearchOrganicResult.title()).isEqualTo("noTitle");
        assertThat(webSearchOrganicResult.link()).isEqualTo("noLink");
        assertThat(webSearchOrganicResult.content()).isEqualTo("content");
        assertThat(webSearchOrganicResult.resultMetadata()).isEmpty();

        assertThat(webSearchOrganicResult).hasToString("WebSearchOrganicResult{title='noTitle', link='noLink', content='content', resultMetadata={}}");
    }

    @Test
    void should_build_webSearchOrganicResult_with_custom_title_and_link(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.webSearchOrganicResult("title", "link", "content");

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.link()).isEqualTo("link");
        assertThat(webSearchOrganicResult.content()).isEqualTo("content");
        assertThat(webSearchOrganicResult.resultMetadata()).isEmpty();

        assertThat(webSearchOrganicResult).hasToString("WebSearchOrganicResult{title='title', link='link', content='content', resultMetadata={}}");
    }

    @Test
    void should_build_webSearchOrganicResult_with_custom_title_link_and_metadata(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.webSearchOrganicResult("title", "link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.link()).isEqualTo("link");
        assertThat(webSearchOrganicResult.content()).isEqualTo("content");
        assertThat(webSearchOrganicResult.resultMetadata()).containsExactly(new AbstractMap.SimpleEntry<>("key", "value"));

        assertThat(webSearchOrganicResult).hasToString("WebSearchOrganicResult{title='title', link='link', content='content', resultMetadata={key=value}}");
    }

    @Test
    void test_equals_and_hash(){
        WebSearchOrganicResult wsor1 = WebSearchOrganicResult.webSearchOrganicResult("title", "link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        WebSearchOrganicResult wsor2 = WebSearchOrganicResult.webSearchOrganicResult("title", "link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(wsor1)
                .isEqualTo(wsor1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsor2)
                .hasSameHashCodeAs(wsor2);

        assertThat(WebSearchOrganicResult.webSearchOrganicResult("other title", "link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.webSearchOrganicResult("title", "other link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.webSearchOrganicResult("title", "link", "other content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);

        assertThat(WebSearchOrganicResult.webSearchOrganicResult("title", "link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("other key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .isNotEqualTo(wsor1);
    }

    @Test
    void should_return_textSegment(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.webSearchOrganicResult("title", "link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.toTextSegment().text()).isEqualTo("content");
        assertThat(webSearchOrganicResult.toTextSegment().metadata()).isEqualTo(
                Metadata.from(Stream.of(
                        new AbstractMap.SimpleEntry<>("link", "link"),
                        new AbstractMap.SimpleEntry<>("title", "title"),
                        new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
        );
    }

    @Test
    void should_return_document(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.webSearchOrganicResult("title", "link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.toDocument().text()).isEqualTo("content");
        assertThat(webSearchOrganicResult.toDocument().metadata()).isEqualTo(
                Metadata.from(Stream.of(
                        new AbstractMap.SimpleEntry<>("link", "link"),
                        new AbstractMap.SimpleEntry<>("title", "title"),
                        new AbstractMap.SimpleEntry<>("key", "value"))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
        );
    }

    @Test
    void should_return_webSearchOrganicResult_from_content(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("content");

        assertThat(webSearchOrganicResult.title()).isEqualTo("noTitle");
        assertThat(webSearchOrganicResult.link()).isEqualTo("noLink");
        assertThat(webSearchOrganicResult.content()).isEqualTo("content");
        assertThat(webSearchOrganicResult.resultMetadata()).isEmpty();
    }

    @Test
    void should_return_webSearchOrganicResult_from_content_and_link(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("content", "link");

        assertThat(webSearchOrganicResult.title()).isEqualTo("noTitle");
        assertThat(webSearchOrganicResult.link()).isEqualTo("link");
        assertThat(webSearchOrganicResult.content()).isEqualTo("content");
        assertThat(webSearchOrganicResult.resultMetadata()).isEmpty();
    }

    @Test
    void should_return_webSearchOrganicResult_from_title_link_and_content(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("title", "link", "content");

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.link()).isEqualTo("link");
        assertThat(webSearchOrganicResult.content()).isEqualTo("content");
        assertThat(webSearchOrganicResult.resultMetadata()).isEmpty();
    }

    @Test
    void should_return_webSearchOrganicResult_from_title_link_content_and_metadata(){
        WebSearchOrganicResult webSearchOrganicResult = WebSearchOrganicResult.from("title", "link", "content",
                Stream.of(new AbstractMap.SimpleEntry<>("key", "value")).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));

        assertThat(webSearchOrganicResult.title()).isEqualTo("title");
        assertThat(webSearchOrganicResult.link()).isEqualTo("link");
        assertThat(webSearchOrganicResult.content()).isEqualTo("content");
        assertThat(webSearchOrganicResult.resultMetadata()).containsExactly(new AbstractMap.SimpleEntry<>("key", "value"));
    }

    @Test
    void should_throw_illegalArgumentException_without_title(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            WebSearchOrganicResult.from(null, "link", "content");
        });
        assertThat(exception).hasMessage("title cannot be null");
    }

    @Test
    void should_throw_illegalArgumentException_without_link(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            WebSearchOrganicResult.from("title", null, "content");
        });
        assertThat(exception).hasMessage("link cannot be null");
    }

    @Test
    void should_throw_illegalArgumentException_without_content(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            WebSearchOrganicResult.from("title", "link", null);
        });
        assertThat(exception).hasMessage("content cannot be null or blank");
    }
}
