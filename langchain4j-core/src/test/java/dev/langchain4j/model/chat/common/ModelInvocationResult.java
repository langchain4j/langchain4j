package dev.langchain4j.model.chat.common;

import dev.langchain4j.model.chat.response.ChatResponse;

public record ModelInvocationResult(ChatResponse chatResponse,
                                    StreamingMetadata streamingMetadata) {
}
