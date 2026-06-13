/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * ****************************************************************************
 */
package io.github.dsheirer.source.config;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.dsheirer.module.decode.p25.phase1.DecodeConfigP25Phase1;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControlChannelFrequencyUpdaterTest
{
    @Test
    void convertsSingleFrequencyAndAddsDiscoveries()
    {
        SourceConfigTuner source = new SourceConfigTuner();
        source.setFrequency(851_000_000L);
        source.setPreferredTuner("Tuner 1");

        SourceConfigTunerMultipleFrequency updated = ControlChannelFrequencyUpdater.merge(source,
            List.of(852_000_000L, 851_000_000L, 0L));

        assertEquals(List.of(851_000_000L, 852_000_000L), updated.getFrequencies());
        assertEquals("Tuner 1", updated.getPreferredTuner());
    }

    @Test
    void preservesMultipleFrequencySettings()
    {
        SourceConfigTunerMultipleFrequency source = new SourceConfigTunerMultipleFrequency();
        source.setFrequencies(new ArrayList<>(List.of(851_000_000L, 852_000_000L)));
        source.setPreferredTuner("Tuner 2");
        source.setFrequencyRotationDelay(750);

        SourceConfigTunerMultipleFrequency updated = ControlChannelFrequencyUpdater.merge(source,
            List.of(853_000_000L));

        assertEquals(List.of(851_000_000L, 852_000_000L, 853_000_000L), updated.getFrequencies());
        assertEquals("Tuner 2", updated.getPreferredTuner());
        assertEquals(750, updated.getFrequencyRotationDelay());
    }

    @Test
    void capsLearnedFrequencies()
    {
        List<Long> discoveries = new ArrayList<>();

        for(int x = 1; x <= ControlChannelFrequencyUpdater.MAX_FREQUENCIES + 5; x++)
        {
            discoveries.add((long)x);
        }

        SourceConfigTunerMultipleFrequency updated = ControlChannelFrequencyUpdater.merge(new SourceConfigTuner(),
            discoveries);

        assertEquals(ControlChannelFrequencyUpdater.MAX_FREQUENCIES, updated.getFrequencies().size());
    }

    @Test
    void sourceConfigFactoryCreatesIndependentMultipleFrequencyCopy()
    {
        SourceConfigTunerMultipleFrequency source = new SourceConfigTunerMultipleFrequency();
        source.setFrequencies(new ArrayList<>(List.of(851_000_000L)));
        source.setFrequencyRotationDelay(750);

        SourceConfigTunerMultipleFrequency copy = assertInstanceOf(SourceConfigTunerMultipleFrequency.class,
            SourceConfigFactory.copy(source));
        copy.addFrequency(852_000_000L);

        assertFalse(source.getFrequencies().contains(852_000_000L));
        assertEquals(750, copy.getFrequencyRotationDelay());
    }

    @Test
    void persistsLearnControlChannelsSetting() throws Exception
    {
        DecodeConfigP25Phase1 source = new DecodeConfigP25Phase1();
        source.setLearnControlChannels(true);
        XmlMapper mapper = new XmlMapper();

        DecodeConfigP25Phase1 restored = mapper.readValue(mapper.writeValueAsString(source),
            DecodeConfigP25Phase1.class);

        assertTrue(restored.getLearnControlChannels());
    }
}
