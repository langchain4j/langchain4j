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
     * The device type for the search context.
     * Valid values: "desktop", "mobile" or "tablet". Defaults to "desktop".
     */
    private String device;

    /**
     * The geographic location from where the search should originate.
     * Examples: "new york" or "london".
     * For valid values, see: https://www.searchapi.io/docs/locations-api
     */
    private String location;

    /**
     * An alternative to the location parameter.
     * Automatically generated from the "location" parameter but can be overridden.
     * Note: "uule" and "location" parameters can't be used together.
     */
    private String uule;

    /**
     * The Google domain for the search. Defaults to "google.com".
     * For a full list of supported Google domains, see:
     * https://www.searchapi.io/docs/parameters/google/domain
     */
    private String googleDomain;

    /**
     * The language code for the search interface. Defaults to "en".
     * For a full list of supported "hl" values, see:
     * https://www.searchapi.io/docs/parameters/google/hl
     */
    private String hl;

    /**
     * The two-letter country code for the search language. Defaults to "us".
     * For a full list of supported "gl" values, see:
     * https://www.searchapi.io/docs/parameters/google/gl
     */
    private String gl;

    /**
     * The safe search setting.
     * Valid values: "active" or "off". Defaults to "off".
     */
    private Boolean safe;

    /**
     * The filter for "duplicate content" and "host crowding".
     * Defaults to "1" (enabled). To disable, set the value to 0.
     */
    private Integer filter;

    /**
     * The number of results to display per page.
     */
    private Integer num;

    /**
     * The results page to fetch. Defaults to 1.
     */
    private Integer page;

    /**
     * Returns a map of key-value pairs representing optional parameters for the search request.
     *
     * @return A map containing the optional parameters and their values.
     */
    public Map<String, Object> getOptionalParameters() {
        final Map<String, Object> params = new HashMap<String, Object>();

        if (isNotNullOrBlank(device))
            params.put("device", device);
        if (isNotNullOrBlank(location))
            params.put("location", location);
        if (isNotNullOrBlank(uule) && !params.containsKey("location"))
            params.put("uule", uule);
        if (isNotNullOrBlank(googleDomain))
            params.put("google_domain", googleDomain);
        if (isNotNullOrBlank(hl))
            params.put("hl", hl);
        if (isNotNullOrBlank(gl))
            params.put("gl", gl);
        if (safe)
            params.put("safe", "active");
        if (isValidInteger(filter))
            params.put("filter", filter);
        if (isValidInteger(num))
            params.put("num", num);
        if (isValidInteger(page))
            params.put("page", page);

        return params;
    }

    /**
     * Checks if the given Integer value is valid (not null and non-negative).
     *
     * @param value The Integer value to check.
     * @return true if the value is valid, false otherwise.
     */
    private static boolean isValidInteger(final Integer value) {
        return value != null && value.intValue() >= 0;
    }
}
