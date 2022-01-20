package example.micronaut;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.function.aws.MicronautRequestHandler;
import java.util.UUID;

@Introspected
public class BookRequestHandler extends MicronautRequestHandler<Book, BookSaved> {

    @Override
    public BookSaved execute(Book input) {
        BookSaved bookSaved = new BookSaved();
        bookSaved.setName(input.getName());
        bookSaved.setIsbn(UUID.randomUUID().toString());
        return bookSaved;
    }
}
