package com.travelalbum.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExifReaderTest {

    @Test
    void combineCamera_doesNotRepeatMake() {
        assertThat(ExifReader.combineCamera("Canon", "Canon EOS R6")).isEqualTo("Canon EOS R6");
        assertThat(ExifReader.combineCamera("SONY", "ILCE-7M4")).isEqualTo("SONY ILCE-7M4");
        assertThat(ExifReader.combineCamera(null, "X100V")).isEqualTo("X100V");
        assertThat(ExifReader.combineCamera("FUJIFILM", null)).isEqualTo("FUJIFILM");
        assertThat(ExifReader.combineCamera(null, null)).isNull();
    }

    @Test
    void read_returnsEmptyForImageWithoutExif(@TempDir Path dir) throws Exception {
        Path jpg = dir.resolve("plain.jpg");
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "jpg", jpg.toFile());

        assertThat(ExifReader.read(jpg)).isEmpty();
    }

    @Test
    void read_neverThrowsOnGarbageOrMissingFile(@TempDir Path dir) throws Exception {
        Path garbage = dir.resolve("garbage.cr2");
        Files.write(garbage, new byte[]{1, 2, 3, 4, 5});

        assertThat(ExifReader.read(garbage)).isEmpty();
        assertThat(ExifReader.read(dir.resolve("missing.jpg"))).isEmpty();
    }
}
