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
package io.github.dsheirer.module.decode.p25;

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.ModuleEventBusMessage;
import java.util.Set;

/**
 * Notification containing current-site control channel frequencies discovered over the air.
 */
public class P25ControlChannelDiscoveryNotification extends ModuleEventBusMessage
{
    private final Channel mChannel;
    private final Set<Long> mFrequencies;

    public P25ControlChannelDiscoveryNotification(Channel channel, Set<Long> frequencies)
    {
        mChannel = channel;
        mFrequencies = Set.copyOf(frequencies);
    }

    public Channel getChannel()
    {
        return mChannel;
    }

    public Set<Long> getFrequencies()
    {
        return mFrequencies;
    }
}
