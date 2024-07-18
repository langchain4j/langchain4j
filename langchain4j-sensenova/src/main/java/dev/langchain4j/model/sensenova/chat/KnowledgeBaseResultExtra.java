package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;


@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeBaseResultExtra {

	@SerializedName("file_id")
	private String fileId;

	private Integer page;

}
