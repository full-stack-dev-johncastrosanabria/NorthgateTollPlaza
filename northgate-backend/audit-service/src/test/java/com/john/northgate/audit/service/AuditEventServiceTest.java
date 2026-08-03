package com.john.northgate.audit.service;

import com.john.northgate.audit.document.AuditEvent;
import com.john.northgate.audit.dto.AuditEventRequestDto;
import com.john.northgate.audit.dto.AuditEventResponseDto;
import com.john.northgate.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditEventService auditEventService;

    @Test
    @DisplayName("stamps the event with its own timestamp and keeps the caller's payload")
    void recordsEventWithServerTimestamp() {
        givenSaveAssignsId("abc123");
        Instant before = Instant.now();

        AuditEventResponseDto stored = auditEventService.record(new AuditEventRequestDto(
                "PASS_RECORDED", "OP-14", 3, "PASS", "42",
                Map.of("plate", "KTR 8891", "amount", "3.00")));

        AuditEvent saved = capturedEvent();
        assertThat(saved.getOccurredAt()).isBetween(before, Instant.now());
        assertThat(saved.getEventType()).isEqualTo("PASS_RECORDED");
        assertThat(saved.getStaffCode()).isEqualTo("OP-14");
        assertThat(saved.getLaneNumber()).isEqualTo(3);
        assertThat(saved.getEntityType()).isEqualTo("PASS");
        assertThat(saved.getEntityId()).isEqualTo("42");
        assertThat(saved.getPayload()).containsEntry("plate", "KTR 8891");
        assertThat(stored.id()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("stores an empty payload rather than null when none is sent")
    void substitutesEmptyPayloadForNull() {
        givenSaveAssignsId("abc124");

        auditEventService.record(new AuditEventRequestDto("SIGN_IN", "MG-02", null, "STAFF", "2", null));

        // A null map would blow up on read; the collection stays queryable this way.
        assertThat(capturedEvent().getPayload()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("filters by lane in preference to any other criterion")
    void filtersByLaneFirst() {
        when(auditEventRepository.findByLaneNumberOrderByOccurredAtDesc(eq(3), any(Pageable.class)))
                .thenReturn(List.of());

        auditEventService.query(3, "OP-14", "SIGN_IN", 50);

        verify(auditEventRepository).findByLaneNumberOrderByOccurredAtDesc(eq(3), any(Pageable.class));
        verify(auditEventRepository, never())
                .findByStaffCodeIgnoreCaseOrderByOccurredAtDesc(anyString(), any());
        verify(auditEventRepository, never()).findByOrderByOccurredAtDesc(any());
    }

    @Test
    @DisplayName("falls back to the staff code when no lane is given")
    void filtersByStaffCodeWhenNoLane() {
        when(auditEventRepository.findByStaffCodeIgnoreCaseOrderByOccurredAtDesc(eq("OP-14"), any(Pageable.class)))
                .thenReturn(List.of());

        auditEventService.query(null, "OP-14", "SIGN_IN", 50);

        verify(auditEventRepository)
                .findByStaffCodeIgnoreCaseOrderByOccurredAtDesc(eq("OP-14"), any(Pageable.class));
    }

    @Test
    @DisplayName("treats a blank staff code as absent and moves on to the event type")
    void treatsBlankStaffCodeAsAbsent() {
        when(auditEventRepository.findByEventTypeOrderByOccurredAtDesc(eq("SIGN_IN"), any(Pageable.class)))
                .thenReturn(List.of());

        auditEventService.query(null, "   ", "SIGN_IN", 50);

        verify(auditEventRepository).findByEventTypeOrderByOccurredAtDesc(eq("SIGN_IN"), any(Pageable.class));
        verify(auditEventRepository, never())
                .findByStaffCodeIgnoreCaseOrderByOccurredAtDesc(anyString(), any());
    }

    @Test
    @DisplayName("returns the unfiltered trail when no criteria are given, honouring the limit")
    void returnsUnfilteredTrailWithLimit() {
        when(auditEventRepository.findByOrderByOccurredAtDesc(any(Pageable.class))).thenReturn(List.of());

        auditEventService.query(null, null, null, 25);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findByOrderByOccurredAtDesc(page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(25);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(event.capture());
        return event.getValue();
    }

    private void givenSaveAssignsId(String id) {
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            event.setId(id);
            return event;
        });
    }
}
