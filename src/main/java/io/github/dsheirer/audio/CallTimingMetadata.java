/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
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

package io.github.dsheirer.audio;

/**
 * Production call timing metadata for audio recordings.
 */
public class CallTimingMetadata
{
    public static final String SOURCE_RECEIVER_P25_CONTROL_GRANT = "receiver_p25_control_grant";
    public static final String SOURCE_RECEIVER_FIRST_AUDIO_BUFFER = "receiver_first_audio_buffer";
    public static final String QUALITY_LOCKED = "locked";
    public static final String QUALITY_PRACTICAL = "practical";
    public static final String QUALITY_UNUSABLE = "unusable";
    public static final String QUALITY_UNAVAILABLE = "unavailable";

    private long mCallStartTimestamp;
    private String mCallStartSource;
    private Long mP25SystemTimeEstimateTimestamp;
    private String mP25SystemTimeQuality;

    /**
     * Constructs an instance.
     */
    public CallTimingMetadata(long callStartTimestamp, String callStartSource, Long p25SystemTimeEstimateTimestamp,
                              String p25SystemTimeQuality)
    {
        mCallStartTimestamp = callStartTimestamp;
        mCallStartSource = callStartSource;
        mP25SystemTimeEstimateTimestamp = p25SystemTimeEstimateTimestamp;
        mP25SystemTimeQuality = p25SystemTimeQuality;
    }

    /**
     * Creates timing metadata using the receiver-local P25 control channel grant timestamp.
     */
    public static CallTimingMetadata receiverP25ControlGrant(long callStartTimestamp,
                                                            Long p25SystemTimeEstimateTimestamp,
                                                            String p25SystemTimeQuality)
    {
        return new CallTimingMetadata(callStartTimestamp, SOURCE_RECEIVER_P25_CONTROL_GRANT,
            p25SystemTimeEstimateTimestamp, p25SystemTimeQuality);
    }

    /**
     * Creates fallback timing metadata using the receiver-local first audio buffer timestamp.
     */
    public static CallTimingMetadata receiverFirstAudioBuffer(long callStartTimestamp)
    {
        return new CallTimingMetadata(callStartTimestamp, SOURCE_RECEIVER_FIRST_AUDIO_BUFFER, null,
            QUALITY_UNAVAILABLE);
    }

    public long getCallStartTimestamp()
    {
        return mCallStartTimestamp;
    }

    public String getCallStartSource()
    {
        return mCallStartSource;
    }

    public Long getP25SystemTimeEstimateTimestamp()
    {
        return mP25SystemTimeEstimateTimestamp;
    }

    public String getP25SystemTimeQuality()
    {
        return mP25SystemTimeQuality;
    }

    public boolean isReceiverP25ControlGrant()
    {
        return SOURCE_RECEIVER_P25_CONTROL_GRANT.equals(mCallStartSource);
    }
}
