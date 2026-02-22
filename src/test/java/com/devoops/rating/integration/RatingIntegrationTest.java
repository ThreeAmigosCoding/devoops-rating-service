package com.devoops.rating.integration;

import com.devoops.rating.dto.request.CreateRatingRequest;
import com.devoops.rating.dto.request.UpdateRatingRequest;
import com.devoops.rating.grpc.AccommodationGrpcClient;
import com.devoops.rating.grpc.ReservationEligibilityResult;
import com.devoops.rating.grpc.ReservationGrpcClient;
import com.devoops.rating.grpc.UserGrpcClient;
import com.devoops.rating.grpc.UserSummaryResult;
import com.devoops.rating.model.RatingTargetType;
import com.devoops.rating.service.RatingEventPublisherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RatingIntegrationTest {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongodb::getConnectionString);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserGrpcClient userGrpcClient;

    @MockitoBean
    private ReservationGrpcClient reservationGrpcClient;

    @MockitoBean
    private AccommodationGrpcClient accommodationGrpcClient;

    @MockitoBean
    private RatingEventPublisherService ratingEventPublisherService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    /** Shared across ordered tests — set in @Order(1), used by later tests. */
    private static UUID createdRatingId;

    // ---- helpers ----

    private void mockEligible() {
        when(reservationGrpcClient.checkRatingEligibility(any(), any(), any()))
                .thenReturn(new ReservationEligibilityResult(true, "eligible"));
    }

    private void mockGuestSummary() {
        when(userGrpcClient.getUserSummary(GUEST_ID))
                .thenReturn(new UserSummaryResult(true, GUEST_ID, "guest@example.com", "John", "Doe", "GUEST", false));
    }

    // ================================================================
    //  Tests (ordered — MongoDB state flows from test 1 onwards)
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("1. createRating – valid request returns 201 and stores rating")
    void createRating_ValidRequest_Returns201() throws Exception {
        mockEligible();
        mockGuestSummary();
        doNothing().when(ratingEventPublisherService).publishHostRated(any());

        CreateRatingRequest request = new CreateRatingRequest(TARGET_ID, RatingTargetType.HOST, 4);

        String body = mockMvc.perform(post("/api/rating")
                        .header(USER_ID_HEADER, GUEST_ID.toString())
                        .header(USER_ROLE_HEADER, "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.score").value(4))
                .andExpect(jsonPath("$.targetId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.guestId").value(GUEST_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        createdRatingId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        verify(ratingEventPublisherService).publishHostRated(any());
    }

    @Test
    @Order(2)
    @DisplayName("2. createRating – duplicate by same guest returns 409")
    void createRating_DuplicateByGuest_Returns409() throws Exception {
        mockEligible();

        CreateRatingRequest request = new CreateRatingRequest(TARGET_ID, RatingTargetType.HOST, 5);

        mockMvc.perform(post("/api/rating")
                        .header(USER_ID_HEADER, GUEST_ID.toString())
                        .header(USER_ROLE_HEADER, "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    @DisplayName("3. createRating – no auth headers returns 401")
    void createRating_NoAuthHeaders_Returns401() throws Exception {
        CreateRatingRequest request = new CreateRatingRequest(UUID.randomUUID(), RatingTargetType.HOST, 5);

        mockMvc.perform(post("/api/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("4. createRating – HOST role returns 403")
    void createRating_HostRole_Returns403() throws Exception {
        CreateRatingRequest request = new CreateRatingRequest(UUID.randomUUID(), RatingTargetType.HOST, 5);

        mockMvc.perform(post("/api/rating")
                        .header(USER_ID_HEADER, UUID.randomUUID().toString())
                        .header(USER_ROLE_HEADER, "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("5. createRating – ineligible guest returns 403")
    void createRating_IneligibleGuest_Returns403() throws Exception {
        UUID differentTarget = UUID.randomUUID();
        when(reservationGrpcClient.checkRatingEligibility(any(), eq(differentTarget), any()))
                .thenReturn(new ReservationEligibilityResult(false, "No past reservation"));

        CreateRatingRequest request = new CreateRatingRequest(differentTarget, RatingTargetType.HOST, 5);

        mockMvc.perform(post("/api/rating")
                        .header(USER_ID_HEADER, GUEST_ID.toString())
                        .header(USER_ROLE_HEADER, "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("6. createRating – score=6 returns 400")
    void createRating_InvalidScore_Returns400() throws Exception {
        CreateRatingRequest request = new CreateRatingRequest(UUID.randomUUID(), RatingTargetType.HOST, 6);

        mockMvc.perform(post("/api/rating")
                        .header(USER_ID_HEADER, GUEST_ID.toString())
                        .header(USER_ROLE_HEADER, "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    @DisplayName("7. getRatingById – existing id returns 200 with correct score")
    void getRatingById_ExistingId_Returns200() throws Exception {
        mockMvc.perform(get("/api/rating/{id}", createdRatingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdRatingId.toString()))
                .andExpect(jsonPath("$.score").value(4));
    }

    @Test
    @Order(8)
    @DisplayName("8. getRatingById – random UUID returns 404")
    void getRatingById_NonExistingId_Returns404() throws Exception {
        mockMvc.perform(get("/api/rating/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    @DisplayName("9. getAllRatings – returns list with at least one entry")
    void getAllRatings_Returns200WithList() throws Exception {
        mockMvc.perform(get("/api/rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(10)
    @DisplayName("10. getRatingsByTargetId – returns summary with correct count and average")
    void getRatingsByTargetId_Returns200WithSummary() throws Exception {
        mockMvc.perform(get("/api/rating/target/{targetId}", TARGET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.averageScore").value(4.0))
                .andExpect(jsonPath("$.ratings.length()").value(1));
    }

    @Test
    @Order(11)
    @DisplayName("11. getRatingsByGuestId – returns guest's ratings")
    void getRatingsByGuestId_Returns200WithList() throws Exception {
        mockMvc.perform(get("/api/rating/guest")
                        .header(USER_ID_HEADER, GUEST_ID.toString())
                        .header(USER_ROLE_HEADER, "GUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(12)
    @DisplayName("12. updateRating – valid owner updates score to 2")
    void updateRating_ValidRequest_Returns200() throws Exception {
        UpdateRatingRequest request = new UpdateRatingRequest(2);

        mockMvc.perform(put("/api/rating/{id}", createdRatingId)
                        .header(USER_ID_HEADER, GUEST_ID.toString())
                        .header(USER_ROLE_HEADER, "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(2));
    }

    @Test
    @Order(13)
    @DisplayName("13. updateRating – different guest returns 403")
    void updateRating_DifferentGuest_Returns403() throws Exception {
        UpdateRatingRequest request = new UpdateRatingRequest(1);

        mockMvc.perform(put("/api/rating/{id}", createdRatingId)
                        .header(USER_ID_HEADER, UUID.randomUUID().toString())
                        .header(USER_ROLE_HEADER, "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("14. deleteRating – valid owner returns 204")
    void deleteRating_ValidOwner_Returns204() throws Exception {
        mockMvc.perform(delete("/api/rating/{id}", createdRatingId)
                        .header(USER_ID_HEADER, GUEST_ID.toString())
                        .header(USER_ROLE_HEADER, "GUEST"))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(15)
    @DisplayName("15. getRatingById after delete returns 404 (soft-delete verified)")
    void deleteRating_ThenGetById_Returns404() throws Exception {
        mockMvc.perform(get("/api/rating/{id}", createdRatingId))
                .andExpect(status().isNotFound());
    }
}
