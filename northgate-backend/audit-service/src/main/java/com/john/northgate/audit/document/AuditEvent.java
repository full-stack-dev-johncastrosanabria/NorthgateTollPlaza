package com.john.northgate.audit.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable business audit record. The payload shape varies per event type,
 * which is why this lives in MongoDB rather than the relational model.
 */
@Document(collection = "audit_events")
@CompoundIndex(name = "lane_occurred_idx", def = "{'laneNumber': 1, 'occurredAt': -1}")
@CompoundIndex(name = "staff_occurred_idx", def = "{'staffCode': 1, 'occurredAt': -1}")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    private String id;

    @Indexed
    private String eventType;

    private String staffCode;

    private Integer laneNumber;

    private String entityType;

    private String entityId;

    private Map<String, Object> payload;

    @Indexed
    private Instant occurredAt;
}
