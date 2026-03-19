package dev.langchain4j.model.embedding.onnx;

/**
 * Configuration for image preprocessing before vision model inference.
 *
 * <p>
 * Encapsulates the transformation parameters (resizing, cropping, normalization) required to prepare an image for
 * an ONNX vision model.
 *
 * <p>
 * <strong>Hugging Face Compatibility:</strong> This configuration directly corresponds — albeit incomplete — to the
 * {@code preprocessor_config.json} (or {@code preprocessor_config.yml}) file found in Hugging Face
 * model repositories. These values ensure that the image input is processed identically to
 * how the model was trained.
 */
public final class ImagePreprocessorConfig {
    private final int imageSize;
    private final int cropSize;
    private final boolean doResize;
    private final boolean doCenterCrop;
    private final boolean doNormalize;
    private final boolean doConvertRgb;
    private final float[] imageMean;
    private final float[] imageStd;

    private ImagePreprocessorConfig(Builder builder) {
        this.imageSize = builder.imageSize;
        this.cropSize = builder.cropSize;
        this.doResize = builder.doResize;
        this.doCenterCrop = builder.doCenterCrop;
        this.doNormalize = builder.doNormalize;
        this.doConvertRgb = builder.doConvertRgb;
        this.imageMean = builder.imageMean;
        this.imageStd = builder.imageStd;
    }

    /** @return The size to which the image should be resized. */
    public int imageSize() {
        return imageSize;
    }

    /** @return The size of the final square crop taken from the center of the image. */
    public int cropSize() {
        return cropSize;
    }

    /** @return Whether the image should be resized before inference. */
    public boolean doResize() {
        return doResize;
    }

    /** @return Whether a center crop should be applied to the image. */
    public boolean doCenterCrop() {
        return doCenterCrop;
    }

    /** @return Whether the pixel values should be normalized using mean and standard deviation. */
    public boolean doNormalize() {
        return doNormalize;
    }

    /** @return Whether the image should be converted to the RGB color space. */
    public boolean doConvertRgb() {
        return doConvertRgb;
    }

    /** @return The mean values for each channel (R, G, B) used for normalization. */
    public float[] imageMean() {
        return imageMean;
    }

    /** @return The standard deviation values for each channel (R, G, B) used for normalization. */
    public float[] imageStd() {
        return imageStd;
    }

    /** @return A new builder instance with default configuration values. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ImagePreprocessorConfig}.
     * Defaults are aligned with common vision models (e.g., 224x224 input).
     */
    public static class Builder {
        private int imageSize = 224;
        private int cropSize = 224;
        private boolean doResize = true;
        private boolean doCenterCrop = true;
        private boolean doNormalize = true;
        private boolean doConvertRgb = true;
        private float[] imageMean = {0.5f, 0.5f, 0.5f};
        private float[] imageStd = {0.5f, 0.5f, 0.5f};

        public Builder imageSize(int imageSize) {
            this.imageSize = imageSize;
            return this;
        }

        public Builder cropSize(int cropSize) {
            this.cropSize = cropSize;
            return this;
        }

        public Builder doResize(boolean doResize) {
            this.doResize = doResize;
            return this;
        }

        public Builder doCenterCrop(boolean doCenterCrop) {
            this.doCenterCrop = doCenterCrop;
            return this;
        }

        public Builder doNormalize(boolean doNormalize) {
            this.doNormalize = doNormalize;
            return this;
        }

        public Builder doConvertRgb(boolean doConvertRgb) {
            this.doConvertRgb = doConvertRgb;
            return this;
        }

        public Builder imageMean(float[] imageMean) {
            this.imageMean = imageMean;
            return this;
        }

        public Builder imageStd(float[] imageStd) {
            this.imageStd = imageStd;
            return this;
        }

        public ImagePreprocessorConfig build() {
            return new ImagePreprocessorConfig(this);
        }
    }
}
