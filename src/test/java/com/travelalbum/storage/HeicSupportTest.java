package com.travelalbum.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HeicSupportTest {

    private static byte[] ftyp(String brand) {
        byte[] b = new byte[16];
        b[3] = 0x18;
        b[4] = 'f';
        b[5] = 't';
        b[6] = 'y';
        b[7] = 'p';
        for (int i = 0; i < 4; i++) {
            b[8 + i] = (byte) brand.charAt(i);
        }
        return b;
    }

    @Test
    void isHeicFileName_matchesHeicAndHeifCaseInsensitive() {
        assertThat(HeicSupport.isHeicFileName("IMG_8219.HEIC")).isTrue();
        assertThat(HeicSupport.isHeicFileName("a.heif")).isTrue();
        assertThat(HeicSupport.isHeicFileName("a.jpg")).isFalse();
        assertThat(HeicSupport.isHeicFileName(null)).isFalse();
    }

    @Test
    void needsPreview_coversRawAndHeicButNotJpeg() {
        assertThat(HeicSupport.needsPreview("a.CR2")).isTrue();
        assertThat(HeicSupport.needsPreview("a.HEIC")).isTrue();
        assertThat(HeicSupport.needsPreview("a.jpg")).isFalse();
        assertThat(HeicSupport.needsPreview("a.png")).isFalse();
    }

    @Test
    void hasHeicSignature_acceptsIsoBmffHeifBrandsOnly() {
        assertThat(HeicSupport.hasHeicSignature(ftyp("heic"))).isTrue();
        assertThat(HeicSupport.hasHeicSignature(ftyp("mif1"))).isTrue();
        assertThat(HeicSupport.hasHeicSignature(ftyp("heix"))).isTrue();
        assertThat(HeicSupport.hasHeicSignature(ftyp("isom"))).isFalse(); // mp4, không phải ảnh HEIF
        assertThat(HeicSupport.hasHeicSignature("not an image file".getBytes())).isFalse();
        assertThat(HeicSupport.hasHeicSignature(new byte[4])).isFalse();
    }

    @Test
    void writePreview_returnsEmptyForInvalidFile_neverThrows(@TempDir Path dir) throws Exception {
        Path bad = dir.resolve("bad.heic");
        Files.write(bad, ftyp("heic"));

        assertThat(HeicSupport.writePreview(bad, dir.resolve(".previews/bad.heic.jpg"))).isEmpty();
        assertThat(Files.exists(dir.resolve(".previews/bad.heic.jpg.tmp.jpg"))).isFalse(); // dọn file tạm
    }
}
