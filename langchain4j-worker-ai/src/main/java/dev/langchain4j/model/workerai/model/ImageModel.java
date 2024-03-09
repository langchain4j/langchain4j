package dev.langchain4j.model.workerai.model;

import dev.langchain4j.model.output.Response;

import java.io.File;

/**
 * Model to work with images. It can generate or classify
 */
public interface ImageModel {

    /**
     * Generate an image and get its binary content.
     *
     * @param prompt
     *       image prompt
     * @return
     *      generated image content
     */
    Response<byte[]> generate(String prompt);

    /**
     * Generate an image and save it to the destination.
     *
     * @param prompt
     *       image prompt
     * @param destination
     *      image destination
     * @return
     *      generated image
     */
    Response<File> generate(String prompt, String destination);

}
