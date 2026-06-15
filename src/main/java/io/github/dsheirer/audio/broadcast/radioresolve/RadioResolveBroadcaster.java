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

import com.google.common.net.HttpHeaders;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.audio.broadcast.AbstractAudioBroadcaster;
import io.github.dsheirer.audio.broadcast.AudioRecording;
import io.github.dsheirer.audio.broadcast.BroadcastEvent;
import io.github.dsheirer.audio.broadcast.BroadcastState;
import io.github.dsheirer.audio.convert.InputAudioFormat;
import io.github.dsheirer.audio.convert.MP3Setting;
import io.github.dsheirer.gui.playlist.radioreference.RadioReferenceDecoder;
import io.github.dsheirer.identifier.Form;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierClass;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.alias.TalkerAliasIdentifier;
import io.github.dsheirer.identifier.configuration.ConfigurationLongIdentifier;
import io.github.dsheirer.identifier.patch.PatchGroup;
import io.github.dsheirer.identifier.patch.PatchGroupIdentifier;
import io.github.dsheirer.identifier.radio.RadioIdentifier;
import io.github.dsheirer.identifier.talkgroup.TalkgroupIdentifier;
import io.github.dsheirer.util.ThreadPool;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio broadcaster to push completed MP3 call recordings to the RadioResolve node upload API.
 */
public class RadioResolveBroadcaster extends AbstractAudioBroadcaster<RadioResolveConfiguration>
{
    private final static Logger mLog = LoggerFactory.getLogger(RadioResolveBroadcaster.class);

    public static final String UPLOAD_PATH = "/api/node/upload-call";
    public static final String TEST_PATH = "/api/node/test";
    public static final String AGENT_VERSION = "sdrtrunk-radioresolve";
    public static final String RESULT_OK = "OK";
    public static final String RESULT_INVALID_API_KEY = "Invalid API Key";
    public static final String RESULT_NO_SERVER = "No Response";
    public static final String RESULT_ERROR = "Error";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";
    private static final int MAX_QUEUED_RECORDINGS = 500;
    private static final int MAX_IN_FLIGHT_UPLOADS = 4;
    private static final long[] RETRY_BACKOFF_MS = {5000, 15000, 30000, 60000, 120000};

    private final Object mQueueLock = new Object();
    private Deque<PendingUpload> mAudioRecordingQueue = new ArrayDeque<>();
    private ScheduledFuture<?> mAudioRecordingProcessorFuture;
    private HttpClient mHttpClient;
    private AtomicInteger mInFlightUploads = new AtomicInteger();
    private AtomicInteger mConsecutiveUploadFailures = new AtomicInteger();
    private long mLastConnectionAttempt;
    private long mConnectionAttemptInterval = 5000;
    private AliasModel mAliasModel;
    private volatile boolean mRunning;
    private volatile boolean mServerReachable;

    /**
     * Constructs an instance.
     */
    public RadioResolveBroadcaster(RadioResolveConfiguration config, InputAudioFormat inputAudioFormat,
                                   MP3Setting mp3Setting, AliasModel aliasModel)
    {
        super(config);
        mAliasModel = aliasModel;
        mHttpClient = createHttpClient(config);
    }

