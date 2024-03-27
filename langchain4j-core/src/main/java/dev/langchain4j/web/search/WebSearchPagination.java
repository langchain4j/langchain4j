package dev.langchain4j.web.search;

import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;


/**
 * Represents pagination information for web search results. Normally it's used as part of a {@link WebSearchResults} to navigate through the search results.
 * Typically, this includes the current page number, the URL of the next page, the URL of the previous page,
 * and a map of page numbers to their respective URLs.
 */
public class WebSearchPagination {

    private final Integer current;
    private final String next;
    private final String previous;
    private final Map<Integer, String> otherPages;

    /**
     * Constructs a new WebSearchPagination object with the specified current page.
     *
     * @param current the current page number
     */
    public WebSearchPagination(Integer current) {
        this(current, null, null, null);
    }

    /**
     * Constructs a new WebSearchPagination object with the specified parameters.
     *
     * @param current     the current page number
     * @param next        the URL of the next page
     * @param previous    the URL of the previous page
     * @param otherPages  a map of page numbers to their respective URLs
     */
    public WebSearchPagination(Integer current, String next, String previous, Map<Integer, String> otherPages) {
        this.current = ensureGreaterThanZero(current, "current");
        this.next = next;
        this.previous = previous;
        this.otherPages = otherPages;
    }

    /**
     * Returns the current page number.
     *
     * @return the current page number
     */
    public Integer current() {
        return current;
    }

    /**
     * Returns the URL of the next page.
     *
     * @return the URL of the next page
     */
    public String next() {
        return next;
    }

    /**
     * Returns the URL of the previous page.
     *
     * @return the URL of the previous page
     */
    public String previous() {
        return previous;
    }

    /**
     * Returns a map of page numbers to their respective URLs.
     *
     * @return a map of page numbers to their respective URLs
     */
    public Map<Integer, String> otherPages() {
        return otherPages;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSearchPagination that = (WebSearchPagination) o;
        return Objects.equals(current, that.current)
                && Objects.equals(next, that.next)
                && Objects.equals(previous, that.previous)
                && Objects.equals(otherPages, that.otherPages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(current, next, previous, otherPages);
    }

    @Override
    public String toString() {
        return "WebSearchPagination{" +
                "current=" + current +
                ", next='" + next + '\'' +
                ", previous='" + previous + '\'' +
                ", otherPages=" + otherPages +
                '}';
    }

    /**
     * Creates a new WebSearchPagination object with the specified current page.
     *
     * @param current the current page number
     * @return a new WebSearchPagination object
     */
    public static WebSearchPagination from(Integer current) {
        return new WebSearchPagination(current);
    }

    /**
     * Creates a new WebSearchPagination object with the specified parameters.
     *
     * @param current     the current page number
     * @param next        the URL of the next page
     * @param previous    the URL of the previous page
     * @param otherPages  a map of page numbers to their respective URLs
     * @return a new WebSearchPagination object
     */
    public static WebSearchPagination from(Integer current, String next, String previous, Map<Integer, String> otherPages) {
        return new WebSearchPagination(current, next, previous, otherPages);
    }
}
