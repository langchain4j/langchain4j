package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.internal.Utils;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TextContent implements Content {
    private final String type;
    private final String text;

    public TextContent(String type, String text) {
        this.type = Utils.getOrDefault(type, "text");
        this.text = text;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public static TextContentBuilder builder() {
        return new TextContentBuilder();
    }


    public static class TextContentBuilder {
        private String type;
        private String text;

        public TextContentBuilder type(String type) {
            this.type = type;
            return this;
        }

        public TextContentBuilder text(String text) {
            this.text = text;
            return this;
        }

        public TextContent build() {
            return new TextContent(this.type, this.text);
        }
    }
}
