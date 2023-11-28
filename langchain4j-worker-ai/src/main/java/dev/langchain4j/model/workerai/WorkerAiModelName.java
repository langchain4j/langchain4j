package dev.langchain4j.model.workerai;

/**
 * Group model name in a class to ease the usage.
 * It has been preferred as an Enum for flexibility.
 */
@SuppressWarnings("unused")
public class WorkerAiModelName {

    /**
     * Max token for text generation
     */
    public static final int MAX_TOKENS =  512;

    // ---------------------------------------------------------------------
    // Text Generation
    // https://developers.cloudflare.com/workers-ai/models/text-generation/
    // ---------------------------------------------------------------------
    /**
     * Model names
     */
    public static final String LLAMA2_7B_FULL = "@cf/meta/llama-2-7b-chat-fp16";
    /**
     * Model names
     */
    public static final String LLAMA2_7B_QUANTIZED = "@cf/meta/llama-2-7b-chat-int8";
    /**
     * Model names
     */
    public static final String LLAMA2_7B_AWQ = "@hf/thebloke/codellama-7b-instruct-awq";
    /**
     * Model names
     */
    public static final String MISTRAL_7B_INSTRUCT = "@cf/mistral/mistral-7b-instruct-v0.1";

    // ---------------------------------------------------------------------
    // Text Embeddings
    // https://developers.cloudflare.com/workers-ai/models/text-embeddings/
    // ---------------------------------------------------------------------

    /**
     * Model names
     * maxToken: 512, dimension 384
     */
    public static final String BAAI_EMBEDDING_SMALL = "@cf/baai/bge-small-en-v1.5";
    /**
     * Model names
     * maxToken: 512, dimension 768
     */
    public static final String BAAI_EMBEDDING_BASE = "@cf/baai/bge-base-en-v1.5";
    /**
     * Model names
     * maxToken: 512, dimension 1024
     */
    public static final String BAAI_EMBEDDING_LARGE = "@cf/baai/bge-large-en-v1.5";


    // ---------------------------------------------------------------------
    // Text to image
    // https://developers.cloudflare.com/workers-ai/models/text-to-image/
    // ---------------------------------------------------------------------

    /**
     * Model names
     */
    public static final String STABLE_DIFFUSION_XL = "@cf/stabilityai/stable-diffusion-xl-base-1.0";

    /**
     * Hide constructor for utility classes.
     */
    private WorkerAiModelName() {
    }
}
