package com.travelalbum.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body của PUT /api/events/{id}/public: {"public": true|false}. */
public record SetPublicRequest(@JsonProperty("public") boolean publicAlbum) {
}
