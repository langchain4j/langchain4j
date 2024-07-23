package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Bedrock stability AI model
 * This is for image generation.
 * Might not make sense to make it a chat model.
 * <p>
 * <a href="https://docs.stability-ai.com/bedrock-runtime-api-reference/invoke-model">...</a>
 */
@Getter
@SuperBuilder
public class BedrockStabilityAIChatModel extends AbstractBedrockChatModel<BedrockStabilityAIChatModelResponse> {

    @Getter
    public enum StylePreset {
        ThreeDModel("3d-model"),
        Anime("anime"),
        Cinematic("cinematic"),
        ComicBook("comic-book"),
        DigitalArt("digital-art"),
        Enhance("enhance"),
        FantasyArt("fantasy-art"),
        Isometric("isometric"),
        LineArt("line-art"),
        LowPoly("low-poly"),
        ModelingCompound("modeling-compound"),
        NeonPunk("neon-punk"),
        Origami("origami"),
        Photographic("photographic"),
        PixelArt("pixel-art"),
        TileTexture("tile-texture"),
        AnalogFilm("analog-film");

        private final String value;

        StylePreset(String value) {
            this.value = value;
        }
    }

    @Builder.Default
    private final String model = Types.StableDiffuseXlV1.getValue();
    @Builder.Default
    private final int cfgScale = 10;
    @Builder.Default
    private final int width = 512;
    @Builder.Default
    private final int height = 512;
    @Builder.Default
    private final int seed = 0;
    @Builder.Default
    private final int steps = 50;
    @Builder.Default
    private final double promptWeight = 0.5;
    @Builder.Default
    private final StylePreset stylePreset = StylePreset.ThreeDModel;


    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> textPrompt = new HashMap<>(2);
        textPrompt.put("text", prompt);
        textPrompt.put("weight", promptWeight);


        final Map<String, Object> parameters = new HashMap<>(4);

        parameters.put("text_prompts", Collections.singletonList(textPrompt));
        parameters.put("cfg_scale", cfgScale);
        parameters.put("seed", seed);
        parameters.put("steps", steps);
        parameters.put("width", width);
        parameters.put("height", height);
        parameters.put("style_preset", stylePreset.getValue());

        return parameters;
    }

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Class<BedrockStabilityAIChatModelResponse> getResponseClassType() {
        return BedrockStabilityAIChatModelResponse.class;
    }

    /**
     * Bedrock Amazon Stability AI model ids
     */
    @Getter
    public enum Types {
        StableDiffuseXlV0("stability.stable-diffusion-xl-v0"),
        StableDiffuseXlV1("stability.stable-diffusion-xl-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }
    }
}
