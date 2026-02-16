package com.devoops.rating.service;

import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.response.RatingResponse;
import com.devoops.rating.dto.request.UpdateRatingRequest;

import java.util.List;
import java.util.UUID;

public interface RatingService {

    RatingResponse createRating(CreateRatingRequest request);

    RatingResponse getRatingById(UUID id);

    List<RatingResponse> getAllRatings();

    List<RatingResponse> getRatingsByTargetId(UUID targetId);

    List<RatingResponse> getRatingsByGuestId(UUID guestId);

    RatingResponse updateRating(UUID id, UpdateRatingRequest request);

    void deleteRating(UUID id);
}

