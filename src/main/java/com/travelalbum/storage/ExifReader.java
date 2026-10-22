package com.travelalbum.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

/**
 * Đọc EXIF thật từ file ảnh (JPEG, và RAW dạng TIFF/CR3/RAF) bằng metadata-extractor.
 * Không bao giờ ném lỗi ra ngoài: EXIF chỉ là thông tin phụ, lỗi đọc không được làm hỏng việc
 * upload. GPS cố ý KHÔNG đọc/lưu (vị trí chính xác là dữ liệu nhạy cảm, và ảnh có thể được
 * chia sẻ qua link).
 */
public final class ExifReader {

    private static final int MAX_TEXT = 120;

    private ExifReader() {
    }

    public static Optional<ExifInfo> read(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

            String make = ifd0 != null ? clean(ifd0.getString(ExifIFD0Directory.TAG_MAKE)) : null;
            String model = ifd0 != null ? clean(ifd0.getString(ExifIFD0Directory.TAG_MODEL)) : null;

            Integer orientation = ifd0 != null ? ifd0.getInteger(ExifIFD0Directory.TAG_ORIENTATION) : null;

            String lens = null;
            String focal = null;
            String focal35 = null;
            String aperture = null;
            String shutter = null;
            Integer iso = null;
            LocalDateTime takenAt = null;
            if (sub != null) {
                lens = clean(sub.getString(ExifSubIFDDirectory.TAG_LENS_MODEL));
                focal = clean(sub.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
                focal35 = clean(sub.getDescription(ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH));
                aperture = clean(sub.getDescription(ExifSubIFDDirectory.TAG_FNUMBER));
                shutter = clean(sub.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME));
                iso = sub.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
                Date taken = sub.getDateOriginal();
                if (taken != null) {
                    takenAt = LocalDateTime.ofInstant(taken.toInstant(), ZoneId.systemDefault());
                }
            }

            ExifInfo info = new ExifInfo(combineCamera(make, model), lens, focal, focal35, aperture, shutter, iso, takenAt, orientation);
            return info.isEmpty() ? Optional.empty() : Optional.of(info);
        } catch (IOException | ImageProcessingException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** "Canon" + "Canon EOS R6" -> "Canon EOS R6" (không lặp tên hãng). */
    static String combineCamera(String make, String model) {
        if (model == null) {
            return make;
        }
        if (make == null || model.toLowerCase().startsWith(make.toLowerCase())) {
            return model;
        }
        return make + " " + model;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        // Bỏ ký tự điều khiển/NUL (EXIF hay có padding) rồi cắt độ dài cho vừa cột DB.
        String v = value.replaceAll("\\p{Cntrl}", "").strip();
        if (v.isEmpty()) {
            return null;
        }
        return v.length() > MAX_TEXT ? v.substring(0, MAX_TEXT) : v;
    }
}
