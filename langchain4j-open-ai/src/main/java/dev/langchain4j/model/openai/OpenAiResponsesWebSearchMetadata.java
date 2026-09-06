package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.copy;

import java.util.List;
import java.util.Objects;

/**
 * Metadata about the {@code web_search} server tool of the OpenAI Responses API: the search
 * queries that were executed and the citations ({@code url_citation} annotations) contained in
 * the answer.
 *
 * <p>Exposed via {@link OpenAiResponsesChatResponseMetadata#webSearchMetadata()}, mirroring how
 * the Gemini integration exposes grounding metadata.
 */
public class OpenAiResponsesWebSearchMetadata {

    private final List<String> searchQueries;
    private final List<UrlCitation> citations;

    private OpenAiResponsesWebSearchMetadata(Builder builder) {
        this.searchQueries = copy(builder.searchQueries);
        this.citations = copy(builder.citations);
    }

    /**
     * @return the queries that were executed for this response, in order. May be empty.
     */
    public List<String> searchQueries() {
        return searchQueries;
    }

    /**
     * @return the {@code url_citation} annotations contained in the answer, in order. May be empty.
     */
    public List<UrlCitation> citations() {
        return citations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiResponsesWebSearchMetadata that = (OpenAiResponsesWebSearchMetadata) o;
        return Objects.equals(searchQueries, that.searchQueries) && Objects.equals(citations, that.citations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchQueries, citations);
    }

    @Override
    public String toString() {
        return "OpenAiResponsesWebSearchMetadata{" + "searchQueries=" + searchQueries + ", citations=" + citations
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<String> searchQueries;
        private List<UrlCitation> citations;

        public Builder searchQueries(List<String> searchQueries) {
            this.searchQueries = searchQueries;
            return this;
        }

        public Builder citations(List<UrlCitation> citations) {
            this.citations = citations;
            return this;
        }

        public OpenAiResponsesWebSearchMetadata build() {
            return new OpenAiResponsesWebSearchMetadata(this);
        }
    }

    /**
     * A {@code url_citation} annotation attached to an {@code output_text} content part: the source
     * the cited span (between {@code startIndex} and {@code endIndex} in the text) was taken from.
     */
    public record UrlCitation(String url, String title, Integer startIndex, Integer endIndex) {}
}
