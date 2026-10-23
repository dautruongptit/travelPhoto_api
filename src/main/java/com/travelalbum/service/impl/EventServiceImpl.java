package com.travelalbum.service.impl;

import com.travelalbum.dto.request.CreateEventRequest;
import com.travelalbum.dto.request.UpdateEventRequest;
import com.travelalbum.dto.response.BatchDeleteFailure;
import com.travelalbum.dto.response.BatchDeleteResponse;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.User;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.EventMapper;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.EventService;
import com.travelalbum.storage.FolderNameUtils;
import com.travelalbum.storage.StorageService;
import com.travelalbum.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private static final Set<String> ALLOWED_COVER_MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_COVER_SIZE = 10L * 1024 * 1024;
    private static final int MAX_BATCH_DELETE = 100;

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventMapper eventMapper;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public EventResponse create(CreateEventRequest req, MultipartFile coverImage, Long ownerId) {
        if (eventRepository.existsByNameIgnoreCase(req.getName())) {
            throw new BusinessException("Event name already exists", "EVENT_EXIST");
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (owner.getStorageFolder() == null) {
            String userFolder = FolderNameUtils.buildUserFolderName(owner.getEmail(), owner.getId());
            owner.setStorageFolder(userFolder);
            userRepository.save(owner);
            storageService.createUserRootFolder(userFolder);
        }

        LocalDate startDate = req.getStartDate() != null ? req.getStartDate() : LocalDate.now();

        Event event = Event.builder()
                .name(req.getName())
                .description(req.getDescription())
                .ownerId(ownerId)
                .storageFolder("PENDING_" + UUID.randomUUID())
                .startDate(startDate)
                .endDate(req.getEndDate())
                .location(req.getLocation())
                .photoCount(0)
                .totalSize(0L)
                .build();
        Event saved = eventRepository.save(event);

        String eventFolder = FolderNameUtils.buildEventFolderName(startDate, req.getName(), saved.getId());
        saved.setStorageFolder(eventFolder);
        storageService.createEventFolder(owner.getStorageFolder(), eventFolder);

        if (coverImage != null && !coverImage.isEmpty()) {
            validateCoverImage(coverImage);
            StoredFile stored = storageService.store(owner.getStorageFolder(), eventFolder, coverImage);
            saved.setCoverImagePath(stored.relativePath());
        }

        eventRepository.save(saved);

        auditLogService.log(ownerId, "CREATE_EVENT", "EVENT", saved.getId(), null, null, "SUCCESS");
        return eventMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public EventResponse update(Long id, UpdateEventRequest req, Long ownerId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        event.setName(req.getName());
        event.setDescription(req.getDescription());
        event.setStartDate(req.getStartDate());
        event.setEndDate(req.getEndDate());
        event.setLocation(req.getLocation());
        Event saved = eventRepository.save(event);
        auditLogService.log(ownerId, "UPDATE_EVENT", "EVENT", id, null, null, "SUCCESS");
        return eventMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long requesterId, boolean isAdmin) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!isAdmin && !event.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        User owner = userRepository.findById(event.getOwnerId())
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        storageService.deleteEventFolder(owner.getStorageFolder(), event.getStorageFolder());
        eventRepository.delete(event);
        auditLogService.log(requesterId, "DELETE_EVENT", "EVENT", id, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public BatchDeleteResponse deleteBatch(List<Long> ids, Long requesterId, boolean isAdmin) {
        if (ids.isEmpty()) {
            throw new BusinessException("No event id provided", "EMPTY_LIST");
        }
        if (ids.size() > MAX_BATCH_DELETE) {
            throw new BusinessException("Too many events in one request", "TOO_MANY_ITEMS");
        }

        List<Long> deletedIds = new ArrayList<>();
        List<BatchDeleteFailure> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                // Gọi lại delete() để tái dùng nguyên logic check quyền/dọn storage/audit log —
                // item nào lỗi thì bỏ qua, không làm hỏng cả batch.
                delete(id, requesterId, isAdmin);
                deletedIds.add(id);
            } catch (NotFoundException ex) {
                failures.add(new BatchDeleteFailure(id, "NOT_FOUND"));
            } catch (AccessDeniedException ex) {
                failures.add(new BatchDeleteFailure(id, "ACCESS_DENIED"));
            }
        }
        return new BatchDeleteResponse(deletedIds, failures);
    }

    @Override
    public EventResponse getById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        return eventMapper.toResponse(event);
    }

    @Override
    public EventResponse getById(Long id, Long requesterId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        return eventMapper.toResponse(event, requesterId);
    }

    @Override
    @Transactional
    public EventResponse setPublic(Long id, boolean publicAlbum) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        event.setPublicAlbum(publicAlbum);
        return eventMapper.toResponse(eventRepository.save(event));
    }

    @Override
    public Page<EventResponse> list(Pageable pageable, Long requesterId, boolean isAdmin) {
        return toResponsePage(isAdmin ? eventRepository.findAll(pageable) : eventRepository.findAccessibleByUser(requesterId, pageable), requesterId);
    }

    @Override
    public Page<EventResponse> search(String keyword, Pageable pageable, Long requesterId, boolean isAdmin) {
        return toResponsePage(isAdmin ? eventRepository.search(keyword, pageable) : eventRepository.searchAccessibleByUser(keyword, requesterId, pageable), requesterId);
    }

    private Page<EventResponse> toResponsePage(Page<Event> events, Long requesterId) {
        return new PageImpl<>(eventMapper.toResponseList(events.getContent(), requesterId), events.getPageable(), events.getTotalElements());
    }

    private void validateCoverImage(MultipartFile file) {
        if (file.getSize() > MAX_COVER_SIZE) {
            throw new BusinessException("Cover image too large", "FILE_TOO_LARGE");
        }
        if (!ALLOWED_COVER_MIME.contains(file.getContentType())) {
            throw new BusinessException("Invalid cover image type", "INVALID_TYPE");
        }
    }
}