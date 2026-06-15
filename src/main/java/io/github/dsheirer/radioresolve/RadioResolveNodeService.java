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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveBroadcaster;
import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveConfiguration;
import io.github.dsheirer.preference.radioresolve.RadioResolvePreference;
import io.github.dsheirer.util.ThreadPool;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional RadioResolve node service for check-ins, clock status reporting, and safe remote commands.
 */
public class RadioResolveNodeService
{
    public static final String AGENT_VERSION = "sdrtrunk-radioresolve-node";
    private static final Logger mLog = LoggerFactory.getLogger(RadioResolveNodeService.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String CHECK_IN_PATH = "/api/node/check-in";
    private static final String COMMAND_RESULT_PATH = "/api/node/command-result";

    private final RadioResolvePreference mPreference;
    private final Supplier<Optional<RadioResolveConfiguration>> mConfigurationSupplier;
    private final AtomicBoolean mCheckInRunning = new AtomicBoolean();
    private ScheduledFuture<?> mCheckInFuture;
    private long mLastCheckInEpochMilliseconds;
    private Long mLastClockOffsetMilliseconds;

    /**
     * Constructs an instance.
     * @param preference RadioResolve node preferences
     * @param configurationSupplier supplier for the active RadioResolve streaming configuration
     */
    public RadioResolveNodeService(RadioResolvePreference preference,
                                   Supplier<Optional<RadioResolveConfiguration>> configurationSupplier)
    {
        mPreference = preference;
        mConfigurationSupplier = configurationSupplier;
    }

    /**
     * Starts the optional background service.
     */
    public void start()
    {
        if(mCheckInFuture == null || mCheckInFuture.isCancelled())
        {
            mCheckInFuture = ThreadPool.SCHEDULED.scheduleAtFixedRate(this::checkInIfDue, 5, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Stops the optional background service.
     */
    public void stop()
    {
        if(mCheckInFuture != null)
        {
            mCheckInFuture.cancel(true);
            mCheckInFuture = null;
        }
    }

    public Long getLastClockOffsetMilliseconds()
    {
        return mLastClockOffsetMilliseconds;
    }

    private void checkInIfDue()
    {
        try
        {
            if(!mPreference.isCheckInEnabled() || getConfiguration().isEmpty())
            {
                return;
            }

            long now = System.currentTimeMillis();
            long minimumInterval = TimeUnit.SECONDS.toMillis(mPreference.getCheckInIntervalSeconds());

            if(now - mLastCheckInEpochMilliseconds >= minimumInterval)
            {
                sendCheckIn();
            }
        }
        catch(Exception e)
        {
            mLog.warn("RadioResolve node check-in failed: {}", e.getMessage());
        }
    }

    /**
     * Sends a node check-in immediately.
     * @return result
     */
    public CheckInResult sendCheckIn()
    {
        if(!mCheckInRunning.compareAndSet(false, true))
        {
            return new CheckInResult(false, "RadioResolve check-in is already running");
        }

        try
        {
            Optional<RadioResolveConfiguration> optionalConfiguration = getConfiguration();

            if(optionalConfiguration.isEmpty())
            {
                return new CheckInResult(false, "An enabled RadioResolve stream is required");
            }

            RadioResolveConfiguration configuration = optionalConfiguration.get();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(resolveEndpoint(configuration.getHost(), CHECK_IN_PATH))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + configuration.getApiKey())
                .header("Content-Type", "application/json")
                .header("User-Agent", "sdrtrunk")
                .POST(HttpRequest.BodyPublishers.ofString(createCheckInPayload(configuration).toString()))
                .build();

            HttpResponse<String> response = RadioResolveBroadcaster.createHttpClient(configuration)
                .send(request, HttpResponse.BodyHandlers.ofString());
            mLastCheckInEpochMilliseconds = System.currentTimeMillis();

            if(response.statusCode() >= 200 && response.statusCode() <= 299)
            {
                handleCheckInResponse(response.body());
                return new CheckInResult(true, "RadioResolve check-in accepted");
            }

            if(response.statusCode() == 401 || response.statusCode() == 403)
            {
                return new CheckInResult(false, "RadioResolve check-in rejected: invalid API key");
            }

            return new CheckInResult(false, "RadioResolve check-in rejected: HTTP " + response.statusCode());
        }
        catch(Exception e)
        {
            return new CheckInResult(false, "RadioResolve check-in failed: " + e.getMessage());
        }
        finally
        {
            mCheckInRunning.set(false);
        }
    }

    private JsonObject createCheckInPayload(RadioResolveConfiguration configuration)
    {
        JsonObject root = new JsonObject();
        root.addProperty("agentVersion", AGENT_VERSION);
        root.addProperty("application", "sdrtrunk");
        root.addProperty("nodeName", getNodeName(configuration));
        root.addProperty("hostname", getNodeName(configuration));
        root.addProperty("timezone", getNodeTimezone(configuration));

        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("completedCallUpload", true);
        capabilities.addProperty("rfTelemetry", mPreference.isTelemetryEnabled());
        capabilities.addProperty("nodeCheckIn", true);
        capabilities.addProperty("doctor", true);
        capabilities.addProperty("clockOffsetCheck", true);
        capabilities.addProperty("remoteCommands", mPreference.isRemoteCommandsEnabled());
        capabilities.addProperty("processSupervisor", false);
        root.add("capabilities", capabilities);

        JsonObject system = new JsonObject();
        system.addProperty("osName", System.getProperty("os.name", ""));
        system.addProperty("osVersion", System.getProperty("os.version", ""));
        system.addProperty("osArch", System.getProperty("os.arch", ""));
        system.addProperty("javaVersion", System.getProperty("java.version", ""));
        system.addProperty("uptimeMilliseconds", ManagementFactory.getRuntimeMXBean().getUptime());
        root.add("system", system);

        JsonObject clock = new JsonObject();
        clock.addProperty("nodeEpochMilliseconds", System.currentTimeMillis());
        clock.addProperty("nodeTimeUtc", Instant.now().toString());
        clock.addProperty("warnOffsetMilliseconds", mPreference.getClockWarnOffsetMilliseconds());
        clock.addProperty("blockOffsetMilliseconds", mPreference.getClockBlockOffsetMilliseconds());
        if(mLastClockOffsetMilliseconds != null)
        {
            clock.addProperty("lastServerOffsetMilliseconds", mLastClockOffsetMilliseconds);
        }
        root.add("clock", clock);

        return root;
    }

    private void handleCheckInResponse(String body)
    {
        if(body == null || body.isBlank())
        {
            return;
        }

        try
        {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            updateClockOffset(root);
            JsonArray commands = getCommands(root);

            if(commands != null && mPreference.isRemoteCommandsEnabled())
            {
                for(JsonElement commandElement: commands)
                {
                    if(commandElement.isJsonObject())
                    {
                        handleRemoteCommand(commandElement.getAsJsonObject());
                    }
                }
            }
        }
        catch(Exception e)
        {
            mLog.warn("RadioResolve check-in response could not be parsed: {}", e.getMessage());
        }
    }

    private void updateClockOffset(JsonObject root)
    {
        Long offset = getLong(root, "clockOffsetMilliseconds");

        if(offset == null && root.has("data") && root.get("data").isJsonObject())
        {
            offset = getLong(root.getAsJsonObject("data"), "clockOffsetMilliseconds");
        }

        if(offset == null && root.has("nodeClockOffsetMs"))
        {
            offset = getLong(root, "nodeClockOffsetMs");
        }

        if(offset != null)
        {
            mLastClockOffsetMilliseconds = offset;
        }
    }

    private JsonArray getCommands(JsonObject root)
    {
        if(root.has("commands") && root.get("commands").isJsonArray())
        {
            return root.getAsJsonArray("commands");
        }

        if(root.has("data") && root.get("data").isJsonObject())
        {
            JsonObject data = root.getAsJsonObject("data");

            if(data.has("commands") && data.get("commands").isJsonArray())
            {
                return data.getAsJsonArray("commands");
            }
        }

        return null;
    }

    private void handleRemoteCommand(JsonObject commandObject)
    {
        String commandId = getString(commandObject, "id");
        if(commandId == null)
        {
            commandId = getString(commandObject, "commandId");
        }

        String command = getString(commandObject, "command");
        if(command == null)
        {
            command = getString(commandObject, "type");
        }

        if(command == null)
        {
            reportCommandResult(commandId, "unknown", false, "Missing command name");
            return;
        }

        switch(command.toLowerCase(Locale.ENGLISH))
        {
            case "send_checkin_now":
            case "reload_config":
                reportCommandResult(commandId, command, true, "Command accepted by SDRTrunk RadioResolve service");
                break;
            case "run_doctor":
                final String doctorCommandId = commandId;
                final String doctorCommand = command;
                getConfiguration().ifPresentOrElse(configuration ->
                    reportCommandResult(doctorCommandId, doctorCommand, true,
                        RadioResolveDoctor.run(configuration, mPreference).toSummaryString()),
                    () -> reportCommandResult(doctorCommandId, doctorCommand, false, "No enabled RadioResolve stream"));
                break;
            case "restart_sdrtrunk":
            case "start_sdrtrunk":
            case "stop_sdrtrunk":
            case "reboot_node":
                reportCommandResult(commandId, command, false,
                    "Command is not supported inside SDRTrunk; use an external supervisor for process control");
                break;
            case "sync_clock":
            case "check_clock":
                RadioResolveClockOffset.Result result = RadioResolveClockOffset.checkOffset();
                reportCommandResult(commandId, command, result.success(), result.message());
                break;
            default:
                reportCommandResult(commandId, command, false, "Unsupported command");
                break;
        }
    }

    private void reportCommandResult(String commandId, String command, boolean success, String message)
    {
        Optional<RadioResolveConfiguration> optionalConfiguration = getConfiguration();

        if(optionalConfiguration.isEmpty())
        {
            return;
        }

        RadioResolveConfiguration configuration = optionalConfiguration.get();

        try
        {
            JsonObject root = new JsonObject();
            root.addProperty("commandId", commandId == null ? "" : commandId);
            root.addProperty("command", command);
            root.addProperty("success", success);
            root.addProperty("message", message);
            root.addProperty("completedAtEpochMilliseconds", System.currentTimeMillis());

            HttpRequest request = HttpRequest.newBuilder()
                .uri(resolveEndpoint(configuration.getHost(), COMMAND_RESULT_PATH))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + configuration.getApiKey())
                .header("Content-Type", "application/json")
                .header("User-Agent", "sdrtrunk")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .build();

            RadioResolveBroadcaster.createHttpClient(configuration)
                .sendAsync(request, HttpResponse.BodyHandlers.discarding());
        }
        catch(Exception e)
        {
            mLog.warn("RadioResolve command result could not be sent: {}", e.getMessage());
        }
    }

    public static URI resolveEndpoint(String host, String path)
    {
        String sanitized = host == null || host.isBlank() ? RadioResolveConfiguration.PRODUCTION_ENDPOINT : host.trim();

        if(sanitized.endsWith("/"))
        {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }

        return URI.create(sanitized + path);
    }

    private static String getString(JsonObject object, String memberName)
    {
        if(object.has(memberName) && !object.get(memberName).isJsonNull())
        {
            return object.get(memberName).getAsString();
        }

        return null;
    }

    private static Long getLong(JsonObject object, String memberName)
    {
        if(object.has(memberName) && !object.get(memberName).isJsonNull())
        {
            return object.get(memberName).getAsLong();
        }

        return null;
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

    /**
     * Result of a RadioResolve node check-in.
     */
    public record CheckInResult(boolean success, String message)
    {
    }
}
