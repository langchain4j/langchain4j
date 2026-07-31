package dev.langchain4j.model.embedding.onnx.internal;

import static dev.langchain4j.model.embedding.onnx.internal.ImagePreprocessor.bilinearResize;
import static dev.langchain4j.model.embedding.onnx.internal.ImagePreprocessor.centerCrop;
import static dev.langchain4j.model.embedding.onnx.internal.ImagePreprocessor.interpolateChannel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.embedding.onnx.ImagePreprocessorConfig;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImagePreprocessorTest {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    @Nested
    @DisplayName("process")
    class Process {
        private static final int SIZE = 224;

        @Nested
        @DisplayName("via Image object")
        class ViaImage {
            private static URI uriForResource(String image) throws URISyntaxException {
                return ImagePreprocessorTest.class.getResource(image).toURI();
            }

            @Test
            @DisplayName("delegates correctly from Image object to pixel-based process")
            void delegatesToPixelProcess() throws Exception {
                // Given
                var config = ImagePreprocessorConfig.builder()
                        .doResize(false)
                        .doCenterCrop(false)
                        .build();
                var subject = new ImagePreprocessor(config);

                var image = Image.builder().url(uriForResource("/cat.jpg")).build();

                // When
                float[][][][] result = subject.process(image);

                // Then
                assertThat(result).isNotNull().hasNumberOfRows(1);
                assertThat(result[0]).hasNumberOfRows(3);
            }

            @Test
            void respectsRgbConversionFlag() throws Exception {
                // Given — an RGBA PNG image (4 channels) with known dimensions
                var config = ImagePreprocessorConfig.builder()
                        .doConvertRgb(true)
                        .doResize(false)
                        .doCenterCrop(false)
                        .doNormalize(false)
                        .build();
                var subject = new ImagePreprocessor(config);
                var image = Image.builder().url(uriForResource("/cat.png")).build();

                var sourceImage =
                        ImageIO.read(new File(getClass().getResource("/cat.png").toURI()));
                int expectedWidth = sourceImage.getWidth();
                int expectedHeight = sourceImage.getHeight();

                // When
                float[][][][] result = subject.process(image);

                // Then — output is [1, 3, H, W] with exactly 3 channels (alpha stripped)
                assertThat(result).hasNumberOfRows(1);
                assertThat(result[0]).hasNumberOfRows(3); // RGB, not RGBA
                assertThat(result[0][0]).hasNumberOfRows(expectedHeight);
                assertThat(result[0][0][0]).hasSize(expectedWidth);

                // All pixel values should be in [0, 1] range (unnormalized RGB)
                for (int c = 0; c < 3; c++) {
                    for (int y = 0; y < expectedHeight; y++) {
                        for (int x = 0; x < expectedWidth; x++) {
                            assertThat(result[0][c][y][x]).isBetween(0.0f, 1.0f);
                        }
                    }
                }
            }

            @Test
            void skipsRgbConversionWhenDisabled() throws Exception {
                // Given — same RGBA image but conversion disabled
                var config = ImagePreprocessorConfig.builder()
                        .doConvertRgb(false)
                        .doResize(false)
                        .doCenterCrop(false)
                        .doNormalize(false)
                        .build();
                var subject = new ImagePreprocessor(config);
                var image = Image.builder().url(uriForResource("/cat.png")).build();

                // When
                float[][][][] result = subject.process(image);

                // Then — still 3 channels because getRGB() returns packed ARGB, but pixel values may differ from the
                // converted version due to alpha not being composited onto white.
                assertThat(result).hasNumberOfRows(1);
                assertThat(result[0]).hasNumberOfRows(3);
            }

            @Test
            void rgbConversionIsNoOpForJpeg() throws Exception {
                // Given — a JPEG (already RGB, no alpha channel)
                var configWithConversion = ImagePreprocessorConfig.builder()
                        .doConvertRgb(true)
                        .doResize(false)
                        .doCenterCrop(false)
                        .doNormalize(false)
                        .build();
                var configWithoutConversion = ImagePreprocessorConfig.builder()
                        .doConvertRgb(false)
                        .doResize(false)
                        .doCenterCrop(false)
                        .doNormalize(false)
                        .build();
                var image = Image.builder().url(uriForResource("/cat.jpg")).build();

                // When
                float[][][][] withConversion = new ImagePreprocessor(configWithConversion).process(image);
                float[][][][] withoutConversion = new ImagePreprocessor(configWithoutConversion).process(image);

                // Then — results should be identical for an already-RGB image
                assertThat(withConversion).isDeepEqualTo(withoutConversion);
            }
        }

        @Nested
        @DisplayName("when both doResize and doCenterCrop are enabled")
        class ResizeAndCrop {
            private ImagePreprocessor subject;

            @BeforeEach
            void setUp() {
                subject = new ImagePreprocessor(ImagePreprocessorConfig.builder()
                        .cropSize(SIZE)
                        .imageSize(SIZE)
                        .build());
            }

            @Test
            @DisplayName("output tensor has crop dimensions")
            void outputShape() {
                float[][][][] t = subject.process(grid(640, 480), 640, 480);

                assertThat(t).hasNumberOfRows(1);
                assertThat(t[0]).hasNumberOfRows(3);
                assertThat(t[0][0]).hasDimensions(SIZE, SIZE);
            }

            @Test
            @DisplayName("landscape image: height becomes imageSize, width scales proportionally then crops")
            void landscapeScaling() {
                float[][][][] t = subject.process(grid(640, 480), 640, 480);
                assertThat(t[0][0]).hasNumberOfRows(SIZE);
                assertThat(t[0][0][0]).hasSize(SIZE);
            }

            @Test
            @DisplayName("portrait image: width becomes imageSize, height scales proportionally then crops")
            void portraitScaling() {
                float[][][][] t = subject.process(grid(480, 640), 480, 640);
                assertThat(t[0][0]).hasNumberOfRows(SIZE);
                assertThat(t[0][0][0]).hasSize(SIZE);
            }

            @Test
            @DisplayName("square image at exact target size is unchanged")
            void alreadyCorrectSize() {
                int[] px = grid(SIZE, SIZE);
                float[][][][] t = subject.process(px, SIZE, SIZE);

                float expectedR = ((px[0] >> 16) & 0xFF) / 255f;
                float normalised = expectedR * 2f - 1f;
                assertThat(t[0][0][0][0]).isCloseTo(normalised, within(1e-4f));
            }

            @Test
            @DisplayName("non-square image smaller than target is upscaled before crop")
            void upscalesSmallImage() {
                float[][][][] t = subject.process(grid(100, 50), 100, 50);
                assertThat(t[0][0]).hasNumberOfRows(SIZE);
                assertThat(t[0][0][0]).hasSize(SIZE);
            }
        }

        @Nested
        @DisplayName("when only doResize is enabled")
        class ResizeOnly {

            @Test
            @DisplayName("output tensor matches resized dimensions, no crop applied")
            void resizedButNotCropped() {
                var subject = new ImagePreprocessor(
                        ImagePreprocessorConfig.builder().doCenterCrop(false).build());

                float[][][][] t = subject.process(grid(640, 480), 640, 480);
                assertThat(t[0][0]).hasNumberOfRows(224);
                assertThat(t[0][0][0]).hasSize(299);
            }
        }

        @Nested
        @DisplayName("when only doCenterCrop is enabled")
        class CropOnly {

            @Test
            @DisplayName("crops from original dimensions without prior resize")
            void croppedButNotResized() {
                var subject = new ImagePreprocessor(
                        ImagePreprocessorConfig.builder().doResize(false).build());

                float[][][][] t = subject.process(grid(640, 480), 640, 480);
                assertThat(t[0][0]).hasDimensions(SIZE, SIZE);
            }

            @Test
            @DisplayName("crop centre pixel comes from the original image centre")
            void cropCentreMatchesOriginal() {
                int w = 640, h = 480;
                int[] px = grid(w, h);
                var subject = new ImagePreprocessor(
                        ImagePreprocessorConfig.builder().doResize(false).build());

                float[][][][] t = subject.process(px, w, h);

                int cx = w / 2, cy = h / 2;
                int centrePixel = px[cy * w + cx];
                float expectedR = ((centrePixel >> 16) & 0xFF) / 255f * 2f - 1f;
                float expectedG = ((centrePixel >> 8) & 0xFF) / 255f * 2f - 1f;

                int tc = SIZE / 2;
                assertThat(t[0][0][tc][tc]).isCloseTo(expectedR, within(1e-4f));
                assertThat(t[0][1][tc][tc]).isCloseTo(expectedG, within(1e-4f));
            }
        }

        @Nested
        @DisplayName("when neither doResize nor doCenterCrop is enabled")
        class PassThrough {

            @Test
            @DisplayName("output tensor matches original dimensions")
            void originalDimensions() {
                var subject = new ImagePreprocessor(ImagePreprocessorConfig.builder()
                        .doCenterCrop(false)
                        .doResize(false)
                        .build());

                float[][][][] t = subject.process(grid(37, 13), 37, 13);
                assertThat(t[0][0]).hasNumberOfRows(13);
                assertThat(t[0][0][0]).hasSize(37);
            }

            @Test
            @DisplayName("pixel values pass straight through to tensor")
            void valuesPreserved() {
                var subject = new ImagePreprocessor(ImagePreprocessorConfig.builder()
                        .doCenterCrop(false)
                        .doResize(false)
                        .build());

                int[] px = {0xFFFF0000};
                float[][][][] t = subject.process(px, 1, 1);

                assertThat(t[0][0][0][0]).isCloseTo(1.0f, within(1e-6f));
                assertThat(t[0][1][0][0]).isCloseTo(-1.0f, within(1e-6f));
                assertThat(t[0][2][0][0]).isCloseTo(-1.0f, within(1e-6f));
            }
        }

        @Nested
        @DisplayName("input safety")
        class InputSafety {

            @Test
            @DisplayName("process does not mutate the original pixel array")
            void doesNotMutateInput() {
                var subject =
                        new ImagePreprocessor(ImagePreprocessorConfig.builder().build());

                int[] px = grid(640, 480);
                int[] copy = px.clone();
                subject.process(px, 640, 480);

                assertThat(px).containsExactly(copy);
            }
        }
    }

    @Nested
    @DisplayName("bilinearResize")
    class BilinearResizeTest {
        @Test
        @DisplayName("Same size → returns identical pixels")
        void sameSize() {
            int[] src = grid(4, 4);
            int[] resized = bilinearResize(src, 4, 4, 4, 4);

            assertThat(resized).containsExactly(src);
        }

        @Test
        @DisplayName("Result length is targetWidth × targetHeight")
        void resultLength() {
            int[] resized = bilinearResize(grid(10, 8), 10, 8, 5, 4);
            assertThat(resized).hasSize(20);
        }

        @Test
        @DisplayName("1×1 source scaled up → uniform colour")
        void singlePixelScaleUp() {
            int[] src = {rgb(100, 150, 200)};
            int[] resized = bilinearResize(src, 1, 1, 5, 5);

            assertThat(resized).containsOnly(rgb(100, 150, 200));
        }

        @Test
        @DisplayName("Uniform image stays uniform after resize")
        void uniformImage() {
            int w = 8, h = 6;
            int[] src = new int[w * h];
            java.util.Arrays.fill(src, rgb(42, 42, 42));

            int[] resized = bilinearResize(src, w, h, 3, 3);

            assertThat(resized).containsOnly(rgb(42, 42, 42));
        }

        @Test
        @DisplayName("Downscale — top-left output comes from top-left region of source")
        void topLeftFromTopLeftRegion() {
            int w = 8, h = 8;
            int[] src = grid(w, h);

            int[] resized = bilinearResize(src, w, h, 2, 2);

            int r = (resized[0] >> 16) & 0xFF;
            int g = (resized[0] >> 8) & 0xFF;
            assertThat(r).as("x should be in left half").isLessThan(w / 2);
            assertThat(g).as("y should be in top half").isLessThan(h / 2);
        }

        @Test
        @DisplayName("Downscale — bottom-right output comes from bottom-right region of source")
        void bottomRightFromBottomRightRegion() {
            int w = 8, h = 8;
            int[] src = grid(w, h);

            int[] resized = bilinearResize(src, w, h, 2, 2);

            int r = (resized[3] >> 16) & 0xFF;
            int g = (resized[3] >> 8) & 0xFF;
            assertThat(r).as("x should be in right half").isGreaterThanOrEqualTo(w / 2);
            assertThat(g).as("y should be in bottom half").isGreaterThanOrEqualTo(h / 2);
        }

        @Test
        @DisplayName("Scale down 2× — each output pixel samples the centre of its 2×2 block")
        void halfSize() {
            int[] src = new int[4 * 4];
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) src[y * 4 + x] = rgb(x * 80, 0, 0);

            int[] resized = bilinearResize(src, 4, 4, 2, 2);

            int r0 = (resized[0] >> 16) & 0xFF;
            int r1 = (resized[1] >> 16) & 0xFF;
            assertThat(r0)
                    .as("Left pixel should have less red than right pixel")
                    .isLessThan(r1);
        }

        @Test
        @DisplayName("Scale up 2× — output is smooth (no identical blocks)")
        void doubleSize() {
            int[] src = {rgb(0, 0, 0), rgb(255, 0, 0), rgb(0, 255, 0), rgb(255, 255, 0)};

            int[] resized = bilinearResize(src, 2, 2, 4, 4);

            assertThat(resized).hasSize(16);
            int centre = resized[5];
            int r = (centre >> 16) & 0xFF;
            int g = (centre >> 8) & 0xFF;

            assertThat(r).isBetween(1, 254);
            assertThat(g).isBetween(1, 254);
        }

        @Test
        @DisplayName("Horizontal gradient — monotonically increasing after resize")
        void horizontalGradientMonotonic() {
            int w = 256, h = 1;
            int[] src = new int[w];
            for (int x = 0; x < w; x++) src[x] = rgb(x, 0, 0);

            int targetW = 64;
            int[] resized = bilinearResize(src, w, h, targetW, 1);

            for (int x = 1; x < targetW; x++) {
                int prev = (resized[x - 1] >> 16) & 0xFF;
                int curr = (resized[x] >> 16) & 0xFF;
                assertThat(curr).as("Red should be non-decreasing at x=%d", x).isGreaterThanOrEqualTo(prev);
            }
        }

        @Test
        @DisplayName("Vertical gradient — monotonically increasing after resize")
        void verticalGradientMonotonic() {
            int w = 1, h = 256;
            int[] src = new int[h];
            for (int y = 0; y < h; y++) src[y] = rgb(0, y, 0);

            int targetH = 64;
            int[] resized = bilinearResize(src, w, h, 1, targetH);

            for (int y = 1; y < targetH; y++) {
                int prev = (resized[y - 1] >> 8) & 0xFF;
                int curr = (resized[y] >> 8) & 0xFF;
                assertThat(curr).as("Green should be non-decreasing at y=%d", y).isGreaterThanOrEqualTo(prev);
            }
        }

        @Test
        @DisplayName("Resize does not mutate the source array")
        void doesNotMutateSource() {
            int[] src = grid(6, 6);
            int[] copy = src.clone();

            bilinearResize(src, 6, 6, 3, 3);

            assertThat(src).containsExactly(copy);
        }

        @ParameterizedTest(name = "source {0}×{1} → target {2}×{3}")
        @CsvSource({"10,10,5,5", "5,5,10,10", "640,480,224,224", "100,50,50,100", "1,1,10,10", "3,7,7,3"})
        @DisplayName("Various resize combinations produce correct output length")
        void variousSizes(int sw, int sh, int tw, int th) {
            int[] src = new int[sw * sh];
            int[] resized = bilinearResize(src, sw, sh, tw, th);

            assertThat(resized).hasSize(tw * th);
        }

        @Test
        @DisplayName("Symmetry — horizontally symmetric input stays symmetric after resize")
        void horizontalSymmetry() {
            int[] src = {rgb(50, 0, 0), rgb(200, 0, 0), rgb(200, 0, 0), rgb(50, 0, 0)};

            int[] resized = bilinearResize(src, 4, 1, 6, 1);

            for (int x = 0; x < 3; x++) {
                int left = (resized[x] >> 16) & 0xFF;
                int right = (resized[5 - x] >> 16) & 0xFF;
                assertThat(left).as("Pixel %d and %d should mirror", x, 5 - x).isEqualTo(right);
            }
        }
    }

    @Nested
    @DisplayName("centerCrop")
    class CenterCrop {
        @Test
        @DisplayName("cropSize == source size → returns identical pixels")
        void sameSize() {
            int[] src = grid(4, 4);
            int[] cropped = centerCrop(src, 4, 4, 4);

            assertThat(cropped).containsExactly(src);
        }

        @Test
        @DisplayName("Result length is cropSize × cropSize")
        void resultLength() {
            int[] cropped = centerCrop(grid(10, 10), 10, 10, 6);
            assertThat(cropped).hasSize(36);
        }

        @Test
        @DisplayName("Square source — crop is centred")
        void squareSource_centred() {
            int[] src = grid(6, 6);
            int[] cropped = centerCrop(src, 6, 6, 2);

            assertThat(cropped[0]).as("top-left of crop").isEqualTo(pixel(2, 2));
            assertThat(cropped[1]).as("top-right of crop").isEqualTo(pixel(3, 2));
            assertThat(cropped[2]).as("bottom-left of crop").isEqualTo(pixel(2, 3));
            assertThat(cropped[3]).as("bottom-right of crop").isEqualTo(pixel(3, 3));
        }

        @Test
        @DisplayName("Wide source — crops horizontally, full height")
        void wideSource() {
            int[] src = grid(10, 4);
            int[] cropped = centerCrop(src, 10, 4, 4);

            for (int y = 0; y < 4; y++)
                for (int x = 0; x < 4; x++)
                    assertThat(cropped[y * 4 + x]).as("(%d,%d)", x, y).isEqualTo(pixel(x + 3, y));
        }

        @Test
        @DisplayName("Tall source — crops vertically, full width")
        void tallSource() {
            int[] src = grid(4, 10);
            int[] cropped = centerCrop(src, 4, 10, 4);

            for (int y = 0; y < 4; y++)
                for (int x = 0; x < 4; x++)
                    assertThat(cropped[y * 4 + x]).as("(%d,%d)", x, y).isEqualTo(pixel(x, y + 3));
        }

        @Test
        @DisplayName("Odd source, even crop — offset rounds down")
        void oddSourceEvenCrop() {
            int[] src = grid(7, 7);
            int[] cropped = centerCrop(src, 7, 7, 4);

            assertThat(cropped[0]).as("top-left").isEqualTo(pixel(1, 1));
            assertThat(cropped[15]).as("bottom-right").isEqualTo(pixel(4, 4));
        }

        @Test
        @DisplayName("1×1 crop from any source returns the centre pixel")
        void cropSizeOne() {
            int[] src = grid(5, 5);
            int[] cropped = centerCrop(src, 5, 5, 1);

            assertThat(cropped).containsExactly(pixel(2, 2));
        }

        @ParameterizedTest(name = "source {0}×{1}, crop {2}")
        @CsvSource({"100,100,50", "640,480,224", "480,640,224", "224,224,224"})
        @DisplayName("Various sizes produce correct output length")
        void variousSizes(int w, int h, int crop) {
            int[] src = new int[w * h];
            int[] cropped = centerCrop(src, w, h, crop);

            assertThat(cropped).hasSize(crop * crop);
        }

        @Test
        @DisplayName("Crop does not mutate the source array")
        void doesNotMutateSource() {
            int[] src = grid(6, 6);
            int[] copy = src.clone();

            centerCrop(src, 6, 6, 4);

            assertThat(src).containsExactly(copy);
        }
    }

    @Nested
    @DisplayName("interpolateChannel")
    class InterpolateChannelTest {

        @Test
        @DisplayName("All corners equal → returns that value regardless of fractions")
        void uniformCorners() {
            for (int channelShift : new int[] {RED_SHIFT, GREEN_SHIFT, BLUE_SHIFT}) {
                assertThat(uniform(100, channelShift, 0.0, 0.0)).isEqualTo(100);
                assertThat(uniform(100, channelShift, 0.5, 0.5)).isEqualTo(100);
                assertThat(uniform(100, channelShift, 1.0, 1.0)).isEqualTo(100);
                assertThat(uniform(100, channelShift, 0.73, 0.29)).isEqualTo(100);
            }
        }

        @Test
        @DisplayName("Fraction (0,0) → returns topLeft")
        void topLeftCorner() {
            int topLeft = pack(10, RED_SHIFT), topRight = pack(20, RED_SHIFT);
            int bottomLeft = pack(30, RED_SHIFT), bottomRight = pack(40, RED_SHIFT);

            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.0, 0.0, RED_SHIFT))
                    .isEqualTo(10);
        }

        @Test
        @DisplayName("Fraction (1,0) → returns topRight")
        void topRightCorner() {
            int topLeft = pack(10, RED_SHIFT), topRight = pack(20, RED_SHIFT);
            int bottomLeft = pack(30, RED_SHIFT), bottomRight = pack(40, RED_SHIFT);

            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 1.0, 0.0, RED_SHIFT))
                    .isEqualTo(20);
        }

        @Test
        @DisplayName("Fraction (0,1) → returns bottomLeft")
        void bottomLeftCorner() {
            int topLeft = pack(10, RED_SHIFT), topRight = pack(20, RED_SHIFT);
            int bottomLeft = pack(30, RED_SHIFT), bottomRight = pack(40, RED_SHIFT);

            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.0, 1.0, RED_SHIFT))
                    .isEqualTo(30);
        }

        @Test
        @DisplayName("Fraction (1,1) → returns bottomRight")
        void bottomRightCorner() {
            int topLeft = pack(10, RED_SHIFT), topRight = pack(20, RED_SHIFT);
            int bottomLeft = pack(30, RED_SHIFT), bottomRight = pack(40, RED_SHIFT);

            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 1.0, 1.0, RED_SHIFT))
                    .isEqualTo(40);
        }

        @Test
        @DisplayName("Midpoint of four corners → average of all four")
        void centreIsMean() {
            int topLeft = pack(0, RED_SHIFT), topRight = pack(100, RED_SHIFT);
            int bottomLeft = pack(100, RED_SHIFT), bottomRight = pack(200, RED_SHIFT);

            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.5, 0.5, RED_SHIFT))
                    .isEqualTo(100);
        }

        @Test
        @DisplayName("Horizontal interpolation only (yFraction=0)")
        void horizontalLerp() {
            int topLeft = pack(0, GREEN_SHIFT), topRight = pack(200, GREEN_SHIFT);
            int bottomLeft = pack(0, GREEN_SHIFT), bottomRight = pack(200, GREEN_SHIFT);

            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.5, 0.0, GREEN_SHIFT))
                    .isEqualTo(100);
            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.25, 0.0, GREEN_SHIFT))
                    .isEqualTo(50);
            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.75, 0.0, GREEN_SHIFT))
                    .isEqualTo(150);
        }

        @Test
        @DisplayName("Vertical interpolation only (xFraction=0)")
        void verticalLerp() {
            int topLeft = pack(0, BLUE_SHIFT), topRight = pack(0, BLUE_SHIFT);
            int bottomLeft = pack(200, BLUE_SHIFT), bottomRight = pack(200, BLUE_SHIFT);

            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.0, 0.5, BLUE_SHIFT))
                    .isEqualTo(100);
            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.0, 0.25, BLUE_SHIFT))
                    .isEqualTo(50);
            assertThat(interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.0, 0.75, BLUE_SHIFT))
                    .isEqualTo(150);
        }

        @Test
        @DisplayName("Works correctly for each channel shift")
        void eachChannelShift() {
            int redValue = 80, greenValue = 160, blueValue = 240;
            int pixel = (redValue << RED_SHIFT) | (greenValue << GREEN_SHIFT) | (blueValue << BLUE_SHIFT);

            assertThat(interpolateChannel(pixel, pixel, pixel, pixel, 0.5, 0.5, RED_SHIFT))
                    .isEqualTo(redValue);
            assertThat(interpolateChannel(pixel, pixel, pixel, pixel, 0.5, 0.5, GREEN_SHIFT))
                    .isEqualTo(greenValue);
            assertThat(interpolateChannel(pixel, pixel, pixel, pixel, 0.5, 0.5, BLUE_SHIFT))
                    .isEqualTo(blueValue);
        }

        @Test
        @DisplayName("Other channels in the int do not bleed through")
        void noChannelBleed() {
            int allChannelsMax = (255 << RED_SHIFT) | (255 << GREEN_SHIFT) | (255 << BLUE_SHIFT);
            int zero = 0;

            int result = interpolateChannel(allChannelsMax, allChannelsMax, zero, zero, 0.5, 0.5, RED_SHIFT);
            assertThat(result).isBetween(127, 128);
        }

        @Test
        @DisplayName("Result is in [0, 255]")
        void resultInRange() {
            int lo = pack(0, RED_SHIFT), hi = pack(255, RED_SHIFT);

            for (double xFraction = 0.0; xFraction <= 1.0; xFraction += 0.1) {
                for (double yFraction = 0.0; yFraction <= 1.0; yFraction += 0.1) {
                    int result = interpolateChannel(lo, hi, hi, lo, xFraction, yFraction, RED_SHIFT);
                    assertThat(result)
                            .as("Out of range at (%.1f, %.1f)", xFraction, yFraction)
                            .isBetween(0, 255);
                }
            }
        }

        @Test
        @DisplayName("Bilinear interpolation is symmetric")
        void symmetry() {
            int topLeft = pack(0, RED_SHIFT), topRight = pack(200, RED_SHIFT);
            int bottomLeft = pack(200, RED_SHIFT), bottomRight = pack(0, RED_SHIFT);

            int atPoint = interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.3, 0.7, RED_SHIFT);
            int atMirror = interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, 0.7, 0.3, RED_SHIFT);
            assertThat(atPoint).isEqualTo(atMirror);
        }

        @Test
        @DisplayName("Monotonic along x-axis")
        void monotonicX() {
            int topLeft = pack(0, RED_SHIFT), topRight = pack(255, RED_SHIFT);
            int bottomLeft = pack(0, RED_SHIFT), bottomRight = pack(255, RED_SHIFT);

            int previous = 0;
            for (double xFraction = 0.0; xFraction <= 1.0; xFraction += 0.05) {
                int current = interpolateChannel(topLeft, topRight, bottomLeft, bottomRight, xFraction, 0.0, RED_SHIFT);
                assertThat(current)
                        .as("Not monotonic at xFraction=%.2f", xFraction)
                        .isGreaterThanOrEqualTo(previous);
                previous = current;
            }
        }
    }

    @Nested
    @DisplayName("toNchwTensor")
    class ToNchwTensor {

        @Nested
        @DisplayName("with default config")
        class DefaultConfig {
            private final ImagePreprocessor subject =
                    new ImagePreprocessor(ImagePreprocessorConfig.builder().build());

            @Test
            @DisplayName("Result shape is [1][3][H][W] for a single image")
            void shape_singleImage() {
                int w = 4, h = 3;
                float[][][][] t = subject.toNchwTensor(new int[w * h], w, h);

                assertThat(t).hasNumberOfRows(1);
                assertThat(t[0]).hasNumberOfRows(3);
                assertThat(t[0][0]).hasDimensions(h, w);
            }

            @Test
            @DisplayName("1×1 image produces shape [1][3][1][1]")
            void shape_singlePixel() {
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(128, 64, 32)}, 1, 1);

                assertThat(t).hasNumberOfRows(1);
                assertThat(t[0]).hasNumberOfRows(3);
                assertThat(t[0][0]).hasDimensions(1, 1);
            }

            @Test
            @DisplayName("Single pixel — channels are correctly split and normalised to [-1,1]")
            void singlePixel_channelValues() {
                int r = 200, g = 100, b = 50;
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(r, g, b)}, 1, 1);

                assertThat(t[0][0][0][0]).as("Red").isCloseTo(norm(r), within(1e-5f));
                assertThat(t[0][1][0][0]).as("Green").isCloseTo(norm(g), within(1e-5f));
                assertThat(t[0][2][0][0]).as("Blue").isCloseTo(norm(b), within(1e-5f));
            }

            @Test
            @DisplayName("Mid-grey (128,128,128) → all channels ≈ 0.003 (near zero)")
            void midGrey() {
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(128, 128, 128)}, 1, 1);

                float expected = norm(128);
                for (int c = 0; c < 3; c++) {
                    assertThat(t[0][c][0][0]).as("Channel " + c).isCloseTo(expected, within(1e-5f));
                }
            }

            @Test
            @DisplayName("Pure red pixel → R=1, G=-1, B=-1")
            void pureRed() {
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(255, 0, 0)}, 1, 1);

                assertThat(t[0][0][0][0]).isCloseTo(1.0f, within(1e-5f));
                assertThat(t[0][1][0][0]).isCloseTo(-1.0f, within(1e-5f));
                assertThat(t[0][2][0][0]).isCloseTo(-1.0f, within(1e-5f));
            }

            @Test
            @DisplayName("White pixel (255,255,255) → all channels 1.0")
            void whitePixel() {
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(255, 255, 255)}, 1, 1);
                for (int c = 0; c < 3; c++) {
                    assertThat(t[0][c][0][0]).as("Channel " + c).isCloseTo(1.0f, within(1e-5f));
                }
            }

            @Test
            @DisplayName("Black pixel (0,0,0) → all channels -1.0")
            void blackPixel() {
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(0, 0, 0)}, 1, 1);
                for (int c = 0; c < 3; c++) {
                    assertThat(t[0][c][0][0]).as("Channel " + c).isCloseTo(-1.0f, within(1e-5f));
                }
            }

            @Test
            @DisplayName("All output values are in [-1.0, 1.0]")
            void valuesInNormalisedRange() {
                int w = 8, h = 8;
                int[] pixels = new int[w * h];
                for (int i = 0; i < pixels.length; i++) {
                    pixels[i] = rgb(i * 4 % 256, (i * 7) % 256, (i * 13) % 256);
                }

                float[][][][] t = subject.toNchwTensor(pixels, w, h);

                for (int c = 0; c < 3; c++)
                    for (int row = 0; row < h; row++)
                        for (int col = 0; col < w; col++) {
                            assertThat(t[0][c][row][col])
                                    .as("Value at [0][%d][%d][%d]", c, row, col)
                                    .isBetween(-1.0f, 1.0f);
                        }
            }

            @Test
            @DisplayName("Horizontal red gradient is preserved in channel 0")
            void horizontalGradient() {
                int w = 256, h = 1;
                int[] pixels = new int[w];
                for (int x = 0; x < w; x++) pixels[x] = rgb(x, 0, 0);

                float[][][][] t = subject.toNchwTensor(pixels, w, h);

                for (int x = 0; x < w; x++) {
                    assertThat(t[0][0][0][x]).as("Red at x=" + x).isCloseTo(norm(x), within(1e-5f));
                    assertThat(t[0][1][0][x]).as("Green at x=" + x).isCloseTo(-1.0f, within(1e-5f));
                    assertThat(t[0][2][0][x]).as("Blue at x=" + x).isCloseTo(-1.0f, within(1e-5f));
                }
            }

            @Test
            @DisplayName("Handles 0xFFFFFFFF (white, fully opaque) — a negative int in Java")
            void negativeIntPixel_white() {
                float[][][][] t = subject.toNchwTensor(new int[] {0xFFFFFFFF}, 1, 1);
                assertThat(t[0][0][0][0]).as("Red").isCloseTo(1.0f, within(1e-5f));
            }
        }

        @Nested
        @DisplayName("with doNormalize=false")
        class NoNormalize {
            private final ImagePreprocessor subject = new ImagePreprocessor(
                    ImagePreprocessorConfig.builder().doNormalize(false).build());

            @Test
            @DisplayName("Values are raw channel / 255.0")
            void rawValues() {
                int r = 200, g = 100, b = 50;
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(r, g, b)}, 1, 1);

                assertThat(t[0][0][0][0]).as("Red").isCloseTo(r / 255f, within(1e-5f));
                assertThat(t[0][1][0][0]).as("Green").isCloseTo(g / 255f, within(1e-5f));
                assertThat(t[0][2][0][0]).as("Blue").isCloseTo(b / 255f, within(1e-5f));
            }

            @Test
            @DisplayName("All output values are in [0.0, 1.0]")
            void valuesInUnitRange() {
                int w = 8, h = 8;
                int[] pixels = new int[w * h];
                for (int i = 0; i < pixels.length; i++) {
                    pixels[i] = rgb(i * 4 % 256, (i * 7) % 256, (i * 13) % 256);
                }

                float[][][][] t = subject.toNchwTensor(pixels, w, h);

                for (int c = 0; c < 3; c++)
                    for (int row = 0; row < h; row++)
                        for (int col = 0; col < w; col++) {
                            assertThat(t[0][c][row][col]).isBetween(0.0f, 1.0f);
                        }
            }
        }

        @Nested
        @DisplayName("with custom mean/std (ImageNet)")
        class CustomMeanStd {
            private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
            private static final float[] STD = {0.229f, 0.224f, 0.225f};

            private final ImagePreprocessor subject = new ImagePreprocessor(ImagePreprocessorConfig.builder()
                    .doNormalize(true)
                    .imageMean(MEAN)
                    .imageStd(STD)
                    .build());

            private float expected(int channelValue, int c) {
                return (channelValue / 255f - MEAN[c]) / STD[c];
            }

            @Test
            @DisplayName("Single pixel normalised with ImageNet mean/std")
            void singlePixel() {
                int r = 200, g = 100, b = 50;
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(r, g, b)}, 1, 1);

                assertThat(t[0][0][0][0]).as("Red").isCloseTo(expected(r, 0), within(1e-5f));
                assertThat(t[0][1][0][0]).as("Green").isCloseTo(expected(g, 1), within(1e-5f));
                assertThat(t[0][2][0][0]).as("Blue").isCloseTo(expected(b, 2), within(1e-5f));
            }

            @Test
            @DisplayName("Per-channel mean/std — channels are not mixed up")
            void channelsNotSwapped() {
                float[][][][] t = subject.toNchwTensor(new int[] {rgb(255, 0, 128)}, 1, 1);

                assertThat(t[0][0][0][0]).as("Red uses MEAN[0]").isCloseTo(expected(255, 0), within(1e-5f));
                assertThat(t[0][1][0][0]).as("Green uses MEAN[1]").isCloseTo(expected(0, 1), within(1e-5f));
                assertThat(t[0][2][0][0]).as("Blue uses MEAN[2]").isCloseTo(expected(128, 2), within(1e-5f));
            }
        }

        @Nested
        @DisplayName("config-independent behaviour")
        class ConfigIndependent {
            private final ImagePreprocessor subject =
                    new ImagePreprocessor(ImagePreprocessorConfig.builder().build());

            @Test
            @DisplayName("2×2 image — pixel positions map correctly to [H][W]")
            void spatialLayout_2x2() {
                int[] pixels = {rgb(10, 20, 30), rgb(40, 50, 60), rgb(70, 80, 90), rgb(100, 110, 120)};

                float[][][][] t = subject.toNchwTensor(pixels, 2, 2);

                assertThat(t[0][0][0][0]).as("R at (0,0)").isCloseTo(norm(10), within(1e-5f));
                assertThat(t[0][0][0][1]).as("R at (1,0)").isCloseTo(norm(40), within(1e-5f));
                assertThat(t[0][0][1][0]).as("R at (0,1)").isCloseTo(norm(70), within(1e-5f));
                assertThat(t[0][0][1][1]).as("R at (1,1)").isCloseTo(norm(100), within(1e-5f));
            }

            @Test
            @DisplayName("Non-square image (3w × 2h) — all positions correct")
            void spatialLayout_nonSquare() {
                int w = 3, h = 2;
                int[] pixels = new int[w * h];
                for (int i = 0; i < pixels.length; i++) pixels[i] = rgb(i * 40, 0, 0);

                float[][][][] t = subject.toNchwTensor(pixels, w, h);

                for (int row = 0; row < h; row++)
                    for (int col = 0; col < w; col++) {
                        assertThat(t[0][0][row][col]).isCloseTo(norm((row * w + col) * 40), within(1e-5f));
                    }
            }

            @Test
            @DisplayName("Different alpha values do not affect RGB output")
            void alphaDoesNotAffectRgb() {
                float[][][][] t1 = subject.toNchwTensor(new int[] {argb(0xFF, 100, 150, 200)}, 1, 1);
                float[][][][] t2 = subject.toNchwTensor(new int[] {argb(0x00, 100, 150, 200)}, 1, 1);

                assertThat(t1).isEqualTo(t2);
            }

            @Test
            @DisplayName("Total number of float elements = 1 × 3 × H × W")
            void totalElementCount() {
                int w = 7, h = 5;
                float[][][][] t = subject.toNchwTensor(new int[w * h], w, h);

                int count = 0;
                for (float[][][] n : t) for (float[][] c : n) for (float[] row : c) count += row.length;

                assertThat(count).isEqualTo(3 * h * w);
            }
        }
    }

    /**
     * Packs ARGB int from individual channels.
     *
     * @param a Alpha (0-255)
     * @param r Red (0-255)
     * @param g Green (0-255)
     * @param b Blue (0-255)
     * @return Packed ARGB integer
     */
    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int rgb(int r, int g, int b) {
        return argb(0xFF, r, g, b);
    }

    private static float norm(int channel) {
        return channel / 255.0f * 2.0f - 1.0f;
    }

    private static int pixel(int x, int y) {
        return rgb(x & 0xFF, y & 0xFF, 0);
    }

    private static int[] grid(int w, int h) {
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) pixels[y * w + x] = pixel(x, y);
        return pixels;
    }

    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int BLUE_SHIFT = 0;

    private static int pack(int value, int channelShift) {
        return value << channelShift;
    }

    private int uniform(int value, int channelShift, double xFraction, double yFraction) {
        int packed = pack(value, channelShift);
        return interpolateChannel(packed, packed, packed, packed, xFraction, yFraction, channelShift);
    }
}
