package dev.langchain4j.model.workersai.client;

import java.io.InputStream;

/**
 * Response to generate an image.
 */
public class WorkersAiImageGenerationResponse
        extends ApiResponse<dev.langchain4j.model.workersai.client.WorkersAiImageGenerationResponse.ImageGenerationResult> {

    /**
     * Default constructor.
     */
    public WorkersAiImageGenerationResponse() {
    }

    /**
     * Body of the image generating process
     */
    public static class ImageGenerationResult {
        private InputStream image;

        /**
         * Default constructor.
         */
        @SuppressWarnings("unused")
        public ImageGenerationResult() {
        }

        public ImageGenerationResult(InputStream image) {
            this.image = image;
        }

        public InputStream getImage() {
            return this.image;
        }

        public void setImage(InputStream image) {
            this.image = image;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof ImageGenerationResult)) return false;
            final ImageGenerationResult other = (ImageGenerationResult) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$image = this.getImage();
            final Object other$image = other.getImage();
            if (this$image == null ? other$image != null : !this$image.equals(other$image)) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof ImageGenerationResult;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $image = this.getImage();
            result = result * PRIME + ($image == null ? 43 : $image.hashCode());
            return result;
        }

        public String toString() {
            return "WorkersAiImageGenerationResponse.ImageGenerationResult(image=" + this.getImage() + ")";
        }
    }

}
