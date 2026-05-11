package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import java.util.function.Function;

public record ConditionalChatModel(Function<AgenticScope, Integer> selector, ChatModel... models) {

    ChatModel select(AgenticScope scope) {
        return models[selector.apply(scope)];
    }
}
