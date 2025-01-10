package io.micronaut.gradle.crac;

import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class TeeStringWriter extends Writer {

    private final Logger logger;
    private final StringWriter delegate;

    public TeeStringWriter(Logger logger) {
        this.logger = logger;
        this.delegate = new StringWriter();
    }

    @Override
    @SuppressWarnings("java:S2629") // This is done by Gradle
    public void write(char[] cbuf, int off, int len) {
        delegate.write(cbuf, off, len);
        logger.lifecycle(new String(cbuf, off, len).trim());
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
