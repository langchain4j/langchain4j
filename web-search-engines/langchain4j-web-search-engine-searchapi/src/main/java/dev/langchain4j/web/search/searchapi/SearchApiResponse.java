package dev.langchain4j.web.search.searchapi;

import com.google.gson.JsonObject;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
class SearchApiResponse {

//    private String answer;
//    private String query;
//    private Double responseTime;
//    private List<String> images;
//    private List<String> followUpQuestions;
//    private List<SearchApiSearchResult> json;
	private JsonObject json;
//	private Map<String, Object> json; //= new Gson().fromJson(json, Map.class);
}
