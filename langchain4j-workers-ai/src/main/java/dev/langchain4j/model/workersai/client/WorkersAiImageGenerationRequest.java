package dev.langchain4j.model.workersai.client;

/**
 * Request to generate an image.
 */
public class WorkersAiImageGenerationRequest {

    /**
     * Prompt to generate the image.
     */
    String prompt;

    /**
     * Source image to edit
     */
    int[] image;

    /**
     * Mask image to edit (optional)
     */
    int[] mask;

    /**
     * Mask operation to apply.
     */
    Integer num_steps;

    /**
     * Strength
     */
    Integer strength;

    /**
     * File to save the image.
     */
    String destinationFile;

    /**
     * Default constructor.
     */
    public WorkersAiImageGenerationRequest() {
    }

    public WorkersAiImageGenerationRequest(String prompt, int[] image, int[] mask, Integer num_steps, Integer strength, String destinationFile) {
        this.prompt = prompt;
        this.image = image;
        this.mask = mask;
        this.num_steps = num_steps;
        this.strength = strength;
        this.destinationFile = destinationFile;
    }

    public String getPrompt() {
        return this.prompt;
    }

    public int[] getImage() {
        return this.image;
    }

    public int[] getMask() {
        return this.mask;
    }

    public Integer getNum_steps() {
        return this.num_steps;
    }

    public Integer getStrength() {
        return this.strength;
    }

    public String getDestinationFile() {
        return this.destinationFile;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setImage(int[] image) {
        this.image = image;
    }

    public void setMask(int[] mask) {
        this.mask = mask;
    }

    public void setNum_steps(Integer num_steps) {
        this.num_steps = num_steps;
    }

    public void setStrength(Integer strength) {
        this.strength = strength;
    }

    public void setDestinationFile(String destinationFile) {
        this.destinationFile = destinationFile;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof WorkersAiImageGenerationRequest)) return false;
        final WorkersAiImageGenerationRequest other = (WorkersAiImageGenerationRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$prompt = this.getPrompt();
        final Object other$prompt = other.getPrompt();
        if (this$prompt == null ? other$prompt != null : !this$prompt.equals(other$prompt)) return false;
        if (!java.util.Arrays.equals(this.getImage(), other.getImage())) return false;
        if (!java.util.Arrays.equals(this.getMask(), other.getMask())) return false;
        final Object this$num_steps = this.getNum_steps();
        final Object other$num_steps = other.getNum_steps();
        if (this$num_steps == null ? other$num_steps != null : !this$num_steps.equals(other$num_steps)) return false;
        final Object this$strength = this.getStrength();
        final Object other$strength = other.getStrength();
        if (this$strength == null ? other$strength != null : !this$strength.equals(other$strength)) return false;
        final Object this$destinationFile = this.getDestinationFile();
        final Object other$destinationFile = other.getDestinationFile();
        if (this$destinationFile == null ? other$destinationFile != null : !this$destinationFile.equals(other$destinationFile))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof WorkersAiImageGenerationRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $prompt = this.getPrompt();
        result = result * PRIME + ($prompt == null ? 43 : $prompt.hashCode());
        result = result * PRIME + java.util.Arrays.hashCode(this.getImage());
        result = result * PRIME + java.util.Arrays.hashCode(this.getMask());
        final Object $num_steps = this.getNum_steps();
        result = result * PRIME + ($num_steps == null ? 43 : $num_steps.hashCode());
        final Object $strength = this.getStrength();
        result = result * PRIME + ($strength == null ? 43 : $strength.hashCode());
        final Object $destinationFile = this.getDestinationFile();
        result = result * PRIME + ($destinationFile == null ? 43 : $destinationFile.hashCode());
        return result;
    }

    public String toString() {
        return "WorkersAiImageGenerationRequest(prompt=" + this.getPrompt() + ", image=" + java.util.Arrays.toString(this.getImage()) + ", mask=" + java.util.Arrays.toString(this.getMask()) + ", num_steps=" + this.getNum_steps() + ", strength=" + this.getStrength() + ", destinationFile=" + this.getDestinationFile() + ")";
    }
}
