package dev.langchain4j.data.message;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

public enum ContentType {

    TEXT,
    IMAGE;

    static Class<? extends Content> classOf(ContentType type) {
        switch (type) {
            case TEXT:
                return TextContent.class;
            case IMAGE:
                return ImageContent.class;
            default:
                throw illegalArgument("Unknown ContentType: %s", type);
        }
    }
}
