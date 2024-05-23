package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.Part;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

class ContentsMapper {
    static class InstructionAndContent {
        public Content systemInstruction = null;
        public List<Content> contents = new ArrayList<>();
    }

    static InstructionAndContent splitInstructionAndContent(List<ChatMessage> messages) {
        InstructionAndContent instructionAndContent = new InstructionAndContent();

        List<Part> sysInstructionParts = new ArrayList<>();

        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                sysInstructionParts.addAll(PartsMapper.map(message));
            } else {
                instructionAndContent.contents.add(Content.newBuilder()
                        .setRole(RoleMapper.map(message.type()))
                        .addAllParts(PartsMapper.map(message))
                        .build());
            }
        }

        instructionAndContent.systemInstruction = Content.newBuilder()
            .setRole("system")
            .addAllParts(sysInstructionParts)
            .build();

        return instructionAndContent;
    }
}
