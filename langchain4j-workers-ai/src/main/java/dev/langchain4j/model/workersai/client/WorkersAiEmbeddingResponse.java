package dev.langchain4j.model.workersai.client;

import java.util.List;

/**
 * Response to compute embeddings
 */
public class WorkersAiEmbeddingResponse extends ApiResponse<WorkersAiEmbeddingResponse.EmbeddingResult> {

    /**
     * Default constructor.
     */
    public WorkersAiEmbeddingResponse() {
    }

    /**
     * Beam to hold results
     */
    public static class EmbeddingResult {

        /**
         * Shape of the result
         */
        private List<Integer> shape;

        /**
         * Embedding data
         */
        private List<List<Float>> data;

        /**
         * Default constructor.
         */
        public EmbeddingResult() {
        }

        public List<Integer> getShape() {
            return this.shape;
        }

        public List<List<Float>> getData() {
            return this.data;
        }

        public void setShape(List<Integer> shape) {
            this.shape = shape;
        }

        public void setData(List<List<Float>> data) {
            this.data = data;
        }

        public boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof EmbeddingResult)) return false;
            final EmbeddingResult other = (EmbeddingResult) o;
            if (!other.canEqual((Object) this)) return false;
            final Object this$shape = this.getShape();
            final Object other$shape = other.getShape();
            if (this$shape == null ? other$shape != null : !this$shape.equals(other$shape)) return false;
            final Object this$data = this.getData();
            final Object other$data = other.getData();
            if (this$data == null ? other$data != null : !this$data.equals(other$data)) return false;
            return true;
        }

        protected boolean canEqual(final Object other) {
            return other instanceof EmbeddingResult;
        }

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $shape = this.getShape();
            result = result * PRIME + ($shape == null ? 43 : $shape.hashCode());
            final Object $data = this.getData();
            result = result * PRIME + ($data == null ? 43 : $data.hashCode());
            return result;
        }

        public String toString() {
            return "WorkersAiEmbeddingResponse.EmbeddingResult(shape=" + this.getShape() + ", data=" + this.getData() + ")";
        }
    }

}
