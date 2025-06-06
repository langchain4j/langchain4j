package dev.langchain4j.model.bedrock;

import java.util.Map;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockAnthropicContent {

    private String type;
    private String text;
    private String id;
    private String name;
    private String tool_use_id;
    private String content;
    private BedrockAnthropicImageSource source;
    private Map<String, Object> input;

    public BedrockAnthropicContent() {}

    public BedrockAnthropicContent(
            final String type,
            final String text,
            final String id,
            final String name,
            final String tool_use_id,
            final String content,
            final BedrockAnthropicImageSource source,
            final Map<String, Object> input) {
        this.type = type;
        this.text = text;
        this.id = id;
        this.name = name;
        this.tool_use_id = tool_use_id;
        this.content = content;
        this.source = source;
        this.input = input;
    }

    public BedrockAnthropicContent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public BedrockAnthropicContent(String type, BedrockAnthropicImageSource source) {
        this.type = type;
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(final String text) {
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getTool_use_id() {
        return tool_use_id;
    }

    public void setTool_use_id(final String tool_use_id) {
        this.tool_use_id = tool_use_id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public BedrockAnthropicImageSource getSource() {
        return source;
    }

    public void setSource(final BedrockAnthropicImageSource source) {
        this.source = source;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(final Map<String, Object> input) {
        this.input = input;
    }

    public static BedrockAnthropicContentBuilder builder() {
        return new BedrockAnthropicContentBuilder();
    }

    public static class BedrockAnthropicContentBuilder {
        private String type;
        private String text;
        private String id;
        private String name;
        private String tool_use_id;
        private String content;
        private BedrockAnthropicImageSource source;
        private Map<String, Object> input;

        public BedrockAnthropicContentBuilder type(String type) {
            this.type = type;
            return this;
        }

        public BedrockAnthropicContentBuilder text(String text) {
            this.text = text;
            return this;
        }

        public BedrockAnthropicContentBuilder id(String id) {
            this.id = id;
            return this;
        }

        public BedrockAnthropicContentBuilder name(String name) {
            this.name = name;
            return this;
        }

        public BedrockAnthropicContentBuilder tool_use_id(String tool_use_id) {
            this.tool_use_id = tool_use_id;
            return this;
        }

        public BedrockAnthropicContentBuilder content(String content) {
            this.content = content;
            return this;
        }

        public BedrockAnthropicContentBuilder source(BedrockAnthropicImageSource source) {
            this.source = source;
            return this;
        }

        public BedrockAnthropicContentBuilder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        public BedrockAnthropicContent build() {
            return new BedrockAnthropicContent(
                    this.type, this.text, this.id, this.name, this.tool_use_id, this.content, this.source, this.input);
        }

        public String toString() {
            return "BedrockAnthropicContent.BedrockAnthropicContentBuilder(type=" + this.type + ", text=" + this.text
                    + ", id=" + this.id + ", name=" + this.name + ", tool_use_id=" + this.tool_use_id + ", content="
                    + this.content + ", source=" + this.source + ", input=" + this.input + ")";
        }
    }
}
