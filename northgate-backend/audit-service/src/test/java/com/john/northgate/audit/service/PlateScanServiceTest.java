package com.john.northgate.audit.service;

import com.john.northgate.audit.document.PlateScan;
import com.john.northgate.audit.dto.PlateScanRequestDto;
import com.john.northgate.audit.dto.PlateScanResponseDto;
import com.john.northgate.audit.exception.ResourceNotFoundException;
import com.john.northgate.audit.repository.PlateScanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlateScanServiceTest {

    @Mock
    private PlateScanRepository plateScanRepository;

    @InjectMocks
    private PlateScanService plateScanService;

    @Test
    @DisplayName("normalises the scanned plate and stamps the read time")
    void normalisesPlateAndStampsScanTime() {
        when(plateScanRepository.save(any(PlateScan.class))).thenAnswer(i -> i.getArgument(0));
        Instant before = Instant.now();

        plateScanService.record(new PlateScanRequestDto(3, "  hrv 7745 ", 0.97, null));

        ArgumentCaptor<PlateScan> scan = ArgumentCaptor.forClass(PlateScan.class);
        verify(plateScanRepository).save(scan.capture());
        // The console compares this against typed input, so casing has to match.
        assertThat(scan.getValue().getPlate()).isEqualTo("HRV 7745");
        assertThat(scan.getValue().getLaneNumber()).isEqualTo(3);
        assertThat(scan.getValue().getConfidence()).isEqualTo(0.97);
        assertThat(scan.getValue().getScannedAt()).isBetween(before, Instant.now());
    }

    @Test
    @DisplayName("keeps a null tag id, which is what an unread transponder looks like")
    void keepsNullTagIdForAnUnreadTransponder() {
        when(plateScanRepository.save(any(PlateScan.class))).thenAnswer(i -> i.getArgument(0));

        PlateScanResponseDto stored = plateScanService.record(
                new PlateScanRequestDto(4, "TSD 1190", 0.42, null));

        assertThat(stored.tagId()).isNull();
        assertThat(stored.confidence()).isEqualTo(0.42);
    }

    @Test
    @DisplayName("returns the most recent scan for a lane")
    void returnsLatestScanForLane() {
        PlateScan latest = new PlateScan();
        latest.setId("s1");
        latest.setLaneNumber(3);
        latest.setPlate("KTR 8891");
        latest.setConfidence(0.99);
        latest.setScannedAt(Instant.now());
        when(plateScanRepository.findFirstByLaneNumberOrderByScannedAtDesc(3)).thenReturn(Optional.of(latest));

        assertThat(plateScanService.latestForLane(3).plate()).isEqualTo("KTR 8891");
    }

    @Test
    @DisplayName("reports not-found when a lane has no scan waiting")
    void reportsNotFoundWhenNoScanWaiting() {
        when(plateScanRepository.findFirstByLaneNumberOrderByScannedAtDesc(6)).thenReturn(Optional.empty());

        // The console treats this as "nothing to auto-read", not as an error.
        assertThatThrownBy(() -> plateScanService.latestForLane(6))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("6");
    }
}
