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
import io.github.dsheirer.audio.broadcast.AbstractAudioBroadcaster;
import io.github.dsheirer.audio.broadcast.AudioRecording;
import io.github.dsheirer.audio.broadcast.BroadcastEvent;
import io.github.dsheirer.audio.broadcast.BroadcastState;
import io.github.dsheirer.util.ThreadPool;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

    private Queue<AudioRecording> mAudioRecordingQueue = new LinkedTransferQueue<>();
    private ScheduledFuture<?> mAudioRecordingProcessorFuture;
    private HttpClient mHttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    private long mLastConnectionAttempt;
    private long mConnectionAttemptInterval = 5000;

    /**
     * Constructs an instance.
     */
    public RadioResolveBroadcaster(RadioResolveConfiguration config)
    {
        super(config);
    }

    /**
     * Starts the audio recording processor thread.
     */
    @Override
    public void start()
    {
        setBroadcastState(BroadcastState.CONNECTING);
        updateConnectionState(testConnection(getBroadcastConfiguration()), "connecting to");
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
        AudioRecording audioRecording = mAudioRecordingQueue.poll();

        while(audioRecording != null)
        {
            audioRecording.removePendingReplay();
            audioRecording = mAudioRecordingQueue.poll();
        }
    }

    @Override
    public int getAudioQueueSize()
    {
        return mAudioRecordingQueue.size();
    }

    @Override
    public void receive(AudioRecording audioRecording)
    {
        mAudioRecordingQueue.offer(audioRecording);
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
            setBroadcastState(BroadcastState.CONNECTING);
            updateConnectionState(testConnection(getBroadcastConfiguration()), "reconnecting to");
            mLastConnectionAttempt = System.currentTimeMillis();
        }

        return getBroadcastState() == BroadcastState.CONNECTED;
    }

    /**
     * Updates broadcaster state from a connection test result.
     */
    private void updateConnectionState(String response, String action)
    {
        if(RESULT_OK.equals(response))
        {
            setBroadcastState(BroadcastState.CONNECTED);
        }
        else if(RESULT_INVALID_API_KEY.equals(response))
        {
            setBroadcastState(BroadcastState.INVALID_CREDENTIALS);
            mLog.error("Error " + action + " RadioResolve server [invalid API key]");
        }
        else if(RESULT_NO_SERVER.equals(response))
        {
            setBroadcastState(BroadcastState.NO_SERVER);
            mLog.error("Error " + action + " RadioResolve server [server not found or not reachable]");
        }
        else
        {
            setBroadcastState(BroadcastState.ERROR);
            mLog.error("Error " + action + " RadioResolve server [" + response + "]");
        }
    }

    /**
     * Processes enqueued audio recordings.  Uploads are asynchronous, matching the other completed-call broadcasters.
     */
    private void processRecordingQueue()
    {
        while(connected() && !mAudioRecordingQueue.isEmpty())
        {
            final AudioRecording audioRecording = mAudioRecordingQueue.poll();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_QUEUE_CHANGE));

            if(isValid(audioRecording) && audioRecording.getRecordingLength() > 0)
            {
                try
                {
                    HttpRequest fileRequest = createUploadRequest(getBroadcastConfiguration(), audioRecording);

                    mHttpClient.sendAsync(fileRequest, HttpResponse.BodyHandlers.ofString())
                        .whenComplete((fileResponse, throwable) -> {
                            handleUploadResponse(fileResponse, throwable);
                            audioRecording.removePendingReplay();
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
                    setBroadcastState(BroadcastState.TEMPORARY_BROADCAST_ERROR);
                    incrementErrorAudioCount();
                    broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
                    audioRecording.removePendingReplay();
                }
            }
            else if(audioRecording != null)
            {
                audioRecording.removePendingReplay();
            }
        }

        ageOffInvalidRecordings();
    }

    /**
     * Handles an upload response.
     */
    private void handleUploadResponse(HttpResponse<String> fileResponse, Throwable throwable)
    {
        if(throwable != null)
        {
            setBroadcastState(BroadcastState.TEMPORARY_BROADCAST_ERROR);
            incrementErrorAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
            return;
        }

        int statusCode = fileResponse.statusCode();

        if(statusCode >= 200 && statusCode < 300)
        {
            incrementStreamedAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_STREAMED_COUNT_CHANGE));
        }
        else if(statusCode == 401 || statusCode == 403)
        {
            setBroadcastState(BroadcastState.INVALID_CREDENTIALS);
            incrementErrorAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
            mLog.error("RadioResolve upload rejected [invalid API key or access denied]");
        }
        else
        {
            setBroadcastState(BroadcastState.TEMPORARY_BROADCAST_ERROR);
            incrementErrorAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_ERROR_COUNT_CHANGE));
            mLog.error("RadioResolve upload failed [status " + statusCode + "]");
        }
    }

    /**
     * Removes queued recordings that are too old.
     */
    private void ageOffInvalidRecordings()
    {
        AudioRecording audioRecording = mAudioRecordingQueue.peek();

        while(audioRecording != null)
        {
            if(isValid(audioRecording))
            {
                return;
            }

            mAudioRecordingQueue.poll();
            audioRecording.removePendingReplay();
            incrementAgedOffAudioCount();
            broadcast(new BroadcastEvent(this, BroadcastEvent.Event.BROADCASTER_AGED_OFF_COUNT_CHANGE));
            audioRecording = mAudioRecordingQueue.peek();
        }
    }

    /**
     * Creates an upload request for tests and production uploads.
     */
    static HttpRequest createUploadRequest(RadioResolveConfiguration configuration, AudioRecording audioRecording)
        throws FileNotFoundException
    {
        Path path = audioRecording.getPath();
        String filename = path.getFileName() != null ? path.getFileName().toString() : path.toString();

        return HttpRequest.newBuilder()
            .uri(createUri(configuration.getHost(), UPLOAD_PATH))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + configuration.getApiKey())
            .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
            .header(HttpHeaders.USER_AGENT, "sdrtrunk")
            .header("X-Filename", filename)
            .header("X-Agent-Version", AGENT_VERSION)
            .header("X-Node-Hostname", getNodeName(configuration))
            .header("X-Node-Timezone", getNodeTimezone(configuration))
            .POST(HttpRequest.BodyPublishers.ofFile(path))
            .build();
    }

    /**
     * Tests the connection and API key against RadioResolve.
     */
    public static String testConnection(RadioResolveConfiguration configuration)
    {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(createUri(configuration.getHost(), TEST_PATH))
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
                return RESULT_OK;
            }
            else if(statusCode == 401 || statusCode == 403)
            {
                return RESULT_INVALID_API_KEY;
            }

            return RESULT_ERROR + " Status Code:" + statusCode;
        }
        catch(Exception e)
        {
            Throwable throwableCause = e.getCause();

            if(e instanceof ConnectException || e instanceof CompletionException || throwableCause instanceof ConnectException)
            {
                return RESULT_NO_SERVER;
            }

            return safeMessage(e);
        }
    }

    /**
     * Creates an endpoint URI from a host value and API path.
     */
    static URI createUri(String host, String path)
    {
        String base = host != null ? host.trim() : "";

        while(base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }

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
}
