package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated Will be removed in the next release, this functionality will not be supported anymore.
 * Please reach out (via GitHub issues) if you use it.
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockStabilityAIChatModel extends AbstractBedrockChatModel<BedrockStabilityAIChatModelResponse> {

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

        public String getValue() {
            return value;
        }
    }

    private static final String DEFAULT_MODEL = Types.StableDiffuseXlV1.getValue();
    private static final int DEFAULT_CFG_SCALE = 10;
    private static final int DEFAULT_WIDTH = 512;
    private static final int DEFAULT_HEIGHT = 512;
    private static final int DEFAULT_SEED = 0;
    private static final int DEFAULT_STEPS = 50;
    private static final double DEFAULT_PROMPT_WEIGHT = 0.5;
    private static final StylePreset DEFAULT_STYLE_PRESET = StylePreset.ThreeDModel;

    private final String model;
    private final int cfgScale;
    private final int width;
    private final int height;
    private final int seed;
    private final int steps;
    private final double promptWeight;
    private final StylePreset stylePreset;

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
    public enum Types {
        StableDiffuseXlV0("stability.stable-diffusion-xl-v0"),
        StableDiffuseXlV1("stability.stable-diffusion-xl-v1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }

        public String getValue() {
            return value;
        }
    }

    public String getModel() {
        return model;
    }

    public int getCfgScale() {
        return cfgScale;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getSeed() {
        return seed;
    }

    public int getSteps() {
        return steps;
    }

    public double getPromptWeight() {
        return promptWeight;
    }

    public StylePreset getStylePreset() {
        return stylePreset;
    }

    protected BedrockStabilityAIChatModel(BedrockStabilityAIChatModelBuilder<?, ?> builder) {
        super(builder);
        if (builder.isModelSet) {
            this.model = builder.model;
        } else {
            this.model = DEFAULT_MODEL;
        }

        if (builder.isCfgScaleSet) {
            this.cfgScale = builder.cfgScale;
        } else {
            this.cfgScale = DEFAULT_CFG_SCALE;
        }

        if (builder.isWidthSet) {
            this.width = builder.width;
        } else {
            this.width = DEFAULT_WIDTH;
        }

        if (builder.isHeightSet) {
            this.height = builder.height;
        } else {
            this.height = DEFAULT_HEIGHT;
        }

        if (builder.isSeedSet) {
            this.seed = builder.seed;
        } else {
            this.seed = DEFAULT_SEED;
        }

        if (builder.isStepsSet) {
            this.steps = builder.steps;
        } else {
            this.steps = DEFAULT_STEPS;
        }

        if (builder.isPromptWeightSet) {
            this.promptWeight = builder.promptWeight;
        } else {
            this.promptWeight = DEFAULT_PROMPT_WEIGHT;
        }

        if (builder.isStylePresetSet) {
            this.stylePreset = builder.stylePreset;
        } else {
            this.stylePreset = DEFAULT_STYLE_PRESET;
        }
    }

    public static BedrockStabilityAIChatModelBuilder<?, ?> builder() {
        return new BedrockStabilityAIChatModelBuilderImpl();
    }

    public abstract static class BedrockStabilityAIChatModelBuilder<
                    C extends BedrockStabilityAIChatModel, B extends BedrockStabilityAIChatModelBuilder<C, B>>
            extends AbstractBedrockChatModel.AbstractBedrockChatModelBuilder<
                    BedrockStabilityAIChatModelResponse, C, B> {
        private boolean isModelSet;
        private String model;
        private boolean isCfgScaleSet;
        private int cfgScale;
        private boolean isWidthSet;
        private int width;
        private boolean isHeightSet;
        private int height;
        private boolean isSeedSet;
        private int seed;
        private boolean isStepsSet;
        private int steps;
        private boolean isPromptWeightSet;
        private double promptWeight;
        private boolean isStylePresetSet;
        private StylePreset stylePreset;

        public B model(String model) {
            this.model = model;
            this.isModelSet = true;
            return self();
        }

        public B cfgScale(int cfgScale) {
            this.cfgScale = cfgScale;
            this.isCfgScaleSet = true;
            return self();
        }

        public B width(int width) {
            this.width = width;
            this.isWidthSet = true;
            return self();
        }

        public B height(int height) {
            this.height = height;
            this.isHeightSet = true;
            return self();
        }

        public B seed(int seed) {
            this.seed = seed;
            this.isSeedSet = true;
            return self();
        }

        public B steps(int steps) {
            this.steps = steps;
            this.isStepsSet = true;
            return self();
        }

        public B promptWeight(double promptWeight) {
            this.promptWeight = promptWeight;
            this.isPromptWeightSet = true;
            return self();
        }

        public B stylePreset(StylePreset stylePreset) {
            this.stylePreset = stylePreset;
            this.isStylePresetSet = true;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        @Override
        public String toString() {
            return "BedrockStabilityAIChatModel.BedrockStabilityAIChatModelBuilder(super=" + super.toString()
                    + ", model$value=" + this.model + ", cfgScale$value=" + this.cfgScale + ", width$value="
                    + this.width + ", height$value=" + this.height + ", seed$value=" + this.seed + ", steps$value="
                    + this.steps + ", promptWeight$value=" + this.promptWeight + ", stylePreset$value="
                    + this.stylePreset + ")";
        }
    }

    private static final class BedrockStabilityAIChatModelBuilderImpl
            extends BedrockStabilityAIChatModelBuilder<
                    BedrockStabilityAIChatModel, BedrockStabilityAIChatModelBuilderImpl> {
        protected BedrockStabilityAIChatModelBuilderImpl self() {
            return this;
        }

        public BedrockStabilityAIChatModel build() {
            return new BedrockStabilityAIChatModel(this);
        }
    }
}
