package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.Part;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

import static java.util.stream.Collectors.toList;

class VertexAiGeminiContentMapper {

    public static List<Content> map(List<ChatMessage> messages) {
        return messages.stream()
                .map(chatMessage -> Content.newBuilder()
                        .setRole(VertexAiGeminiRoleMapper.map(chatMessage.type()))
                        .addParts(Part.newBuilder()
                                .setText(chatMessage.text())
                                .build())
                        .build())
                .collect(toList());
    }
}
