package com.devoops.rating.grpc;

import com.devoops.rating.grpc.proto.accommodation.AccommodationSummaryRequest;
import com.devoops.rating.grpc.proto.accommodation.AccommodationSummaryResponse;
import com.devoops.rating.grpc.proto.accommodation.AccommodationInternalServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class AccommodationGrpcClient {

    @GrpcClient("accommodation-service")
    private AccommodationInternalServiceGrpc.AccommodationInternalServiceBlockingStub accommodationStub;

    public AccommodationSummaryResult getAccommodationSummary(UUID accommodationId) {
        log.debug("Calling accommodation service for summary: accommodationId={}", accommodationId);

        AccommodationSummaryRequest request = AccommodationSummaryRequest.newBuilder()
                .setAccommodationId(accommodationId.toString())
                .build();

        AccommodationSummaryResponse response = accommodationStub.getAccommodationSummary(request);

        log.debug("Received accommodation summary response: found={}", response.getFound());

        if (!response.getFound()) {
            return new AccommodationSummaryResult(false, null, null);
        }

        return new AccommodationSummaryResult(
                true,
                response.getAccommodationName(),
                UUID.fromString(response.getHostId())
        );
    }
}
