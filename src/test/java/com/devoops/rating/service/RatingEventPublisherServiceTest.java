package com.devoops.rating.service;

import com.devoops.rating.dto.message.AccommodationRatedMessage;
import com.devoops.rating.dto.message.HostRatedMessage;
import com.devoops.rating.grpc.AccommodationGrpcClient;
import com.devoops.rating.grpc.AccommodationSummaryResult;
import com.devoops.rating.grpc.UserGrpcClient;
import com.devoops.rating.grpc.UserSummaryResult;
import com.devoops.rating.model.Rating;
import com.devoops.rating.model.RatingTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RatingEventPublisherService Unit Tests")
class RatingEventPublisherServiceTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private UserGrpcClient userGrpcClient;
    @Mock private AccommodationGrpcClient accommodationGrpcClient;

    @InjectMocks
    private RatingEventPublisherService publisherService;

    private static final String EXCHANGE = "notification.exchange";
    private static final String HOST_RATED_KEY = "notification.rating.host";
    private static final String ACCOMMODATION_RATED_KEY = "notification.rating.accommodation";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisherService, "exchange", EXCHANGE);
        ReflectionTestUtils.setField(publisherService, "hostRatedKey", HOST_RATED_KEY);
        ReflectionTestUtils.setField(publisherService, "accommodationRatedKey", ACCOMMODATION_RATED_KEY);
    }

    private Rating buildRating(UUID targetId, RatingTargetType type, String firstName, String lastName, int score) {
        Rating r = new Rating();
        r.setId(UUID.randomUUID());
        r.setTargetId(targetId);
        r.setTargetType(type);
        r.setGuestFirstName(firstName);
        r.setGuestLastName(lastName);
        r.setScore(score);
        return r;
    }

    private UserSummaryResult hostFound(UUID userId, String email) {
        return new UserSummaryResult(true, userId, email, "Host", "User", "HOST", false);
    }

    // ================================================================
    //  publishHostRated
    // ================================================================

    @Nested
    @DisplayName("publishHostRated")
    class PublishHostRated {

        @Test
        @DisplayName("host found publishes correct HostRatedMessage")
        void publishHostRated_HostFound_PublishesCorrectMessage() {
            UUID hostId = UUID.randomUUID();
            Rating rating = buildRating(hostId, RatingTargetType.HOST, "Jane", "Doe", 4);

            when(userGrpcClient.getUserSummary(hostId)).thenReturn(hostFound(hostId, "host@example.com"));

            publisherService.publishHostRated(rating);

            ArgumentCaptor<HostRatedMessage> captor = ArgumentCaptor.forClass(HostRatedMessage.class);
            verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(HOST_RATED_KEY), captor.capture());

            HostRatedMessage msg = captor.getValue();
            assertThat(msg.userId()).isEqualTo(hostId);
            assertThat(msg.userEmail()).isEqualTo("host@example.com");
            assertThat(msg.guestName()).isEqualTo("Jane Doe");
            assertThat(msg.rating()).isEqualTo(4);
            assertThat(msg.comment()).isNull();
        }

        @Test
        @DisplayName("host not found logs warning and does not publish")
        void publishHostRated_HostNotFound_LogsWarningAndDoesNotPublish() {
            UUID hostId = UUID.randomUUID();
            Rating rating = buildRating(hostId, RatingTargetType.HOST, "Jane", "Doe", 4);

            when(userGrpcClient.getUserSummary(hostId))
                    .thenReturn(new UserSummaryResult(false, null, null, null, null, null, false));

            publisherService.publishHostRated(rating);

            verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
        }
    }

    // ================================================================
    //  publishAccommodationRated
    // ================================================================

    @Nested
    @DisplayName("publishAccommodationRated")
    class PublishAccommodationRated {

        @Test
        @DisplayName("all found publishes correct AccommodationRatedMessage")
        void publishAccommodationRated_AllFound_PublishesCorrectMessage() {
            UUID accommodationId = UUID.randomUUID();
            UUID hostId = UUID.randomUUID();
            Rating rating = buildRating(accommodationId, RatingTargetType.ACCOMMODATION, "Bob", "Smith", 5);

            when(accommodationGrpcClient.getAccommodationSummary(accommodationId))
                    .thenReturn(new AccommodationSummaryResult(true, "Sunny Villa", hostId));
            when(userGrpcClient.getUserSummary(hostId))
                    .thenReturn(hostFound(hostId, "host@example.com"));

            publisherService.publishAccommodationRated(rating);

            ArgumentCaptor<AccommodationRatedMessage> captor = ArgumentCaptor.forClass(AccommodationRatedMessage.class);
            verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ACCOMMODATION_RATED_KEY), captor.capture());

            AccommodationRatedMessage msg = captor.getValue();
            assertThat(msg.userId()).isEqualTo(hostId);
            assertThat(msg.userEmail()).isEqualTo("host@example.com");
            assertThat(msg.guestName()).isEqualTo("Bob Smith");
            assertThat(msg.accommodationName()).isEqualTo("Sunny Villa");
            assertThat(msg.rating()).isEqualTo(5);
            assertThat(msg.comment()).isNull();
        }

        @Test
        @DisplayName("accommodation not found logs warning and does not publish")
        void publishAccommodationRated_AccommodationNotFound_LogsWarningAndDoesNotPublish() {
            UUID accommodationId = UUID.randomUUID();
            Rating rating = buildRating(accommodationId, RatingTargetType.ACCOMMODATION, "Bob", "Smith", 5);

            when(accommodationGrpcClient.getAccommodationSummary(accommodationId))
                    .thenReturn(new AccommodationSummaryResult(false, null, null));

            publisherService.publishAccommodationRated(rating);

            verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
        }

        @Test
        @DisplayName("accommodation found but host not found does not publish")
        void publishAccommodationRated_HostNotFound_LogsWarningAndDoesNotPublish() {
            UUID accommodationId = UUID.randomUUID();
            UUID hostId = UUID.randomUUID();
            Rating rating = buildRating(accommodationId, RatingTargetType.ACCOMMODATION, "Bob", "Smith", 5);

            when(accommodationGrpcClient.getAccommodationSummary(accommodationId))
                    .thenReturn(new AccommodationSummaryResult(true, "Sunny Villa", hostId));
            when(userGrpcClient.getUserSummary(hostId))
                    .thenReturn(new UserSummaryResult(false, null, null, null, null, null, false));

            publisherService.publishAccommodationRated(rating);

            verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
        }
    }
}
