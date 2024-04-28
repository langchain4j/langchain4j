package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.List;

@Data
@Builder
public final class ChatCompletionChoice {

	private Integer index;
	private String message;
	private Role role;
	private String delta;

	@SerializedName("finish_reason")
	private String finishReason;

	@SerializedName("tool_calls")
	private final List<ToolCall> toolCalls;
}