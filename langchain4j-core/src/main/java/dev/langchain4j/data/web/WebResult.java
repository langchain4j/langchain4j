package dev.langchain4j.data.web;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class WebResult {
    private final String title;
    private final String snippet;
    private final String link;

    public WebResult(String title) {
       this(title, "", "");
    }

    public WebResult(String title, String snippet) {
        this(title, snippet, "");
    }

    public WebResult(String title, String snippet, String link) {
        this.title = ensureNotNull(title, "title");
        this.snippet = ensureNotBlank(snippet, "snippet");
        this.link = ensureNotNull(link, "link");
    }

    public String title() {
        return title;
    }

    public String snippet() {
        return snippet;
    }

    public String link() {
        return link;
    }

    public TextSegment toTextSegment() {
        return TextSegment.from(
                snippet,
                Metadata.from("title", title).add("link", link));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebResult webResult = (WebResult) o;
        return Objects.equals(title, webResult.title)
                && Objects.equals(snippet, webResult.snippet)
                && Objects.equals(link, webResult.link);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, snippet, link);
    }

    @Override
    public String toString() {
        return "WebResult{" +
                "title='" + title + '\'' +
                ", snippet='" + snippet + '\'' +
                ", link='" + link + '\'' +
                '}';
    }

    public static WebResult from(String title, String snippet, String link) {
        return new WebResult(title, snippet, link);
    }

    public static WebResult from(String title, String snippet) {
        return new WebResult(title, snippet);
    }

    public static WebResult from(String title) {
        return new WebResult(title);
    }

    public static  WebResult webResult(String snippet) {
        return from(snippet);
    }

    public static  WebResult webResult(String snippet, String link) {
        return from(snippet, link);
    }

    public static  WebResult webResult(String title, String snippet, String link) {
        return from(title, snippet, link);
    }
}
