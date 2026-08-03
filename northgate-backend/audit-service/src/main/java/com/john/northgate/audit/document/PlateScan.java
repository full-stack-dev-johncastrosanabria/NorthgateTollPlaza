package com.john.northgate.audit.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Raw ANPR read from a lane camera. Transient sensor data, expired after 24h
 * by a TTL index — it never belongs in the relational model.
 */
@Document(collection = "plate_scans")
@CompoundIndex(name = "lane_scanned_idx", def = "{'laneNumber': 1, 'scannedAt': -1}")
@Getter
@Setter
@NoArgsConstructor
public class PlateScan {

    @Id
    private String id;

    private Integer laneNumber;

    private String plate;

    private Double confidence;

    /** Null when the transponder tag could not be read. */
    private String tagId;

    @Indexed(expireAfter = "24h")
    private Instant scannedAt;
}
