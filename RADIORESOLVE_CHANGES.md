# RadioResolve Modified Build

This repository contains an unofficial modified build of sdrtrunk for RadioResolve workflows.

Upstream project: https://github.com/DSheirer/sdrtrunk

Base upstream commit:

```text
a053315675e6764fe95c671139d796893c2b41a1
2026-02-19
#2364 Audio Playback supports 64-line alsa mixers as stereo output (#2374)
```

Modified by Tyler / RadioResolve on 2026-06-12.

## Changes

- Adds production MP3 timing metadata:
  - `call_start_ms`
  - `call_start_source`
  - `p25_system_time_estimate_ms`
  - `p25_system_time_quality`
- Uses the receiver-local P25 control-channel grant timestamp for recording filenames and ID3 date fields when available.
- Falls back to the first audio buffer timestamp when no grant timestamp is available.
- Includes an optional P25 system-time estimate as informational metadata only.
- Adds automatic P25 discovered control-channel frequency update support.
- Adds documentation for generated MP3 recording metadata in `docs/mp3-recording-metadata.md`.

## License

sdrtrunk is licensed under the GNU General Public License v3.0. This modified version remains under GPLv3. See `LICENSE`.
