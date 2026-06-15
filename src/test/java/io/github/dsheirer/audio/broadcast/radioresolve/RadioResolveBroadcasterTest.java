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

import io.github.dsheirer.audio.broadcast.AudioRecording;
import io.github.dsheirer.audio.broadcast.BroadcastState;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RadioResolveBroadcasterTest
{
    @Test
    void uploadRequestContainsExpectedHeaders()
        throws Exception
    {
        Path path = Files.createTempFile("radioresolve-test", ".mp3");
        Files.write(path, "audio".getBytes(StandardCharsets.UTF_8));

        try
        {
            RadioResolveConfiguration configuration = configuration("https://calls.example.com", "secret-key");
            configuration.setNodeName("node-a");
            configuration.setNodeTimezone("America/New_York");
            AudioRecording recording = new AudioRecording(path, Collections.emptyList(), null, 1000, 1000);

            HttpRequest request = RadioResolveBroadcaster.createUploadRequest(configuration, recording);

            assertEquals("https://calls.example.com/api/node/upload-call", request.uri().toString());
            assertEquals("Bearer secret-key", request.headers().firstValue("Authorization").orElse(null));
            assertTrue(request.headers().firstValue("Content-Type").orElse("").startsWith("multipart/form-data; boundary="));
            assertEquals("sdrtrunk", request.headers().firstValue("User-Agent").orElse(null));
            assertEquals(HttpClient.Version.HTTP_1_1, request.version().orElse(null));
            assertFalse(request.toString().contains("secret-key"));
        }
        finally
        {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void successfulUploadIncrementsStreamedCount()
        throws Exception
    {
        try(MockRadioResolveServer server = new MockRadioResolveServer(200, 200))
        {
            RadioResolveBroadcaster broadcaster = new RadioResolveBroadcaster(configuration(server.getHost(), "secret-key"),
                null, null, null);
            AudioRecording recording = recording();
            recording.addPendingReplay();

            try
            {
                broadcaster.start();
                broadcaster.receive(recording);

                assertTrue(waitFor(() -> broadcaster.getStreamedAudioCount() == 1, 5000));
                assertEquals(BroadcastState.CONNECTED, broadcaster.getBroadcastState());
                assertEquals(0, broadcaster.getAudioErrorCount());
                assertTrue(server.awaitRequests(2));
                assertEquals("/api/node/test", server.getRequests().get(0).mPath);
                assertEquals("/api/node/upload-call", server.getRequests().get(1).mPath);
            }
            finally
            {
                broadcaster.stop();
                Files.deleteIfExists(recording.getPath());
            }
        }
    }

    @Test
    void uploadUnauthorizedSetsInvalidCredentials()
        throws Exception
    {
        try(MockRadioResolveServer server = new MockRadioResolveServer(200, 401))
        {
            RadioResolveBroadcaster broadcaster = new RadioResolveBroadcaster(configuration(server.getHost(), "secret-key"),
                null, null, null);
            AudioRecording recording = recording();
            recording.addPendingReplay();

            try
            {
                broadcaster.start();
                broadcaster.receive(recording);

                assertTrue(waitFor(() -> broadcaster.getAudioErrorCount() == 1, 5000));
                assertEquals(BroadcastState.INVALID_CREDENTIALS, broadcaster.getBroadcastState());
                assertTrue(server.awaitRequests(2));
            }
            finally
            {
                broadcaster.stop();
                Files.deleteIfExists(recording.getPath());
            }
        }
    }

    @Test
    void uploadServerErrorIncrementsErrorCount()
        throws Exception
    {
        try(MockRadioResolveServer server = new MockRadioResolveServer(200, 500))
        {
            RadioResolveBroadcaster broadcaster = new RadioResolveBroadcaster(configuration(server.getHost(), "secret-key"),
                null, null, null);
            AudioRecording recording = recording();
            recording.addPendingReplay();

            try
            {
                broadcaster.start();
                broadcaster.receive(recording);

                assertTrue(waitFor(() -> broadcaster.getAudioErrorCount() == 1, 5000));
                assertEquals(BroadcastState.TEMPORARY_BROADCAST_ERROR, broadcaster.getBroadcastState());
                assertTrue(server.awaitRequests(2));
            }
            finally
            {
                broadcaster.stop();
                Files.deleteIfExists(recording.getPath());
            }
        }
    }

    @Test
    void uploadServerErrorRetries()
        throws Exception
    {
        try(MockRadioResolveServer server = new MockRadioResolveServer(200, 500, 200, 200))
        {
            RadioResolveConfiguration configuration = configuration(server.getHost(), "secret-key");
            configuration.setMaximumRecordingAge(60000);
            RadioResolveBroadcaster broadcaster = new RadioResolveBroadcaster(configuration, null, null, null);
            AudioRecording recording = recording();
            recording.addPendingReplay();

            try
            {
                broadcaster.start();
                broadcaster.receive(recording);

                assertTrue(waitFor(() -> broadcaster.getAudioErrorCount() == 1, 5000));
                assertTrue(waitFor(() -> broadcaster.getStreamedAudioCount() == 1, 12000));
                assertEquals(BroadcastState.CONNECTED, broadcaster.getBroadcastState());
                assertTrue(server.awaitRequests(4));
                assertEquals("/api/node/test", server.getRequests().get(0).mPath);
                assertEquals("/api/node/upload-call", server.getRequests().get(1).mPath);
                assertEquals("/api/node/test", server.getRequests().get(2).mPath);
                assertEquals("/api/node/upload-call", server.getRequests().get(3).mPath);
            }
            finally
            {
                broadcaster.stop();
                Files.deleteIfExists(recording.getPath());
            }
        }
    }

    private static RadioResolveConfiguration configuration(String host, String apiKey)
    {
        RadioResolveConfiguration configuration = new RadioResolveConfiguration();
        configuration.setHost(host);
        configuration.setApiKey(apiKey);
        configuration.setNodeName("node-a");
        configuration.setNodeTimezone("America/New_York");
        return configuration;
    }

    private static AudioRecording recording()
        throws IOException
    {
        Path path = Files.createTempFile("radioresolve-upload", ".mp3");
        Files.write(path, "audio".getBytes(StandardCharsets.UTF_8));
        return new AudioRecording(path, Collections.emptyList(), null, System.currentTimeMillis(), 1000);
    }

    private static boolean waitFor(BooleanSupplier supplier, long timeoutMillis)
        throws InterruptedException
    {
        long start = System.currentTimeMillis();

        while(System.currentTimeMillis() - start < timeoutMillis)
        {
            if(supplier.getAsBoolean())
            {
                return true;
            }

            Thread.sleep(50);
        }

        return false;
    }

    private interface BooleanSupplier
    {
        boolean getAsBoolean();
    }

    private static class MockRadioResolveServer implements AutoCloseable
    {
        private final ServerSocket mServerSocket;
        private final List<Integer> mStatuses = new ArrayList<>();
        private final List<Request> mRequests = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch mRequestLatch;
        private final Thread mThread;
        private volatile boolean mRunning = true;

        MockRadioResolveServer(int... statuses)
            throws IOException
        {
            mServerSocket = new ServerSocket(0);

            for(int status: statuses)
            {
                mStatuses.add(status);
            }

            mRequestLatch = new CountDownLatch(statuses.length);
            mThread = new Thread(this::run, "radioresolve-test-server");
            mThread.start();
        }

        String getHost()
        {
            return "http://127.0.0.1:" + mServerSocket.getLocalPort();
        }

        List<Request> getRequests()
        {
            return mRequests;
        }

        boolean awaitRequests(int count)
            throws InterruptedException
        {
            long start = System.currentTimeMillis();

            while(System.currentTimeMillis() - start < 5000)
            {
                if(mRequests.size() >= count)
                {
                    return true;
                }

                Thread.sleep(50);
            }

            return mRequestLatch.await(1, TimeUnit.MILLISECONDS);
        }

        private void run()
        {
            for(int status: mStatuses)
            {
                if(!mRunning)
                {
                    return;
                }

                try(Socket socket = mServerSocket.accept())
                {
                    Request request = readRequest(socket.getInputStream());
                    mRequests.add(request);
                    writeResponse(socket, status);
                    mRequestLatch.countDown();
                }
                catch(IOException e)
                {
                    if(mRunning)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        private Request readRequest(InputStream inputStream)
            throws IOException
        {
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            int matched = 0;
            byte[] terminator = new byte[] {'\r', '\n', '\r', '\n'};

            while(matched < terminator.length)
            {
                int value = inputStream.read();

                if(value < 0)
                {
                    break;
                }

                headerBytes.write(value);

                if((byte)value == terminator[matched])
                {
                    matched++;
                }
                else
                {
                    matched = (byte)value == terminator[0] ? 1 : 0;
                }
            }

            String headers = headerBytes.toString(StandardCharsets.UTF_8);
            String[] lines = headers.split("\\r?\\n");
            String[] requestLine = lines[0].split(" ");
            Map<String,String> headerMap = new LinkedHashMap<>();

            for(int x = 1; x < lines.length; x++)
            {
                int separator = lines[x].indexOf(':');

                if(separator > 0)
                {
                    headerMap.put(lines[x].substring(0, separator).toLowerCase(Locale.US),
                        lines[x].substring(separator + 1).trim());
                }
            }

            int contentLength = 0;
            String contentLengthHeader = headerMap.get("content-length");

            if(contentLengthHeader != null)
            {
                contentLength = Integer.parseInt(contentLengthHeader);
            }

            while(contentLength > 0)
            {
                long skipped = inputStream.skip(contentLength);

                if(skipped <= 0)
                {
                    break;
                }

                contentLength -= skipped;
            }

            return new Request(requestLine[0], requestLine[1], headerMap);
        }

        private void writeResponse(Socket socket, int status)
            throws IOException
        {
            String reason = status >= 200 && status < 300 ? "OK" : "ERROR";
            byte[] body = reason.getBytes(StandardCharsets.UTF_8);
            String response = "HTTP/1.1 " + status + " " + reason + "\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Connection: close\r\n\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
        }

        @Override
        public void close()
            throws Exception
        {
            mRunning = false;
            mServerSocket.close();
            mThread.join(1000);
        }
    }

    private static class Request
    {
        private final String mMethod;
        private final String mPath;
        private final Map<String,String> mHeaders;

        Request(String method, String path, Map<String,String> headers)
        {
            mMethod = method;
            mPath = path;
            mHeaders = headers;
        }
    }
}
