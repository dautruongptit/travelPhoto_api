package com.travelalbum.service.impl;

import com.travelalbum.entity.Photo;
import com.travelalbum.entity.User;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.storage.ExifInfo;
import com.travelalbum.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExifBackfillServiceTest {

    @Mock private PhotoRepository photoRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;

    @InjectMocks
    private ExifBackfillService service;

    @Test
    void backfillBatch_copiesExifSwapsDimensionsAndMarksRead() {
        Photo photo = Photo.builder().id(1L).uploadedBy(7L).path("ev/IMG_8227.JPG").originalName("IMG_8227.JPG")
                .width(2560).height(1184).build();
        when(photoRepository.findTop50ByExifReadAtIsNullOrderByIdAsc()).thenReturn(List.of(photo));
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).storageFolder("u_000007").build()));
        when(storageService.readExif("u_000007", "ev/IMG_8227.JPG")).thenReturn(new ExifInfo(
                "Apple iPhone 13", "iPhone 13 back dual wide camera 5.1mm f/1.6", "5.1 mm", "26 mm",
                "f/1.6", "1/120 sec", 50, LocalDateTime.of(2026, 10, 1, 9, 30), 6));

        int processed = service.backfillBatch();

        assertThat(processed).isEqualTo(1);
        assertThat(photo.getCameraModel()).isEqualTo("Apple iPhone 13");
        assertThat(photo.getFocalLength35mm()).isEqualTo("26 mm");
        assertThat(photo.getAperture()).isEqualTo("f/1.6");
        assertThat(photo.getWidth()).isEqualTo(1184); // Orientation=6 -> đảo lại
        assertThat(photo.getHeight()).isEqualTo(2560);
        assertThat(photo.getExifReadAt()).isNotNull();
    }

    @Test
    void backfillBatch_marksPhotoReadEvenWhenFileHasNoExif() {
        Photo photo = Photo.builder().id(2L).uploadedBy(7L).path("ev/a.png").originalName("a.png")
                .width(100).height(50).build();
        when(photoRepository.findTop50ByExifReadAtIsNullOrderByIdAsc()).thenReturn(List.of(photo));
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).storageFolder("u_000007").build()));
        when(storageService.readExif("u_000007", "ev/a.png")).thenReturn(null);

        service.backfillBatch();

        assertThat(photo.getExifReadAt()).isNotNull(); // không lặp lại ở lần khởi động sau
        assertThat(photo.getWidth()).isEqualTo(100);
        assertThat(photo.getCameraModel()).isNull();
    }

    @Test
    void backfillBatch_returnsZeroWhenNothingToDo() {
        when(photoRepository.findTop50ByExifReadAtIsNullOrderByIdAsc()).thenReturn(List.of());

        assertThat(service.backfillBatch()).isZero();
    }
}
