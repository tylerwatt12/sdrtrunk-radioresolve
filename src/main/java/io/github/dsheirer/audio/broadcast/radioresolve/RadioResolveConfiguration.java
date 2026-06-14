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

package io.github.dsheirer.audio.broadcast.radioresolve;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.audio.broadcast.BroadcastConfiguration;
import io.github.dsheirer.audio.broadcast.BroadcastFormat;
import io.github.dsheirer.audio.broadcast.BroadcastServerType;
import java.net.InetAddress;
import java.time.ZoneId;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Streaming configuration for RadioResolve completed call uploads.
 *
 * Note: this API is not a continuous streaming audio service; it receives completed MP3 call recordings through the
 * existing sdrtrunk audio streaming subsystem.
 */
public class RadioResolveConfiguration extends BroadcastConfiguration
{
    public static final String PRODUCTION_ENDPOINT = "https://calls.radioresolve.com";

    private StringProperty mApiKey = new SimpleStringProperty();
    private StringProperty mNodeName = new SimpleStringProperty(getDefaultNodeName());
    private StringProperty mNodeTimezone = new SimpleStringProperty(getDefaultNodeTimezone());

    /**
     * Constructor for faster jackson.
     */
    public RadioResolveConfiguration()
    {
        this(BroadcastFormat.MP3);
    }

    /**
     * Public constructor.
     * @param format to use for audio recording (MP3)
     */
    public RadioResolveConfiguration(BroadcastFormat format)
    {
        super(format);

        if(getHost() == null || getHost().isEmpty())
        {
            setHost(PRODUCTION_ENDPOINT);
        }

        mValid.unbind();
        mValid.bind(Bindings.and(Bindings.isNotEmpty(mHost), Bindings.isNotEmpty(mApiKey)));
    }

    /**
     * API key as a property.
     */
    public StringProperty apiKeyProperty()
    {
        return mApiKey;
    }

    /**
     * Node name as a property.
     */
    public StringProperty nodeNameProperty()
    {
        return mNodeName;
    }

    /**
     * Node timezone as a property.
     */
    public StringProperty nodeTimezoneProperty()
    {
        return mNodeTimezone;
    }

    /**
     * API key.
     */
    @JacksonXmlProperty(isAttribute = true, localName = "api_key")
    public String getApiKey()
    {
        return mApiKey.get();
    }

    /**
     * Sets the API key.
     */
    public void setApiKey(String apiKey)
    {
        mApiKey.set(apiKey);
    }

    /**
     * Optional node name.  Defaults to the local hostname.
     */
    @JacksonXmlProperty(isAttribute = true, localName = "node_name")
    public String getNodeName()
    {
        return mNodeName.get();
    }

    /**
     * Sets the node name.
     */
    public void setNodeName(String nodeName)
    {
        mNodeName.set(nodeName);
    }

    /**
     * Optional node timezone.  Defaults to the system timezone.
     */
    @JacksonXmlProperty(isAttribute = true, localName = "node_timezone")
    public String getNodeTimezone()
    {
        return mNodeTimezone.get();
    }

    /**
     * Sets the node timezone.
     */
    public void setNodeTimezone(String nodeTimezone)
    {
        mNodeTimezone.set(nodeTimezone);
    }

    @JacksonXmlProperty(isAttribute = true, localName = "type", namespace = "http://www.w3.org/2001/XMLSchema-instance")
    @Override
    public BroadcastServerType getBroadcastServerType()
    {
        return BroadcastServerType.RADIORESOLVE;
    }

    @Override
    public BroadcastConfiguration copyOf()
    {
        RadioResolveConfiguration copy = new RadioResolveConfiguration();
        copy.setName(getName());
        copy.setHost(getHost());
        copy.setApiKey(getApiKey());
        copy.setNodeName(getNodeName());
        copy.setNodeTimezone(getNodeTimezone());
        copy.setMaximumRecordingAge(getMaximumRecordingAge());
        copy.setDelay(getDelay());
        copy.setEnabled(isEnabled());
        return copy;
    }

    /**
     * Default node name.
     */
    public static String getDefaultNodeName()
    {
        try
        {
            String hostName = InetAddress.getLocalHost().getHostName();

            if(hostName != null && !hostName.isBlank())
            {
                return hostName;
            }
        }
        catch(Exception e)
        {
            //Use fallback below.
        }

        return "sdrtrunk";
    }

    /**
     * Default node timezone.
     */
    public static String getDefaultNodeTimezone()
    {
        return ZoneId.systemDefault().getId();
    }
}
