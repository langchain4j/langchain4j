package dev.langchain4j.web.search.searchapi;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import dev.langchain4j.web.search.searchapi.result.OrganicResult;
import dev.langchain4j.web.search.searchapi.result.Pagination;

@Getter
@Setter
@Builder
public class SearchApiResponse {
	/**
	 * This is an example of the JSON returned by searchapi.io showing top-level JSON elements.
	 * 
	 {
		"search_metadata": {
		
		    "id": "search_omMjwVJvBznTYQ8Z9vynp5OY",
		    "status": "Success",
		    "created_at": "2024-06-01T10:25:48Z",
		    "request_time_taken": 2.96,
		    "parsing_time_taken": 0.33,
		    "total_time_taken": 3.28,
		    "request_url": "https://www.google.com/search?q=Advancements+in+Robotics&oq=Advancements+in+Robotics&gl=us&hl=en&ie=UTF-8",
		    "html_url": "https://www.searchapi.io/api/v1/searches/search_omMjwVJvBznTYQ8Z9vynp5OY.html",
		    "json_url": "https://www.searchapi.io/api/v1/searches/search_omMjwVJvBznTYQ8Z9vynp5OY"
		
		},
		"search_parameters": {
		
		    "engine": "google",
		    "q": "Advancements in Robotics",
		    "device": "desktop",
		    "google_domain": "google.com",
		    "hl": "en",
		    "gl": "us"
		
		},
		"search_information": {
		
		    "query_displayed": "Advancements in Robotics",
		    "total_results": 33700000,
		    "time_taken_displayed": 0.29
		
		},
		
		"organic_results": [ ... ],
		"related_questions": [ ... ],
		"related_searches": [ ... ],
		"pagination": {

		    "current": 1,
		    "next": "https://www.google.com/search?q=Advancements+in+Robotics&oq=Advancements+in+Robotics&gl=us&hl=en&start=10&ie=UTF-8"
		
		}
	 }
	 * 
	 * The following JSON elements appear to be mandatory in the response sent by searchapi.io. 
	 * 	"search_metadata", 
	 * 	"search_parameters", 
	 * 	"search_information", 
	 * 	"organic_results" and 
	 * 	"pagination" 
	 * 
	 * Mandatory JSON elements are present in every response while optional JSON elements (e.g. "inline_images", "inline_videos", "knowledge_graph", "answer_box", "top_stories" etc) are query-dependent. 
	 * 
	 * The optional JSON elements appear in the response depending on the type of query (e.g. local business, online shopping, population fact etc) 
	 * or whether extra parameters are included in the search like "location".
	 * 
	 * Note that:
	 * 	"related_questions" 
	 *	"related_searches" 
	 *	are also Mandatory JSON elements. They are always included in the API response but are currently ignored since they are never used directly.
	 * 
	 */

	
	/** Mandatory JSON elements */
	private Map<String, Object> searchMetadata;
	private Map<String, Object> searchParameters;
	private Map<String, Object> searchInformation;
	private List<OrganicResult> organicResults;

	/** Optional JSON elements */
	private List<Map<String, Object>> inlineVideos;

	private Map<String, Object> inlineImages;
	private Map<String, Object> knowledgeGraph;
	private Map<String, Object> answerBox;

	/** Mandatory JSON element */
	private Pagination pagination;

}
