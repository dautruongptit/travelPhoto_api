package com.travelalbum.storage;

/** Kết quả tạo bản xem trước JPEG cho file RAW: path tương đối trong storage + kích thước. */
public record RawPreview(String relativePath, int width, int height) {
}
