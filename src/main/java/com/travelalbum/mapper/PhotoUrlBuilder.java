package com.travelalbum.mapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PhotoUrlBuilder {

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    public String buildUrl(Long photoId) {
        return apiBaseUrl + "/api/photos/download/" + photoId;
    }

    public String buildPreviewUrl(Long photoId) {
        return apiBaseUrl + "/api/photos/" + photoId + "/preview";
    }

    public String buildThumbnailUrl(Long photoId) {
        return apiBaseUrl + "/api/photos/" + photoId + "/thumbnail";
    }

    public String buildEventCoverUrl(Long eventId) {
        return apiBaseUrl + "/api/events/" + eventId + "/cover";
    }
}