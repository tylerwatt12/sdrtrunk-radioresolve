/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.radioresolve;

import io.github.dsheirer.audio.broadcast.BroadcastConfiguration;
import io.github.dsheirer.audio.broadcast.BroadcastModel;
import io.github.dsheirer.audio.broadcast.ConfiguredBroadcast;
import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveConfiguration;
import java.util.Optional;

/**
 * RadioResolve broadcast configuration lookup helpers.
 */
public class RadioResolveConfigurations
{
    private RadioResolveConfigurations()
    {
    }

    /**
     * Finds the first enabled RadioResolve streaming configuration.
     */
    public static Optional<RadioResolveConfiguration> findEnabled(BroadcastModel broadcastModel)
    {
        return find(broadcastModel, true);
    }

    /**
     * Finds the first RadioResolve streaming configuration regardless of enabled state.
     */
    public static Optional<RadioResolveConfiguration> findAny(BroadcastModel broadcastModel)
    {
        return find(broadcastModel, false);
    }

    private static Optional<RadioResolveConfiguration> find(BroadcastModel broadcastModel, boolean enabledOnly)
    {
        if(broadcastModel == null)
        {
            return Optional.empty();
        }

        for(ConfiguredBroadcast configuredBroadcast: broadcastModel.getConfiguredBroadcasts())
        {
            BroadcastConfiguration configuration = configuredBroadcast.getBroadcastConfiguration();

            if(configuration instanceof RadioResolveConfiguration radioResolveConfiguration &&
                (!enabledOnly || radioResolveConfiguration.isEnabled()))
            {
                return Optional.of(radioResolveConfiguration);
            }
        }

        return Optional.empty();
    }
}
