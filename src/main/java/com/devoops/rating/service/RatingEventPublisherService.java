package com.devoops.rating.service;

import com.devoops.rating.dto.message.AccommodationRatedMessage;
import com.devoops.rating.dto.message.HostRatedMessage;
import com.devoops.rating.grpc.AccommodationGrpcClient;
import com.devoops.rating.grpc.AccommodationSummaryResult;
import com.devoops.rating.grpc.UserGrpcClient;
import com.devoops.rating.grpc.UserSummaryResult;
import com.devoops.rating.model.Rating;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingEventPublisherService {

    private final RabbitTemplate rabbitTemplate;
    private final UserGrpcClient userGrpcClient;
    private final AccommodationGrpcClient accommodationGrpcClient;

    @Value("${rabbitmq.exchange.notification}")
    private String exchange;

    @Value("${rabbitmq.routing-key.host-rated}")
    private String hostRatedKey;

    @Value("${rabbitmq.routing-key.accommodation-rated}")
    private String accommodationRatedKey;

    public void publishHostRated(Rating rating) {
        UserSummaryResult hostSummary = userGrpcClient.getUserSummary(rating.getTargetId());

        if (!hostSummary.found()) {
            log.warn("Host not found for rating {}, skipping notification", rating.getId());
            return;
        }

        String guestName = rating.getGuestFirstName() + " " + rating.getGuestLastName();

        HostRatedMessage message = new HostRatedMessage(
                rating.getTargetId(),
                hostSummary.email(),
                guestName,
                rating.getScore(),
                null
        );

        log.info("Publishing host rated event: ratingId={}, hostEmail={}, guestName={}",
                rating.getId(), hostSummary.email(), guestName);

        rabbitTemplate.convertAndSend(exchange, hostRatedKey, message);
    }

    public void publishAccommodationRated(Rating rating) {
        AccommodationSummaryResult accommodationSummary = accommodationGrpcClient.getAccommodationSummary(rating.getTargetId());

        if (!accommodationSummary.found()) {
            log.warn("Accommodation not found for rating {}, skipping notification", rating.getId());
            return;
        }

        UserSummaryResult hostSummary = userGrpcClient.getUserSummary(accommodationSummary.hostId());

        if (!hostSummary.found()) {
            log.warn("Host not found for accommodation rating {}, skipping notification", rating.getId());
            return;
        }

        String guestName = rating.getGuestFirstName() + " " + rating.getGuestLastName();

        AccommodationRatedMessage message = new AccommodationRatedMessage(
                accommodationSummary.hostId(),
                hostSummary.email(),
                guestName,
                accommodationSummary.name(),
                rating.getScore(),
                null
        );

        log.info("Publishing accommodation rated event: ratingId={}, hostEmail={}, accommodationName={}",
                rating.getId(), hostSummary.email(), accommodationSummary.name());

        rabbitTemplate.convertAndSend(exchange, accommodationRatedKey, message);
    }
}
