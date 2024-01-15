package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.Content;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

import java.util.List;

import static java.util.stream.Collectors.toList;

class ContentsMapper {

    static List<Content> map(List<ChatMessage> messages) {
        return messages.stream()
                .peek(message -> {
                    if (message instanceof SystemMessage) {
                        throw new IllegalArgumentException("SystemMessage is currently not supported by Gemini");
                    }
                })
                .map(message -> Content.newBuilder()
                        .setRole(RoleMapper.map(message.type()))
                        .addAllParts(PartsMapper.map(message))
                        .build())
                .collect(toList());
    }
}
