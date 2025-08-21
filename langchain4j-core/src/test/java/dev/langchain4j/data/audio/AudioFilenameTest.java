package dev.langchain4j.data.audio;

/**
 * Simple test class to validate Audio.getFilename() functionality
 * This can be run manually to verify the implementation
 */
public class AudioFilenameTest {
    
    public static void main(String[] args) {
        System.out.println("Testing Audio.getFilename() functionality...\n");
        
        // Test 1: Basic filename extraction
        testBasicFilename();
        
        // Test 2: Complex path scenarios
        testComplexPaths();
        
        // Test 3: Edge cases
        testEdgeCases();
        
        // Test 4: Platform separators
        testPlatformSeparators();
        
        System.out.println("\nAll tests completed successfully!");
    }
    
    private static void testBasicFilename() {
        System.out.println("Test 1: Basic filename extraction");
        
        // Simple filename
        Audio audio1 = Audio.builder()
            .url("https://example.com/audio.mp3")
            .mimeType("audio/mpeg")
            .build();
        assertEqual("audio.mp3", audio1.getFilename(), "Simple filename");
        
        // Filename with path
        Audio audio2 = Audio.builder()
            .url("https://example.com/files/recording.wav")
            .mimeType("audio/wav")
            .build();
        assertEqual("recording.wav", audio2.getFilename(), "Filename with path");
        
        // Filename with query parameters
        Audio audio3 = Audio.builder()
            .url("https://example.com/audio.mp3?version=1&format=high")
            .mimeType("audio/mpeg")
            .build();
        assertEqual("audio.mp3", audio3.getFilename(), "Filename with query params");
        
        System.out.println("✓ Basic filename extraction tests passed\n");
    }
    
    private static void testComplexPaths() {
        System.out.println("Test 2: Complex path scenarios");
        
        // Deep nested path
        Audio audio1 = Audio.builder()
            .url("https://cdn.example.com/media/2023/12/audio/recording.mp3")
            .mimeType("audio/mpeg")
            .build();
        assertEqual("recording.mp3", audio1.getFilename(), "Deep nested path");
        
        // Special characters in filename
        Audio audio2 = Audio.builder()
            .url("https://example.com/voice-note_final(1).mp3")
            .mimeType("audio/mpeg")
            .build();
        assertEqual("voice-note_final(1).mp3", audio2.getFilename(), "Special characters");
        
        // Multiple dots in filename
        Audio audio3 = Audio.builder()
            .url("https://example.com/backup.2023.12.01.audio.wav")
            .mimeType("audio/wav")
            .build();
        assertEqual("backup.2023.12.01.audio.wav", audio3.getFilename(), "Multiple dots");
        
        System.out.println("✓ Complex path tests passed\n");
    }
    
    private static void testEdgeCases() {
        System.out.println("Test 3: Edge cases");
        
        // No filename (ends with slash)
        Audio audio1 = Audio.builder()
            .url("https://example.com/folder/")
            .mimeType("audio/mpeg")
            .build();
        assertEqual("", audio1.getFilename(), "No filename (ends with slash)");
        
        // Root path
        Audio audio2 = Audio.builder()
            .url("https://example.com/")
            .mimeType("audio/mpeg")
            .build();
        assertEqual("", audio2.getFilename(), "Root path");
        
        // Fragment in URL
        Audio audio3 = Audio.builder()
            .url("https://example.com/audio.mp3#section1")
            .mimeType("audio/mpeg")
            .build();
        assertEqual("audio.mp3", audio3.getFilename(), "URL with fragment");
        
        // Non-URL based Audio objects should return null
        Audio audio4 = Audio.builder()
            .binaryData(new byte[]{1, 2, 3})
            .mimeType("audio/mpeg")
            .build();
        assertEqual(null, audio4.getFilename(), "Binary data (should be null)");
        
        Audio audio5 = Audio.builder()
            .base64Data("dGVzdA==")
            .mimeType("audio/mpeg")
            .build();
        assertEqual(null, audio5.getFilename(), "Base64 data (should be null)");
        
        System.out.println("✓ Edge case tests passed\n");
    }
    
    private static void testPlatformSeparators() {
        System.out.println("Test 4: Platform separators");
        
        // Windows-style path (backslashes) - use file protocol
        Audio audio1 = Audio.builder()
            .url("file:///C:/Users/John/Documents/recording.wav")
            .mimeType("audio/wav")
            .build();
        assertEqual("recording.wav", audio1.getFilename(), "Windows path");
        
        // Unix-style path
        Audio audio2 = Audio.builder()
            .url("file:///home/user/music/song.mp3")
            .mimeType("audio/mpeg")
            .build();
        assertEqual("song.mp3", audio2.getFilename(), "Unix path");
        
        // Path with encoded spaces
        Audio audio3 = Audio.builder()
            .url("https://example.com/my%20audio%20files/recording.wav")
            .mimeType("audio/wav")
            .build();
        assertEqual("recording.wav", audio3.getFilename(), "Path with encoded spaces");
        
        System.out.println("✓ Platform separator tests passed\n");
    }
    
    private static void assertEqual(Object expected, Object actual, String testName) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            System.out.println("  ✓ " + testName + ": " + actual);
        } else {
            System.err.println("  ✗ " + testName + ": expected '" + expected + "' but got '" + actual + "'");
            throw new AssertionError("Test failed: " + testName);
        }
    }
}