    /**
     * Starts the audio recording processor thread.
     */
    @Override
    public void start()
    {
        mRunning = true;
        setBroadcastState(BroadcastState.CONNECTING);
        mServerReachable = updateConnectionState(testConnectionDetailed(getBroadcastConfiguration()), "connecting to",
            true);
        mLastConnectionAttempt = System.currentTimeMillis();

        if(mAudioRecordingProcessorFuture == null)
        {
            mAudioRecordingProcessorFuture = ThreadPool.SCHEDULED.scheduleAtFixedRate(new AudioRecordingProcessor(),
                0, 500, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the audio recording processor thread.
     */
    @Override
    public void stop()
    {
        mRunning = false;

        if(mAudioRecordingProcessorFuture != null)
        {
            mAudioRecordingProcessorFuture.cancel(true);
            mAudioRecordingProcessorFuture = null;
            dispose();
            setBroadcastState(BroadcastState.DISCONNECTED);
        }
    }

    /**
     * Prepares for disposal.
     */
    @Override
    public void dispose()
    {
        PendingUpload pendingUpload;

        synchronized(mQueueLock)
        {
            pendingUpload = mAudioRecordingQueue.poll();
        }

        while(pendingUpload != null)
        {
            pendingUpload.getAudioRecording().removePendingReplay();

            synchronized(mQueueLock)
            {
                pendingUpload = mAudioRecordingQueue.poll();
            }
        }
    }

    @Override
    public int getAudioQueueSize()
    {
        synchronized(mQueueLock)
        {
            return mAudioRecordingQueue.size();
        }
    }

    @Override
    public void receive(AudioRecording audioRecording)
    {
        synchronized(mQueueLock)
        {
            mAudioRecordingQueue.offer(new PendingUpload(audioRecording));
            ageOffOverflowRecordings();
        }

        broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_QUEUE_CHANGE));
    }

    /**
     * Indicates if the audio recording is non-null and not too old.
     */
    private boolean isValid(AudioRecording audioRecording)
    {
        return audioRecording != null && System.currentTimeMillis() - audioRecording.getStartTime() <=
            getBroadcastConfiguration().getMaximumRecordingAge();
    }

    /**
     * Indicates if the broadcaster is connected, attempting a reconnect when appropriate.
     */
    private boolean connected()
    {
        if(getBroadcastState() == BroadcastState.INVALID_CREDENTIALS)
        {
            return false;
        }

        if(getBroadcastState() != BroadcastState.CONNECTED &&
            (System.currentTimeMillis() - mLastConnectionAttempt > mConnectionAttemptInterval))
        {
            if(!hasRecentUploadFailure())
            {
                setBroadcastState(BroadcastState.CONNECTING);
            }

            mServerReachable = updateConnectionState(testConnectionDetailed(getBroadcastConfiguration()),
                "reconnecting to", !hasRecentUploadFailure());
            mLastConnectionAttempt = System.currentTimeMillis();
        }

        return mServerReachable;
    }

    /**
     * Updates broadcaster state from a connection test result.
     */
    private boolean updateConnectionState(TestResult result, String action, boolean allowConnectedState)
    {
        if(result.success())
        {
            if(allowConnectedState)
            {
                setBroadcastState(BroadcastState.CONNECTED);
            }

            return true;
        }
        else if(RESULT_INVALID_API_KEY.equals(result.message()))
        {
            setBroadcastState(BroadcastState.INVALID_CREDENTIALS);
            mLog.error("Error " + action + " RadioResolve server [invalid API key]");
        }
        else if(RESULT_NO_SERVER.equals(result.message()))
        {
            setBroadcastState(BroadcastState.NO_SERVER);
            mLog.error("Error " + action + " RadioResolve server [server not found or not reachable]");
        }
        else
        {
            setBroadcastState(BroadcastState.ERROR);
            mLog.error("Error " + action + " RadioResolve server [" + result.message() + "]");
        }

        return false;
    }

    /**
     * Processes enqueued audio recordings.  Uploads are asynchronous, matching the other completed-call broadcasters.
     */
    private void processRecordingQueue()
    {
        ageOffInvalidRecordings();

        while(connected() && mInFlightUploads.get() < MAX_IN_FLIGHT_UPLOADS)
        {
            final PendingUpload pendingUpload = getNextReadyUpload();

            if(pendingUpload == null)
            {
                return;
            }

            final AudioRecording audioRecording = pendingUpload.getAudioRecording();

            if(isValid(audioRecording) && audioRecording.getRecordingLength() > 0)
            {
                try
                {
                    HttpRequest fileRequest = createUploadRequest(getBroadcastConfiguration(), audioRecording, mAliasModel);
                    mInFlightUploads.incrementAndGet();

                    mHttpClient.sendAsync(fileRequest, HttpResponse.BodyHandlers.ofString())
                        .whenComplete((fileResponse, throwable) -> {
                            try
                            {
                                handleUploadResponse(pendingUpload, fileResponse, throwable);
                            }
                            finally
                            {
                                mInFlightUploads.decrementAndGet();
                            }
                        });
                }
                catch(FileNotFoundException fnfe)
                {
                    mLog.error("RadioResolve upload file not found [" + audioRecording.getPath() + "]");
                    incrementErrorAudioCount();
                    broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
                    audioRecording.removePendingReplay();
                }
                catch(Exception e)
                {
                    mLog.error("RadioResolve upload request failed [" + safeMessage(e) + "]");
                    recordUploadFailure(safeMessage(e));
                    incrementErrorAudioCount();
                    broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
                    retryOrRemove(pendingUpload, safeMessage(e));
                }
            }
            else if(audioRecording != null)
            {
                audioRecording.removePendingReplay();
            }
        }
    }

    /**
     * Handles an upload response.
     */
    private void handleUploadResponse(PendingUpload pendingUpload, HttpResponse<String> fileResponse, Throwable throwable)
    {
        if(throwable != null)
        {
            recordUploadFailure("temporary upload failure");
            incrementErrorAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
            retryOrRemove(pendingUpload, "temporary upload failure");
            return;
        }

        int statusCode = fileResponse.statusCode();

        if(statusCode >= 200 && statusCode < 300)
        {
            recordUploadSuccess();
            incrementStreamedAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_STREAMED_COUNT_CHANGE));
            pendingUpload.getAudioRecording().removePendingReplay();
        }
        else if(statusCode == 401 || statusCode == 403)
        {
            recordUploadFailure("invalid API key or access denied");
            setBroadcastState(BroadcastState.INVALID_CREDENTIALS);
            incrementErrorAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
            mLog.error("RadioResolve upload rejected [invalid API key or access denied]");
            pendingUpload.getAudioRecording().removePendingReplay();
        }
        else if(isRetryableStatus(statusCode))
        {
            recordUploadFailure("HTTP " + statusCode);
            incrementErrorAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
            mLog.error("RadioResolve upload failed [status " + statusCode + "]");
            retryOrRemove(pendingUpload, "status " + statusCode);
        }
        else
        {
            recordUploadFailure("HTTP " + statusCode);
            incrementErrorAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
            mLog.error("RadioResolve upload failed [status " + statusCode + "]");
            pendingUpload.getAudioRecording().removePendingReplay();
        }
    }

    private void recordUploadSuccess()
    {
        RadioResolveConfiguration configuration = getBroadcastConfiguration();
        configuration.setLastSuccessfulUploadEpochMilliseconds(System.currentTimeMillis());
        configuration.setLastUploadFailureMessage(null);
        mConsecutiveUploadFailures.set(0);
        mServerReachable = true;
        setBroadcastState(BroadcastState.CONNECTED);
    }

    private void recordUploadFailure(String message)
    {
        RadioResolveConfiguration configuration = getBroadcastConfiguration();
        configuration.setLastFailedUploadEpochMilliseconds(System.currentTimeMillis());
        configuration.setLastUploadFailureMessage(message);
        mConsecutiveUploadFailures.incrementAndGet();
        setBroadcastState(BroadcastState.TEMPORARY_BROADCAST_ERROR);
    }

    private boolean hasRecentUploadFailure()
    {
        RadioResolveConfiguration configuration = getBroadcastConfiguration();
        return mConsecutiveUploadFailures.get() > 0 &&
            configuration.getLastFailedUploadEpochMilliseconds() > configuration.getLastSuccessfulUploadEpochMilliseconds();
    }

    private boolean isRetryableStatus(int statusCode)
    {
        return statusCode == 408 || statusCode == 429 || statusCode == 500 || statusCode == 502 ||
            statusCode == 503 || statusCode == 504;
    }

    private PendingUpload getNextReadyUpload()
    {
        PendingUpload pendingUpload = null;
        long now = System.currentTimeMillis();

        synchronized(mQueueLock)
        {
            int size = mAudioRecordingQueue.size();

            for(int x = 0; x < size; x++)
            {
                PendingUpload candidate = mAudioRecordingQueue.poll();

                if(candidate == null)
                {
                    break;
                }

                if(pendingUpload == null && candidate.getNextAttemptTime() <= now)
                {
                    pendingUpload = candidate;
                }
                else
                {
                    mAudioRecordingQueue.offer(candidate);
                }
            }
        }

        if(pendingUpload != null)
        {
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_QUEUE_CHANGE));
        }

        return pendingUpload;
    }

    private void retryOrRemove(PendingUpload pendingUpload, String reason)
    {
        if(mRunning && isValid(pendingUpload.getAudioRecording()))
        {
            pendingUpload.retry();

            synchronized(mQueueLock)
            {
                mAudioRecordingQueue.offer(pendingUpload);
                ageOffOverflowRecordings();
            }

            mLog.info("RadioResolve upload retry scheduled [" + reason + "] attempt [" +
                pendingUpload.getAttemptCount() + "]");
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_QUEUE_CHANGE));
        }
        else
        {
            pendingUpload.getAudioRecording().removePendingReplay();
            incrementAgedOffAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_AGED_OFF_COUNT_CHANGE));
        }
    }

    /**
     * Removes queued recordings that are too old.
     */
    private void ageOffInvalidRecordings()
    {
        boolean changed = false;

        synchronized(mQueueLock)
        {
            int size = mAudioRecordingQueue.size();

            for(int x = 0; x < size; x++)
            {
                PendingUpload pendingUpload = mAudioRecordingQueue.poll();

                if(pendingUpload == null)
                {
                    break;
                }

                if(isValid(pendingUpload.getAudioRecording()))
                {
                    mAudioRecordingQueue.offer(pendingUpload);
                }
                else
                {
                    pendingUpload.getAudioRecording().removePendingReplay();
                    incrementAgedOffAudioCount();
                    changed = true;
                }
            }
        }

        if(changed)
        {
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_AGED_OFF_COUNT_CHANGE));
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_QUEUE_CHANGE));
        }
    }

    private void ageOffOverflowRecordings()
    {
        while(mAudioRecordingQueue.size() > MAX_QUEUED_RECORDINGS)
        {
            PendingUpload pendingUpload = mAudioRecordingQueue.poll();

            if(pendingUpload != null)
            {
                pendingUpload.getAudioRecording().removePendingReplay();
                incrementAgedOffAudioCount();
                broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_AGED_OFF_COUNT_CHANGE));
            }
        }
    }

    /**
     * Creates an upload request for tests and production uploads.
     */
    static HttpRequest createUploadRequest(RadioResolveConfiguration configuration, AudioRecording audioRecording)
        throws IOException
    {
        return createUploadRequest(configuration, audioRecording, null);
    }

    /**
     * Creates an upload request for tests and production uploads.
     */
    static HttpRequest createUploadRequest(RadioResolveConfiguration configuration, AudioRecording audioRecording,
                                           AliasModel aliasModel) throws IOException
    {
        Path path = audioRecording.getPath();
        String filename = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        RadioResolveBuilder bodyBuilder = new RadioResolveBuilder();
        bodyBuilder.addFile(path, filename)
            .addPart("call_time_ms", audioRecording.getStartTime())
            .addPart("duration_sec", formatSeconds(audioRecording.getRecordingLength()))
            .addPart("target_id", getTo(audioRecording, aliasModel))
            .addPart("source_id", getFrom(audioRecording))
            .addPart("frequency_mhz", formatFrequencyMHz(getFrequency(audioRecording)))
            .addPart("system_label", getConfigurationIdentifier(audioRecording, Form.SYSTEM))
            .addPart("site_label", getConfigurationIdentifier(audioRecording, Form.SITE))
            .addPart("logical_channel", getDecoderIdentifier(audioRecording, Form.CHANNEL_NAME))
            .addPart("audio_protocol", getConfigurationIdentifier(audioRecording, Form.DECODER_TYPE))
            .addPart("talkgroup_label", getTalkgroupLabel(audioRecording, aliasModel))
            .addPart("talkgroup_group", getTalkgroupGroup(audioRecording, aliasModel))
            .addPart("talker_alias", getTalkerAlias(audioRecording))
            .addPart("patches", getPatches(audioRecording))
            .addPart("node_name", getNodeName(configuration))
            .addPart("node_timezone", getNodeTimezone(configuration))
            .addPart("agent_version", AGENT_VERSION)
            .addPart("original_filename", filename);

        return HttpRequest.newBuilder()
            .uri(createUri(configuration.getHost(), UPLOAD_PATH))
            .version(HttpClient.Version.HTTP_1_1)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + configuration.getApiKey())
            .header(HttpHeaders.CONTENT_TYPE, MULTIPART_FORM_DATA + "; boundary=" + bodyBuilder.getBoundary())
            .header(HttpHeaders.USER_AGENT, "sdrtrunk")
            .POST(bodyBuilder.build())
            .build();
    }

    private static String formatSeconds(long milliseconds)
    {
        return String.format(Locale.US, "%.3f", milliseconds / 1000.0d);
    }

    private static String formatFrequencyMHz(Long frequency)
    {
        if(frequency != null && frequency > 0)
        {
            return String.format(Locale.US, "%.5f", frequency / 1E6d);
        }

        return null;
    }

    private static Long getFrequency(AudioRecording audioRecording)
    {
        if(!audioRecording.hasIdentifierCollection())
        {
            return null;
        }

        Identifier identifier = audioRecording.getIdentifierCollection().getIdentifier(IdentifierClass.CONFIGURATION,
            Form.CHANNEL_FREQUENCY, Role.ANY);

        if(identifier instanceof ConfigurationLongIdentifier configurationLongIdentifier)
        {
            return configurationLongIdentifier.getValue();
        }

        return null;
    }

    private static String getFrom(AudioRecording audioRecording)
    {
        if(!audioRecording.hasIdentifierCollection())
        {
            return "0";
        }

        for(Identifier identifier: audioRecording.getIdentifierCollection().getIdentifiers(Role.FROM))
        {
            if(identifier instanceof RadioIdentifier radioIdentifier)
            {
                return radioIdentifier.getValue().toString();
            }
        }

        return "0";
    }

    private static String getTalkerAlias(AudioRecording audioRecording)
    {
        if(!audioRecording.hasIdentifierCollection())
        {
            return null;
        }

        for(Identifier identifier: audioRecording.getIdentifierCollection().getIdentifiers(Role.FROM))
        {
            if(identifier instanceof TalkerAliasIdentifier talkerAliasIdentifier && talkerAliasIdentifier.isValid())
            {
                return talkerAliasIdentifier.getValue();
            }
        }

        return null;
    }

    private static String getTo(AudioRecording audioRecording, AliasModel aliasModel)
    {
        if(!audioRecording.hasIdentifierCollection())
        {
            return "0";
        }

        Identifier identifier = audioRecording.getIdentifierCollection().getToIdentifier();

        if(identifier != null)
        {
            AliasList aliasList = getAliasList(audioRecording, aliasModel);

            if(aliasList != null)
            {
                List<Alias> aliases = aliasList.getAliases(identifier);
                Optional<Alias> streamAs = aliases.stream().filter(alias -> alias.getStreamTalkgroupAlias() != null).findFirst();

                if(streamAs.isPresent())
                {
                    return String.valueOf(streamAs.get().getStreamTalkgroupAlias().getValue());
                }
            }

            if(identifier instanceof PatchGroupIdentifier patchGroupIdentifier)
            {
                return patchGroupIdentifier.getValue().getPatchGroup().getValue().toString();
            }
            else if(identifier instanceof TalkgroupIdentifier talkgroupIdentifier)
            {
                return String.valueOf(RadioReferenceDecoder.convertToRadioReferenceTalkgroup(talkgroupIdentifier.getValue(),
                    talkgroupIdentifier.getProtocol()));
            }
            else if(identifier instanceof RadioIdentifier radioIdentifier)
            {
                return radioIdentifier.getValue().toString();
            }
        }

        return "0";
    }

    private static String getTalkgroupLabel(AudioRecording audioRecording, AliasModel aliasModel)
    {
        Alias alias = getFirstToAlias(audioRecording, aliasModel);
        return alias != null ? alias.toString() : null;
    }

    private static String getTalkgroupGroup(AudioRecording audioRecording, AliasModel aliasModel)
    {
        Alias alias = getFirstToAlias(audioRecording, aliasModel);
        return alias != null ? alias.getGroup() : null;
    }

    private static Alias getFirstToAlias(AudioRecording audioRecording, AliasModel aliasModel)
    {
        if(!audioRecording.hasIdentifierCollection())
        {
            return null;
        }

        AliasList aliasList = getAliasList(audioRecording, aliasModel);
        Identifier identifier = audioRecording.getIdentifierCollection().getToIdentifier();

        if(aliasList != null && identifier != null)
        {
            List<Alias> aliases = aliasList.getAliases(identifier);

            if(!aliases.isEmpty())
            {
                return aliases.get(0);
            }
        }

        return null;
    }

    private static AliasList getAliasList(AudioRecording audioRecording, AliasModel aliasModel)
    {
        return aliasModel != null && audioRecording.hasIdentifierCollection() ?
            aliasModel.getAliasList(audioRecording.getIdentifierCollection()) : null;
    }

    private static String getPatches(AudioRecording audioRecording)
    {
        if(!audioRecording.hasIdentifierCollection())
        {
            return null;
        }

        Identifier identifier = audioRecording.getIdentifierCollection().getToIdentifier();

        if(identifier instanceof PatchGroupIdentifier patchGroupIdentifier)
        {
            PatchGroup patchGroup = patchGroupIdentifier.getValue();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append(patchGroup.getPatchGroup().getValue());

            for(TalkgroupIdentifier patched: patchGroup.getPatchedTalkgroupIdentifiers())
            {
                sb.append(",").append(patched.getValue());
            }

            for(RadioIdentifier patched: patchGroup.getPatchedRadioIdentifiers())
            {
                sb.append(",").append(patched.getValue());
            }

            sb.append("]");
            return sb.toString();
        }

        return null;
    }

    private static String getConfigurationIdentifier(AudioRecording audioRecording, Form form)
    {
        if(!audioRecording.hasIdentifierCollection())
        {
            return null;
        }

        Identifier identifier = audioRecording.getIdentifierCollection().getIdentifier(IdentifierClass.CONFIGURATION,
            form, Role.ANY);
        return identifier != null && identifier.isValid() ? identifier.toString() : null;
    }

    private static String getDecoderIdentifier(AudioRecording audioRecording, Form form)
    {
        if(!audioRecording.hasIdentifierCollection())
        {
            return null;
        }

        Identifier identifier = audioRecording.getIdentifierCollection().getIdentifier(IdentifierClass.DECODER,
            form, Role.BROADCAST);
        return identifier != null && identifier.isValid() ? identifier.toString() : null;
    }

    /**
     * Tests the connection and API key against RadioResolve.
     */
    public static String testConnection(RadioResolveConfiguration configuration)
    {
        TestResult result = testConnectionDetailed(configuration);
        return result.success() ? RESULT_OK : result.message();
    }

    /**
     * Tests the connection and returns server-resolved node identity when available.
     */
    public static TestResult testConnectionDetailed(RadioResolveConfiguration configuration)
    {
        HttpClient httpClient = createHttpClient(configuration);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(createUri(configuration.getHost(), TEST_PATH))
            .version(HttpClient.Version.HTTP_1_1)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + configuration.getApiKey())
            .header(HttpHeaders.USER_AGENT, "sdrtrunk")
            .GET()
            .build();

        try
        {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if(statusCode >= 200 && statusCode < 300)
            {
                return parseTestResponse(response.body());
            }
            else if(statusCode == 401 || statusCode == 403)
            {
                return TestResult.failure(RESULT_INVALID_API_KEY);
            }

            return TestResult.failure(RESULT_ERROR + " Status Code:" + statusCode);
        }
        catch(Exception e)
        {
            Throwable throwableCause = e.getCause();

            if(e instanceof ConnectException || e instanceof CompletionException || throwableCause instanceof ConnectException)
            {
                return TestResult.failure(RESULT_NO_SERVER);
            }

            return TestResult.failure(safeMessage(e));
        }
    }

    private static TestResult parseTestResponse(String body)
    {
        if(body == null || body.isBlank())
        {
            return TestResult.success(null, null, null);
        }

        try
        {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject node = root.has("node") && root.get("node").isJsonObject() ? root.getAsJsonObject("node") : null;
            Integer nodeId = null;
            String nodeName = null;
            String serverTimeUtc = root.has("serverTimeUtc") && !root.get("serverTimeUtc").isJsonNull() ?
                root.get("serverTimeUtc").getAsString() : null;

            if(node != null)
            {
                if(node.has("id") && !node.get("id").isJsonNull())
                {
                    nodeId = node.get("id").getAsInt();
                }

                if(node.has("name") && !node.get("name").isJsonNull())
                {
                    nodeName = node.get("name").getAsString();
                }
            }

            return TestResult.success(nodeId, nodeName, serverTimeUtc);
        }
        catch(Exception e)
        {
            return TestResult.success(null, null, null);
        }
    }

    /**
     * Detailed connection test result.
     */
    public record TestResult(boolean success, String message, Integer nodeId, String nodeName, String serverTimeUtc)
    {
        public static TestResult success(Integer nodeId, String nodeName, String serverTimeUtc)
        {
            return new TestResult(true, RESULT_OK, nodeId, nodeName, serverTimeUtc);
        }

        public static TestResult failure(String message)
        {
            return new TestResult(false, message, null, null, null);
        }

        public String displayMessage()
        {
            if(!success)
            {
                return message;
            }

            if(nodeName != null && nodeId != null)
            {
                return "Authenticated as " + nodeName + " (node " + nodeId + ")";
            }
            else if(nodeName != null)
            {
                return "Authenticated as " + nodeName;
            }
            else if(nodeId != null)
            {
                return "Authenticated as node " + nodeId;
            }

            return RESULT_OK;
        }
    }

    /**
     * Creates an HTTP client for RadioResolve API requests.
     */
    public static HttpClient createHttpClient(RadioResolveConfiguration configuration)
    {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20));

        if(configuration != null && configuration.isIgnoreCertificateErrors())
        {
            try
            {
                builder.sslContext(createTrustAllSSLContext());
                SSLParameters sslParameters = new SSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("");
                builder.sslParameters(sslParameters);
            }
            catch(Exception e)
            {
                mLog.error("Unable to configure RadioResolve certificate error bypass [" + safeMessage(e) + "]");
            }
        }

        return builder.build();
    }

    /**
     * Creates an SSL context that trusts all certificates. Only used when the user explicitly enables
     * Ignore Certificate Errors for the RadioResolve stream.
     */
    private static SSLContext createTrustAllSSLContext()
        throws Exception
    {
        TrustManager[] trustManagers = new TrustManager[] {
            new X509TrustManager()
            {
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers()
                {
                    return new java.security.cert.X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                {
                }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, new java.security.SecureRandom());
        return sslContext;
    }

    /**
     * Creates an endpoint URI from a host value and API path.
     */
    static URI createUri(String host, String path)
    {
        String base = RadioResolveConfiguration.normalizeHost(host);
        return URI.create(base + path);
    }

    /**
     * Gets the configured node name or fallback.
     */
    private static String getNodeName(RadioResolveConfiguration configuration)
    {
        String nodeName = configuration.getNodeName();
        return nodeName != null && !nodeName.isBlank() ? nodeName : RadioResolveConfiguration.getDefaultNodeName();
    }

    /**
     * Gets the configured node timezone or fallback.
     */
    private static String getNodeTimezone(RadioResolveConfiguration configuration)
    {
        String timezone = configuration.getNodeTimezone();
        return timezone != null && !timezone.isBlank() ? timezone : RadioResolveConfiguration.getDefaultNodeTimezone();
    }

    /**
     * Avoids accidentally logging sensitive request details.
     */
    private static String safeMessage(Exception e)
    {
        String message = e.getLocalizedMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    /**
     * Scheduled queue processor.
     */
    public class AudioRecordingProcessor implements Runnable
    {
        @Override
        public void run()
        {
            processRecordingQueue();
        }
    }

    private static class PendingUpload
    {
        private AudioRecording mAudioRecording;
        private int mAttemptCount;
        private long mNextAttemptTime;

        PendingUpload(AudioRecording audioRecording)
        {
            mAudioRecording = audioRecording;
            mNextAttemptTime = System.currentTimeMillis();
        }

        AudioRecording getAudioRecording()
        {
            return mAudioRecording;
        }

        int getAttemptCount()
        {
            return mAttemptCount;
        }

        long getNextAttemptTime()
        {
            return mNextAttemptTime;
        }

        void retry()
        {
            mAttemptCount++;
            int backoffIndex = Math.min(mAttemptCount - 1, RETRY_BACKOFF_MS.length - 1);
            mNextAttemptTime = System.currentTimeMillis() + RETRY_BACKOFF_MS[backoffIndex];
        }
    }
}
