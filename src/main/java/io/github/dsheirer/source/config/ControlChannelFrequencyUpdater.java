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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Merges discovered control channels into tuner source configurations.
 */
public class ControlChannelFrequencyUpdater
{
    public static final int MAX_FREQUENCIES = 32;

    private ControlChannelFrequencyUpdater()
    {
    }

    public static SourceConfigTunerMultipleFrequency merge(SourceConfiguration sourceConfiguration,
                                                           Collection<Long> discoveredFrequencies)
    {
        SourceConfigTunerMultipleFrequency updated = new SourceConfigTunerMultipleFrequency();
        List<Long> frequencies = new ArrayList<>();

        if(sourceConfiguration instanceof SourceConfigTuner tuner)
        {
            add(frequencies, tuner.getFrequency());
            updated.setPreferredTuner(tuner.getPreferredTuner());
        }
        else if(sourceConfiguration instanceof SourceConfigTunerMultipleFrequency multiple)
        {
            for(long frequency: multiple.getFrequencies())
            {
                add(frequencies, frequency);
            }

            updated.setPreferredTuner(multiple.getPreferredTuner());
            updated.setFrequencyRotationDelay(multiple.getFrequencyRotationDelay());
        }

        for(long frequency: discoveredFrequencies)
        {
            add(frequencies, frequency);
        }

        updated.setFrequencies(frequencies);
        return updated;
    }

    private static void add(List<Long> frequencies, long frequency)
    {
        if(frequency > 0 && frequencies.size() < MAX_FREQUENCIES && !frequencies.contains(frequency))
        {
            frequencies.add(frequency);
        }
    }
}
