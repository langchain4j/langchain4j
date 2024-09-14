package dev.langchain4j.web.search.searxng;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class SearXNGResult {
	private String title;
	private String content;
	private String url;
	private String engine;
	private List<String> parsedUrl;
	private String template;
	private List<String> engines;
	private List<Integer> positions;
	private double score;
	private String category;
}
