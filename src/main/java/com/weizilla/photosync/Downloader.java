package com.weizilla.photosync;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Downloader {
    private static final Logger logger = LoggerFactory.getLogger(Downloader.class);
    private final ExecutorService executor;
    private final PhotosAlbumClient photosAlbumClient;
    private final String outputDir;
    private final ProcessedTracker processedTracker;

    public Downloader(String albumName, String credentialsDir, String outputDir) {
        this.outputDir = outputDir;
        executor = Executors.newFixedThreadPool(10);
        photosAlbumClient = new PhotosAlbumClient(albumName, credentialsDir);
        processedTracker = new ProcessedTracker(outputDir);
    }

    public void start() throws Exception {
        var numRead = processedTracker.readProcessed();
        logger.info("Loaded {} processed files", numRead);

        var start = Instant.now();
        var items = photosAlbumClient.getItems();

        var futures = items.stream()
            .map(i -> executor.submit(new MediaItemDownloader(processedTracker, start, i, outputDir)))
            .collect(Collectors.toList());

        var statuses = futures.stream()
            .map(Downloader::getOrThrow)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        printStats(statuses, items.size());

        logger.info("Shutting down");
        executor.shutdownNow();

        int numWrote = processedTracker.writeProcessed();
        logger.info("Wrote {} processed", numWrote);
    }

    private static ResultStatus getOrThrow(Future<ResultStatus> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void printStats(Map<ResultStatus, Long> statuses, int numAlbum) {
        long numProcessed = statuses.values().stream().mapToLong(Long::longValue).sum();

        StringBuilder builder = new StringBuilder("Finished. ");
        statuses.forEach((status, count) -> builder.append(status).append("=").append(count).append(" "));
        builder.append("TOTAL_ALBUM=").append(numAlbum).append(" ");
        builder.append("TOTAL_PROCESSED=").append(numProcessed).append(" ");

        logger.info(builder.toString());
    }

    public static void main(String[] args) throws Exception {
        Args parsedArgs = new Args();
        JCommander jCommander = JCommander.newBuilder()
            .addObject(parsedArgs)
            .build();
        try {
            jCommander.parse(args);
        } catch (ParameterException e) {
            logger.error(e.getMessage());
            jCommander.usage();
            System.exit(1);
        }

        Downloader app = new Downloader(
            parsedArgs.getAlbumName(),
            parsedArgs.getCredentialsDir(),
            parsedArgs.getOutputDir());
        app.start();
    }

    public static class Args {
        @Parameter(names = {"--album-name"}, required = true, description = "The album to download")
        private String albumName;

        @Parameter(names = {"--credentials-dir"}, required = true, description = "The directory to store the credentials along with credentials.json")
        private String credentialsDir;

        @Parameter(names = {"--output-dir"}, required = true, description = "The directory to save the media to")
        private String outputDir;

        public String getAlbumName() {
            return albumName;
        }

        public String getCredentialsDir() {
            return credentialsDir;
        }

        public String getOutputDir() {
            return outputDir;
        }
    }
}
