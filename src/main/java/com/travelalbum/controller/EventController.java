package com.travelalbum.controller;

import com.travelalbum.audit.Auditable;
import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.request.CreateEventRequest;
import com.travelalbum.dto.request.UpdateEventRequest;
import com.travelalbum.dto.response.BatchDeleteResponse;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.User;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.EventService;
import com.travelalbum.storage.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @GetMapping
    public ApiResponse<Page<EventResponse>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("OK", eventService.list(pageable, principal.getId(), principal.isAdmin()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.canView(#id, authentication)")
    public ApiResponse<EventResponse> getById(@PathVariable Long id) {
        return ApiResponse.success("OK", eventService.getById(id));
    }

    @Auditable(action = "CREATE_EVENT", targetType = "EVENT")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<EventResponse> create(@Valid @ModelAttribute CreateEventRequest req,
                                             @RequestParam(value = "coverImage", required = false) MultipartFile coverImage,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Event created", eventService.create(req, coverImage, principal.getId()));
    }

    @Auditable(action = "UPDATE_EVENT", targetType = "EVENT")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<EventResponse> update(@PathVariable Long id,
                                             @Valid @ModelAttribute UpdateEventRequest req,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Event updated", eventService.update(id, req, principal.getId()));
    }

    @Auditable(action = "DELETE_EVENT", targetType = "EVENT")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        eventService.delete(id, principal.getId(), principal.isAdmin());
        return ApiResponse.success("Event deleted", null);
    }

    // Quyền sở hữu của từng event được check bên trong EventService.deleteBatch (item nào
    // không phải của mình thì bỏ qua, không throw cho cả request) nên chỉ cần yêu cầu đăng nhập.
    @DeleteMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<BatchDeleteResponse> deleteBatch(@RequestParam List<Long> ids,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Batch delete processed",
                eventService.deleteBatch(ids, principal.getId(), principal.isAdmin()));
    }

    // Ảnh bìa cũng là dữ liệu riêng tư: phải đăng nhập + là owner/member (web tải qua fetch
    // có Authorization, xem web/src/components/AuthImg.tsx). Trước đây permitAll -> ai cũng
    // quét được ảnh bìa mọi event bằng cách đếm id.
    @GetMapping("/{id}/cover")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.canView(#id, authentication)")
    public void cover(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (event.getCoverImagePath() == null) {
            throw new NotFoundException("This event has no cover image");
        }
        User owner = userRepository.findById(event.getOwnerId())
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        Resource resource = storageService.load(owner.getStorageFolder(), event.getCoverImagePath());
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        try (InputStream in = resource.getInputStream(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}