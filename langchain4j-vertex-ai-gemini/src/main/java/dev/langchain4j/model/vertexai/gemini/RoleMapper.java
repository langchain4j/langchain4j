package dev.langchain4j.model.vertexai.gemini;

import dev.langchain4j.data.message.ChatMessageType;

class RoleMapper {

    static String map(ChatMessageType type) {
        switch (type) {
            case TOOL_EXECUTION_RESULT:
            case USER:
                return "user";
            case AI:
                return "model";
            case SYSTEM:
                return "system";
        }
        throw new IllegalArgumentException(type + " is not allowed.");
    }
}
