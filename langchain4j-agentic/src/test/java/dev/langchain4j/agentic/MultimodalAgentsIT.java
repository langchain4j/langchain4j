package dev.langchain4j.agentic;

import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.imageGenerationModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static dev.langchain4j.agentic.Models.visionModel;
import static org.assertj.core.api.Assertions.assertThat;


@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class MultimodalAgentsIT {

    private static Image imageOf3LLamas = Image.builder()
            .url("https://as1.ftcdn.net/jpg/17/31/32/26/1000_F_1731322642_GQDXVbTdzxOA1zr6Tw9QXhbmubumfoP3.webp")
            .build();

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

        String response = animalsExpert.analyzeAnimals(ImageContent.from(imageOf3LLamas));
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

        String response = animalsExpert.analyzeAnimals("Which type of animals and how many of them are present in the given image?", ImageContent.from(imageOf3LLamas));
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
        var gemini = Models.MODEL_PROVIDER.GEMINI;

        SceneDescriptorGenerator sceneDescriptorGenerator = AgenticServices.agentBuilder(SceneDescriptorGenerator.class)
                .chatModel(baseModel(gemini))
                .outputKey("requiredImage")
                .build();

        ImageGenerator imageGenerator = AgenticServices.agentBuilder(ImageGenerator.class)
                .chatModel(imageGenerationModel(gemini))
                .outputKey("generatedImage")
                .build();

        ImageStyler imageStyler = AgenticServices.agentBuilder(ImageStyler.class)
                .chatModel(imageGenerationModel(gemini))
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

        writeToDisk(image.image(), "/tmp/output");
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
}
