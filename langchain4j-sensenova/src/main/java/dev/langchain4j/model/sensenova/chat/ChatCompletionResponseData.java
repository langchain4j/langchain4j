package dev.langchain4j.model.sensenova.chat;


import dev.langchain4j.model.sensenova.shared.Usage;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class ChatCompletionResponseData {
	private String id;
	private Integer created;
	private String model;
	private List<ChatCompletionChoice> choices;
	private Usage usage;
	private List<KnowledgeBaseResult> knowledgeBaseResults;
	private Plugins plugins;
}
