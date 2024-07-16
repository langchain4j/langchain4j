package dev.langchain4j.web.search.searchapi;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a search request for the Search API.
 * <p>
 * This class encapsulates all the parameters that can be used to customize a search request to the Search API.
 * It includes both mandatory and optional parameters to fine-tune the search results.
 * </p>
 * <p>
 * Mandatory parameters:
 * <ul>
 *    <li>{@link #apiKey} - The API key for authentication with the Search API.</li>
 *    <li>{@link #engine} - The search engine to retrieve real-time data from.</li>
 *    <li>{@link #q} - The search query.</li>
 * </ul>
 * </p>
 * <p>
 * Optional parameters:
 * <ul>
 *    <li>{@link #safe} - The safe search setting.</li>
 *    <li>{@link #num} - The number of results to display per page.</li>
 *    <li>{@link #page} - The results page to fetch.</li>
 * </ul>
 * </p>
 */
@Getter
@Setter
@Builder
class SearchApiRequest {

    // Mandatory parameters

	/**
     * The API key for authentication with the Search API.
     */
    private String apiKey;

    /**
     * The search engine to retrieve real-time data from. Defaults to "google".
     */
    private String engine;

    /**
     * The search query.
     */
    private String q;

    // Optional parameters
    
    /**
     * A map containing the optional parameters and their values.
     */
    protected final Map<String, Object> params = new HashMap<>();

    /**
     * The safe search setting.
     * Valid values: "active" or "off". Defaults to "off".
     */
    protected boolean safe;

    /**
     * The number of results to display per page.
     */
    protected Integer num;

    /**
     * The results page to fetch. Defaults to 1.
     */
    protected Integer page;
    
    /**
     * Functional interface to allow customization of search request parameters;
     */
    protected Consumer<Map<String, Object>> customizeParameters;

}