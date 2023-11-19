package dev.langchain4j.image.openai.dalle;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class OpenAiImageDownloader {

    static String downloadFile(String fileUrl, String destination) {
        try {
            URL netUrl = new URL(fileUrl);
            String destinationFilePath = destination + "/" + extractFileName(fileUrl);
            try (
                    InputStream inputStream = netUrl.openStream();
                    ReadableByteChannel channel = Channels.newChannel(inputStream);
                    FileOutputStream outputStream = new FileOutputStream(destinationFilePath)
            ) {
                outputStream.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
            }
            return destinationFilePath;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractFileName(String url) {
        Pattern pattern = Pattern.compile(".*/([^/?]+)\\?.*");
        Matcher matcher = pattern.matcher(url);

        return matcher.find() ? matcher.group(1) : "image.png";
    }
}
