package dev.langchain4j.model.ollama;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.OllamaImage.TOOL_MODEL;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class OllamaAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {

    private static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", OllamaImage.OLLAMA_IMAGE, TOOL_MODEL);

    static LangChain4jOllamaContainer ollama;

    static {
        String ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL");
        if (isNullOrEmpty(ollamaBaseUrl)) {
            ollama = new LangChain4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                    .withModel(TOOL_MODEL);
            ollama.start();
            ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
        }
    }

    public static String ollamaBaseUrl() {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            return ollama.getEndpoint();
        } else {
            return OLLAMA_BASE_URL;
        }
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl())
                .modelName(TOOL_MODEL)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }


    interface PersonExtractor9 {

        class Person {

            String name;
            Set<PersonExtractor9.Pet> pets;
        }

        class Pet {

            String name;
        }

        PersonExtractor9.Person extractPersonFrom(String text);
    }

    // 1) adding only schema to prompt -> test pass
    // 2) adding text 'Use schema to extract data' -> test pass

    @Test
    protected void should_extract_pojo_with_set_of_pojos() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor9 personExtractor = AiServices.create(PersonExtractor9.class, model);

            String additionalPrompt = """
                    %s
                    
                    """.formatted("Use schema to extract data");
//                    .formatted(OllamaJsonUtils.toJson(JsonSchemaElementHelper.toMap(JsonSchemaElementHelper.jsonSchemaElementFrom(PersonExtractor9.Person.class))));

            String text = additionalPrompt + "Klaus has 2 pets: Peanut and Muffin";
//            String text = "Klaus has 2 pets: Peanut and Muffin";

            // when
            PersonExtractor9.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.pets).hasSize(2);
            Iterator<PersonExtractor9.Pet> iterator = person.pets.iterator();
            assertThat(iterator.next().name).isEqualTo("Peanut");
            assertThat(iterator.next().name).isEqualTo("Muffin");

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addProperty("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .addStringProperty("name")
                                                            .required("name")
                                                            .build())
                                                    .build())
                                            .required("name", "pets")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor2 {

        class Person {

            String name;
            PersonExtractor2.Address shippingAddress;
            PersonExtractor2.Address billingAddress;
        }

        class Address {

            String city;
        }

        PersonExtractor2.Person extractPersonFrom(String text);
    }
    // without change llama3.1 always returns full name (Klaus Heissler, halucination, Heissler is never seen in input data)

    // 1) changed field from name to firstName -> test pass
    // 2) adding to prompt 'Use schema to extract data` without schema in prompt -> test pass
    // 2) adding only json schema in prompt -> test fails

    @Test
    protected void should_extract_pojo_with_nested_pojo() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor2 personExtractor = AiServices.create(PersonExtractor2.class, model);


            String jsonSchema = """
                    %s
                    
                    
                    """
                    .formatted("Use schema to extract data")
//                    .formatted(OllamaJsonUtils.toJson(JsonSchemaElementHelper.toMap(JsonSchemaElementHelper.jsonSchemaElementFrom(PersonExtractor2.Person.class))));
                    ;
            String text = jsonSchema + "Klaus wants a delivery to Langley Falls, but his company is in New York";

            // when
            PersonExtractor2.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.shippingAddress.city).isEqualTo("Langley Falls");
            assertThat(person.billingAddress.city).isEqualTo("New York");

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addProperty("shippingAddress", JsonObjectSchema.builder()
                                                    .addStringProperty("city")
                                                    .required("city")
                                                    .build())
                                            .addProperty("billingAddress", JsonObjectSchema.builder()
                                                    .addStringProperty("city")
                                                    .required("city")
                                                    .build())
                                            .required("name", "shippingAddress", "billingAddress")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }




    interface PersonExtractor8 {

        class Person {

            String name;
            List<PersonExtractor8.Pet> pets;
        }

        class Pet {

            String name;
        }

        PersonExtractor8.Person extractPersonFrom(String text);
    }
    // 1) adding additional text 'Use schema to extract data' -> test pass
    // 2) adding only json schema to prompt  -> test pass

    @Test
    protected void should_extract_pojo_with_list_of_pojos() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor8 personExtractor = AiServices.create(PersonExtractor8.class, model);

            String jsonSchema = """
                    %s
                    
                    
                    """
                    .formatted("Use schema to extract data")
//                    .formatted(OllamaJsonUtils.toJson(JsonSchemaElementHelper.toMap(JsonSchemaElementHelper.jsonSchemaElementFrom(PersonExtractor8.Person.class))));
                    ;
            String text = jsonSchema + "Klaus has 2 pets: Peanut and Muffin";
//            String text = "Klaus has 2 pets: Peanut and Muffin";

            // when
            PersonExtractor8.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.pets).hasSize(2);
            assertThat(person.pets.get(0).name).isEqualTo("Peanut");
            assertThat(person.pets.get(1).name).isEqualTo("Muffin");

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addProperty("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .addStringProperty("name")
                                                            .required("name")
                                                            .build())
                                                    .build())
                                            .required("name", "pets")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }




    interface PersonExtractor7 {

        class Person {

            String name;
            PersonExtractor7.Pet[] pets;
        }

        class Pet {

            String name;
        }

        PersonExtractor7.Person extractPersonFrom(String text);
    }

    // 1) adding json schema to prompt -> test pass
    // 2) adding 'use schema to extract data' without json schema -> test pass

    @Test
    protected void should_extract_pojo_with_array_of_pojos() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor7 personExtractor = AiServices.create(PersonExtractor7.class, model);


            String jsonSchema = """
                    %s
                    
                    
                    """
                    .formatted("Use schema to extract data")
//                    .formatted(OllamaJsonUtils.toJson(JsonSchemaElementHelper.toMap(JsonSchemaElementHelper.jsonSchemaElementFrom(PersonExtractor7.Person.class))));
            ;

//            String text =  "Klaus has 2 pets: Peanut and Muffin";
            String text =  jsonSchema + "Klaus has 2 pets: Peanut and Muffin";

            // when
            PersonExtractor7.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.pets).hasSize(2);
            assertThat(person.pets[0].name).isEqualTo("Peanut");
            assertThat(person.pets[1].name).isEqualTo("Muffin");

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addProperty("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .addStringProperty("name")
                                                            .required("name")
                                                            .build())
                                                    .build())
                                            .required("name", "pets")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }



}
