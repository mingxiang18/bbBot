package com.bb.bot.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AutoCloseable 包装的临时文件，配合 try-with-resources 自动清理。
 *
 * <pre>{@code
 * try (TempFile temp = TempFile.write(image, "png")) {
 *     bbMessageApi.send(buildMessage(temp.path()));
 * }
 * }</pre>
 *
 * 这条对所有 Splatoon handler 用 {@link FileUtils#buildTmpFile} 的位置都适用。
 *
 * @author ren
 */
@Slf4j
public final class TempFile implements AutoCloseable {

    private final Path path;

    private TempFile(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    public File toFile() {
        return path.toFile();
    }

    /** 把 {@link BufferedImage} 落到临时文件，调用方负责 close。 */
    public static TempFile write(BufferedImage image, String formatName) throws IOException {
        Path tmp = Files.createTempFile("bbbot-", "." + formatName);
        ImageIO.write(image, formatName, tmp.toFile());
        return new TempFile(tmp);
    }

    /** 直接接管已有的 {@link Path}（仍负责清理）。 */
    public static TempFile wrap(Path path) {
        return new TempFile(path);
    }

    @Override
    public void close() {
        try {
            boolean removed = Files.deleteIfExists(path);
            if (!removed) {
                log.debug("Temp file {} already removed", path);
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp file {}", path, e);
        }
    }
}
