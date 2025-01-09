# 
build:
	mvn --version
	mvn -U -T12C clean test

# Analyze code for errors, potential issues, and coding standard violations.
# Reports problems but does not modify the code.
lint:
	mvn -T12C spotless:check
	mvn -T12C detekt:check -am -pl langchain4j-parent,langchain4j-core

# Automatically format the code to conform to a style guide.
# Modifies the code to ensure consistent formatting.
format:
	mvn -T12C spotless:apply
