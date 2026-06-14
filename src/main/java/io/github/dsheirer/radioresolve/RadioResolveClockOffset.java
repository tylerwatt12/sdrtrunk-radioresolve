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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;

/**
 * Simple SNTP clock offset probe.  This never changes the system clock.
 */
public class RadioResolveClockOffset
{
    public static final String DEFAULT_NTP_HOST = "time.cloudflare.com";
    private static final int NTP_PORT = 123;
    private static final int NTP_PACKET_SIZE = 48;
    private static final int NTP_TIMEOUT_MILLISECONDS = 5000;
    private static final long NTP_EPOCH_OFFSET_SECONDS = 2208988800L;

    private RadioResolveClockOffset()
    {
    }

    /**
     * Checks system clock offset from the default NTP source.
     * @return result
     */
    public static Result checkOffset()
    {
        return checkOffset(DEFAULT_NTP_HOST);
    }

    /**
     * Checks system clock offset from an NTP source.
     * @param host NTP hostname
     * @return result
     */
    public static Result checkOffset(String host)
    {
        try(DatagramSocket socket = new DatagramSocket())
        {
            socket.setSoTimeout(NTP_TIMEOUT_MILLISECONDS);

            byte[] request = new byte[NTP_PACKET_SIZE];
            request[0] = 0x1B;

            long t1 = System.currentTimeMillis();
            writeTimestamp(request, 40, t1);

            InetAddress address = InetAddress.getByName(host);
            socket.send(new DatagramPacket(request, request.length, address, NTP_PORT));

            byte[] response = new byte[NTP_PACKET_SIZE];
            socket.receive(new DatagramPacket(response, response.length));
            long t4 = System.currentTimeMillis();

            long t2 = readTimestamp(response, 32);
            long t3 = readTimestamp(response, 40);
            long offset = ((t2 - t1) + (t3 - t4)) / 2;
            long delay = (t4 - t1) - (t3 - t2);

            return new Result(true, host, offset, delay, Instant.ofEpochMilli(t4),
                "Clock offset " + offset + " ms from " + host + " (round trip " + delay + " ms)");
        }
        catch(Exception e)
        {
            return new Result(false, host, null, null, Instant.now(),
                "Clock offset check failed: " + e.getMessage());
        }
    }

    private static long readTimestamp(byte[] bytes, int offset)
    {
        long seconds = readUnsignedInt(bytes, offset) - NTP_EPOCH_OFFSET_SECONDS;
        long fraction = readUnsignedInt(bytes, offset + 4);
        return (seconds * 1000L) + ((fraction * 1000L) / 0x100000000L);
    }

    private static long readUnsignedInt(byte[] bytes, int offset)
    {
        return ((long)bytes[offset] & 0xFF) << 24 |
            ((long)bytes[offset + 1] & 0xFF) << 16 |
            ((long)bytes[offset + 2] & 0xFF) << 8 |
            ((long)bytes[offset + 3] & 0xFF);
    }

    private static void writeTimestamp(byte[] bytes, int offset, long timestampMilliseconds)
    {
        long seconds = timestampMilliseconds / 1000L + NTP_EPOCH_OFFSET_SECONDS;
        long milliseconds = timestampMilliseconds % 1000L;
        long fraction = (milliseconds * 0x100000000L) / 1000L;

        writeUnsignedInt(bytes, offset, seconds);
        writeUnsignedInt(bytes, offset + 4, fraction);
    }

    private static void writeUnsignedInt(byte[] bytes, int offset, long value)
    {
        bytes[offset] = (byte)(value >>> 24);
        bytes[offset + 1] = (byte)(value >>> 16);
        bytes[offset + 2] = (byte)(value >>> 8);
        bytes[offset + 3] = (byte)value;
    }

    /**
     * Clock offset check result.
     */
    public record Result(boolean success, String host, Long offsetMilliseconds, Long roundTripMilliseconds,
                         Instant checkedAt, String message)
    {
    }
}
