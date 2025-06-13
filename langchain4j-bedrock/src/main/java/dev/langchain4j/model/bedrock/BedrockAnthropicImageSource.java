package dev.langchain4j.model.bedrock;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockAnthropicImageSource {

    private String type;
    private String media_type;
    private String data;

    public BedrockAnthropicImageSource(final String type, final String media_type, final String data) {
        this.type = type;
        this.media_type = media_type;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getMedia_type() {
        return media_type;
    }

    public void setMedia_type(final String media_type) {
        this.media_type = media_type;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }
}
