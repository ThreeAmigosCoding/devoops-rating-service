package com.devoops.rating.grpc;

import com.devoops.rating.grpc.proto.reservation.CheckRatingEligibilityRequest;
import com.devoops.rating.grpc.proto.reservation.CheckRatingEligibilityResponse;
import com.devoops.rating.grpc.proto.reservation.ReservationInternalServiceGrpc;
import com.devoops.rating.model.RatingTargetType;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class ReservationGrpcClient {

    @GrpcClient("reservation-service")
    private ReservationInternalServiceGrpc.ReservationInternalServiceBlockingStub reservationStub;

    public ReservationEligibilityResult checkRatingEligibility(UUID guestId, UUID targetId, RatingTargetType targetType) {
        log.debug("Calling reservation service for rating eligibility: guestId={}, targetId={}, targetType={}",
                guestId, targetId, targetType);

        CheckRatingEligibilityRequest request = CheckRatingEligibilityRequest.newBuilder()
                .setGuestId(guestId.toString())
                .setTargetId(targetId.toString())
                .setTargetType(targetType.name())
                .build();

        CheckRatingEligibilityResponse response = reservationStub.checkRatingEligibility(request);

        log.debug("Received eligibility response: eligible={}", response.getEligible());

        return new ReservationEligibilityResult(response.getEligible(), response.getReason());
    }
}
