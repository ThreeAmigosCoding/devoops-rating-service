package com.devoops.rating.service;

import com.devoops.rating.config.UserContext;
import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.response.RatingResponse;
import com.devoops.rating.dto.request.UpdateRatingRequest;
import com.devoops.rating.exception.RatingNotFoundException;
import com.devoops.rating.grpc.UserGrpcClient;
import com.devoops.rating.grpc.UserSummaryResult;
import com.devoops.rating.mapper.RatingMapper;
import com.devoops.rating.model.Rating;
import com.devoops.rating.repository.RatingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final RatingRepository ratingRepository;
    private final RatingMapper ratingMapper;
    private final UserGrpcClient userGrpcClient;

    public RatingService(RatingRepository ratingRepository, RatingMapper ratingMapper, UserGrpcClient userGrpcClient) {
        this.ratingRepository = ratingRepository;
        this.ratingMapper = ratingMapper;
        this.userGrpcClient = userGrpcClient;
    }

    public RatingResponse createRating(CreateRatingRequest request, UserContext userContext) {
        log.debug("Creating new rating for target: {}", request.targetId());

        UserSummaryResult userSummary = userGrpcClient.getUserSummary(userContext.userId());

        Rating rating = ratingMapper.toEntity(request);
        rating.setGuestId(userContext.userId());
        rating.setGuestFirstName(userSummary.found() ? userSummary.firstName() : "");
        rating.setGuestLastName(userSummary.found() ? userSummary.lastName() : "");

        Rating savedRating = ratingRepository.save(rating);
        log.info("Created rating with id: {}", savedRating.getId());

        return ratingMapper.toResponse(savedRating);
    }

    public RatingResponse getRatingById(UUID id) {
        log.debug("Fetching rating with id: {}", id);

        Rating rating = ratingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RatingNotFoundException(id));

        return ratingMapper.toResponse(rating);
    }

    public List<RatingResponse> getAllRatings() {
        log.debug("Fetching all ratings");

        return ratingMapper.toResponseList(ratingRepository.findAllByIsDeletedFalse());
    }

    public List<RatingResponse> getRatingsByTargetId(UUID targetId) {
        log.debug("Fetching ratings for target: {}", targetId);

        return ratingMapper.toResponseList(ratingRepository.findAllByTargetIdAndIsDeletedFalse(targetId));
    }

    public List<RatingResponse> getRatingsByGuestId(UUID guestId) {
        log.debug("Fetching ratings by guest: {}", guestId);

        return ratingMapper.toResponseList(ratingRepository.findAllByGuestIdAndIsDeletedFalse(guestId));
    }

    public RatingResponse updateRating(UUID id, UpdateRatingRequest request) {
        log.debug("Updating rating with id: {}", id);

        Rating rating = ratingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RatingNotFoundException(id));

        rating.setScore(request.score());
        rating.setUpdatedAt(LocalDateTime.now());

        Rating updatedRating = ratingRepository.save(rating);
        log.info("Updated rating with id: {}", updatedRating.getId());

        return ratingMapper.toResponse(updatedRating);
    }

    public void deleteRating(UUID id) {
        log.debug("Soft deleting rating with id: {}", id);

        Rating rating = ratingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RatingNotFoundException(id));

        rating.setDeleted(true);
        rating.setUpdatedAt(LocalDateTime.now());
        ratingRepository.save(rating);

        log.info("Soft deleted rating with id: {}", id);
    }
}
