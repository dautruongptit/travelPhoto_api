package com.travelalbum.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StorageFileNamesTest {

    @Test
    void sanitize_keepsOriginalNameIncludingVietnamese() {
        assertThat(StorageFileNames.sanitize("IMG_0042.jpg")).isEqualTo("IMG_0042.jpg");
        assertThat(StorageFileNames.sanitize("Ảnh đẹp Đà Lạt (1).JPG")).isEqualTo("Ảnh đẹp Đà Lạt (1).JPG");
    }

    @Test
    void sanitize_replacesNtfsForbiddenCharsAndStripsPath() {
        assertThat(StorageFileNames.sanitize("a<b>c:d\"e|f?g*h.jpg")).isEqualTo("a_b_c_d_e_f_g_h.jpg");
        assertThat(StorageFileNames.sanitize("../../etc/passwd.jpg")).isEqualTo("passwd.jpg");
        assertThat(StorageFileNames.sanitize("C:" + (char) 92 + "Users" + (char) 92 + "x" + (char) 92 + "photo.png")).isEqualTo("photo.png");
    }

    @Test
    void sanitize_handlesEdgeCases() {
        assertThat(StorageFileNames.sanitize(null)).isEqualTo("file");
        assertThat(StorageFileNames.sanitize("   ")).isEqualTo("file");
        assertThat(StorageFileNames.sanitize("..")).isEqualTo("file");
        assertThat(StorageFileNames.sanitize("photo.jpg. ")).isEqualTo("photo.jpg");
        assertThat(StorageFileNames.sanitize("CON.jpg")).isEqualTo("_CON.jpg");
    }

    @Test
    void sanitize_truncatesLongNamesKeepingExtension() {
        String longName = "a".repeat(300) + ".CR2";
        String result = StorageFileNames.sanitize(longName);
        assertThat(result.length()).isLessThanOrEqualTo(200);
        assertThat(result).endsWith(".CR2");
    }

    @Test
    void uniqueIn_addsSuffixWhenNameExists(@TempDir Path dir) throws Exception {
        assertThat(StorageFileNames.uniqueIn(dir, "a.jpg")).isEqualTo("a.jpg");
        Files.createFile(dir.resolve("a.jpg"));
        assertThat(StorageFileNames.uniqueIn(dir, "a.jpg")).isEqualTo("a (1).jpg");
        Files.createFile(dir.resolve("a (1).jpg"));
        assertThat(StorageFileNames.uniqueIn(dir, "a.jpg")).isEqualTo("a (2).jpg");
    }
}
