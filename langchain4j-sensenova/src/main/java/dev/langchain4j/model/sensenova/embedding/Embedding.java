package dev.langchain4j.model.sensenova.embedding;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.List;

@Data
@Builder
public final class Embedding {

	private List<Float> embedding;

	private String object;

	private Integer index;

	@SerializedName("status_code")
	private String statusCode;

	@SerializedName("status_message")
	private String statusMessage;

}
