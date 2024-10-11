package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class ChatRequest {

    private String model;
    private List<Message> messages;
    private Options options;
    private String format;
    private Boolean stream;
    private List<Tool> tools;

    ChatRequest() {

    }

    ChatRequest(String model, List<Message> messages, Options options, Boolean stream, List<Tool> tools, String format) {
        this.model = model;
        this.messages = messages;
        this.options = options;
        this.stream = stream;
        this.tools = tools;
        this.format = format;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }

    static class Builder {

        private String model;
        private List<Message> messages;
        private Options options;
        private String format;
        private Boolean stream;
        private List<Tool> tools;

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        Builder options(Options options) {
            this.options = options;
            return this;
        }

        Builder format(String format) {
            this.format = format;
            return this;
        }

        Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        Builder tools(List<Tool> tools) {
            this.tools = tools;
            return this;
        }

        ChatRequest build() {
            return new ChatRequest(model, messages, options, stream, tools, format);
        }
    }
}
