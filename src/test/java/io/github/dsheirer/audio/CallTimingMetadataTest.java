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
package io.github.dsheirer.audio;

import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.identifier.IdentifierCollection;
import io.github.dsheirer.record.wave.AudioMetadata;
import io.github.dsheirer.record.wave.AudioMetadataUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CallTimingMetadataTest
{
    @Test
    void p25ControlGrantTimingTakesPrecedenceOverFirstAudioFallback()
    {
        AudioSegment audioSegment = new AudioSegment(new AliasList("test"), 0);
        audioSegment.setCallTimingMetadata(CallTimingMetadata.receiverP25ControlGrant(1781288785833L,
            1781288785901L, CallTimingMetadata.QUALITY_PRACTICAL));
        audioSegment.addAudio(new float[2]);

        assertEquals(1781288785833L, audioSegment.getCallStartTimestamp());
        assertEquals(CallTimingMetadata.SOURCE_RECEIVER_P25_CONTROL_GRANT,
            audioSegment.getCallTimingMetadata().getCallStartSource());
        assertEquals(1781288785901L, audioSegment.getCallTimingMetadata().getP25SystemTimeEstimateTimestamp());
        assertEquals(CallTimingMetadata.QUALITY_PRACTICAL,
            audioSegment.getCallTimingMetadata().getP25SystemTimeQuality());
    }

    @Test
    void firstAudioBufferProvidesFallbackTiming()
    {
        AudioSegment audioSegment = new AudioSegment(new AliasList("test"), 0);
        audioSegment.addAudio(new float[2]);

        assertEquals(audioSegment.getStartTimestamp(), audioSegment.getCallStartTimestamp());
        assertEquals(CallTimingMetadata.SOURCE_RECEIVER_FIRST_AUDIO_BUFFER,
            audioSegment.getCallTimingMetadata().getCallStartSource());
        assertEquals(CallTimingMetadata.QUALITY_UNAVAILABLE,
            audioSegment.getCallTimingMetadata().getP25SystemTimeQuality());
    }

    @Test
    void metadataUsesCallStartAndIncludesCompactProductionTimingFields()
    {
        CallTimingMetadata callTimingMetadata = CallTimingMetadata.receiverP25ControlGrant(1781288785833L,
            null, CallTimingMetadata.QUALITY_UNAVAILABLE);

        Map<AudioMetadata,String> metadata = AudioMetadataUtils.getMetadataMap(new IdentifierCollection(),
            new AliasList("test"), callTimingMetadata);

        assertEquals(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(1781288785833L)),
            metadata.get(AudioMetadata.DATE_CREATED));

        String comments = metadata.get(AudioMetadata.COMMENTS);
        assertTrue(comments.contains("call_start_ms:1781288785833;"));
        assertTrue(comments.contains("call_start_source:receiver_p25_control_grant;"));
        assertTrue(comments.contains("p25_system_time_estimate_ms:null;"));
        assertTrue(comments.contains("p25_system_time_quality:unavailable;"));
    }
}
