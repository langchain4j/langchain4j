package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;

/**
 * Request object for counting tokens in a message using the Anthropic API.
 * <p>
 * This class is used to estimate token usage before sending a request,
 * allowing for cost estimation and message size validation.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AnthropicCountTokensRequest {

    /**
     * The model to use for token counting.
     */
    private String model;

    /**
     * The list of messages to count tokens for.
     */
    private List<AnthropicMessage> messages;

    /**
     * Optional system prompt content.
     */
    private List<AnthropicTextContent> system;

    /**
     * Optional list of tools available for the model.
     */
    private List<AnthropicTool> tools;

    /**
     * Optional thinking configuration.
     */
    private AnthropicThinking thinking;

    /**
     * Default constructor.
     */
    public AnthropicCountTokensRequest() {}

    private AnthropicCountTokensRequest(Builder builder) {
        this.model = builder.model;
        this.messages = builder.messages;
        this.system = builder.system;
        this.tools = builder.tools;
        this.thinking = builder.thinking;
    }

    /**
     * Gets the model name.
     *
     * @return the model name
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the model name.
     *
     * @param model the model name
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Gets the list of messages.
     *
     * @return the list of messages
     */
    public List<AnthropicMessage> getMessages() {
        return messages;
    }

    /**
     * Sets the list of messages.
     *
     * @param messages the list of messages
     */
    public void setMessages(List<AnthropicMessage> messages) {
        this.messages = messages;
    }

    /**
     * Gets the system prompt content.
     *
     * @return the system prompt content
     */
    public List<AnthropicTextContent> getSystem() {
        return system;
    }

    /**
     * Sets the system prompt content.
     *
     * @param system the system prompt content
     */
    public void setSystem(List<AnthropicTextContent> system) {
        this.system = system;
    }

    /**
     * Gets the list of tools.
     *
     * @return the list of tools
     */
    public List<AnthropicTool> getTools() {
        return tools;
    }

    /**
     * Sets the list of tools.
     *
     * @param tools the list of tools
     */
    public void setTools(List<AnthropicTool> tools) {
        this.tools = tools;
    }

    /**
     * Gets the thinking configuration.
     *
     * @return the thinking configuration
     */
    public AnthropicThinking getThinking() {
        return thinking;
    }

    /**
     * Sets the thinking configuration.
     *
     * @param thinking the thinking configuration
     */
    public void setThinking(AnthropicThinking thinking) {
        this.thinking = thinking;
    }

    /**
     * Creates a new builder for constructing {@link AnthropicCountTokensRequest} instances.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, messages, system, tools, thinking);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnthropicCountTokensRequest)) return false;
        AnthropicCountTokensRequest that = (AnthropicCountTokensRequest) obj;
        return Objects.equals(model, that.model)
                && Objects.equals(messages, that.messages)
                && Objects.equals(system, that.system)
                && Objects.equals(tools, that.tools)
                && Objects.equals(thinking, that.thinking);
    }

    @Override
    public String toString() {
        return "AnthropicCountTokensRequest{" + "model='"
                + model + '\'' + ", messages="
                + messages + ", system="
                + system + ", tools="
                + tools + ", thinking="
                + thinking + '}';
    }

    /**
     * Builder for constructing {@link AnthropicCountTokensRequest} instances.
     */
    public static class Builder {

        private String model;
        private List<AnthropicMessage> messages;
        private List<AnthropicTextContent> system;
        private List<AnthropicTool> tools;
        private AnthropicThinking thinking;

        /**
         * Sets the model name.
         *
         * @param model the model name
         * @return this builder for chaining
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the list of messages.
         *
         * @param messages the list of messages
         * @return this builder for chaining
         */
        public Builder messages(List<AnthropicMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Sets the system prompt content.
         *
         * @param system the system prompt content
         * @return this builder for chaining
         */
        public Builder system(List<AnthropicTextContent> system) {
            this.system = system;
            return this;
        }

        /**
         * Sets the list of tools.
         *
         * @param tools the list of tools
         * @return this builder for chaining
         */
        public Builder tools(List<AnthropicTool> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Sets the thinking configuration.
         *
         * @param thinking the thinking configuration
         * @return this builder for chaining
         */
        public Builder thinking(AnthropicThinking thinking) {
            this.thinking = thinking;
            return this;
        }

        /**
         * Builds the {@link AnthropicCountTokensRequest} instance.
         *
         * @return a new {@link AnthropicCountTokensRequest} instance
         */
        public AnthropicCountTokensRequest build() {
            return new AnthropicCountTokensRequest(this);
        }
    }
}
