package com.travelalbum.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Chạy {@link ExifBackfillService} ở luồng nền ngay sau khi backend khởi động (không chặn startup). */
@Component
@RequiredArgsConstructor
public class ExifBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExifBackfillRunner.class);

    private final ExifBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        Thread t = new Thread(() -> {
            int total = 0;
            try {
                int processed;
                do {
                    processed = backfillService.backfillBatch();
                    total += processed;
                } while (processed > 0);
                if (total > 0) {
                    log.info("EXIF backfill done: {} photo(s) processed", total);
                }
            } catch (RuntimeException ex) {
                log.warn("EXIF backfill stopped: {}", ex.toString());
            }
        }, "exif-backfill");
        t.setDaemon(true);
        t.start();
    }
}
