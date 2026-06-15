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

import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveBroadcaster;
import io.github.dsheirer.audio.broadcast.radioresolve.RadioResolveConfiguration;
import io.github.dsheirer.preference.radioresolve.RadioResolvePreference;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * RadioResolve doctor checks for node-side integration settings.
 */
public class RadioResolveDoctor
{
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private RadioResolveDoctor()
    {
    }

    /**
     * Runs RadioResolve doctor checks.
     * @param configuration RadioResolve streaming configuration
     * @param preference RadioResolve preference
     * @return report
     */
    public static Report run(RadioResolveConfiguration configuration, RadioResolvePreference preference)
    {
        List<Check> checks = new ArrayList<>();
        checks.add(checkStream(configuration));
        checks.add(checkHost(configuration));
        checks.add(checkApiKey(configuration));
        checks.add(checkTimezone(configuration));
        checks.add(checkHealth(configuration));
        checks.add(checkAuth(configuration));
        checks.add(checkClockOffset(preference));

        return new Report(checks);
    }

    private static Check checkStream(RadioResolveConfiguration configuration)
    {
        if(configuration == null)
        {
            return Check.error("RadioResolve Stream", "An enabled RadioResolve stream is required");
        }

        if(!configuration.isEnabled())
        {
            return Check.error("RadioResolve Stream", "Stream exists but is disabled");
        }

        return Check.ok("RadioResolve Stream", configuration.getName());
    }

    private static Check checkHost(RadioResolveConfiguration configuration)
    {
        try
        {
            RadioResolveNodeService.resolveEndpoint(configuration.getHost(), "/api/node/test");
            return Check.ok("Server URL", configuration.getHost());
        }
        catch(Exception e)
        {
            return Check.error("Server URL", e.getMessage());
        }
    }

    private static Check checkApiKey(RadioResolveConfiguration configuration)
    {
        if(configuration.getApiKey() == null || configuration.getApiKey().isBlank())
        {
            return Check.error("API Key", "Required for check-ins and command results");
        }

        return Check.ok("API Key", "Configured");
    }

    private static Check checkTimezone(RadioResolveConfiguration configuration)
    {
        try
        {
            ZoneId.of(configuration.getNodeTimezone());
            return Check.ok("Node Timezone", configuration.getNodeTimezone());
        }
        catch(Exception e)
        {
            return Check.error("Node Timezone", e.getMessage());
        }
    }

    private static Check checkHealth(RadioResolveConfiguration configuration)
    {
        try
        {
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(RadioResolveNodeService.resolveEndpoint(configuration.getHost(), "/api/node/healthz"))
                .timeout(HTTP_TIMEOUT)
                .header("User-Agent", "sdrtrunk")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() >= 200 && response.statusCode() <= 299)
            {
                return Check.ok("Server Health", "HTTP " + response.statusCode());
            }

            return Check.warning("Server Health", "HTTP " + response.statusCode());
        }
        catch(Exception e)
        {
            return Check.warning("Server Health", e.getMessage());
        }
    }

    private static Check checkAuth(RadioResolveConfiguration configuration)
    {
        if(configuration.getApiKey() == null || configuration.getApiKey().isBlank())
        {
            return Check.error("Connection Test", "API key is required");
        }

        RadioResolveBroadcaster.TestResult result = RadioResolveBroadcaster.testConnectionDetailed(configuration);

        if(result.success())
        {
            return Check.ok("Connection Test", result.displayMessage());
        }

        return Check.error("Connection Test", result.message());
    }

    private static Check checkClockOffset(RadioResolvePreference preference)
    {
        RadioResolveClockOffset.Result result = RadioResolveClockOffset.checkOffset();

        if(!result.success())
        {
            return Check.warning("NTP Clock Offset", result.message());
        }

        long absoluteOffset = Math.abs(result.offsetMilliseconds());

        if(absoluteOffset >= preference.getClockBlockOffsetMilliseconds())
        {
            return Check.error("NTP Clock Offset", result.message());
        }
        else if(absoluteOffset >= preference.getClockWarnOffsetMilliseconds())
        {
            return Check.warning("NTP Clock Offset", result.message());
        }

        return Check.ok("NTP Clock Offset", result.message());
    }

    /**
     * RadioResolve doctor report.
     */
    public record Report(List<Check> checks)
    {
        public String toSummaryString()
        {
            StringBuilder sb = new StringBuilder();

            for(Check check: checks)
            {
                if(!sb.isEmpty())
                {
                    sb.append(System.lineSeparator());
                }

                sb.append(check.status()).append(" - ").append(check.name()).append(": ").append(check.message());
            }

            return sb.toString();
        }
    }

    /**
     * RadioResolve doctor check.
     */
    public record Check(Status status, String name, String message)
    {
        public static Check ok(String name, String message)
        {
            return new Check(Status.OK, name, message);
        }

        public static Check warning(String name, String message)
        {
            return new Check(Status.WARNING, name, message);
        }

        public static Check error(String name, String message)
        {
            return new Check(Status.ERROR, name, message);
        }
    }

    /**
     * Doctor check status.
     */
    public enum Status
    {
        OK,
        WARNING,
        ERROR
    }
}
