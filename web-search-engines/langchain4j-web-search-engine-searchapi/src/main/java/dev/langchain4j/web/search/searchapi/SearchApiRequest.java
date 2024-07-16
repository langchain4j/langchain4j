package dev.langchain4j.web.search.searchapi;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
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
 *    <li>{@link #device} - The device type for the search context.</li>
 *    <li>{@link #location} - The geographic location from where the search should originate.</li>
 *    <li>{@link #uule} - An alternative to the location parameter.</li>
 *    <li>{@link #googleDomain} - The Google domain for the search.</li>
 *    <li>{@link #hl} - The language code for the search interface.</li>
 *    <li>{@link #gl} - The country code for the search language.</li>
 *    <li>{@link #safe} - The safe search setting.</li>
 *    <li>{@link #filter} - The filter for duplicate content and host crowding.</li>
 *    <li>{@link #num} - The number of results to display per page.</li>
 *    <li>{@link #page} - The results page to fetch.</li>
 * </ul>
 * Note that <code>hl</code> & <code>gl</code> default to <code>hl="en"</code> and <code>gl="us"</code>.
 * When concatenated they become the locale string "en-us".
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
    protected Map<String, Object> params;

    /**
     * The safe search setting.
     * Valid values: "active" or "off". Defaults to "off".
     */
    protected Boolean safe;

    /**
     * The number of results to display per page.
     */
    protected Integer num;

    /**
     * The results page to fetch. Defaults to 1.
     */
    protected Integer page;

}
