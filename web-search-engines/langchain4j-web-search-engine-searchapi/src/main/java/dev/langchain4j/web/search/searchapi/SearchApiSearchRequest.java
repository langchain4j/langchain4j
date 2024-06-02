package dev.langchain4j.web.search.searchapi;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
class SearchApiSearchRequest {

	/************************************************************************************************ 
	 * Mandatory parameters 
	 * **********************************************************************************************/
	
	/* Search API key. */
    private String apiKey;

	/* Search engine to retrieve real-time data from. Defaults to "google". */
    private String engine;
    
    /* Search query. */
    private String q;

    
    
    /************************************************************************************************  
	 * Optional parameters 
	 * **********************************************************************************************/
    
    /* Search device. Valid values: "desktop", "mobile" or "tablet". Defaults to "desktop". */
    private String device;
    
    /** Geographic Location */ 
    /* Location from where you want the search to originate. Examples "new york" or "london". Valid values see: https://www.searchapi.io/docs/locations-api */
    private String location;
    
    /* Automatically generated from the "location" parameter but can be overridden. "uule" and "location" parameters can't be used together. */
    private String uule;
    
    
    /** Localization */
    /* The Google domain of the search. Defaults to "google.com". Full list of supported Google domains, see: https://www.searchapi.io/docs/parameters/google/domain. */
    private String googleDomain;
    
    /* Together hl & gl when concatenated become "en-us".
    /* Two-letter language code for the search interface. Defaults to "en". Full list of supported "hl" values, see https://www.searchapi.io/docs/parameters/google/hl. */
    private String hl;

    /* Two-letter country code for the search language. Defaults to "us". Full list of supported "gl" values, see https://www.searchapi.io/docs/parameters/google/gl. */
    private String gl; 

    
    
    /** Filters */
    /* Safe search. Valid values: "active" or "off". Defaults to "off". */
    private Boolean safe;
    
    /* "Duplicate Content" and "Host Crowding" filters. Defaults to "1" (enabled). To disable, set the value to 0. */
    private Integer filter;
    
    
    /** Pagination */
    /* Number of results to display per page. */
    private Integer num;
    
    /* The results page to fetch next. Defaults to 1. */
    private Integer page;
    
    
    
    
    /* Returns a map of key-value pairs representing optional parameters for the search request. */ 
    public Map<String, Object> getOptionalParameters() {
    	final Map<String, Object> params = new HashMap<String, Object>();
    	
    	if (isNotNullOrBlank(device)) params.put("device", getOrDefault(device, "desktop"));
    	if (isNotNullOrBlank(location)) params.put("location", location);
    	if (isNotNullOrBlank(uule) && !params.containsKey("location")) params.put("uule", uule);
    	if (isNotNullOrBlank(googleDomain)) params.put("google_domain", googleDomain);
    	if (isNotNullOrBlank(hl)) params.put("hl", getOrDefault(hl, "en"));
    	if (isNotNullOrBlank(gl)) params.put("gl", getOrDefault(gl, "us"));
    	if (safe) params.put("safe", "active");
    	if (isValidInteger(filter)) params.put("filter", filter);
    	if (isValidInteger(num)) params.put("num", num);
    	if (isValidInteger(page)) params.put("page", page);
    	
		return params;
    }
    
    private static boolean isValidInteger(final Integer value) {
    	boolean isValid = true;
    	
		if (value == null) isValid = false;
		else if (value.intValue() < 0) isValid = false;
    		    	
    	return isValid;
    }

}
