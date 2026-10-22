package com.travelalbum.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * Quy tắc tên FILE trên đĩa (xem docs/STORAGE_NAMING.md): giữ nguyên tên gốc (kể cả tiếng
 * Việt có dấu), chỉ loại ký tự không hợp lệ trên NTFS/Windows và ký tự điều khiển; khi trùng
 * tên trong cùng thư mục thì thêm hậu tố " (1)", " (2)"...
 */
public final class StorageFileNames {

    private static final int MAX_NAME_LENGTH = 200;
    private static final String FALLBACK = "file";
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private StorageFileNames() {
    }

    /** Chỉ giữ phần tên file (bỏ mọi đường dẫn), thay ký tự cấm bằng "_". */
    public static String sanitize(String original) {
        if (original == null || original.isBlank()) {
            return FALLBACK;
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        // Ký tự cấm trên NTFS + ký tự điều khiển
        name = name.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_");
        // NTFS không cho tên kết thúc bằng dấu chấm hoặc khoảng trắng; ".."/"." là tên đặc biệt
        name = name.replaceAll("[. ]+$", "").strip();
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            return FALLBACK;
        }
        String base = baseName(name);
        if (WINDOWS_RESERVED.contains(base.toUpperCase(Locale.ROOT))) {
            name = "_" + name;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            String ext = extension(name);
            int keep = Math.max(1, MAX_NAME_LENGTH - ext.length());
            name = name.substring(0, keep) + ext;
        }
        return name;
    }

    /** Trả về tên chưa tồn tại trong {@code dir}: name, "name (1).ext", "name (2).ext"... */
    public static String uniqueIn(Path dir, String name) {
        if (!Files.exists(dir.resolve(name))) {
            return name;
        }
        String ext = extension(name);
        String base = name.substring(0, name.length() - ext.length());
        for (int i = 1; i < 10_000; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!Files.exists(dir.resolve(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot find a free file name for " + name);
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot);
    }

    private static String baseName(String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
