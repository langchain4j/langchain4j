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
public class SearXNGResults {
	private String query;
	private int numberOfResults;
	private List<SearXNGResult> results;
	private List<String> answers;
	private List<String> corrections;
	private List<String> suggestions;
	private List<List<String>> unresponsiveEngines;
	// Skipping other returned fields like infoboxes for now
}

