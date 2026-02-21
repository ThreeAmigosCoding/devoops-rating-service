package com.devoops.rating.service;

import com.devoops.rating.config.UserContext;
import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.response.RatingResponse;
import com.devoops.rating.dto.response.RatingsSummaryResponse;
import com.devoops.rating.dto.request.UpdateRatingRequest;
import com.devoops.rating.exception.ConflictException;
import com.devoops.rating.exception.ForbiddenException;
import com.devoops.rating.exception.RatingNotFoundException;
import com.devoops.rating.grpc.ReservationGrpcClient;
import com.devoops.rating.grpc.ReservationEligibilityResult;
import com.devoops.rating.grpc.UserGrpcClient;
import com.devoops.rating.grpc.UserSummaryResult;
import com.devoops.rating.mapper.RatingMapper;
import com.devoops.rating.model.Rating;
import com.devoops.rating.model.RatingTargetType;
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
    private final ReservationGrpcClient reservationGrpcClient;
    private final RatingEventPublisherService eventPublisher;

    public RatingService(RatingRepository ratingRepository, RatingMapper ratingMapper,
                         UserGrpcClient userGrpcClient, ReservationGrpcClient reservationGrpcClient,
                         RatingEventPublisherService eventPublisher) {
        this.ratingRepository = ratingRepository;
        this.ratingMapper = ratingMapper;
        this.userGrpcClient = userGrpcClient;
        this.reservationGrpcClient = reservationGrpcClient;
        this.eventPublisher = eventPublisher;
    }

    public RatingResponse createRating(CreateRatingRequest request, UserContext userContext) {
        log.debug("Creating new rating for target: {} (type={})", request.targetId(), request.targetType());

        ReservationEligibilityResult eligibility = reservationGrpcClient.checkRatingEligibility(
                userContext.userId(), request.targetId(), request.targetType());
        if (!eligibility.eligible()) {
            throw new ForbiddenException("Not eligible to rate: " + eligibility.reason());
        }

        ratingRepository.findByTargetIdAndGuestIdAndIsDeletedFalse(request.targetId(), userContext.userId())
                .ifPresent(_ -> {
                    throw new ConflictException("You have already rated this target");
                });

        UserSummaryResult userSummary = userGrpcClient.getUserSummary(userContext.userId());

        Rating rating = ratingMapper.toEntity(request);
        rating.setGuestId(userContext.userId());
        rating.setGuestFirstName(userSummary.found() ? userSummary.firstName() : "");
        rating.setGuestLastName(userSummary.found() ? userSummary.lastName() : "");

        Rating savedRating = ratingRepository.save(rating);
        log.info("Created rating with id: {}", savedRating.getId());

        if (request.targetType() == RatingTargetType.HOST) {
            eventPublisher.publishHostRated(savedRating);
        } else if (request.targetType() == RatingTargetType.ACCOMMODATION) {
            eventPublisher.publishAccommodationRated(savedRating);
        }

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

    public RatingsSummaryResponse getRatingsByTargetId(UUID targetId) {
        log.debug("Fetching ratings for target: {}", targetId);

        List<Rating> ratings = ratingRepository.findAllByTargetIdAndIsDeletedFalse(targetId);
        List<RatingResponse> responseList = ratingMapper.toResponseList(ratings);
        double avg = responseList.stream().mapToInt(RatingResponse::score).average().orElse(0.0);
        return new RatingsSummaryResponse(responseList, avg, responseList.size());
    }

    public List<RatingResponse> getRatingsByGuestId(UUID guestId) {
        log.debug("Fetching ratings by guest: {}", guestId);

        return ratingMapper.toResponseList(ratingRepository.findAllByGuestIdAndIsDeletedFalse(guestId));
    }

    public RatingResponse updateRating(UUID id, UpdateRatingRequest request, UserContext userContext) {
        log.debug("Updating rating with id: {}", id);

        Rating rating = ratingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RatingNotFoundException(id));

        if (!rating.getGuestId().equals(userContext.userId())) {
            throw new ForbiddenException("You are not allowed to update this rating");
        }

        rating.setScore(request.score());
        rating.setUpdatedAt(LocalDateTime.now());

        Rating updatedRating = ratingRepository.save(rating);
        log.info("Updated rating with id: {}", updatedRating.getId());

        return ratingMapper.toResponse(updatedRating);
    }

    public void deleteRating(UUID id, UserContext userContext) {
        log.debug("Soft deleting rating with id: {}", id);

        Rating rating = ratingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RatingNotFoundException(id));

        if (!rating.getGuestId().equals(userContext.userId())) {
            throw new ForbiddenException("You are not allowed to delete this rating");
        }

        rating.setDeleted(true);
        rating.setUpdatedAt(LocalDateTime.now());
        ratingRepository.save(rating);

        log.info("Soft deleted rating with id: {}", id);
    }
}
