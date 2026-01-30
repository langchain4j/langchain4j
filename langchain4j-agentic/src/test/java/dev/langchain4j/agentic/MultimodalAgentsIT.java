package dev.langchain4j.agentic;

import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.imageGenerationModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static dev.langchain4j.agentic.Models.visionModel;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
@Disabled("Flaky tests, sometimes authentication on the Gemini test fails, especially on CI")
public class MultimodalAgentsIT {

    private static final ChatModel IMAGE_GENERATION_MODEL = imageGenerationModel(Models.MODEL_PROVIDER.GEMINI);

    private static final Path THREE_LLAMAS_IMAGE_PATH = Path.of("src", "test", "resources", "3llamas.png");
    private static final Path BLACK_HOLES_PAPER_PDF_PATH = Path.of("src", "test", "resources", "Black holes paper.pdf");

    public interface AnimalsIdentifier {

        @SystemMessage("You are an expert in identifying animals from images.")
        @UserMessage("""
            Recognize the type of animals present in the given image.
            Reply with the name of the animal only and nothing else, using the plural if they are more than one.
            """)
        @Agent("Recognize the type of animals present in an image.")
        String identify(@UserMessage @V("animalsImage") ImageContent animalsImage);
    }

    public interface AnimalsCounter {

        @SystemMessage("You are an expert in counting specific animals from images.")
        @UserMessage("""
            How many {{animalType}} are present in the given image?
            """)
        @Agent("Count the number of animals of a given type present in an image.")
        int count(@UserMessage @V("animalsImage") ImageContent animalsImage, @V("animalType") String animalType);
    }

    public interface AnimalsExpert {

        @Agent
        String analyzeAnimals(@V("animalsImage") ImageContent animalsImage);
    }

    public interface SupervisorAnimalsExpert {

        @Agent
        String analyzeAnimals(@V("request") String request, @V("animalsImage") ImageContent animalsImage);
    }

    @Test
    void sequence_test() {
        AnimalsIdentifier animalsIdentifier = AgenticServices.agentBuilder(AnimalsIdentifier.class)
                .chatModel(visionModel())
                .outputKey("animalType")
                .build();

        AnimalsCounter animalsCounter = AgenticServices.agentBuilder(AnimalsCounter.class)
                .chatModel(visionModel())
                .outputKey("animalCount")
                .build();

        AnimalsExpert animalsExpert = AgenticServices.sequenceBuilder(AnimalsExpert.class)
                .subAgents(animalsIdentifier, animalsCounter)
                .outputKey("animalsExpertAnswer")
                .output(scope -> scope.readState("animalCount") + " " + scope.readState("animalType"))
                .build();

        String response = animalsExpert.analyzeAnimals(ImageContent.from(THREE_LLAMAS_IMAGE_PATH, "image/png"));
        assertThat(response).contains("3").containsAnyOf("llamas", "alpacas", "Llamas", "Alpacas");
    }

    @Test
    void supervisor_test() {
        AnimalsIdentifier animalsIdentifier = AgenticServices.agentBuilder(AnimalsIdentifier.class)
                .chatModel(visionModel())
                .outputKey("animalType")
                .build();

        AnimalsCounter animalsCounter = AgenticServices.agentBuilder(AnimalsCounter.class)
                .chatModel(visionModel())
                .outputKey("animalCount")
                .build();

        SupervisorAnimalsExpert animalsExpert = AgenticServices.supervisorBuilder(SupervisorAnimalsExpert.class)
                .chatModel(plannerModel())
                .subAgents(animalsIdentifier, animalsCounter)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .build();

        String response = animalsExpert.analyzeAnimals("Which type of animals and how many of them are present in the given image?",
                ImageContent.from(THREE_LLAMAS_IMAGE_PATH, "image/png"));
        assertThat(response).contains("3").containsAnyOf("llamas", "alpacas", "Llamas", "Alpacas");
    }

    public interface SceneDescriptorGenerator {

        @UserMessage("Generate a detailed description of a scene containing {{sceneContent}}.")
        @Agent
        String describeScene(@V("sceneContent") String sceneContent);
    }

    public interface ImageGenerator {

        @UserMessage("A high-resolution, studio-lit product photograph of {{requiredImage}}")
        @Agent
        Image generateImageOf(@V("requiredImage") String requiredImage);
    }

    public interface ImageContentGenerator {

        @UserMessage("A high-resolution, studio-lit product photograph of {{requiredImage}}")
        @Agent
        ImageContent generateImageOf(@V("requiredImage") String requiredImage);
    }

    public interface ImageStyler {

        @UserMessage("Edit the provided image to better fit the {{style}} style.")
        @Agent
        ImageContent generateImageOf(@UserMessage @V("generatedImage") ImageContent image, @V("style") String style);
    }

    public interface ImageGeneratorWithStyle {

