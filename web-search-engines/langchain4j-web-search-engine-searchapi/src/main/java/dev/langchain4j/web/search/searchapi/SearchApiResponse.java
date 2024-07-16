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

	/** Mandatory JSON elements */
	private Map<String, Object> searchMetadata;
	private Map<String, Object> searchParameters;
	private List<OrganicResult> organicResults;

	/** Optional JSON elements */
	private List<Map<String, Object>> inlineVideos;

	private Map<String, Object> inlineImages;
	private Map<String, Object> knowledgeGraph;
	private Map<String, Object> answerBox;

	/** Mandatory JSON element */
	private Pagination pagination;

}
