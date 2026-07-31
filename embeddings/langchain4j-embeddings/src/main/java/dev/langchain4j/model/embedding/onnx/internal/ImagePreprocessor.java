package dev.langchain4j.model.embedding.onnx.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.embedding.onnx.ImagePreprocessorConfig;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Loads and preprocesses images for vision models: decode, convert to RGB,
 * resize, center-crop, normalize, and convert to NCHW float tensor format.
 */
@Internal
public class ImagePreprocessor {
    private final ImagePreprocessorConfig config;

    public ImagePreprocessor(ImagePreprocessorConfig config) {
        this.config = config;
    }

    /**
     * Load and preprocess a LangChain4j {@link Image} into a [1, 3, H, W] NCHW float tensor.
     *
     * @param image a LangChain4j Image (URL or base64)
     * @return float tensor in [batch, channels, height, width] format
     */
    public float[][][][] process(Image image) {
        BufferedImage bufferedImage = ImageFactory.load(image);

        if (config.doConvertRgb()) {
            bufferedImage = ImageFactory.convertToRgb(bufferedImage);
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int[] pixels = bufferedImage.getRGB(0, 0, width, height, null, 0, width);

        return process(pixels, width, height);
    }

    /**
     * Preprocess raw ARGB pixels into a [1, 3, H, W] NCHW float tensor.
     *
     * @param pixels      packed ARGB pixel array
     * @param imageWidth  original image width
     * @param imageHeight original image height
     * @return float tensor in [batch, channels, height, width] format
     */
    public float[][][][] process(int[] pixels, int imageWidth, int imageHeight) {
        Objects.requireNonNull(pixels, "Pixels array cannot be null");
        if (pixels.length == 0 || imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("Invalid image dimensions or empty data");
        }

        int[] currentPixels = pixels;
        int currentWidth = imageWidth;
        int currentHeight = imageHeight;

        if (config.doResize()) {
            int shortestSide = Math.min(currentWidth, currentHeight);
            double scaleFactor = (double) config.imageSize() / shortestSide;
            int resizedWidth = (int) Math.round(currentWidth * scaleFactor);
            int resizedHeight = (int) Math.round(currentHeight * scaleFactor);
            currentPixels = bilinearResize(currentPixels, currentWidth, currentHeight, resizedWidth, resizedHeight);
            currentWidth = resizedWidth;
            currentHeight = resizedHeight;
        }

        if (config.doCenterCrop()) {
            int cropSize = config.cropSize();
            currentPixels = centerCrop(currentPixels, currentWidth, currentHeight, cropSize);
            currentWidth = cropSize;
            currentHeight = cropSize;
        }

        return toNchwTensor(currentPixels, currentWidth, currentHeight);
    }

    /**
     * Resize an image using bilinear interpolation with half-pixel center-aligned
     * coordinate mapping, matching the convention used by PIL/HuggingFace.
     *
     * @param sourcePixels packed ARGB pixel array of the source image
     * @param sourceWidth  width of the source image in pixels
     * @param sourceHeight height of the source image in pixels
     * @param targetWidth  desired width of the resized image
     * @param targetHeight desired height of the resized image
     * @return packed ARGB pixel array of the resized image
     */
    static int[] bilinearResize(
            int[] sourcePixels, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        int[] targetPixels = new int[targetWidth * targetHeight];
        double xScale = (double) sourceWidth / targetWidth;
        double yScale = (double) sourceHeight / targetHeight;

        for (int targetY = 0; targetY < targetHeight; targetY++) {
            double mappedY = Math.max(0, (targetY + 0.5) * yScale - 0.5);
            int topY = (int) mappedY;
            int bottomY = Math.min(topY + 1, sourceHeight - 1);
            double yFraction = mappedY - topY;

            for (int targetX = 0; targetX < targetWidth; targetX++) {
                double mappedX = Math.max(0, (targetX + 0.5) * xScale - 0.5);
                int leftX = (int) mappedX;
                int rightX = Math.min(leftX + 1, sourceWidth - 1);
                double xFraction = mappedX - leftX;

                int topLeft = sourcePixels[topY * sourceWidth + leftX];
                int topRight = sourcePixels[topY * sourceWidth + rightX];
                int bottomLeft = sourcePixels[bottomY * sourceWidth + leftX];
                int bottomRight = sourcePixels[bottomY * sourceWidth + rightX];

                int red = interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, xFraction, yFraction, 16);
                int green = interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, xFraction, yFraction, 8);
                int blue = interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, xFraction, yFraction, 0);

                targetPixels[targetY * targetWidth + targetX] = 0xFF000000 | (red << 16) | (green << 8) | blue;
            }
        }
        return targetPixels;
    }

    /**
     * Extracts a square centre crop from the source image.
     *
     * @param sourcePixels row-major ARGB pixels
     * @param sourceWidth  source image width
     * @param sourceHeight source image height
     * @param cropSize     side length of the square crop
     * @return cropSize × cropSize pixels taken from the centre
     */
    static int[] centerCrop(int[] sourcePixels, int sourceWidth, int sourceHeight, int cropSize) {
        int xOffset = (sourceWidth - cropSize) / 2;
        int yOffset = (sourceHeight - cropSize) / 2;
        int[] croppedPixels = new int[cropSize * cropSize];

        for (int row = 0; row < cropSize; row++) {
            System.arraycopy(
                    sourcePixels, (row + yOffset) * sourceWidth + xOffset, croppedPixels, row * cropSize, cropSize);
        }
        return croppedPixels;
    }

    /**
     * Interpolates a single color channel using bilinear weights at the given fractional position.
     *
     * @param topLeft     packed ARGB pixel at the top-left corner
     * @param topRight    packed ARGB pixel at the top-right corner
     * @param bottomLeft  packed ARGB pixel at the bottom-left corner
     * @param bottomRight packed ARGB pixel at the bottom-right corner
     * @param xFraction   horizontal interpolation weight (0.0 = left edge, 1.0 = right edge)
     * @param yFraction   vertical interpolation weight (0.0 = top edge, 1.0 = bottom edge)
     * @param channelShift bit shift to extract the target channel (e.g. 16 for red, 8 for green, 0 for blue)
     * @return the interpolated channel value, clamped to [0, 255]
     */
    static int interpolateChannel(
            int topLeft,
            int topRight,
            int bottomLeft,
            int bottomRight,
            double xFraction,
            double yFraction,
            int channelShift) {
        double topInterpolated =
                ((topLeft >> channelShift) & 0xFF) * (1 - xFraction) + ((topRight >> channelShift) & 0xFF) * xFraction;
        double bottomInterpolated = ((bottomLeft >> channelShift) & 0xFF) * (1 - xFraction)
                + ((bottomRight >> channelShift) & 0xFF) * xFraction;
        double interpolatedValue = topInterpolated * (1 - yFraction) + bottomInterpolated * yFraction;
        return Math.min(255, Math.max(0, (int) Math.round(interpolatedValue)));
    }

    /**
     * Converts ARGB-packed pixels into an NCHW tensor normalised to [-1, 1].
     *
     * @param pixels row-major ARGB pixels (e.g. from {@link BufferedImage#getRGB})
     * @param width  image width
     * @param height image height
     * @return float[1][3][height][width] — batch of one, RGB channels
     */
    float[][][][] toNchwTensor(int[] pixels, int width, int height) {
        float[][][][] tensor = new float[1][3][height][width];
        float[] channelMean = config.imageMean();
        float[] channelStd = config.imageStd();
        boolean shouldNormalize = config.doNormalize();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int argb = pixels[row * width + col];
                float red = ((argb >> 16) & 0xFF) / 255.0f;
                float green = ((argb >> 8) & 0xFF) / 255.0f;
                float blue = (argb & 0xFF) / 255.0f;

                if (shouldNormalize) {
                    tensor[0][0][row][col] = (red - channelMean[0]) / channelStd[0];
                    tensor[0][1][row][col] = (green - channelMean[1]) / channelStd[1];
                    tensor[0][2][row][col] = (blue - channelMean[2]) / channelStd[2];
                } else {
                    tensor[0][0][row][col] = red;
                    tensor[0][1][row][col] = green;
                    tensor[0][2][row][col] = blue;
                }
            }
        }
        return tensor;
    }
}
