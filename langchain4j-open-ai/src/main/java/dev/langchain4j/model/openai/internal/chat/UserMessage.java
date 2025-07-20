package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.model.openai.internal.chat.ContentType.*;
import static dev.langchain4j.model.openai.internal.chat.Role.USER;
import static java.util.Collections.unmodifiableList;

@JsonDeserialize(builder = UserMessage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class UserMessage implements Message {

    @JsonProperty
    private final Role role = USER;
    @JsonProperty
    private final Object content;
    @JsonProperty
    private final String name;

    public UserMessage(Builder builder) {
        this.content = builder.stringContent != null ? builder.stringContent : builder.content;
        this.name = builder.name;
    }

    public Role role() {
        return role;
    }

    public Object content() {
        return content;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof UserMessage
                && equalTo((UserMessage) another);
    }

    private boolean equalTo(UserMessage another) {
        return Objects.equals(role, another.role)
                && Objects.equals(content, another.content)
                && Objects.equals(name, another.name);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(role);
        h += (h << 5) + Objects.hashCode(content);
        h += (h << 5) + Objects.hashCode(name);
        return h;
    }

    @Override
    public String toString() {
        return "UserMessage{"
                + "role=" + role
                + ", content=" + content
                + ", name=" + name
                + "}";
    }

    public static UserMessage from(String text) {
        return UserMessage.builder()
                .content(text)
                .build();
    }

    public static UserMessage from(String text, String... imageUrls) {
        return UserMessage.builder()
                .addText(text)
                .addImageUrls(imageUrls)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String stringContent; // keeping it for compatibility with other OpenAI-like APIs
        private List<Content> content;
        private String name;

        public Builder addText(String text) {
            initializeContent();
            Content content = Content.builder()
                    .type(TEXT)
                    .text(text)
                    .build();
            this.content.add(content);
            return this;
        }

        public Builder addImageUrl(String imageUrl) {
            return addImageUrl(imageUrl, null);
        }

        public Builder addImageUrl(String imageUrl, ImageDetail imageDetail) {
            initializeContent();
            Content content = Content.builder()
                    .type(IMAGE_URL)
                    .imageUrl(ImageUrl.builder()
                            .url(imageUrl)
                            .detail(imageDetail)
                            .build())
                    .build();
            this.content.add(content);
            return this;
        }

        public Builder addImageUrls(String... imageUrls) {
            for (String imageUrl : imageUrls) {
                addImageUrl(imageUrl);
            }
            return this;
        }
        
        public Builder addInputAudio(InputAudio inputAudio) {
            initializeContent();
            this.content.add(
                Content.builder()
                    .type(AUDIO)
                    .inputAudio(inputAudio)
                .build()
            );
            
            return this;
        }

        public Builder addPdfFile(PdfFile pdfFile) {
            initializeContent();
            this.content.add(
                    Content.builder()
                            .type(FILE)
                            .file(pdfFile)
                            .build()
            );

            return this;
        }

        public Builder content(List<Content> content) {
            if (content != null) {
                this.content = unmodifiableList(content);
            }
            return this;
        }

        public Builder content(String content) {
            this.stringContent = content;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public UserMessage build() {
            return new UserMessage(this);
        }

        private void initializeContent() {
            if (this.content == null) {
                this.content = new ArrayList<>();
            }
        }
    }
}
