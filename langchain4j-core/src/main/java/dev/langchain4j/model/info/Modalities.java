package dev.langchain4j.model.info;

import java.util.List;
import java.util.Objects;

/**
 * Represents the input/output modalities supported by a model.
 */
public class Modalities {
    private List<String> input;
    private List<String> output;

    public Modalities() {}

    public Modalities(List<String> input, List<String> output) {
        this.input = input;
        this.output = output;
    }

    public List<String> getInput() {
        return input;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }

    public List<String> getOutput() {
        return output;
    }

    public void setOutput(List<String> output) {
        this.output = output;
    }

    public boolean supportsInputModality(String modality) {
        return input != null && input.contains(modality);
    }

    public boolean supportsOutputModality(String modality) {
        return output != null && output.contains(modality);
    }

    public boolean supportsText() {
        return supportsInputModality("text") || supportsOutputModality("text");
    }

    public boolean supportsImage() {
        return supportsInputModality("image") || supportsOutputModality("image");
    }

    public boolean supportsVideo() {
        return supportsInputModality("video") || supportsOutputModality("video");
    }

    public boolean supportsAudio() {
        return supportsInputModality("audio") || supportsOutputModality("audio");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Modalities that = (Modalities) o;
        return Objects.equals(input, that.input) && Objects.equals(output, that.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(input, output);
    }

    @Override
    public String toString() {
        return "Modalities{" + "input=" + input + ", output=" + output + '}';
    }
}
