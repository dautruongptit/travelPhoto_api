package com.travelalbum.storage;

import com.travelalbum.storage.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    void createUserRootFolder(String parentFolder);

    void createEventFolder(String parentFolder, String eventFolder);

    StoredFile store(String parentFolder, String eventFolder, MultipartFile file);

    Resource load(String parentFolder, String relativePath);

    /**
     * Tạo bản xem trước JPEG cho file RAW đã lưu tại {@code relativePath} (lấy JPEG nhúng trong
     * RAW). Lưu ở thư mục ".previews" cạnh file gốc. Trả về null nếu RAW không có JPEG nhúng.
     */
    RawPreview createRawPreview(String parentFolder, String relativePath);

    /** Doc EXIF that cua file da luu. Tra ve null neu khong co EXIF hoac khong doc duoc. */
    ExifInfo readExif(String parentFolder, String relativePath);

    void delete(String parentFolder, String relativePath);

    void deleteEventFolder(String parentFolder, String eventFolder);

    void deleteUserRootFolder(String parentFolder);

    long calculateUsedSpace(String parentFolder);

    long getDiskTotalSpace();

    long getDiskUsableSpace();
}