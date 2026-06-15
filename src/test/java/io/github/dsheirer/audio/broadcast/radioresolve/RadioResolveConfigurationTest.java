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

package io.github.dsheirer.audio.broadcast.radioresolve;

import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.dsheirer.audio.broadcast.BroadcastConfiguration;
import io.github.dsheirer.playlist.PlaylistV2;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RadioResolveConfigurationTest
{
    @Test
    void defaultsAndValidity()
    {
        RadioResolveConfiguration configuration = new RadioResolveConfiguration();

        assertEquals(RadioResolveConfiguration.PRODUCTION_ENDPOINT, configuration.getHost());
        assertNotNull(configuration.getNodeName());
        assertNotNull(configuration.getNodeTimezone());
        assertFalse(configuration.isIgnoreCertificateErrors());
        assertFalse(configuration.isValid());

        configuration.setApiKey("test-key");
        assertTrue(configuration.isValid());
    }

    @Test
    void copyIncludesRadioResolveFields()
    {
        RadioResolveConfiguration configuration = new RadioResolveConfiguration();
        configuration.setName("RadioResolve");
        configuration.setHost("https://calls.example.com:8443");
        configuration.setApiKey("test-key");
        configuration.setNodeName("node-a");
        configuration.setNodeTimezone("America/New_York");
        configuration.setIgnoreCertificateErrors(true);
        configuration.setMaximumRecordingAge(123000);
        configuration.setDelay(1000);
        configuration.setEnabled(true);

        RadioResolveConfiguration copy = (RadioResolveConfiguration)configuration.copyOf();

        assertEquals(configuration.getName(), copy.getName());
        assertEquals(configuration.getHost(), copy.getHost());
        assertEquals(configuration.getApiKey(), copy.getApiKey());
        assertEquals(configuration.getNodeName(), copy.getNodeName());
        assertEquals(configuration.getNodeTimezone(), copy.getNodeTimezone());
        assertEquals(configuration.isIgnoreCertificateErrors(), copy.isIgnoreCertificateErrors());
        assertEquals(configuration.getMaximumRecordingAge(), copy.getMaximumRecordingAge());
        assertEquals(configuration.getDelay(), copy.getDelay());
        assertEquals(configuration.isEnabled(), copy.isEnabled());
    }

    @Test
    void hostAllowsExplicitPort()
    {
        RadioResolveConfiguration configuration = new RadioResolveConfiguration();

        configuration.setHost("https://calls.example.com:8443/");
        assertEquals("https://calls.example.com:8443", configuration.getHost());
        assertEquals("https://calls.example.com:8443/api/node/upload-call",
            RadioResolveBroadcaster.createUri(configuration.getHost(), RadioResolveBroadcaster.UPLOAD_PATH).toString());
    }

    @Test
    void hostDefaultsToHttpsWhenSchemeOmitted()
    {
        RadioResolveConfiguration configuration = new RadioResolveConfiguration();

        configuration.setHost("calls.example.com:9443");
        assertEquals("https://calls.example.com:9443", configuration.getHost());
        assertEquals("https://calls.example.com:9443/api/node/test",
            RadioResolveBroadcaster.createUri(configuration.getHost(), RadioResolveBroadcaster.TEST_PATH).toString());
    }

    @Test
    void playlistXmlRoundTrip()
        throws Exception
    {
        RadioResolveConfiguration configuration = new RadioResolveConfiguration();
        configuration.setName("RadioResolve");
        configuration.setHost("https://calls.example.com:8443");
        configuration.setApiKey("test-key");
        configuration.setNodeName("node-a");
        configuration.setNodeTimezone("America/New_York");
        configuration.setIgnoreCertificateErrors(true);

        PlaylistV2 playlist = new PlaylistV2();
        List<BroadcastConfiguration> configurations = new ArrayList<>();
        configurations.add(configuration);
        playlist.setBroadcastConfigurations(configurations);

        JacksonXmlModule xmlModule = new JacksonXmlModule();
        xmlModule.setDefaultUseWrapper(false);
        XmlMapper mapper = new XmlMapper(xmlModule);

        PlaylistV2 restored = mapper.readValue(mapper.writeValueAsString(playlist), PlaylistV2.class);

        assertEquals(1, restored.getBroadcastConfigurations().size());
        BroadcastConfiguration restoredConfiguration = restored.getBroadcastConfigurations().get(0);
        assertInstanceOf(RadioResolveConfiguration.class, restoredConfiguration);

        RadioResolveConfiguration restoredRadioResolve = (RadioResolveConfiguration)restoredConfiguration;
        assertEquals("https://calls.example.com:8443", restoredRadioResolve.getHost());
        assertEquals("test-key", restoredRadioResolve.getApiKey());
        assertEquals("node-a", restoredRadioResolve.getNodeName());
        assertEquals("America/New_York", restoredRadioResolve.getNodeTimezone());
        assertTrue(restoredRadioResolve.isIgnoreCertificateErrors());
    }
}
