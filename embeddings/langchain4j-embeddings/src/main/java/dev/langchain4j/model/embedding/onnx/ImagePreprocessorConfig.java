package dev.langchain4j.model.embedding.onnx;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import java.util.Arrays;

/**
 * Describes how an image must be prepared before it is fed into a vision model: how it is resized, cropped and
 * normalized.
 * <p>
 * Every vision model expects its input in one specific shape and value range, decided when the model was trained.
 * Feeding it an image prepared differently does not fail, it silently produces a meaningless embedding, so these
 * values must match the model being used.
 * <p>
 * The values for a model published on <a href="https://huggingface.co/">HuggingFace</a> can be found in the
 * {@code preprocessor_config.json} file of its repository, whose fields this class mirrors. For the common case of
 * a CLIP model, {@link #CLIP} already contains them.
 *
 * @see OnnxImageEmbeddingModel
 */
@Experimental
public final class ImagePreprocessorConfig {

    /**
     * The configuration most vision models expect: a 224x224 center crop, with pixel values scaled to [-1, 1].
     */
    public static final ImagePreprocessorConfig DEFAULT =
            ImagePreprocessorConfig.builder().build();

    /**
     * The configuration used by <a href="https://huggingface.co/openai/clip-vit-base-patch32">CLIP</a> models: a
     * 224x224 center crop, normalized with the mean and standard deviation of the dataset CLIP was trained on.
     */
    public static final ImagePreprocessorConfig CLIP = ImagePreprocessorConfig.builder()
            .imageMean(new float[] {0.48145466f, 0.4578275f, 0.40821073f})
            .imageStd(new float[] {0.26862954f, 0.26130258f, 0.27577711f})
            .build();

    private final int imageSize;
    private final int cropSize;
    private final boolean doResize;
    private final boolean doCenterCrop;
    private final boolean doNormalize;
    private final boolean doConvertRgb;
    private final float[] imageMean;
    private final float[] imageStd;

    private ImagePreprocessorConfig(Builder builder) {
        this.imageSize = getOrDefault(builder.imageSize, 224);
        this.cropSize = getOrDefault(builder.cropSize, 224);
        this.doResize = getOrDefault(builder.doResize, true);
        this.doCenterCrop = getOrDefault(builder.doCenterCrop, true);
        this.doNormalize = getOrDefault(builder.doNormalize, true);
        this.doConvertRgb = getOrDefault(builder.doConvertRgb, true);
        this.imageMean = copyOf(getOrDefault(builder.imageMean, new float[] {0.5f, 0.5f, 0.5f}), "imageMean");
        this.imageStd = copyOf(getOrDefault(builder.imageStd, new float[] {0.5f, 0.5f, 0.5f}), "imageStd");
    }

    private static float[] copyOf(float[] channelValues, String name) {
        if (channelValues.length != 3) {
            throw new IllegalArgumentException(
                    name + " must contain exactly 3 values (one per RGB channel), but got " + channelValues.length);
        }
        return Arrays.copyOf(channelValues, channelValues.length);
    }

    /** @return the size the shortest side of the image is resized to. */
    public int imageSize() {
        return imageSize;
    }

    /** @return the side length of the square crop taken from the center of the image. */
    public int cropSize() {
        return cropSize;
    }

    /** @return whether the image is resized before inference. */
    public boolean doResize() {
        return doResize;
    }

    /** @return whether a center crop is applied to the image. */
    public boolean doCenterCrop() {
        return doCenterCrop;
    }

    /** @return whether pixel values are normalized using {@link #imageMean()} and {@link #imageStd()}. */
    public boolean doNormalize() {
        return doNormalize;
    }

    /** @return whether the image is converted to the RGB color space, dropping any transparency. */
    public boolean doConvertRgb() {
        return doConvertRgb;
    }

    /** @return the mean value of each channel (R, G, B) subtracted during normalization. */
    public float[] imageMean() {
        return Arrays.copyOf(imageMean, imageMean.length);
    }

    /** @return the standard deviation of each channel (R, G, B) divided by during normalization. */
    public float[] imageStd() {
        return Arrays.copyOf(imageStd, imageStd.length);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer imageSize;
        private Integer cropSize;
        private Boolean doResize;
        private Boolean doCenterCrop;
        private Boolean doNormalize;
        private Boolean doConvertRgb;
        private float[] imageMean;
        private float[] imageStd;

        /**
         * @param imageSize the size the shortest side of the image is resized to, preserving the aspect ratio.
         *                  Defaults to 224.
         */
        public Builder imageSize(int imageSize) {
            this.imageSize = imageSize;
            return this;
        }

        /**
         * @param cropSize the side length of the square crop taken from the center of the resized image. This is
         *                 the resolution the model finally sees. Defaults to 224.
         */
        public Builder cropSize(int cropSize) {
            this.cropSize = cropSize;
            return this;
        }

        /**
         * @param doResize whether to resize the image to {@link #imageSize(int)}. Defaults to true.
         */
        public Builder doResize(boolean doResize) {
            this.doResize = doResize;
            return this;
        }

        /**
         * @param doCenterCrop whether to crop the image to {@link #cropSize(int)}. Defaults to true.
         */
        public Builder doCenterCrop(boolean doCenterCrop) {
            this.doCenterCrop = doCenterCrop;
            return this;
        }

        /**
         * @param doNormalize whether to normalize pixel values with {@link #imageMean(float[])} and
         *                    {@link #imageStd(float[])}. When false, they are simply scaled to [0, 1].
         *                    Defaults to true.
         */
        public Builder doNormalize(boolean doNormalize) {
            this.doNormalize = doNormalize;
            return this;
        }

        /**
         * @param doConvertRgb whether to convert the image to the RGB color space first, which drops any
         *                     transparency and turns grayscale or indexed-color images into RGB.
         *                     Defaults to true.
         */
        public Builder doConvertRgb(boolean doConvertRgb) {
            this.doConvertRgb = doConvertRgb;
            return this;
        }

        /**
         * @param imageMean the mean value of each channel (R, G, B), subtracted from every pixel during
         *                  normalization. Defaults to {@code {0.5, 0.5, 0.5}}.
         */
        public Builder imageMean(float[] imageMean) {
            this.imageMean = imageMean;
            return this;
        }

        /**
         * @param imageStd the standard deviation of each channel (R, G, B), divided by during normalization.
         *                 Defaults to {@code {0.5, 0.5, 0.5}}.
         */
        public Builder imageStd(float[] imageStd) {
            this.imageStd = imageStd;
            return this;
        }

        public ImagePreprocessorConfig build() {
            return new ImagePreprocessorConfig(this);
        }
    }
}