        @Agent
        ImageContent generateImageWithStyle(@V("sceneContent") String sceneContent, @V("style") String style);
    }

    @Test
    void image_generation_sequence_test() {
        SceneDescriptorGenerator sceneDescriptorGenerator = AgenticServices.agentBuilder(SceneDescriptorGenerator.class)
                .chatModel(baseModel())
                .outputKey("requiredImage")
                .build();

        ImageGenerator imageGenerator = AgenticServices.agentBuilder(ImageGenerator.class)
                .chatModel(IMAGE_GENERATION_MODEL)
                .outputKey("image")
                .build();

        UntypedAgent imageExpert = AgenticServices.sequenceBuilder()
                .subAgents(sceneDescriptorGenerator, imageGenerator)
                .outputKey("image")
                .build();

        Image image = (Image) imageExpert.invoke(Map.of("sceneContent", "pack of wolves"));

        assertThat(image).isNotNull();
        assertThat(image.base64Data()).isNotEmpty();
        assertThat(image.mimeType()).startsWith("image/");

//        writeToDisk(image, "/tmp/output");
    }

    @Test
    void image_manipulation_sequence_test() {
        check_image_manipulation_sequence(true);
    }

    @Test
    void image_content_manipulation_sequence_test() {
        check_image_manipulation_sequence(false);
    }

    void check_image_manipulation_sequence(boolean generateImage) {
        SceneDescriptorGenerator sceneDescriptorGenerator = AgenticServices.agentBuilder(SceneDescriptorGenerator.class)
                .chatModel(baseModel())
                .outputKey("requiredImage")
                .build();

        Object imageGenerator;
        if (generateImage) {
            imageGenerator = AgenticServices.agentBuilder(ImageGenerator.class)
                    .chatModel(IMAGE_GENERATION_MODEL)
                    .outputKey("generatedImage")
                    .build();
        } else {
            imageGenerator = AgenticServices.agentBuilder(ImageContentGenerator.class)
                    .chatModel(IMAGE_GENERATION_MODEL)
                    .outputKey("generatedImage")
                    .build();
        }

        ImageStyler imageStyler = AgenticServices.agentBuilder(ImageStyler.class)
                .chatModel(IMAGE_GENERATION_MODEL)
                .outputKey("editedImage")
                .build();

        ImageGeneratorWithStyle imageExpert = AgenticServices.sequenceBuilder(ImageGeneratorWithStyle.class)
                .subAgents(sceneDescriptorGenerator, imageGenerator, imageStyler)
                .outputKey("editedImage")
                .build();

        ImageContent image = imageExpert.generateImageWithStyle("pack of wolves", "cyberpunk");
        assertThat(image).isNotNull();
        assertThat(image.image().base64Data()).isNotEmpty();
        assertThat(image.image().mimeType()).startsWith("image/");

//        writeToDisk(image.image(), "/tmp/output");
    }

    private static void writeToDisk(Image image, String destination) {
        if (image != null && image.base64Data() != null) {
            String extension = switch (image.mimeType()) {
                case "image/png" -> ".png";
                case "image/jpeg" -> ".jpg";
                case "image/webp" -> ".webp";
                default -> "";
            };
            Path path = Path.of(destination + extension);
            byte[] imageBytes = Base64.getDecoder().decode(image.base64Data());
            try {
                Files.write(path, imageBytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public interface PdfSummarizer {

        @SystemMessage("You are an expert in summarizing PDF documents.")
        @UserMessage("Provide a summary of the given PDF document.")
        @Agent("Provide a summary of the given PDF document.")
        String identify(@UserMessage @V("paper") PdfFileContent paper);
    }

    public interface InfographicGenerator {

        @UserMessage("A visual infographic summarizing the information of the following text: {{textSummary}}")
        @Agent("Generate an infographic of the given text summary")
        Image generateImageOf(@V("textSummary") String textSummary);
    }

    @Test
    void pdf_ingestion_test() {
        PdfSummarizer pdfSummarizer = AgenticServices.agentBuilder(PdfSummarizer.class)
                .chatModel(baseModel())
                .outputKey("textSummary")
                .build();

        InfographicGenerator infographicGenerator = AgenticServices.agentBuilder(InfographicGenerator.class)
                .chatModel(IMAGE_GENERATION_MODEL)
                .outputKey("infographic")
                .build();

        UntypedAgent imageExpert = AgenticServices.sequenceBuilder()
                .subAgents(pdfSummarizer, infographicGenerator)
                .outputKey("infographic")
                .build();

        Image image = (Image) imageExpert.invoke(Map.of("paper", PdfFileContent.from(BLACK_HOLES_PAPER_PDF_PATH)));

        assertThat(image).isNotNull();
        assertThat(image.base64Data()).isNotEmpty();
        assertThat(image.mimeType()).startsWith("image/");

//        writeToDisk(image, "/tmp/output");
    }
}
