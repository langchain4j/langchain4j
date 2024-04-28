package dev.langchain4j.model.sensenova.chat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CharacterFeelingToward {

	private String name;

	/**
	 * The favor ability of the character. The larger the number, the higher the favor ability.
	 * [1, 3]
	 */
	private int level;
}
