package com.devoops.rating;

import com.devoops.rating.service.RatingEventPublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class RatingApplicationTests {

    @MockitoBean
    RatingEventPublisherService ratingEventPublisherService;

    @Test
    void contextLoads() {
    }
}
