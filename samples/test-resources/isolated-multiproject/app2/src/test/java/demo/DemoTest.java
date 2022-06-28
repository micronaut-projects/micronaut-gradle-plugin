package demo;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class DemoTest {

    @Inject
    BookRepository bookRepository;

    @Test
    void testItWorks() {
        Book book = new Book();
        book.setTitle("Yet Another Book");
        Book saved = bookRepository.save(book);
        assertNotNull(saved.getId());
        List<Book> books = bookRepository.findAll();
        assertEquals(2, books.size());
    }

}
