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
package io.github.dsheirer.source.tuner.channel;

import io.github.dsheirer.module.ModuleEventBusMessage;
import java.util.Set;

/**
 * Adds discovered frequencies to a running multiple-frequency tuner source.
 */
public class AddDiscoveredFrequenciesRequest extends ModuleEventBusMessage
{
    private final Set<Long> mFrequencies;

    public AddDiscoveredFrequenciesRequest(Set<Long> frequencies)
    {
        mFrequencies = Set.copyOf(frequencies);
    }

    public Set<Long> getFrequencies()
    {
        return mFrequencies;
    }
}
