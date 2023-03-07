package com.danielflower.apprunner.io;

import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.io.Writer;

public class OutputToWriterBridge implements InvocationOutputHandler, LineConsumer {
    public static final Logger log = LoggerFactory.getLogger(OutputToWriterBridge.class);
    private final Writer writer;

    public OutputToWriterBridge(Writer writer) {
        this.writer = writer;
    }

    public void consumeLine(String line) {
        try {
            writer.write(line + System.lineSeparator());
            writer.flush();
        } catch (InterruptedIOException iox) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.info("Error while writing", e);
        }
    }
}
