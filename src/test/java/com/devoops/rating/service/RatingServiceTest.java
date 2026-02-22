package com.devoops.rating.service;

import com.devoops.rating.config.UserContext;
import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.request.UpdateRatingRequest;
import com.devoops.rating.dto.response.RatingResponse;
import com.devoops.rating.dto.response.RatingsSummaryResponse;
import com.devoops.rating.exception.ConflictException;
import com.devoops.rating.exception.ForbiddenException;
import com.devoops.rating.exception.RatingNotFoundException;
import com.devoops.rating.grpc.ReservationEligibilityResult;
import com.devoops.rating.grpc.ReservationGrpcClient;
import com.devoops.rating.grpc.UserGrpcClient;
import com.devoops.rating.grpc.UserSummaryResult;
import com.devoops.rating.mapper.RatingMapper;
import com.devoops.rating.model.Rating;
import com.devoops.rating.model.RatingTargetType;
import com.devoops.rating.repository.RatingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RatingService Unit Tests")
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private RatingMapper ratingMapper;
    @Mock private UserGrpcClient userGrpcClient;
    @Mock private ReservationGrpcClient reservationGrpcClient;
    @Mock private RatingEventPublisherService eventPublisher;

    @InjectMocks
    private RatingService ratingService;

    // ---- helpers ----

    private Rating buildRating(UUID id, UUID targetId, RatingTargetType type, UUID guestId, int score) {
        Rating r = new Rating();
        r.setId(id);
        r.setTargetId(targetId);
        r.setTargetType(type);
        r.setGuestId(guestId);
        r.setGuestFirstName("John");
        r.setGuestLastName("Doe");
        r.setScore(score);
        return r;
    }

    private RatingResponse buildResponse(Rating r) {
        return new RatingResponse(r.getId(), r.getTargetId(), r.getTargetType(),
                r.getGuestFirstName(), r.getGuestLastName(), r.getGuestId(),
                r.getScore(), LocalDateTime.now(), LocalDateTime.now());
    }

    private UserSummaryResult userFound(UUID userId, String firstName, String lastName) {
        return new UserSummaryResult(true, userId, "user@example.com", firstName, lastName, "GUEST", false);
    }

    // ================================================================
    //  createRating
    // ================================================================

    @Nested
    @DisplayName("createRating")
    class CreateRating {

        @Test
        @DisplayName("eligible guest returns RatingResponse")
        void createRating_EligibleGuest_ReturnsRatingResponse() {
            UUID guestId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            CreateRatingRequest request = new CreateRatingRequest(targetId, RatingTargetType.HOST, 5);
            UserContext userContext = new UserContext(guestId, "GUEST");

            Rating entity = buildRating(UUID.randomUUID(), targetId, RatingTargetType.HOST, null, 5);
            RatingResponse expectedResponse = buildResponse(entity);

            when(reservationGrpcClient.checkRatingEligibility(guestId, targetId, RatingTargetType.HOST))
                    .thenReturn(new ReservationEligibilityResult(true, "eligible"));
            when(ratingRepository.findByTargetIdAndGuestIdAndIsDeletedFalse(targetId, guestId))
                    .thenReturn(Optional.empty());
            when(userGrpcClient.getUserSummary(guestId)).thenReturn(userFound(guestId, "John", "Doe"));
            when(ratingMapper.toEntity(request)).thenReturn(entity);
            when(ratingRepository.save(entity)).thenReturn(entity);
            when(ratingMapper.toResponse(entity)).thenReturn(expectedResponse);

            RatingResponse result = ratingService.createRating(request, userContext);

            assertThat(result).isEqualTo(expectedResponse);
            assertThat(entity.getGuestId()).isEqualTo(guestId);
            verify(ratingRepository).save(entity);
        }

        @Test
        @DisplayName("ineligible guest throws ForbiddenException")
        void createRating_IneligibleGuest_ThrowsForbiddenException() {
            UUID guestId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            CreateRatingRequest request = new CreateRatingRequest(targetId, RatingTargetType.HOST, 5);
            UserContext userContext = new UserContext(guestId, "GUEST");

            when(reservationGrpcClient.checkRatingEligibility(guestId, targetId, RatingTargetType.HOST))
                    .thenReturn(new ReservationEligibilityResult(false, "No completed reservation"));

            assertThatThrownBy(() -> ratingService.createRating(request, userContext))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("duplicate rating by same guest throws ConflictException")
        void createRating_DuplicateRatingByGuest_ThrowsConflictException() {
            UUID guestId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            CreateRatingRequest request = new CreateRatingRequest(targetId, RatingTargetType.HOST, 5);
            UserContext userContext = new UserContext(guestId, "GUEST");

            when(reservationGrpcClient.checkRatingEligibility(guestId, targetId, RatingTargetType.HOST))
                    .thenReturn(new ReservationEligibilityResult(true, "eligible"));
            when(ratingRepository.findByTargetIdAndGuestIdAndIsDeletedFalse(targetId, guestId))
                    .thenReturn(Optional.of(new Rating()));

            assertThatThrownBy(() -> ratingService.createRating(request, userContext))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("HOST target type calls publishHostRated, not publishAccommodationRated")
        void createRating_HostTarget_PublishesHostRatedEvent() {
            UUID guestId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            CreateRatingRequest request = new CreateRatingRequest(targetId, RatingTargetType.HOST, 4);
            UserContext userContext = new UserContext(guestId, "GUEST");

            Rating entity = buildRating(UUID.randomUUID(), targetId, RatingTargetType.HOST, null, 4);

            when(reservationGrpcClient.checkRatingEligibility(guestId, targetId, RatingTargetType.HOST))
                    .thenReturn(new ReservationEligibilityResult(true, "eligible"));
            when(ratingRepository.findByTargetIdAndGuestIdAndIsDeletedFalse(targetId, guestId))
                    .thenReturn(Optional.empty());
            when(userGrpcClient.getUserSummary(guestId)).thenReturn(userFound(guestId, "John", "Doe"));
            when(ratingMapper.toEntity(request)).thenReturn(entity);
            when(ratingRepository.save(entity)).thenReturn(entity);
            when(ratingMapper.toResponse(entity)).thenReturn(buildResponse(entity));

            ratingService.createRating(request, userContext);

            verify(eventPublisher).publishHostRated(entity);
            verify(eventPublisher, never()).publishAccommodationRated(any());
        }

        @Test
        @DisplayName("ACCOMMODATION target type calls publishAccommodationRated, not publishHostRated")
        void createRating_AccommodationTarget_PublishesAccommodationRatedEvent() {
            UUID guestId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            CreateRatingRequest request = new CreateRatingRequest(targetId, RatingTargetType.ACCOMMODATION, 3);
            UserContext userContext = new UserContext(guestId, "GUEST");

            Rating entity = buildRating(UUID.randomUUID(), targetId, RatingTargetType.ACCOMMODATION, null, 3);

            when(reservationGrpcClient.checkRatingEligibility(guestId, targetId, RatingTargetType.ACCOMMODATION))
                    .thenReturn(new ReservationEligibilityResult(true, "eligible"));
            when(ratingRepository.findByTargetIdAndGuestIdAndIsDeletedFalse(targetId, guestId))
                    .thenReturn(Optional.empty());
            when(userGrpcClient.getUserSummary(guestId)).thenReturn(userFound(guestId, "John", "Doe"));
            when(ratingMapper.toEntity(request)).thenReturn(entity);
            when(ratingRepository.save(entity)).thenReturn(entity);
            when(ratingMapper.toResponse(entity)).thenReturn(buildResponse(entity));

            ratingService.createRating(request, userContext);

            verify(eventPublisher).publishAccommodationRated(entity);
            verify(eventPublisher, never()).publishHostRated(any());
        }

        @Test
        @DisplayName("sets guestId, guestFirstName, guestLastName from UserContext and UserSummary")
        void createRating_SetsGuestIdAndNameFromUserContext() {
            UUID guestId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            CreateRatingRequest request = new CreateRatingRequest(targetId, RatingTargetType.HOST, 5);
            UserContext userContext = new UserContext(guestId, "GUEST");

            Rating entity = new Rating();

            when(reservationGrpcClient.checkRatingEligibility(guestId, targetId, RatingTargetType.HOST))
                    .thenReturn(new ReservationEligibilityResult(true, "eligible"));
            when(ratingRepository.findByTargetIdAndGuestIdAndIsDeletedFalse(targetId, guestId))
                    .thenReturn(Optional.empty());
            when(userGrpcClient.getUserSummary(guestId))
                    .thenReturn(new UserSummaryResult(true, guestId, "alice@example.com", "Alice", "Smith", "GUEST", false));
            when(ratingMapper.toEntity(request)).thenReturn(entity);
            when(ratingRepository.save(entity)).thenReturn(entity);
            when(ratingMapper.toResponse(entity)).thenReturn(buildResponse(entity));

            ratingService.createRating(request, userContext);

            assertThat(entity.getGuestId()).isEqualTo(guestId);
            assertThat(entity.getGuestFirstName()).isEqualTo("Alice");
            assertThat(entity.getGuestLastName()).isEqualTo("Smith");
        }
    }

    // ================================================================
    //  getRatingById
    // ================================================================

    @Nested
    @DisplayName("getRatingById")
    class GetRatingById {

        @Test
        @DisplayName("existing id returns RatingResponse")
        void getRatingById_ExistingId_ReturnsResponse() {
            UUID id = UUID.randomUUID();
            Rating rating = buildRating(id, UUID.randomUUID(), RatingTargetType.HOST, UUID.randomUUID(), 4);
            RatingResponse response = buildResponse(rating);

            when(ratingRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(rating));
            when(ratingMapper.toResponse(rating)).thenReturn(response);

            RatingResponse result = ratingService.getRatingById(id);

            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("non-existing id throws RatingNotFoundException")
        void getRatingById_NonExistingId_ThrowsRatingNotFoundException() {
            UUID id = UUID.randomUUID();

            when(ratingRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.getRatingById(id))
                    .isInstanceOf(RatingNotFoundException.class);
        }
    }

    // ================================================================
    //  getAllRatings
    // ================================================================

    @Nested
    @DisplayName("getAllRatings")
    class GetAllRatings {

        @Test
        @DisplayName("returns mapped list of all ratings")
        void getAllRatings_ReturnsAllRatings() {
            Rating r1 = buildRating(UUID.randomUUID(), UUID.randomUUID(), RatingTargetType.HOST, UUID.randomUUID(), 5);
            Rating r2 = buildRating(UUID.randomUUID(), UUID.randomUUID(), RatingTargetType.ACCOMMODATION, UUID.randomUUID(), 3);
            List<Rating> ratings = List.of(r1, r2);
            List<RatingResponse> responses = List.of(buildResponse(r1), buildResponse(r2));

            when(ratingRepository.findAllByIsDeletedFalse()).thenReturn(ratings);
            when(ratingMapper.toResponseList(ratings)).thenReturn(responses);

            List<RatingResponse> result = ratingService.getAllRatings();

            assertThat(result).hasSize(2).isEqualTo(responses);
        }

        @Test
        @DisplayName("empty repository returns empty list")
        void getAllRatings_WhenEmpty_ReturnsEmptyList() {
            when(ratingRepository.findAllByIsDeletedFalse()).thenReturn(List.of());
            when(ratingMapper.toResponseList(List.of())).thenReturn(List.of());

            assertThat(ratingService.getAllRatings()).isEmpty();
        }
    }

    // ================================================================
    //  getRatingsByTargetId
    // ================================================================

    @Nested
    @DisplayName("getRatingsByTargetId")
    class GetRatingsByTargetId {

        @Test
        @DisplayName("returns correct summary with list and count")
        void getRatingsByTargetId_ReturnsCorrectSummary() {
            UUID targetId = UUID.randomUUID();
            UUID guestId = UUID.randomUUID();
            Rating r = buildRating(UUID.randomUUID(), targetId, RatingTargetType.HOST, guestId, 4);
            List<Rating> ratings = List.of(r);
            List<RatingResponse> responses = List.of(buildResponse(r));

            when(ratingRepository.findAllByTargetIdAndIsDeletedFalse(targetId)).thenReturn(ratings);
            when(ratingMapper.toResponseList(ratings)).thenReturn(responses);

            RatingsSummaryResponse result = ratingService.getRatingsByTargetId(targetId);

            assertThat(result.ratings()).isEqualTo(responses);
            assertThat(result.totalCount()).isEqualTo(1);
            assertThat(result.averageScore()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("no ratings returns zero average and count")
        void getRatingsByTargetId_WithNoRatings_ReturnsZeroAverage() {
            UUID targetId = UUID.randomUUID();

            when(ratingRepository.findAllByTargetIdAndIsDeletedFalse(targetId)).thenReturn(List.of());
            when(ratingMapper.toResponseList(List.of())).thenReturn(List.of());

            RatingsSummaryResponse result = ratingService.getRatingsByTargetId(targetId);

            assertThat(result.ratings()).isEmpty();
            assertThat(result.totalCount()).isEqualTo(0);
            assertThat(result.averageScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("scores [5, 3, 4] produce average 4.0")
        void getRatingsByTargetId_ComputesCorrectAverage() {
            UUID targetId = UUID.randomUUID();
            UUID g1 = UUID.randomUUID(), g2 = UUID.randomUUID(), g3 = UUID.randomUUID();
            Rating r1 = buildRating(UUID.randomUUID(), targetId, RatingTargetType.HOST, g1, 5);
            Rating r2 = buildRating(UUID.randomUUID(), targetId, RatingTargetType.HOST, g2, 3);
            Rating r3 = buildRating(UUID.randomUUID(), targetId, RatingTargetType.HOST, g3, 4);
            List<Rating> ratings = List.of(r1, r2, r3);
            List<RatingResponse> responses = List.of(
                    new RatingResponse(r1.getId(), targetId, RatingTargetType.HOST, "A", "B", g1, 5, null, null),
                    new RatingResponse(r2.getId(), targetId, RatingTargetType.HOST, "C", "D", g2, 3, null, null),
                    new RatingResponse(r3.getId(), targetId, RatingTargetType.HOST, "E", "F", g3, 4, null, null)
            );

            when(ratingRepository.findAllByTargetIdAndIsDeletedFalse(targetId)).thenReturn(ratings);
            when(ratingMapper.toResponseList(ratings)).thenReturn(responses);

            RatingsSummaryResponse result = ratingService.getRatingsByTargetId(targetId);

            assertThat(result.averageScore()).isEqualTo(4.0);
            assertThat(result.totalCount()).isEqualTo(3);
        }
    }

    // ================================================================
    //  getRatingsByGuestId
    // ================================================================

    @Nested
    @DisplayName("getRatingsByGuestId")
    class GetRatingsByGuestId {

        @Test
        @DisplayName("returns ratings for the given guest")
        void getRatingsByGuestId_ReturnsGuestRatings() {
            UUID guestId = UUID.randomUUID();
            Rating r = buildRating(UUID.randomUUID(), UUID.randomUUID(), RatingTargetType.HOST, guestId, 5);
            List<Rating> ratings = List.of(r);
            List<RatingResponse> responses = List.of(buildResponse(r));

            when(ratingRepository.findAllByGuestIdAndIsDeletedFalse(guestId)).thenReturn(ratings);
            when(ratingMapper.toResponseList(ratings)).thenReturn(responses);

            assertThat(ratingService.getRatingsByGuestId(guestId)).isEqualTo(responses);
        }

        @Test
        @DisplayName("no ratings for guest returns empty list")
        void getRatingsByGuestId_WhenNoRatings_ReturnsEmptyList() {
            UUID guestId = UUID.randomUUID();

            when(ratingRepository.findAllByGuestIdAndIsDeletedFalse(guestId)).thenReturn(List.of());
            when(ratingMapper.toResponseList(List.of())).thenReturn(List.of());

            assertThat(ratingService.getRatingsByGuestId(guestId)).isEmpty();
        }
    }

    // ================================================================
    //  updateRating
    // ================================================================

    @Nested
    @DisplayName("updateRating")
    class UpdateRating {

        @Test
        @DisplayName("owner updates score and gets updated response")
        void updateRating_ByOwner_ReturnsUpdatedResponse() {
            UUID id = UUID.randomUUID();
            UUID guestId = UUID.randomUUID();
            UserContext userContext = new UserContext(guestId, "GUEST");
            UpdateRatingRequest request = new UpdateRatingRequest(3);
            Rating rating = buildRating(id, UUID.randomUUID(), RatingTargetType.HOST, guestId, 5);
            Rating savedRating = buildRating(id, rating.getTargetId(), RatingTargetType.HOST, guestId, 3);
            RatingResponse response = buildResponse(savedRating);

            when(ratingRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(rating));
            when(ratingRepository.save(rating)).thenReturn(savedRating);
            when(ratingMapper.toResponse(savedRating)).thenReturn(response);

            RatingResponse result = ratingService.updateRating(id, request, userContext);

            assertThat(result).isEqualTo(response);
            assertThat(rating.getScore()).isEqualTo(3);
        }

        @Test
        @DisplayName("wrong owner throws ForbiddenException")
        void updateRating_ByWrongOwner_ThrowsForbiddenException() {
            UUID id = UUID.randomUUID();
            UUID guestId = UUID.randomUUID();
            UserContext userContext = new UserContext(UUID.randomUUID(), "GUEST");
            UpdateRatingRequest request = new UpdateRatingRequest(3);
            Rating rating = buildRating(id, UUID.randomUUID(), RatingTargetType.HOST, guestId, 5);

            when(ratingRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(rating));

            assertThatThrownBy(() -> ratingService.updateRating(id, request, userContext))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("non-existing id throws RatingNotFoundException")
        void updateRating_NonExistingId_ThrowsRatingNotFoundException() {
            UUID id = UUID.randomUUID();
            UserContext userContext = new UserContext(UUID.randomUUID(), "GUEST");

            when(ratingRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.updateRating(id, new UpdateRatingRequest(3), userContext))
                    .isInstanceOf(RatingNotFoundException.class);
        }
    }

    // ================================================================
    //  deleteRating
    // ================================================================

    @Nested
    @DisplayName("deleteRating")
    class DeleteRating {

        @Test
        @DisplayName("owner soft-deletes rating")
        void deleteRating_ByOwner_SoftDeletesRating() {
            UUID id = UUID.randomUUID();
            UUID guestId = UUID.randomUUID();
            UserContext userContext = new UserContext(guestId, "GUEST");
            Rating rating = buildRating(id, UUID.randomUUID(), RatingTargetType.HOST, guestId, 5);

            when(ratingRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(rating));

            ratingService.deleteRating(id, userContext);

            assertThat(rating.isDeleted()).isTrue();
            verify(ratingRepository).save(rating);
        }

        @Test
        @DisplayName("wrong owner throws ForbiddenException")
        void deleteRating_ByWrongOwner_ThrowsForbiddenException() {
            UUID id = UUID.randomUUID();
            UUID guestId = UUID.randomUUID();
            UserContext userContext = new UserContext(UUID.randomUUID(), "GUEST");
            Rating rating = buildRating(id, UUID.randomUUID(), RatingTargetType.HOST, guestId, 5);

            when(ratingRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(rating));

            assertThatThrownBy(() -> ratingService.deleteRating(id, userContext))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("non-existing id throws RatingNotFoundException")
        void deleteRating_NonExistingId_ThrowsRatingNotFoundException() {
            UUID id = UUID.randomUUID();
            UserContext userContext = new UserContext(UUID.randomUUID(), "GUEST");

            when(ratingRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ratingService.deleteRating(id, userContext))
                    .isInstanceOf(RatingNotFoundException.class);
        }
    }
}
