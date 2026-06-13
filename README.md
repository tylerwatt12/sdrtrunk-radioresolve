![RadioResolve Build](https://github.com/tylerwatt12/sdrtrunk-radioresolve/actions/workflows/radioresolve-release.yml/badge.svg)

# sdrtrunk-radioresolve

This is an unofficial RadioResolve fork of [DSHeirer/sdrtrunk](https://github.com/DSheirer/sdrtrunk).

It is based on upstream sdrtrunk commit:

```text
a053315675e6764fe95c671139d796893c2b41a1
2026-02-19
#2364 Audio Playback supports 64-line alsa mixers as stereo output (#2374)
```

## What Is Different?

This fork keeps the upstream sdrtrunk application behavior, with RadioResolve-focused additions for recording metadata and P25 control-channel frequency handling.

Main changes:

* Adds production MP3 timing metadata:
  * `call_start_ms`
  * `call_start_source`
  * `p25_system_time_estimate_ms`
  * `p25_system_time_quality`
* Uses the receiver-local P25 control-channel grant timestamp for recording filenames and ID3 date fields when available.
* Falls back to the first audio buffer timestamp when no grant timestamp is available.
* Includes an optional P25 system-time estimate as informational metadata only.
* Adds automatic P25 discovered control-channel frequency update support.
* Documents generated MP3 metadata in [`docs/mp3-recording-metadata.md`](docs/mp3-recording-metadata.md).

The P25 system-time estimate is not used as sdrtrunk's canonical clock, filename timestamp, or ID3 creation date. It is included only as informational metadata for downstream ingest/reconciliation logic.

## Downloads

RadioResolve builds are published from this fork's [Releases](https://github.com/tylerwatt12/sdrtrunk-radioresolve/releases) page.

Upstream official sdrtrunk releases remain available from [DSHeirer/sdrtrunk releases](https://github.com/DSHeirer/sdrtrunk/releases).

## Source and License

sdrtrunk is licensed under the GNU General Public License v3.0. This modified version remains under GPLv3. See [`LICENSE`](LICENSE).

For a concise list of fork changes, see [`RADIORESOLVE_CHANGES.md`](RADIORESOLVE_CHANGES.md).

# MacOS Tahoe 26.1 Users - Attention:
Changes to USB support in Tahoe version 26.x cause sdrtrunk to fail to launch.  Do the following to install the latest libusb and create a symbolic link and then use the nightly build which includes an updated usb4java native library for Tahoe with ARM processor.  There may still be issue(s) with MacOS accessing your USB SDR tuners.

```
brew install libusb --HEAD
cd /opt
sudo mkdir local
cd local
sudo mkdir lib
```
Next, find where brew installed the libusb library, for example: ```/opt/homebrew/Cellar/libusb/HEAD-9ceaa52/lib/libusb-1.0.0.dylib```    Note: the folder "HEAD-9ceaa52" is the version stamp for HEAD when you installed from it.

Finally, create a symbolic link from the installed library to the place where usb4java is expecting to find libusb (/opt/local/lib/libusb-1.0.0.dylib)

```
sudo ln -s /opt/homebrew/Cellar/libusb/HEAD-9ceaa52/lib/libusb-1.0.0.dylib /opt/local/lib/libusb-1.0.0.dylib
```

# sdrtrunk
A cross-platform java application for decoding, monitoring, recording and streaming trunked mobile and related radio protocols using Software Defined Radios (SDR).

* [Help/Wiki Home Page](https://github.com/DSheirer/sdrtrunk/wiki)
* [Getting Started](https://github.com/DSheirer/sdrtrunk/wiki/Getting-Started)
* [User's Manual](https://github.com/DSheirer/sdrtrunk/wiki/User-Manual)
* [RadioResolve Downloads](https://github.com/tylerwatt12/sdrtrunk-radioresolve/releases)
* [Upstream Official Downloads](https://github.com/DSheirer/sdrtrunk/releases)
* [Support](https://github.com/DSheirer/sdrtrunk/wiki/Support)

![sdrtrunk Application](https://github.com/DSheirer/sdrtrunk/wiki/images/sdrtrunk.png)
**Figure 1:** sdrtrunk Application Screenshot

## Download the Latest Release
All release versions of sdrtrunk are available from the [releases](https://github.com/DSheirer/sdrtrunk/releases) tab.

* **(alpha)** These versions are under development feature previews and likely to contain bugs and unexpected behavior.
* **(beta)** These versions are currently being tested for bugs and functionality prior to final release.
* **(final)** These versions have been tested and are the current release version.

## Download Nightly Software Build
The [nightly](https://github.com/DSheirer/sdrtrunk/releases/tag/nightly) release contains current builds of the software 
for all supported operating systems.  This version of the software may contain bugs and may not run correctly.  However, 
it let's you preview the most recent changes and fixes before the next software release.  **Always backup your 
playlist(s) before you use the nightly builds.**  Note: the nightly release is updated each time code changes are 
committed to the code base, so it's not really 'nightly' as much as it is 'current'.

## Minimum System Requirements
* **Operating System:** Windows (~~32 or~~ 64-bit), Linux (~~32 or~~ 64-bit) or Mac (64-bit, 12.x or higher)
* **CPU:** 4-core
* **RAM:** 8GB or more (preferred).  Depending on usage, 4GB may be sufficient.
