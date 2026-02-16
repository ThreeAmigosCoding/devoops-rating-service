package com.devoops.rating.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document(collection = "ratings")
public class Rating extends BaseDocument {

    private UUID targetId;

    private String guestFirstName;

    private String guestLastName;

    private UUID guestId;

    private Integer score;
}

