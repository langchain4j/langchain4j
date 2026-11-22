package dev.langchain4j.store.embedding.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SecretMaskingTest {

    @Test
    void builder_toString_should_mask_password() {
        String toString =
                HibernateEmbeddingStore.dynamicBuilder().password("super-secret").toString();

        assertThat(toString).doesNotContain("super-secret").contains("password=********");
    }

    @Test
    void builder_toString_should_render_null_password_as_null() {
        String toString = HibernateEmbeddingStore.dynamicBuilder().toString();

        assertThat(toString).contains("password=null");
    }

    @Test
    void builder_toString_should_render_null_jdbc_url_as_null() {
        String toString = HibernateEmbeddingStore.dynamicBuilder().toString();

        assertThat(toString).contains("jdbcUrl=null");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "jdbc:postgresql://localhost:5432/db?user=app&password=super-secret&ssl=true"
                        + " | jdbc:postgresql://localhost:5432/db?user=app&password=***&ssl=true",
                "jdbc:mysql://app:super-secret@localhost:3306/db | jdbc:mysql://app:***@localhost:3306/db",
                "jdbc:mysql://app:super@secret@localhost:3306/db | jdbc:mysql://app:***@localhost:3306/db",
                "jdbc:oracle:thin:app/super@secret@//localhost:1521/db | jdbc:oracle:thin:app/***@//localhost:1521/db",
                "jdbc:sqlserver://localhost:1433;databaseName=db;user=app;password={super;secret};encrypt=true"
                        + " | jdbc:sqlserver://localhost:1433;databaseName=db;user=app;password=***;encrypt=true",
                "jdbc:sqlserver://localhost:1433;accessToken=super-secret | jdbc:sqlserver://localhost:1433;accessToken=***",
                "jdbc:db2://localhost:50000/db:user=app;password=super-secret; | jdbc:db2://localhost:50000/db:user=app;password=***;",
                "jdbc:oracle:thin:app/super-secret@//localhost:1521/db | jdbc:oracle:thin:app/***@//localhost:1521/db",
                "jdbc:oracle:thin:@//localhost:1521/db | jdbc:oracle:thin:@//localhost:1521/db",
                "jdbc:mysql://(host=localhost,port=3306,password=super-secret)/db | jdbc:mysql://(host=localhost,port=3306,password=***)/db",
                "jdbc:h2:mem:db;PASSWORD=super-secret | jdbc:h2:mem:db;PASSWORD=***",
                "jdbc:postgresql://localhost:5432/db?sslpassword=super-secret | jdbc:postgresql://localhost:5432/db?sslpassword=***",
            })
    void builder_toString_should_mask_secrets_in_jdbc_url(String jdbcUrl, String expectedMaskedJdbcUrl) {
        String toString =
                HibernateEmbeddingStore.dynamicBuilder().jdbcUrl(jdbcUrl).toString();

        assertThat(toString).doesNotContain("super").contains("jdbcUrl=" + expectedMaskedJdbcUrl + ",");
    }

    @Test
    void build_should_not_expose_secrets_of_jdbc_url_in_exception_message() {
        HibernateEmbeddingStore.DynamicBuilder builder = HibernateEmbeddingStore.dynamicBuilder()
                .jdbcUrl("jdbc:unknown://app:super-secret@localhost/db?password=other-secret");

        assertThatThrownBy(builder::build)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Can't determine DatabaseKind for JDBC URL: jdbc:unknown://app:***@localhost/db?password=***");
    }
}
