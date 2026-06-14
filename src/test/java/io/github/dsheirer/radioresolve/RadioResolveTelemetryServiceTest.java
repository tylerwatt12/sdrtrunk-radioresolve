/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * ****************************************************************************
 */

package io.github.dsheirer.radioresolve;

import io.github.dsheirer.module.decode.p25.telemetry.P25NetworkConfigurationSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RadioResolveTelemetryServiceTest
{
    @Test
    void snapshotUsefulness()
    {
        P25NetworkConfigurationSnapshot empty = new P25NetworkConfigurationSnapshot("P25_PHASE_1", null, null,
            List.of(), List.of(), List.of(), List.of(), List.of());

        P25NetworkConfigurationSnapshot useful = new P25NetworkConfigurationSnapshot("P25_PHASE_1",
            new P25NetworkConfigurationSnapshot.Network(781824, 840, 835, 0), null, List.of(), List.of(),
            List.of(), List.of(), List.of());

        assertFalse(empty.isUseful());
        assertTrue(useful.isUseful());
    }

    @Test
    void hashChangesOnlyWhenSnapshotChanges()
    {
        P25NetworkConfigurationSnapshot first = snapshot(840);
        P25NetworkConfigurationSnapshot identical = snapshot(840);
        P25NetworkConfigurationSnapshot changed = snapshot(841);

        assertEquals(RadioResolveTelemetryService.hash(first), RadioResolveTelemetryService.hash(identical));
        assertNotEquals(RadioResolveTelemetryService.hash(first), RadioResolveTelemetryService.hash(changed));
    }

    private P25NetworkConfigurationSnapshot snapshot(int system)
    {
        return new P25NetworkConfigurationSnapshot("P25_PHASE_1",
            new P25NetworkConfigurationSnapshot.Network(781824, system, 835, 0),
            new P25NetworkConfigurationSnapshot.CurrentSite(system, 835, 2, 3, 0, true),
            List.of(new P25NetworkConfigurationSnapshot.Channel("primary_control", "0-777", 855862500L,
                810862500L, false, 1)),
            List.of(), List.of(), List.of(), List.of());
    }
}
