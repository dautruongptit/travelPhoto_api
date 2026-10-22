package com.travelalbum.storage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Hỗ trợ ảnh HEIC/HEIF (định dạng ảnh mặc định của iPhone): nhận diện theo đuôi + chữ ký file,
 * và tạo bản xem trước JPEG bằng công cụ {@code heif-convert} (gói libheif-tools, cài trong
 * Docker image) vì Java không giải mã được HEIC và trình duyệt (trừ Safari) cũng không hiển thị
 * được. File HEIC gốc luôn được giữ nguyên để tải về. Thiếu công cụ -> không có preview, upload
 * vẫn thành công (web hiện nhãn thay vì ảnh).
 */
public final class HeicSupport {

    public static final Set<String> HEIC_EXTENSIONS = Set.of(".heic", ".heif");

    private static final Set<String> HEIF_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1");

    /** Lệnh chuyển đổi, đổi được qua biến môi trường HEIF_CONVERT_CMD. */
    private static final String CONVERT_CMD =
            System.getenv().getOrDefault("HEIF_CONVERT_CMD", "heif-convert");
    private static final long TIMEOUT_SECONDS = 90;

    private HeicSupport() {
    }

    public static boolean isHeicFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && HEIC_EXTENSIONS.contains(lower.substring(dot));
    }

    /** Ảnh mà trình duyệt không hiển thị trực tiếp được: RAW hoặc HEIC/HEIF -> cần bản xem trước JPEG. */
    public static boolean needsPreview(String fileName) {
        return RawImageSupport.isRawFileName(fileName) || isHeicFileName(fileName);
    }

    /** Chữ ký ISO BMFF: 4 byte kích thước, "ftyp", rồi mã brand (heic, heix, mif1...). */
    public static boolean hasHeicSignature(byte[] header) {
        if (header == null || header.length < 12) {
            return false;
        }
        if (!new String(header, 4, 4, StandardCharsets.ISO_8859_1).equals("ftyp")) {
            return false;
        }
        return HEIF_BRANDS.contains(new String(header, 8, 4, StandardCharsets.ISO_8859_1));
    }

    /**
     * Chuyển HEIC -> JPEG (cạnh dài tối đa {@value RawImageSupport#PREVIEW_MAX_DIMENSION}px),
     * ghi vào {@code target}. libheif tự áp dụng phép xoay của ảnh nên kích thước trả về là kích thước
     * hiển thị thật. Trả về empty nếu không chuyển được (thiếu heif-convert, file lỗi, quá thời gian).
     */
    public static Optional<int[]> writePreview(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp.jpg");
        try {
            // Tham số cố định, không qua shell -> không có chèn lệnh từ tên file
            Process process = new ProcessBuilder(CONVERT_CMD, "-q", "88", source.toString(), tmp.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0 || !Files.exists(tmp)) {
                return Optional.empty();
            }
            byte[] jpeg = Files.readAllBytes(tmp);
            BufferedImage scaled = RawImageSupport.decodeScaled(jpeg, 0);
            if (scaled == null) {
                return Optional.empty();
            }
            RawImageSupport.writeJpeg(scaled, target);
            return Optional.of(new int[]{scaled.getWidth(), scaled.getHeight()});
        } catch (IOException ex) {
            return Optional.empty(); // thường là chưa cài heif-convert
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
