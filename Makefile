build:
	mvn --version
	mvn -U clean test

# Analyze code for errors, potential issues, and coding standard violations.
# Reports problems but does not modify the code.
lint:
	mvn -T12C spotless:check

# Automatically format the code to conform to a style guide.
# Modifies the code to ensure consistent formatting.
format:
	mvn -T12C spotless:apply
