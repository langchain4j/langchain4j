package dev.langchain4j.model.workersai;

/**
 * Enum for Workers AI Omage Model Name.
 */
public enum WorkersAiImageModelName {

    // ---------------------------------------------------------------------
    // Text to image
    // https://developers.cloudflare.com/workers-ai/models/text-to-image/
    // ---------------------------------------------------------------------

    STABLE_DIFFUSION_XL("@cf/stabilityai/stable-diffusion-xl-base-1.0"),
    DREAM_SHAPER_8_LCM("@cf/lykon/dreamshaper-8-lcm"),
    STABLE_DIFFUSION_V1_5_IMG2IMG("@cf/runwayml/stable-diffusion-v1-5-img2img"),
    STABLE_DIFFUSION_V1_5_IN_PAINTING("@cf/runwayml/stable-diffusion-v1-5-inpainting"),
    STABLE_DIFFUSION_XL_LIGHTNING("@cf/bytedance/stable-diffusion-xl-lightning");

    private final String stringValue;

    WorkersAiImageModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }


}
