package com.bb.bot.common.util;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempFileTest {

    @Test
    void close_deletesFile() throws IOException {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Path captured;
        try (TempFile temp = TempFile.write(img, "png")) {
            captured = temp.path();
            assertTrue(Files.exists(captured), "file should exist while open");
        }
        assertFalse(Files.exists(captured), "file should be deleted after close");
    }

    @Test
    void close_isIdempotent() throws IOException {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        TempFile temp = TempFile.write(img, "png");
        temp.close();
        // second close should not throw
        temp.close();
    }

    @Test
    void close_runsEvenWhenBodyThrows() throws IOException {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Path[] holder = new Path[1];

        assertThrows(IllegalStateException.class, () -> {
            try (TempFile temp = TempFile.write(img, "png")) {
                holder[0] = temp.path();
                throw new IllegalStateException("simulated failure");
            }
        });

        assertFalse(Files.exists(holder[0]),
                "file should still be cleaned up when body throws");
    }

    @Test
    void wrap_givesAutoCloseControlOverExistingPath() throws IOException {
        Path existing = Files.createTempFile("bbbot-test-", ".bin");
        Files.writeString(existing, "x");

        try (TempFile temp = TempFile.wrap(existing)) {
            assertTrue(Files.exists(temp.path()));
        }
        assertFalse(Files.exists(existing));
    }
}
