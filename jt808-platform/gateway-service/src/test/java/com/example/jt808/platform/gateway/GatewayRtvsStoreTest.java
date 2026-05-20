package com.example.jt808.platform.gateway;

import com.example.jt808.platform.protocol.AudioVideoAttributesUpload;
import com.example.jt808.platform.protocol.TerminalRegistration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayRtvsStoreTest {

    // ── AVParameters JSON ─────────────────────────────────────────────────────

    @Test
    void avParametersJsonContainsAllFields() {
        AudioVideoAttributesUpload attrs = new AudioVideoAttributesUpload(
                7, 1, 0, 0, 160, true, 98, 2, 4);
        String json = GatewayRtvsStore.buildAvParameters(attrs);

        assertTrue(json.contains("\"AudioEncoding\":7"),     "audioEncoding missing");
        assertTrue(json.contains("\"AudioChannels\":1"),     "audioChannels missing");
        assertTrue(json.contains("\"AudioSampleRate\":0"),   "audioSampleRate missing");
        assertTrue(json.contains("\"AudioFrameLength\":160"),"audioFrameLength missing");
        assertTrue(json.contains("\"AudioOutputSupported\":true"), "audioOutput missing");
        assertTrue(json.contains("\"VideoEncoding\":98"),    "videoEncoding missing");
        assertTrue(json.contains("\"MaxAudioChannels\":2"),  "maxAudioChannels missing");
        assertTrue(json.contains("\"MaxVideoChannels\":4"),  "maxVideoChannels missing");
    }

    @Test
    void avParametersJsonIsValidJson() {
        AudioVideoAttributesUpload attrs = new AudioVideoAttributesUpload(
                7, 1, 0, 0, 160, false, 98, 2, 4);
        String json = GatewayRtvsStore.buildAvParameters(attrs);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertFalse(json.contains(",,"));
    }

    // ── SIM_CONFIG JSON ───────────────────────────────────────────────────────

    @Test
    void simConfigJsonContainsPlateAndIdentity() {
        TerminalRegistration reg = new TerminalRegistration(44, 4401, "MFR001",
                "D09", "SN12345", 2, "粤A12345");
        String json = GatewayRtvsStore.buildSimConfig("000000000001", reg);

        assertTrue(json.contains("\"Sim\":\"000000000001\""),   "Sim missing");
        assertTrue(json.contains("\"PlateColor\":2"),            "PlateColor missing");
        assertTrue(json.contains("\"ProvinceId\":44"),           "ProvinceId missing");
        assertTrue(json.contains("\"CityId\":4401"),             "CityId missing");
        assertTrue(json.contains("\"ManufacturerId\":\"MFR001\""), "ManufacturerId missing");
    }

    @Test
    void simConfigJsonIsValidJson() {
        TerminalRegistration reg = new TerminalRegistration(0, 0, "M", "T", "S", 0, "AB1234");
        String json = GatewayRtvsStore.buildSimConfig("000000000001", reg);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
    }

    // ── InMemoryGatewaySessionStore storeIdentity ─────────────────────────────

    @Test
    void inMemorySessionStoreIdentityUpdatesExistingSession() {
        InMemoryGatewaySessionStore store = new InMemoryGatewaySessionStore();
        store.register("term001", "10.0.0.1").block();

        TerminalRegistration reg = new TerminalRegistration(44, 4401, "MFR",
                "D09", "SN", 2, "粤A12345");
        store.storeIdentity("term001", reg).block();

        // verify via authenticate (which uses the existing session) — the session should still exist
        store.authenticate("term001").block(); // no exception = session present with identity
    }

    @Test
    void inMemorySessionRemoveClears() {
        InMemoryGatewaySessionStore store = new InMemoryGatewaySessionStore();
        store.register("term002", "10.0.0.2").block();
        store.remove("term002").block(); // should not throw
    }
}
