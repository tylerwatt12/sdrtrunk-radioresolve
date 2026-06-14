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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveConfiguration;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.ProcessingChain;
import io.github.dsheirer.module.decode.p25.telemetry.P25NetworkConfigurationSnapshot;
import io.github.dsheirer.module.decode.p25.telemetry.P25NetworkConfigurationSnapshotProvider;
import io.github.dsheirer.playlist.PlaylistManager;
import io.github.dsheirer.preference.radioresolve.RadioResolvePreference;
import io.github.dsheirer.util.ThreadPool;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional RadioResolve RF/system telemetry service.
 */
public class RadioResolveTelemetryService
{
    private static final Logger mLog = LoggerFactory.getLogger(RadioResolveTelemetryService.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String RF_STATE_PATH = "/api/node/rf-state";
    private static final long SCAN_INTERVAL_SECONDS = 10;
    private static final long MINIMUM_SEND_INTERVAL_MILLISECONDS = TimeUnit.SECONDS.toMillis(30);
    private static final Gson GSON = new Gson();

    private final RadioResolvePreference mPreference;
    private final Supplier<Optional<RadioResolveConfiguration>> mConfigurationSupplier;
    private final PlaylistManager mPlaylistManager;
    private final HttpClient mHttpClient;
    private final Map<Channel,TelemetryState> mStateByChannel = new HashMap<>();
    private final AtomicBoolean mScanRunning = new AtomicBoolean();
    private ScheduledFuture<?> mScanFuture;

    /**
     * Constructs an instance.
     * @param preference RadioResolve preference
     * @param configurationSupplier active RadioResolve stream configuration supplier
     * @param playlistManager playlist manager
     */
    public RadioResolveTelemetryService(RadioResolvePreference preference,
                                        Supplier<Optional<RadioResolveConfiguration>> configurationSupplier,
                                        PlaylistManager playlistManager)
    {
        mPreference = preference;
        mConfigurationSupplier = configurationSupplier;
        mPlaylistManager = playlistManager;
        mHttpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    }

    /**
     * Starts the background telemetry scanner.
     */
    public void start()
    {
        if(mScanFuture == null || mScanFuture.isCancelled())
        {
            mScanFuture = ThreadPool.SCHEDULED.scheduleAtFixedRate(this::scan, 5, SCAN_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        }
    }

    /**
     * Stops the background telemetry scanner.
     */
    public void stop()
    {
        if(mScanFuture != null)
        {
            mScanFuture.cancel(true);
            mScanFuture = null;
        }

        synchronized(mStateByChannel)
        {
            mStateByChannel.clear();
        }
    }

    private void scan()
    {
        if(!mScanRunning.compareAndSet(false, true))
        {
            return;
        }

        try
        {
            Optional<RadioResolveConfiguration> optionalConfiguration = getConfiguration();

            if(!mPreference.isTelemetryEnabled() || optionalConfiguration.isEmpty())
            {
                return;
            }

            RadioResolveConfiguration configuration = optionalConfiguration.get();

            for(Channel channel: mPlaylistManager.getChannelModel().getChannels())
            {
                if(isEligible(channel, configuration))
                {
                    ProcessingChain processingChain =
                        mPlaylistManager.getChannelProcessingManager().getProcessingChain(channel);

                    if(processingChain != null && processingChain.isProcessing())
                    {
                        process(channel, processingChain, configuration);
                    }
                }
            }
        }
        catch(Exception e)
        {
            mLog.warn("RadioResolve RF telemetry scan failed: {}", e.getMessage());
        }
        finally
        {
            mScanRunning.set(false);
        }
    }

    private void process(Channel channel, ProcessingChain processingChain, RadioResolveConfiguration configuration)
    {
        for(Object decoderState: processingChain.getDecoderStates())
        {
            if(decoderState instanceof P25NetworkConfigurationSnapshotProvider provider)
            {
                P25NetworkConfigurationSnapshot snapshot = provider.getP25NetworkConfigurationSnapshot();

                if(snapshot != null && snapshot.isUseful())
                {
                    sendIfChanged(channel, snapshot, configuration);
                }
            }
        }
    }

    private void sendIfChanged(Channel channel, P25NetworkConfigurationSnapshot snapshot,
                               RadioResolveConfiguration configuration)
    {
        String hash = hash(snapshot);
        long now = System.currentTimeMillis();

        synchronized(mStateByChannel)
        {
            TelemetryState state = mStateByChannel.computeIfAbsent(channel, key -> new TelemetryState());

            if(hash.equals(state.mLastSentHash))
            {
                return;
            }

            if(now - state.mLastAttemptEpochMilliseconds < MINIMUM_SEND_INTERVAL_MILLISECONDS)
            {
                return;
            }

            state.mLastAttemptEpochMilliseconds = now;

            if(upload(channel, snapshot, hash, configuration, now))
            {
                state.mLastSentHash = hash;
            }
        }
    }

    private boolean upload(Channel channel, P25NetworkConfigurationSnapshot snapshot, String hash,
                           RadioResolveConfiguration configuration, long observedAt)
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(RadioResolveNodeService.resolveEndpoint(configuration.getHost(), RF_STATE_PATH))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + configuration.getApiKey())
                .header("Content-Type", "application/json")
                .header("User-Agent", "sdrtrunk")
                .POST(HttpRequest.BodyPublishers.ofString(createPayload(channel, snapshot, hash, configuration,
                    observedAt).toString()))
                .build();

            HttpResponse<String> response = mHttpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() >= 200 && response.statusCode() <= 299)
            {
                return true;
            }
            else if(response.statusCode() == 401 || response.statusCode() == 403)
            {
                mLog.warn("RadioResolve RF telemetry rejected: invalid API key");
            }
            else
            {
                mLog.warn("RadioResolve RF telemetry rejected: HTTP {}", response.statusCode());
            }
        }
        catch(Exception e)
        {
            mLog.warn("RadioResolve RF telemetry failed: {}", e.getMessage());
        }

        return false;
    }

    private JsonObject createPayload(Channel channel, P25NetworkConfigurationSnapshot snapshot, String hash,
                                     RadioResolveConfiguration configuration, long observedAt)
    {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("agentVersion", RadioResolveNodeService.AGENT_VERSION);
        root.addProperty("observedAtEpochMilliseconds", observedAt);
        root.addProperty("nodeName", getNodeName(configuration));
        root.addProperty("timezone", getNodeTimezone(configuration));
        root.addProperty("decoder", snapshot.decoder());
        root.addProperty("summaryHash", hash);

        JsonObject channelObject = new JsonObject();
        channelObject.addProperty("name", channel.getName());
        channelObject.addProperty("aliasList", channel.getAliasListName());
        root.add("channel", channelObject);

        root.add("network", GSON.toJsonTree(snapshot.network()));
        root.add("currentSite", GSON.toJsonTree(snapshot.currentSite()));
        root.add("channels", GSON.toJsonTree(snapshot.channels()));
        root.add("neighborSites", GSON.toJsonTree(snapshot.neighborSites()));
        root.add("frequencyBands", GSON.toJsonTree(snapshot.frequencyBands()));
        root.add("patchGroups", GSON.toJsonTree(snapshot.patchGroups()));
        root.add("talkerAliases", GSON.toJsonTree(snapshot.talkerAliases()));
        return root;
    }

    static String hash(P25NetworkConfigurationSnapshot snapshot)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();

            for(byte b: hash)
            {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        }
        catch(Exception e)
        {
            throw new IllegalStateException("Could not hash RadioResolve RF telemetry snapshot", e);
        }
    }

    private boolean isEligible(Channel channel, RadioResolveConfiguration configuration)
    {
        if(channel == null || configuration == null || !configuration.isEnabled())
        {
            return false;
        }

        String streamName = configuration.getName();
        String aliasListName = channel.getAliasListName();

        if(streamName == null || streamName.isBlank() || aliasListName == null || aliasListName.isBlank())
        {
            return false;
        }

        for(Alias alias: mPlaylistManager.getAliasModel().aliasList())
        {
            if(alias.hasList() && aliasListName.equalsIgnoreCase(alias.getAliasListName()) &&
                alias.hasBroadcastChannel(streamName))
            {
                return true;
            }
        }

        return false;
    }

    private Optional<RadioResolveConfiguration> getConfiguration()
    {
        if(mConfigurationSupplier == null)
        {
            return Optional.empty();
        }

        return mConfigurationSupplier.get()
            .filter(configuration -> configuration.getHost() != null && !configuration.getHost().isBlank())
            .filter(configuration -> configuration.getApiKey() != null && !configuration.getApiKey().isBlank());
    }

    private static String getNodeName(RadioResolveConfiguration configuration)
    {
        String nodeName = configuration.getNodeName();
        return nodeName != null && !nodeName.isBlank() ? nodeName : RadioResolveConfiguration.getDefaultNodeName();
    }

    private static String getNodeTimezone(RadioResolveConfiguration configuration)
    {
        String timezone = configuration.getNodeTimezone();
        return timezone != null && !timezone.isBlank() ? timezone : RadioResolveConfiguration.getDefaultNodeTimezone();
    }

    private static class TelemetryState
    {
        private String mLastSentHash;
        private long mLastAttemptEpochMilliseconds;
    }
}
