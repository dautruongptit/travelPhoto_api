package com.travelalbum.mapper;

import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.entity.Photo;
import com.travelalbum.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Viết bằng tay (thay vì MapStruct annotation-processing) để tránh phụ thuộc
 * bước build-time khi review code trực tiếp — hành vi tương đương bản
 * MapStruct mô tả ở SEC-01.
 */
@Component
@RequiredArgsConstructor
public class PhotoMapper {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PhotoUrlBuilder photoUrlBuilder;

    public PhotoResponse toResponse(Photo photo) {
        String uploadedByName = null;
        if (photo.getUploadedBy() != null) {
            uploadedByName = userRepository.findById(photo.getUploadedBy())
                .map(userMapper::toDisplayName)
                .orElse(null);
        }
        return PhotoResponse.builder()
            .id(photo.getId())
            .originalName(photo.getOriginalName())
            .url(photoUrlBuilder.buildUrl(photo.getId()))
            .thumbnailUrl(photoUrlBuilder.buildThumbnailUrl(photo.getId()))
            .previewUrl(photoUrlBuilder.buildPreviewUrl(photo.getId()))
            .raw(com.travelalbum.storage.RawImageSupport.isRawFileName(photo.getOriginalName()))
            .size(photo.getSize())
            .width(photo.getWidth())
            .height(photo.getHeight())
            .uploadedBy(uploadedByName)
            .uploadedTime(photo.getUploadedTime())
            .build();
    }

    public List<PhotoResponse> toResponseList(List<Photo> photos) {
        return photos.stream().map(this::toResponse).toList();
    }
}
