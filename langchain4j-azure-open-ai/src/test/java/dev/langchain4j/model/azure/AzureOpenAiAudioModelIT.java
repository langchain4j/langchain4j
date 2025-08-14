package dev.langchain4j.model.azure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.output.Response;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

/**
 * Integration test for the Azure OpenAI audio model.
 */

public class AzureOpenAiAudioModelIT {

    private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiAudioModelIT.class);
    
    @Test
    void should_transcribe_audio() throws IOException {
        String endpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
        String apiKey = System.getenv("AZURE_OPENAI_KEY");
        String deploymentName = System.getenv("AZURE_OPENAI_AUDIO_DEPLOYMENT_NAME");
        
        // Skip test if environment variables aren't configured
        Assumptions.assumeTrue(endpoint != null && !endpoint.isBlank(), 
            "AZURE_OPENAI_ENDPOINT environment variable not set");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), 
            "AZURE_OPENAI_KEY environment variable not set");
        Assumptions.assumeTrue(deploymentName != null && !deploymentName.isBlank(), 
            "AZURE_OPENAI_AUDIO_DEPLOYMENT_NAME environment variable not set");
        
        logger.info("Running audio transcription test with endpoint: {}, deployment: {}", 
                endpoint, deploymentName);
        
        try {
            AzureOpenAiAudioModel model = AzureOpenAiAudioModel.builder()
                     .endpoint(endpoint)
                     .apiKey(apiKey)
                     .deploymentName(deploymentName)
                     .logRequestsAndResponses(true)
                     .build();
    
            File audioFile = new File("src/test/resources/hello.mp3");
            Assumptions.assumeTrue(audioFile.exists(), 
                "Test audio file not found: src/test/resources/hello.mp3");
                
            byte[] audioData = FileUtils.readFileToByteArray(audioFile);
    
            Audio audio = Audio.builder()
                     .audioData(audioData)
                     .mimeType("audio/mpeg")
                     .build();
            
            Response<String> response = model.transcribe(audio);
            String message = response.content();
            assertThat(message).isNotNull();
            assertThat(message).containsAnyOf("Hello");
            
            logger.info("Successfully transcribed audio: {}", message);
        } catch (Exception e) {
            logger.error("Error during audio transcription test", e);
            throw e;
        }
    }   
}
