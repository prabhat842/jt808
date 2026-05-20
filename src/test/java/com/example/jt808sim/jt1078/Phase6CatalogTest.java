package com.example.jt808sim.jt1078;

import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.jt1078.messages.ResourceListEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Phase6CatalogTest {

    private static VehicleIdentity mediaIdentity() {
        VehicleIdentity id = new VehicleIdentity();
        id.setTerminalId("00000000000000000001");
        id.setMediaCapable(true);
        id.setMediaChannels(List.of(1, 2));
        return id;
    }

    @Test
    void seedProducesAtLeastThreeEntriesPerChannel() {
        TerminalMediaCatalog catalog = TerminalMediaCatalog.seed(mediaIdentity());
        // 2 channels × 3 entries + 1 audio-only for ch1 = 7 entries total
        // At minimum 3 per channel → 6 entries
        List<ResourceListEntry> all = catalog.query(
                new Jt1078Command.QueryResourceList(0, null, null, 0, 0, 0, 0, 0));
        assertTrue(all.size() >= 6, "Expected at least 3 entries per channel (got " + all.size() + ")");
    }

    @Test
    void seedContainsAudioOnlyEntry() {
        TerminalMediaCatalog catalog = TerminalMediaCatalog.seed(mediaIdentity());
        List<ResourceListEntry> audioOnly = catalog.query(
                new Jt1078Command.QueryResourceList(0, null, null, 0, 0, 1, 0, 0));
        assertFalse(audioOnly.isEmpty(), "Expected at least one audio-only resource entry");
        assertTrue(audioOnly.stream().allMatch(e -> e.audioVideoType() == 1));
    }

    @Test
    void seedContainsAlarmFlaggedEntry() {
        TerminalMediaCatalog catalog = TerminalMediaCatalog.seed(mediaIdentity());
        // Entry 2 has alarmFlagsLow=1 (video signal loss). Query with alarmLow=1 to find it.
        List<ResourceListEntry> flagged = catalog.query(
                new Jt1078Command.QueryResourceList(0, null, null, 0, 1L, 0, 0, 0));
        assertFalse(flagged.isEmpty(), "Expected at least one alarm-flagged resource entry");
    }

    @Test
    void seedContainsSubStreamEntry() {
        TerminalMediaCatalog catalog = TerminalMediaCatalog.seed(mediaIdentity());
        // streamType=2 (sub-stream)
        List<ResourceListEntry> subs = catalog.query(
                new Jt1078Command.QueryResourceList(0, null, null, 0, 0, 0, 2, 0));
        assertFalse(subs.isEmpty(), "Expected at least one sub-stream entry");
    }

    @Test
    void seedEmptyForNonMediaCapableTerminal() {
        VehicleIdentity id = new VehicleIdentity();
        id.setTerminalId("00000000000000000002");
        id.setMediaCapable(false);
        TerminalMediaCatalog catalog = TerminalMediaCatalog.seed(id);
        List<ResourceListEntry> all = catalog.query(
                new Jt1078Command.QueryResourceList(0, null, null, 0, 0, 0, 0, 0));
        assertTrue(all.isEmpty());
    }
}
