package com.travelalbum.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RawImageSupportTest {

    private static byte[] jpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void isRawFileName_matchesKnownExtensionsCaseInsensitive() {
        assertThat(RawImageSupport.isRawFileName("IMG_0001.CR2")).isTrue();
        assertThat(RawImageSupport.isRawFileName("a.nef")).isTrue();
        assertThat(RawImageSupport.isRawFileName("a.DNG")).isTrue();
        assertThat(RawImageSupport.isRawFileName("a.jpg")).isFalse();
        assertThat(RawImageSupport.isRawFileName("cr2")).isFalse();
        assertThat(RawImageSupport.isRawFileName(null)).isFalse();
    }

    @Test
    void hasRawSignature_acceptsTiffAndRafAndCr3_rejectsOthers() {
        byte[] tiffLe = {'I', 'I', 0x2A, 0, 8, 0, 0, 0, 0, 0, 0, 0};
        byte[] tiffBe = {'M', 'M', 0, 0x2A, 0, 0, 0, 8, 0, 0, 0, 0};
        byte[] raf = "FUJIFILMCCD-RAW ".getBytes();
        byte[] cr3 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'c', 'r', 'x', ' '};
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] text = "hello world....".getBytes();

        assertThat(RawImageSupport.hasRawSignature(tiffLe)).isTrue();
        assertThat(RawImageSupport.hasRawSignature(tiffBe)).isTrue();
        assertThat(RawImageSupport.hasRawSignature(raf)).isTrue();
        assertThat(RawImageSupport.hasRawSignature(cr3)).isTrue();
        assertThat(RawImageSupport.hasRawSignature(jpg)).isFalse();
        assertThat(RawImageSupport.hasRawSignature(text)).isFalse();
        assertThat(RawImageSupport.hasRawSignature(new byte[4])).isFalse();
    }

    @Test
    void writePreview_extractsLargestEmbeddedJpeg(@TempDir Path dir) throws Exception {
        // Giả lập RAW: header TIFF + JPEG nhỏ (thumbnail) + dữ liệu rác + JPEG lớn (preview)
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(new byte[]{'I', 'I', 0x2A, 0, 8, 0, 0, 0});
        raw.write(jpeg(40, 30));
        raw.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        raw.write(jpeg(1200, 800));
        raw.write(new byte[]{9, 9, 9});
        Path source = dir.resolve("test.cr2");
        Files.write(source, raw.toByteArray());

        Path target = dir.resolve(".previews/test.cr2.jpg");
        Optional<int[]> dim = RawImageSupport.writePreview(source, target);

        assertThat(dim).isPresent();
        assertThat(dim.get()[0]).isEqualTo(1200);
        assertThat(dim.get()[1]).isEqualTo(800);
        BufferedImage out = ImageIO.read(target.toFile());
        assertThat(out.getWidth()).isEqualTo(1200);
    }

    @Test
    void writePreview_returnsEmptyWhenNoEmbeddedJpeg(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("x.nef");
        Files.write(source, new byte[]{'I', 'I', 0x2A, 0, 1, 2, 3, 4, 5, 6, 7, 8});

        assertThat(RawImageSupport.writePreview(source, dir.resolve("p.jpg"))).isEmpty();
    }
}
