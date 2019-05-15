package com.weizilla.photosync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public class ProcessedTracker {
    private static final Logger logger = LoggerFactory.getLogger(ProcessedTracker.class);
    private final String outputDir;
    private final Set<String> processedIds;

    public ProcessedTracker(String outputDir) {
        this.outputDir = outputDir;
        processedIds = new HashSet<>();
    }

    public int readProcessed() throws IOException {
        synchronized (processedIds) {
            processedIds.clear();
            Path progressFile = Paths.get(outputDir, "progress.txt");
            if (Files.isRegularFile(progressFile)) {
                processedIds.addAll(Files.readAllLines(progressFile));
            }
            return processedIds.size();
        }
    }

    public int writeProcessed() throws IOException {
        synchronized (processedIds) {
            Path progressFile = Paths.get(outputDir, "progress.txt");
            OpenOption option = Files.exists(progressFile) ? TRUNCATE_EXISTING : CREATE_NEW;
            Files.write(progressFile, processedIds, option);
            return processedIds.size();
        }
    }

    public void markProcessed(String id) {
        synchronized (processedIds) {
            processedIds.add(id);
        }
    }

    public boolean isProcessed(String id) {
        synchronized (processedIds) {
            return processedIds.contains(id);
        }
    }

    public void unmarkProcessed(String id) {
        synchronized (processedIds) {
            processedIds.remove(id);
        }
    }
}
