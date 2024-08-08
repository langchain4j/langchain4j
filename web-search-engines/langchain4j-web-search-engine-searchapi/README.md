# SearchApi 
This module allows [SearchApi](https://www.searchapi.io/) to be used as a [`WebSearchEngine`](src/main/java/dev/langchain4j/web/search/searchapi/SearchApiWebSearchEngine.java) or as a `Tool` for function calling.

## Design 
The default engine used by the current implementation is the [Google Search engine](https://www.searchapi.io/docs/google). 

The SearchApi API supports not only Google Search but 30+ other APIs including YouTube Search Transcripts, Bing Search (similar JSON response keys) etc. 

The current implementation is easy to adapt for use with other engines because all engines use the same HTTP GET request. 

The only difference between engines are: 
* the request parameters they accept (via [`SearchApiRequest`](src/main/java/dev/langchain4j/web/search/searchapi/SearchApiRequest.java)) and;
* the response JSON returned by each engine (since they accept different parameters). 


To allow developers decide on how they'd like each engine's response to be parsed, the [`SearchApiResponse`](src/main/java/dev/langchain4j/web/search/searchapi/SearchApiResponse.java) class exposes the raw JSON returned by SearchApi.


## Illustrative Example
This is an example of the JSON returned by the Google Search engine showing top-level JSON elements.
```json	  
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
```

 
The following JSON elements appear to be mandatory in the response sent by SearchApi.
* `"search_metadata"`, 
* `"search_parameters"`, 
* `"search_information"`, 
* `"organic_results"` and 
* `"pagination"`


Mandatory JSON elements are present in every response while optional JSON elements (e.g. `"inline_images"`, `"inline_videos"`, `"knowledge_graph"`, `"answer_box"`, `"top_stories"` etc) are query-dependent.
 
The optional JSON elements appear in the response depending on the type of query (e.g. local business, online shopping, population fact etc) 
or whether extra parameters are included in the search like "location".
 
Note that these are also mandatory JSON elements:
 * `"related_questions"`
 * `"related_searches"`

They are always included in the API response but are currently ignored since they are never used directly.

