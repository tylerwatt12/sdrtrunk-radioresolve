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

package io.github.dsheirer.preference.radioresolve;

import io.github.dsheirer.preference.Preference;
import io.github.dsheirer.preference.PreferenceType;
import io.github.dsheirer.sample.Listener;
import java.util.prefs.Preferences;

/**
 * RadioResolve node integration preferences for check-ins, diagnostics, and clock management.
 */
public class RadioResolvePreference extends Preference
{
    public static final int DEFAULT_CHECK_IN_INTERVAL_SECONDS = 60;
    public static final int DEFAULT_CLOCK_WARN_OFFSET_MILLISECONDS = 500;
    public static final int DEFAULT_CLOCK_BLOCK_OFFSET_MILLISECONDS = 5000;

    private static final String PREFERENCE_KEY_CHECK_IN_ENABLED = "radioresolve.checkin.enabled";
    private static final String PREFERENCE_KEY_CHECK_IN_INTERVAL_SECONDS = "radioresolve.checkin.interval.seconds";
    private static final String PREFERENCE_KEY_TELEMETRY_ENABLED = "radioresolve.telemetry.enabled";
    private static final String PREFERENCE_KEY_REMOTE_COMMANDS_ENABLED = "radioresolve.remote.commands.enabled";
    private static final String PREFERENCE_KEY_CLOCK_WARN_OFFSET_MS = "radioresolve.clock.warn.offset.ms";
    private static final String PREFERENCE_KEY_CLOCK_BLOCK_OFFSET_MS = "radioresolve.clock.block.offset.ms";

    private final Preferences mPreferences = Preferences.userNodeForPackage(RadioResolvePreference.class);

    private Boolean mCheckInEnabled;
    private Integer mCheckInIntervalSeconds;
    private Boolean mTelemetryEnabled;
    private Boolean mRemoteCommandsEnabled;
    private Integer mClockWarnOffsetMilliseconds;
    private Integer mClockBlockOffsetMilliseconds;

    /**
     * Constructs an instance.
     * @param updateListener listener for preference changes
     */
    public RadioResolvePreference(Listener<PreferenceType> updateListener)
    {
        super(updateListener);
    }

    @Override
    public PreferenceType getPreferenceType()
    {
        return PreferenceType.RADIO_RESOLVE;
    }

    public boolean isCheckInEnabled()
    {
        if(mCheckInEnabled == null)
        {
            mCheckInEnabled = mPreferences.getBoolean(PREFERENCE_KEY_CHECK_IN_ENABLED, false);
        }

        return mCheckInEnabled;
    }

    public void setCheckInEnabled(boolean enabled)
    {
        mCheckInEnabled = enabled;
        mPreferences.putBoolean(PREFERENCE_KEY_CHECK_IN_ENABLED, enabled);
        notifyPreferenceUpdated();
    }

    public int getCheckInIntervalSeconds()
    {
        if(mCheckInIntervalSeconds == null)
        {
            mCheckInIntervalSeconds = mPreferences.getInt(PREFERENCE_KEY_CHECK_IN_INTERVAL_SECONDS,
                DEFAULT_CHECK_IN_INTERVAL_SECONDS);
        }

        return Math.max(15, mCheckInIntervalSeconds);
    }

    public void setCheckInIntervalSeconds(int seconds)
    {
        mCheckInIntervalSeconds = Math.max(15, seconds);
        mPreferences.putInt(PREFERENCE_KEY_CHECK_IN_INTERVAL_SECONDS, mCheckInIntervalSeconds);
        notifyPreferenceUpdated();
    }

    public boolean isTelemetryEnabled()
    {
        if(mTelemetryEnabled == null)
        {
            mTelemetryEnabled = mPreferences.getBoolean(PREFERENCE_KEY_TELEMETRY_ENABLED, false);
        }

        return mTelemetryEnabled;
    }

    public void setTelemetryEnabled(boolean enabled)
    {
        mTelemetryEnabled = enabled;
        mPreferences.putBoolean(PREFERENCE_KEY_TELEMETRY_ENABLED, enabled);
        notifyPreferenceUpdated();
    }

    public boolean isRemoteCommandsEnabled()
    {
        if(mRemoteCommandsEnabled == null)
        {
            mRemoteCommandsEnabled = mPreferences.getBoolean(PREFERENCE_KEY_REMOTE_COMMANDS_ENABLED, false);
        }

        return mRemoteCommandsEnabled;
    }

    public void setRemoteCommandsEnabled(boolean enabled)
    {
        mRemoteCommandsEnabled = enabled;
        mPreferences.putBoolean(PREFERENCE_KEY_REMOTE_COMMANDS_ENABLED, enabled);
        notifyPreferenceUpdated();
    }

    public int getClockWarnOffsetMilliseconds()
    {
        if(mClockWarnOffsetMilliseconds == null)
        {
            mClockWarnOffsetMilliseconds = mPreferences.getInt(PREFERENCE_KEY_CLOCK_WARN_OFFSET_MS,
                DEFAULT_CLOCK_WARN_OFFSET_MILLISECONDS);
        }

        return Math.max(0, mClockWarnOffsetMilliseconds);
    }

    public void setClockWarnOffsetMilliseconds(int milliseconds)
    {
        mClockWarnOffsetMilliseconds = Math.max(0, milliseconds);
        mPreferences.putInt(PREFERENCE_KEY_CLOCK_WARN_OFFSET_MS, mClockWarnOffsetMilliseconds);
        notifyPreferenceUpdated();
    }

    public int getClockBlockOffsetMilliseconds()
    {
        if(mClockBlockOffsetMilliseconds == null)
        {
            mClockBlockOffsetMilliseconds = mPreferences.getInt(PREFERENCE_KEY_CLOCK_BLOCK_OFFSET_MS,
                DEFAULT_CLOCK_BLOCK_OFFSET_MILLISECONDS);
        }

        return Math.max(0, mClockBlockOffsetMilliseconds);
    }

    public void setClockBlockOffsetMilliseconds(int milliseconds)
    {
        mClockBlockOffsetMilliseconds = Math.max(0, milliseconds);
        mPreferences.putInt(PREFERENCE_KEY_CLOCK_BLOCK_OFFSET_MS, mClockBlockOffsetMilliseconds);
        notifyPreferenceUpdated();
    }
}
