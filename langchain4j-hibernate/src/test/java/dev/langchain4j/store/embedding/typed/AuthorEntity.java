package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.store.embedding.hibernate.Metadata;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table(name = "authors")
public class AuthorEntity {
    @Id
    @Metadata
    @GeneratedValue
    private Long id;

    private String firstname;
    private String lastname;

    @OneToMany(mappedBy = "author")
    private List<BookEntity> books;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(final String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(final String lastname) {
        this.lastname = lastname;
    }

    public List<BookEntity> getBooks() {
        return books;
    }

    public void setBooks(final List<BookEntity> books) {
        this.books = books;
    }
}
