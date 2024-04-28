package dev.langchain4j.model.sensenova.chat;


import lombok.Builder;
import lombok.Data;

@Builder
@Data
public final class ChatCompletionResponse {

	private ChatCompletionResponseData data;
}
