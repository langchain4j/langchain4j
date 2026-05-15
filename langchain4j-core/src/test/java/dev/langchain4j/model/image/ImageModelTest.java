package dev.langchain4j.model.image;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.net.URI;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ImageModelTest implements WithAssertions {

    public static class FixedImageModel implements ImageModel {
        private final Image image;

        public FixedImageModel(Image image) {
            this.image = image;
        }

        @Override
        public Response<Image> generate(String prompt) {
            return Response.from(image);
        }
    }

    /** Records edit-call arguments and returns {@code n} copies of the same image. */
    static class CanonicalEditRecordingModel implements ImageModel {

        List<Image> capturedImages;
        Image capturedMask;
        String capturedPrompt;
        int capturedN;

        @Override
        public Response<Image> generate(String prompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Response<List<Image>> edit(List<Image> images, Image mask, String prompt, int n) {
            this.capturedImages = images;
            this.capturedMask = mask;
            this.capturedPrompt = prompt;
            this.capturedN = n;
            List<Image> result = java.util.Collections.nCopies(n, PLACEHOLDER_IMAGE);
            return Response.from(result, new TokenUsage(1, 2, 3));
        }
    }

    public static final Image PLACEHOLDER_IMAGE;

    static {
        try {
            PLACEHOLDER_IMAGE = Image.builder().url(new URI("https://foo.bar")).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void not_supported() {
        ImageModel model = new FixedImageModel(PLACEHOLDER_IMAGE);

        assertThatThrownBy(() -> model.generate("prompt", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operation is not supported");

        assertThatThrownBy(() -> model.edit((Image) null, "prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operation is not supported");

        assertThatThrownBy(() -> model.edit((Image) null, null, "prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operation is not supported");
    }

    @Test
    void trivial() {
        ImageModel model = new FixedImageModel(PLACEHOLDER_IMAGE);
        Response<Image> response = model.generate("prompt");

        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo(PLACEHOLDER_IMAGE);
    }

    @Test
    void convenience_edit_overloads_delegate_to_canonical() {
        // Overriding only the canonical edit method should be enough — the five other
        // edit overloads on ImageModel are delegating defaults that route through it.
        CanonicalEditRecordingModel model = new CanonicalEditRecordingModel();

        // edit(Image, String) → canonical with a singleton list, mask=null, n=1; result unwrapped.
        Response<Image> r1 = model.edit(PLACEHOLDER_IMAGE, "p1");
        assertThat(r1.content()).isEqualTo(PLACEHOLDER_IMAGE);
        assertThat(r1.tokenUsage()).isNotNull();
        assertThat(model.capturedImages).containsExactly(PLACEHOLDER_IMAGE);
        assertThat(model.capturedMask).isNull();
        assertThat(model.capturedPrompt).isEqualTo("p1");
        assertThat(model.capturedN).isEqualTo(1);

        // edit(Image, Image, String) → canonical with mask, n=1; result unwrapped.
        Image mask = Image.builder().url(URI.create("https://mask")).build();
        Response<Image> r2 = model.edit(PLACEHOLDER_IMAGE, mask, "p2");
        assertThat(r2.content()).isEqualTo(PLACEHOLDER_IMAGE);
        assertThat(model.capturedMask).isSameAs(mask);
        assertThat(model.capturedN).isEqualTo(1);

        // edit(List<Image>, String) → canonical with mask=null, n=1; result unwrapped.
        Response<Image> r3 = model.edit(List.of(PLACEHOLDER_IMAGE), "p3");
        assertThat(r3.content()).isEqualTo(PLACEHOLDER_IMAGE);
        assertThat(model.capturedMask).isNull();
        assertThat(model.capturedN).isEqualTo(1);

        // edit(List<Image>, String, int) → canonical with mask=null, returns the list as-is.
        Response<List<Image>> r4 = model.edit(List.of(PLACEHOLDER_IMAGE), "p4", 3);
        assertThat(r4.content()).hasSize(3);
        assertThat(model.capturedMask).isNull();
        assertThat(model.capturedN).isEqualTo(3);

        // edit(List<Image>, Image, String) → canonical with n=1; result unwrapped.
        Response<Image> r5 = model.edit(List.of(PLACEHOLDER_IMAGE), mask, "p5");
        assertThat(r5.content()).isEqualTo(PLACEHOLDER_IMAGE);
        assertThat(model.capturedMask).isSameAs(mask);
        assertThat(model.capturedN).isEqualTo(1);
    }
}
