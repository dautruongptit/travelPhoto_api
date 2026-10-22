package com.travelalbum.service;

import com.travelalbum.dto.request.CreateEventRequest;
import com.travelalbum.dto.request.UpdateEventRequest;
import com.travelalbum.dto.response.BatchDeleteResponse;
import com.travelalbum.dto.response.EventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface EventService {
    EventResponse create(CreateEventRequest req, MultipartFile coverImage, Long ownerId);
    EventResponse update(Long id, UpdateEventRequest req, Long ownerId);
    void delete(Long id, Long requesterId, boolean isAdmin);
    BatchDeleteResponse deleteBatch(List<Long> ids, Long requesterId, boolean isAdmin);
    EventResponse getById(Long id);
    /** Chủ album bật/tắt "Công khai" (mọi người đã đăng nhập xem được, chỉ đọc). */
    EventResponse setPublic(Long id, boolean publicAlbum);
    Page<EventResponse> list(Pageable pageable, Long requesterId, boolean isAdmin);
    Page<EventResponse> search(String keyword, Pageable pageable, Long requesterId, boolean isAdmin);
}