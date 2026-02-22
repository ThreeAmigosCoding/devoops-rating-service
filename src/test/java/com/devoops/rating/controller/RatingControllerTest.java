package com.devoops.rating.controller;

import com.devoops.rating.config.RoleAuthorizationInterceptor;
import com.devoops.rating.config.UserContextResolver;
import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.request.UpdateRatingRequest;
import com.devoops.rating.dto.response.RatingResponse;
import com.devoops.rating.dto.response.RatingsSummaryResponse;
import com.devoops.rating.exception.ConflictException;
import com.devoops.rating.exception.ForbiddenException;
import com.devoops.rating.exception.GlobalExceptionHandler;
import com.devoops.rating.exception.RatingNotFoundException;
import com.devoops.rating.model.RatingTargetType;
import com.devoops.rating.service.RatingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RatingController Unit Tests")
class RatingControllerTest {

    @Mock
    private RatingService ratingService;

    @InjectMocks
    private RatingController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final UUID GUEST_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserContextResolver())
                .addInterceptors(new RoleAuthorizationInterceptor())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private RatingResponse buildResponse(UUID id, UUID targetId, RatingTargetType type, UUID guestId, int score) {
        return new RatingResponse(id, targetId, type, "John", "Doe", guestId, score,
                LocalDateTime.now(), LocalDateTime.now());
    }

    // ================================================================
    //  POST /api/rating
    // ================================================================

    @Nested
    @DisplayName("POST /api/rating")
    class CreateRating {

        @Test
        @DisplayName("valid GUEST request returns 201")
        void createRating_ValidRequest_Returns201() throws Exception {
            UUID targetId = UUID.randomUUID();
            CreateRatingRequest request = new CreateRatingRequest(targetId, RatingTargetType.HOST, 5);
            RatingResponse response = buildResponse(UUID.randomUUID(), targetId, RatingTargetType.HOST, GUEST_ID, 5);

            when(ratingService.createRating(any(), any())).thenReturn(response);

            mockMvc.perform(post("/api/rating")
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.score").value(5))
                    .andExpect(jsonPath("$.targetId").value(targetId.toString()));
        }

        @Test
        @DisplayName("missing auth headers returns 401")
        void createRating_MissingHeaders_Returns401() throws Exception {
            CreateRatingRequest request = new CreateRatingRequest(UUID.randomUUID(), RatingTargetType.HOST, 5);

            mockMvc.perform(post("/api/rating")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("HOST role returns 403")
        void createRating_HostRole_Returns403() throws Exception {
            CreateRatingRequest request = new CreateRatingRequest(UUID.randomUUID(), RatingTargetType.HOST, 5);

            mockMvc.perform(post("/api/rating")
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("null targetId returns 400")
        void createRating_NullTargetId_Returns400() throws Exception {
            CreateRatingRequest request = new CreateRatingRequest(null, RatingTargetType.HOST, 5);

            mockMvc.perform(post("/api/rating")
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("score=0 returns 400")
        void createRating_ScoreZero_Returns400() throws Exception {
            CreateRatingRequest request = new CreateRatingRequest(UUID.randomUUID(), RatingTargetType.HOST, 0);

            mockMvc.perform(post("/api/rating")
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ConflictException from service returns 409")
        void createRating_ConflictException_Returns409() throws Exception {
            CreateRatingRequest request = new CreateRatingRequest(UUID.randomUUID(), RatingTargetType.HOST, 5);

            when(ratingService.createRating(any(), any())).thenThrow(new ConflictException("Already rated"));

            mockMvc.perform(post("/api/rating")
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("ForbiddenException from service returns 403")
        void createRating_ForbiddenException_Returns403() throws Exception {
            CreateRatingRequest request = new CreateRatingRequest(UUID.randomUUID(), RatingTargetType.HOST, 5);

            when(ratingService.createRating(any(), any())).thenThrow(new ForbiddenException("Not eligible"));

            mockMvc.perform(post("/api/rating")
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  GET /api/rating/{id}
    // ================================================================

    @Nested
    @DisplayName("GET /api/rating/{id}")
    class GetRatingById {

        @Test
        @DisplayName("existing id returns 200")
        void getRatingById_ExistingId_Returns200() throws Exception {
            UUID id = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            RatingResponse response = buildResponse(id, targetId, RatingTargetType.HOST, GUEST_ID, 4);

            when(ratingService.getRatingById(id)).thenReturn(response);

            mockMvc.perform(get("/api/rating/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.score").value(4));
        }

        @Test
        @DisplayName("non-existing id returns 404")
        void getRatingById_NonExistingId_Returns404() throws Exception {
            UUID id = UUID.randomUUID();

            when(ratingService.getRatingById(id)).thenThrow(new RatingNotFoundException(id));

            mockMvc.perform(get("/api/rating/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    // ================================================================
    //  GET /api/rating
    // ================================================================

    @Nested
    @DisplayName("GET /api/rating")
    class GetAllRatings {

        @Test
        @DisplayName("returns 200 with list of ratings")
        void getAllRatings_Returns200() throws Exception {
            List<RatingResponse> responses = List.of(
                    buildResponse(UUID.randomUUID(), UUID.randomUUID(), RatingTargetType.HOST, GUEST_ID, 5),
                    buildResponse(UUID.randomUUID(), UUID.randomUUID(), RatingTargetType.ACCOMMODATION, GUEST_ID, 3)
            );

            when(ratingService.getAllRatings()).thenReturn(responses);

            mockMvc.perform(get("/api/rating"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    // ================================================================
    //  GET /api/rating/target/{targetId}
    // ================================================================

    @Nested
    @DisplayName("GET /api/rating/target/{targetId}")
    class GetRatingsByTargetId {

        @Test
        @DisplayName("returns 200 with RatingsSummaryResponse")
        void getRatingsByTargetId_Returns200() throws Exception {
            UUID targetId = UUID.randomUUID();
            List<RatingResponse> ratings = List.of(
                    buildResponse(UUID.randomUUID(), targetId, RatingTargetType.HOST, GUEST_ID, 4)
            );
            RatingsSummaryResponse summary = new RatingsSummaryResponse(ratings, 4.0, 1);

            when(ratingService.getRatingsByTargetId(targetId)).thenReturn(summary);

            mockMvc.perform(get("/api/rating/target/{targetId}", targetId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.averageScore").value(4.0))
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.ratings.length()").value(1));
        }
    }

    // ================================================================
    //  GET /api/rating/guest
    // ================================================================

    @Nested
    @DisplayName("GET /api/rating/guest")
    class GetRatingsByGuestId {

        @Test
        @DisplayName("GUEST role returns 200 with list")
        void getRatingsByGuestId_GuestRole_Returns200() throws Exception {
            List<RatingResponse> responses = List.of(
                    buildResponse(UUID.randomUUID(), UUID.randomUUID(), RatingTargetType.HOST, GUEST_ID, 5)
            );

            when(ratingService.getRatingsByGuestId(GUEST_ID)).thenReturn(responses);

            mockMvc.perform(get("/api/rating/guest")
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("missing headers returns 401")
        void getRatingsByGuestId_MissingHeaders_Returns401() throws Exception {
            mockMvc.perform(get("/api/rating/guest"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("HOST role returns 403")
        void getRatingsByGuestId_HostRole_Returns403() throws Exception {
            mockMvc.perform(get("/api/rating/guest")
                            .header(USER_ID_HEADER, UUID.randomUUID().toString())
                            .header(USER_ROLE_HEADER, "HOST"))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  PUT /api/rating/{id}
    // ================================================================

    @Nested
    @DisplayName("PUT /api/rating/{id}")
    class UpdateRating {

        @Test
        @DisplayName("valid update returns 200 with updated score")
        void updateRating_Valid_Returns200() throws Exception {
            UUID id = UUID.randomUUID();
            UpdateRatingRequest request = new UpdateRatingRequest(3);
            RatingResponse response = buildResponse(id, UUID.randomUUID(), RatingTargetType.HOST, GUEST_ID, 3);

            when(ratingService.updateRating(eq(id), any(), any())).thenReturn(response);

            mockMvc.perform(put("/api/rating/{id}", id)
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score").value(3));
        }

        @Test
        @DisplayName("missing headers returns 401")
        void updateRating_MissingHeaders_Returns401() throws Exception {
            UUID id = UUID.randomUUID();
            UpdateRatingRequest request = new UpdateRatingRequest(3);

            mockMvc.perform(put("/api/rating/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("HOST role returns 403")
        void updateRating_HostRole_Returns403() throws Exception {
            UUID id = UUID.randomUUID();
            UpdateRatingRequest request = new UpdateRatingRequest(3);

            mockMvc.perform(put("/api/rating/{id}", id)
                            .header(USER_ID_HEADER, UUID.randomUUID().toString())
                            .header(USER_ROLE_HEADER, "HOST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ForbiddenException from service returns 403")
        void updateRating_ForbiddenFromService_Returns403() throws Exception {
            UUID id = UUID.randomUUID();
            UpdateRatingRequest request = new UpdateRatingRequest(3);

            when(ratingService.updateRating(eq(id), any(), any()))
                    .thenThrow(new ForbiddenException("Not your rating"));

            mockMvc.perform(put("/api/rating/{id}", id)
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("rating not found returns 404")
        void updateRating_NotFound_Returns404() throws Exception {
            UUID id = UUID.randomUUID();
            UpdateRatingRequest request = new UpdateRatingRequest(3);

            when(ratingService.updateRating(eq(id), any(), any()))
                    .thenThrow(new RatingNotFoundException(id));

            mockMvc.perform(put("/api/rating/{id}", id)
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ================================================================
    //  DELETE /api/rating/{id}
    // ================================================================

    @Nested
    @DisplayName("DELETE /api/rating/{id}")
    class DeleteRating {

        @Test
        @DisplayName("valid owner returns 204")
        void deleteRating_ValidOwner_Returns204() throws Exception {
            UUID id = UUID.randomUUID();

            doNothing().when(ratingService).deleteRating(eq(id), any());

            mockMvc.perform(delete("/api/rating/{id}", id)
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("missing headers returns 401")
        void deleteRating_MissingHeaders_Returns401() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(delete("/api/rating/{id}", id))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("HOST role returns 403")
        void deleteRating_HostRole_Returns403() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(delete("/api/rating/{id}", id)
                            .header(USER_ID_HEADER, UUID.randomUUID().toString())
                            .header(USER_ROLE_HEADER, "HOST"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("rating not found returns 404")
        void deleteRating_NotFound_Returns404() throws Exception {
            UUID id = UUID.randomUUID();

            doThrow(new RatingNotFoundException(id)).when(ratingService).deleteRating(eq(id), any());

            mockMvc.perform(delete("/api/rating/{id}", id)
                            .header(USER_ID_HEADER, GUEST_ID.toString())
                            .header(USER_ROLE_HEADER, "GUEST"))
                    .andExpect(status().isNotFound());
        }
    }
}
