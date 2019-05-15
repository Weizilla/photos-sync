package com.weizilla.photosync;

import com.google.photos.types.proto.MediaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.Callable;

public class MediaItemDownloader implements Callable<ResultStatus> {
    private static final Logger logger = LoggerFactory.getLogger(MediaItemDownloader.class);
    private final MediaItem item;
    private final Instant start;
    private final String outputDir;
    private final ProcessedTracker tracker;

    public MediaItemDownloader(ProcessedTracker tracker, Instant start, MediaItem item, String outputDir) {
        this.tracker = tracker;
        this.start = start;
        this.item = item;
        this.outputDir = outputDir;
    }

    public ResultStatus call() {
        Path localPath = Paths.get(outputDir, item.getFilename());

        if (tracker.isProcessed(item.getId())) {
            if (Files.exists(localPath)) {
                logger.info("Not processing, already handled and exists {}", item.getFilename());
                return ResultStatus.SKIP;
            } else {
                tracker.unmarkProcessed(item.getId());
            }
        }

        if (item.getMediaMetadata().hasVideo()) {
            logger.info("Not processing. video {}", item.getFilename());
            return ResultStatus.SKIP;
        }

        if (Instant.now().isAfter(start.plus(1, ChronoUnit.HOURS))) {
            logger.info("Not processing. after cutoff time for {}", item.getFilename());
            return ResultStatus.EXPIRED;
        }

        try {
            Thread.sleep(new Random().nextInt(10000));
            URL url = new URL(item.getBaseUrl() + "=d");
            try (InputStream in = url.openStream()) {
                Files.copy(in, localPath, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("Saved {}", item.getFilename());
        } catch (InterruptedException e) {
            return ResultStatus.SKIP;
        } catch (IOException e) {
            logger.error("Error downloading {}: {}", item.getFilename(), e.getMessage());
            return ResultStatus.FAIL;
        }

        try {
            tracker.markProcessed(item.getId());
            tracker.writeProcessed();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return ResultStatus.SUCCESS;
    }
}
