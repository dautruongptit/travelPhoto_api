package com.travelalbum.storage;
import com.travelalbum.exception.StorageException;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalFileStorageServiceImpl implements StorageService {

    @Value("${storage.root-path}")
    private String rootPath;

    private Path root() {
        return Paths.get(rootPath).toAbsolutePath().normalize();
    }

    private Path parentDir(String parentFolder) {
        Path base = root();
        Path target = base.resolve(sanitizeSegment(parentFolder)).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        return target;
    }

    private Path eventDir(String parentFolder, String eventFolder) {
        Path base = root();
        Path target = parentDir(parentFolder).resolve(sanitizeSegment(eventFolder)).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        return target;
    }

    private String sanitizeSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new SecurityException("Invalid folder segment");
        }
        String cleaned = segment.replace("..", "").replaceAll("[\\\\/]", "_");
        if (cleaned.isBlank()) {
            throw new SecurityException("Invalid folder segment");
        }
        return cleaned;
    }

    @Override
    public void createUserRootFolder(String parentFolder) {
        try {
            Files.createDirectories(parentDir(parentFolder));
        } catch (IOException ex) {
            throw new StorageException("Failed to create user root folder", ex);
        }
    }

    @Override
    public void createEventFolder(String parentFolder, String eventFolder) {
        try {
            Files.createDirectories(eventDir(parentFolder, eventFolder));
        } catch (IOException ex) {
            throw new StorageException("Failed to create event folder", ex);
        }
    }

    @Override
    public StoredFile store(String parentFolder, String eventFolder, MultipartFile file) {
        Path dir = eventDir(parentFolder, eventFolder);
        try {
            Files.createDirectories(dir);
            // Giữ nguyên tên gốc (chỉ loại ký tự cấm NTFS); trùng tên trong thư mục -> "(1)", "(2)"...
            String storedName = StorageFileNames.uniqueIn(dir, StorageFileNames.sanitize(file.getOriginalFilename()));
            Path target = dir.resolve(storedName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String checksum;
            try (InputStream in = Files.newInputStream(target)) {
                checksum = DigestUtils.sha256Hex(in);
            }

            Integer width = null;
            Integer height = null;
            // RAW: ImageIO không đọc được, kích thước lấy từ bản xem trước (createRawPreview).
            // Ảnh thường: chỉ đọc header để lấy kích thước, không giải mã cả ảnh vào RAM
            // (ảnh tới 50MB có thể lên hàng trăm megapixel).
            if (!RawImageSupport.isRawFileName(storedName)) {
                int[] dim = readImageSize(target);
                if (dim != null) {
                    width = dim[0];
                    height = dim[1];
                }
            }

            String relativePath = root().relativize(target).toString();
            return new StoredFile(storedName, relativePath, checksum, width, height);
        } catch (IOException ex) {
            throw new StorageException("Failed to store file", ex);
        }
    }

    @Override
    public Resource load(String parentFolder, String relativePath) {
        Path base = parentDir(parentFolder);
        Path target = root().resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String parentFolder, String relativePath) {
        Path base = parentDir(parentFolder);
        Path target = root().resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete file", ex);
        }
    }

    @Override
    public void deleteEventFolder(String parentFolder, String eventFolder) {
        Path dir = eventDir(parentFolder, eventFolder);
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete event folder", ex);
        }
    }

    @Override
    public void deleteUserRootFolder(String parentFolder) {
        Path dir = parentDir(parentFolder);
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (IOException ex) {
            throw new StorageException("Failed to delete user folder", ex);
        }
    }

    @Override
    public long calculateUsedSpace(String parentFolder) {
        Path dir = parentDir(parentFolder);
        if (!Files.exists(dir)) {
            return 0L;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException ex) {
            throw new StorageException("Failed to calculate used space", ex);
        }
    }

    @Override
    public long getDiskTotalSpace() {
        try {
            Files.createDirectories(root());
            return Files.getFileStore(root()).getTotalSpace();
        } catch (IOException ex) {
            throw new StorageException("Failed to read disk total space", ex);
        }
    }

    @Override
    public long getDiskUsableSpace() {
        try {
            Files.createDirectories(root());
            return Files.getFileStore(root()).getUsableSpace();
        } catch (IOException ex) {
            throw new StorageException("Failed to read disk usable space", ex);
        }
    }

    @Override
    public RawPreview createRawPreview(String parentFolder, String relativePath) {
        Path base = parentDir(parentFolder);
        Path source = root().resolve(relativePath).normalize();
        if (!source.startsWith(base)) {
            throw new SecurityException("Invalid storage path");
        }
        Path target = source.getParent().resolve(".previews").resolve(source.getFileName() + ".jpg");
        try {
            return RawImageSupport.writePreview(source, target)
                    .map(dim -> new RawPreview(root().relativize(target).toString(), dim[0], dim[1]))
                    .orElse(null);
        } catch (IOException | RuntimeException ex) {
            // Preview là phần phụ: lỗi tạo preview không được làm hỏng việc upload file gốc.
            return null;
        }
    }

    /** Đọc width/height từ header ảnh mà không giải mã toàn bộ pixel. */
    private int[] readImageSize(Path file) {
        try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                return null;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }
}