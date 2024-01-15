package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.message.ChatMessageType;

class RoleMapper {

    static String map(ChatMessageType type) {
        switch (type) {
            case USER:
                return "user";
            case AI:
                return "model";
        }
        throw new IllegalArgumentException(type + " is not allowed.");
    }
}
