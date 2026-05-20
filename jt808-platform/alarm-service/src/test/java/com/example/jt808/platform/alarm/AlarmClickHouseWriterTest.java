package com.example.jt808.platform.alarm;

import com.example.jt808.platform.contracts.AlarmEvent;
import com.example.jt808.platform.contracts.AttachmentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlarmClickHouseWriterTest {

    private AlarmClickHouseWriter writer;

    @BeforeEach
    void setUp() {
        AlarmProperties props = new AlarmProperties();
        props.getClickHouse().setUrl("http://127.0.0.1:8123/");
        props.getClickHouse().setDatabase("jt808");
        writer = new AlarmClickHouseWriter(props, WebClient.builder());
    }

    // ── alarmRow ──────────────────────────────────────────────────────────────

    @Test
    void alarmRowContainsCoreFields() {
        AlarmEvent event = alarmEvent(0, 0, 0, 0, 0, 0);
        String row = writer.alarmRow(event);

        assertTrue(row.contains("\"vehicle_id\":\"term_001\""));
        assertTrue(row.contains("\"alarm_type\":1"));
        assertTrue(row.contains("\"alarm_level\":2"));
        assertTrue(row.contains("\"cleared\":0"));
    }

    @Test
    void alarmRowIncludesVideoAlarmFields() {
        AlarmEvent event = alarmEvent(0x03, 0x05, 0x02, 0x01, 0x01, 80);
        String row = writer.alarmRow(event);

        assertTrue(row.contains("\"video_alarm\":3"),        "video_alarm_word missing");
        assertTrue(row.contains("\"video_signal_lost_channels\":5"), "signal_lost missing");
        assertTrue(row.contains("\"video_shield_channels\":2"),       "shield_channels missing");
        assertTrue(row.contains("\"memory_fail_mask\":1"),            "memory_fail missing");
        assertTrue(row.contains("\"abnormal_driving_behavior\":1"),   "abnormal_driving missing");
        assertTrue(row.contains("\"fatigue_degree\":80"),             "fatigue_degree missing");
    }

    @Test
    void alarmRowVideoFieldsZeroWhenAbsent() {
        AlarmEvent event = alarmEvent(0, 0, 0, 0, 0, 0);
        String row = writer.alarmRow(event);

        assertTrue(row.contains("\"video_alarm\":0"));
        assertTrue(row.contains("\"video_signal_lost_channels\":0"));
        assertTrue(row.contains("\"video_shield_channels\":0"));
        assertTrue(row.contains("\"memory_fail_mask\":0"));
        assertTrue(row.contains("\"abnormal_driving_behavior\":0"));
        assertTrue(row.contains("\"fatigue_degree\":0"));
    }

    @Test
    void alarmRowClearedFlagEncoded() {
        AlarmEvent event = new AlarmEvent(
                "id", "term_001", "term_001", 1, "Overspeed", 2, 2L,
                31.23, 121.47, 60.0, true, Instant.now(), Instant.now(),
                0, 0, 0, 0, 0, 0);
        assertTrue(writer.alarmRow(event).contains("\"cleared\":1"));
    }

    @Test
    void alarmRowIsValidJson() {
        AlarmEvent event = alarmEvent(1, 2, 3, 4, 1, 50);
        String row = writer.alarmRow(event);
        assertTrue(row.startsWith("{"));
        assertTrue(row.endsWith("}"));
        assertFalse(row.contains(",,"), "double comma in JSON");
    }

    // ── attachmentRow ─────────────────────────────────────────────────────────

    @Test
    void attachmentRowContainsAllFields() {
        Instant now = Instant.now();
        AttachmentEvent event = new AttachmentEvent(
                "alarm_123", "term_001", "term_001",
                3, 1, 0, 0,
                "term_001_1000001.jpg", 51_200L,
                "jt808/media/term_001/term_001_1000001.jpg",
                now, now);

        String row = writer.attachmentRow(event);

        assertTrue(row.contains("\"alarm_id\":\"alarm_123\""));
        assertTrue(row.contains("\"channel\":1"));
        assertTrue(row.contains("\"format\":0"));
        assertTrue(row.contains("\"file_name\":\"term_001_1000001.jpg\""));
        assertTrue(row.contains("\"size\":51200"));
        assertTrue(row.contains("\"url\":\"jt808/media/term_001/term_001_1000001.jpg\""));
    }

    @Test
    void attachmentRowIsValidJson() {
        AttachmentEvent event = new AttachmentEvent(
                "id", "t", "t", 0, 1, 0, 0, "f.jpg", 100L, "url", Instant.now(), Instant.now());
        String row = writer.attachmentRow(event);
        assertTrue(row.startsWith("{"));
        assertTrue(row.endsWith("}"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AlarmEvent alarmEvent(int videoAlarm, int signalLost, int shield,
                                         int memFail, int driving, int fatigue) {
        return new AlarmEvent(
                "alarm_001", "term_001", "term_001",
                1, "Overspeed", 2, 2L,
                31.23, 121.47, 60.0, false,
                Instant.now(), Instant.now(),
                videoAlarm, signalLost, shield, memFail, driving, fatigue);
    }
}
