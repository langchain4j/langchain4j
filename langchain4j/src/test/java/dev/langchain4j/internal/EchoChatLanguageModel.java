package dev.langchain4j.internal;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.stream.Collectors;

public class EchoChatLanguageModel implements ChatLanguageModel {

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages) {
        String response = messages.stream().map(ChatMessage::text).collect(Collectors.joining("; "));
        System.out.println(response);
        return Response.from(AiMessage.from(response));
    }
}
