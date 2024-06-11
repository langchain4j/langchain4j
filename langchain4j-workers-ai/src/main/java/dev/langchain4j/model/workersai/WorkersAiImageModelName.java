package dev.langchain4j.model.workersai;

/**
 * Enum for Workers AI Omage Model Name.
 */
public enum WorkersAiImageModelName {

    // ---------------------------------------------------------------------
    // Text to image
    // https://developers.cloudflare.com/workers-ai/models/text-to-image/
    // ---------------------------------------------------------------------

    /**
     * Diffusion-based text-to-image generative model by Stability AI. Generates and modify images based on text prompts.
     */
    STABLE_DIFFUSION_XL("@cf/stabilityai/stable-diffusion-xl-base-1.0"),
    /**
     * Stable Diffusion model that has been fine-tuned to be better at photorealism without sacrificing range.
     */
    DREAM_SHAPER_8_LCM("@cf/lykon/dreamshaper-8-lcm"),
    /**
     * Stable Diffusion is a latent text-to-image diffusion model capable of generating photo-realistic images. Img2img generate a new image from an input image with Stable Diffusion.
     */
    STABLE_DIFFUSION_V1_5_IMG2IMG("@cf/runwayml/stable-diffusion-v1-5-img2img"),
    /**
     * Stable Diffusion Inpainting is a latent text-to-image diffusion model capable of generating photo-realistic images given any text input, with the extra capability of inpainting the pictures by using a mask.
     */
    STABLE_DIFFUSION_V1_5_IN_PAINTING("@cf/runwayml/stable-diffusion-v1-5-inpainting"),
    /**
     * SDXL-Lightning is a lightning-fast text-to-image generation model. It can generate high-quality 1024px images in a few steps.
     */
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
