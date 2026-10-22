package com.travelalbum.storage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Hỗ trợ ảnh RAW (xem docs/STORAGE_NAMING.md, mục RAW): nhận diện theo đuôi + chữ ký file,
 * và tạo bản xem trước JPEG bằng cách lấy ảnh JPEG nhúng sẵn trong file RAW (hầu hết định dạng
 * RAW đều nhúng 1 JPEG kích thước lớn). Không giải mã dữ liệu RAW thật.
 */
public final class RawImageSupport {

    public static final Set<String> RAW_EXTENSIONS = Set.of(
            ".cr2", ".cr3", ".crw", ".nef", ".nrw", ".arw", ".srf", ".sr2", ".dng",
            ".orf", ".rw2", ".raf", ".pef", ".srw", ".3fr", ".iiq", ".rwl");

    public static final int PREVIEW_MAX_DIMENSION = 2560;
    private static final float PREVIEW_QUALITY = 0.85f;
    public static final int HEADER_BYTES = 16;

    private RawImageSupport() {
    }

    public static boolean isRawFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && RAW_EXTENSIONS.contains(lower.substring(dot));
    }

    /**
     * Kiểm tra chữ ký (magic bytes) để file đổi đuôi thành .cr2 nhưng thực chất là thứ khác
     * không lọt qua: TIFF-based (CR2/NEF/ARW/DNG/ORF/RW2/PEF...), RAF (Fuji), CR3 (ISO BMFF).
     */
    public static boolean hasRawSignature(byte[] header) {
        if (header == null || header.length < 12) {
            return false;
        }
        // TIFF little-endian "II*\0" (CR2, NEF, ARW, DNG, PEF...), ORF "IIRO"/"IIRS", RW2 "IIU\0"
        boolean ii = header[0] == 'I' && header[1] == 'I';
        if (ii && ((header[2] == 0x2A && header[3] == 0x00)
                || (header[2] == 'R' && (header[3] == 'O' || header[3] == 'S'))
                || (header[2] == 'U' && header[3] == 0x00))) {
            return true;
        }
        // TIFF big-endian "MM\0*"
        if (header[0] == 'M' && header[1] == 'M' && header[2] == 0x00 && header[3] == 0x2A) {
            return true;
        }
        // Fujifilm RAF
        if (new String(header, 0, 8, StandardCharsets.ISO_8859_1).equals("FUJIFILM")) {
            return true;
        }
        // Canon CR3 (ISO BMFF: ....ftypcrx )
        return new String(header, 4, 4, StandardCharsets.ISO_8859_1).equals("ftyp")
                && new String(header, 8, 3, StandardCharsets.ISO_8859_1).equals("crx");
    }

    /**
     * Tìm JPEG nhúng lớn nhất trong file RAW và ghi ra {@code target} (JPEG, cạnh dài tối đa
     * {@value #PREVIEW_MAX_DIMENSION}px). Trả về kích thước ảnh preview, hoặc empty nếu không
     * tìm thấy JPEG nhúng nào.
     */
    public static Optional<int[]> writePreview(Path source, Path target) throws IOException {
        byte[] data = Files.readAllBytes(source);
        int bestOffset = -1;
        long bestArea = 0;
        for (int i = 0; i + 3 < data.length; i++) {
            if (data[i] == (byte) 0xFF && data[i + 1] == (byte) 0xD8 && data[i + 2] == (byte) 0xFF) {
                int[] dim = readDimensions(data, i);
                if (dim != null && (long) dim[0] * dim[1] > bestArea) {
                    bestArea = (long) dim[0] * dim[1];
                    bestOffset = i;
                }
            }
        }
        if (bestOffset < 0) {
            return Optional.empty();
        }

        BufferedImage image = decodeScaled(data, bestOffset);
        if (image == null) {
            return Optional.empty();
        }
        Files.createDirectories(target.getParent());
        writeJpeg(image, target);
        return Optional.of(new int[]{image.getWidth(), image.getHeight()});
    }

    private static int[] readDimensions(byte[] data, int offset) {
        try (ImageInputStream in = ImageIO.createImageInputStream(
                new ByteArrayInputStream(data, offset, data.length - offset))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception ex) {
            return null; // không phải JPEG hợp lệ (trùng byte ngẫu nhiên trong dữ liệu RAW)
        }
    }

    static BufferedImage decodeScaled(byte[] data, int offset) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(
                new ByteArrayInputStream(data, offset, data.length - offset))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int longest = Math.max(reader.getWidth(0), reader.getHeight(0));
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, longest / PREVIEW_MAX_DIMENSION);
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    static void writeJpeg(BufferedImage image, Path target) throws IOException {
        // JPEG không hỗ trợ kênh alpha
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB && image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(image, 0, 0, null);
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (OutputStream os = Files.newOutputStream(target);
             ImageOutputStream out = ImageIO.createImageOutputStream(os)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(PREVIEW_QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
