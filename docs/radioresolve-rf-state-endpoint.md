# RadioResolve RF State Endpoint Implementation Guide

## Goal

Add a RadioResolve server endpoint that accepts structured SDRTrunk RF/system telemetry snapshots.

SDRTrunk will send this data directly as JSON. The server must not parse SDRTrunk activity-summary text for this feature.

## Endpoint

```http
POST /api/node/rf-state
Authorization: Bearer {NODE_API_KEY}
Content-Type: application/json
User-Agent: sdrtrunk
```

Return `2xx` for accepted telemetry.

Return `401` or `403` when the node API key is missing, invalid, disabled, or not authorized.

Return `400` for invalid JSON or structurally invalid payloads.

Return `413` if the JSON body is too large.

Return `429` only if rate limiting is intentionally implemented.

## Authentication

Reuse the existing node API key authentication used by:

```http
POST /api/node/upload-call
GET /api/node/test
```

The endpoint should resolve the bearer token to a receiver node record. Do not accept node identity from the request body as authoritative. The `nodeName` in the JSON is descriptive metadata only.

Never log the raw API key. If request logging includes headers, redact `Authorization`.

## Request Payload

Example:

```json
{
  "schemaVersion": 1,
  "agentVersion": "sdrtrunk-radioresolve-node",
  "observedAtEpochMilliseconds": 1710000000000,
  "nodeName": "receiver-1",
  "timezone": "America/New_York",
  "channel": {
    "name": "County P25 Control",
    "aliasList": "County"
  },
  "decoder": "P25_PHASE_1",
  "summaryHash": "a6f5d4...",
  "network": {
    "wacn": 781824,
    "system": 840,
    "nac": 835,
    "lra": 0
  },
  "currentSite": {
    "system": 840,
    "nac": 835,
    "rfss": 2,
    "site": 3,
    "lra": 0,
    "activeRfssNetworkConnection": true
  },
  "channels": [
    {
      "type": "PRIMARY_CONTROL",
      "band": 0,
      "channel": 777,
      "downlinkFrequency": 855862500,
      "uplinkFrequency": 810862500,
      "timeslot": null
    }
  ],
  "neighborSites": [
    {
      "system": 840,
      "nac": 835,
      "rfss": 2,
      "site": 1,
      "lra": 0,
      "band": 1,
      "channel": 1892,
      "downlinkFrequency": 773831250,
      "uplinkFrequency": 803831250,
      "status": ["VALID_INFORMATION", "ACTIVE_RFSS_CONNECTION"]
    }
  ],
  "frequencyBands": [
    {
      "band": 0,
      "access": "FDMA",
      "baseFrequency": 851006250,
      "bandwidth": 12500,
      "spacing": 6250,
      "transmitOffset": -45000000,
      "timeslots": null
    }
  ],
  "patchGroups": [
    {
      "patchGroup": 14492,
      "patchedTalkgroups": [14470]
    }
  ],
  "talkerAliases": [
    {
      "radioId": 1234567,
      "alias": "Unit 12"
    }
  ]
}
```

## Validation

Required top-level fields:

- `schemaVersion`
- `observedAtEpochMilliseconds`
- `decoder`
- `summaryHash`
- `network`
- `currentSite`

Recommended validation:

- `schemaVersion` must currently equal `1`.
- `observedAtEpochMilliseconds` must be a plausible epoch millisecond timestamp.
- `summaryHash` must be non-empty and reasonably bounded, for example 128 characters or less.
- `decoder` should accept known SDRTrunk values such as `P25_PHASE_1` and `P25_PHASE_2`.
- Integer RF fields may be nullable if SDRTrunk has not learned them yet, but the server should reject a payload that has neither useful network nor useful site identity.
- Arrays should default to empty arrays when absent if the server model allows it, but the client is expected to send arrays.
- Reject payloads above a conservative JSON body limit, for example 256 KB or 1 MB.

Do not require every nested field to exist. SDRTrunk may add fields over time, and the server should keep accepting older and newer schema-compatible payloads.

## Storage

Create a table for RF state snapshots. Suggested name:

```sql
radioresolve_rf_state_snapshots
```

Suggested columns:

- `id`
- `node_id`
- `schema_version`
- `observed_at`
- `first_seen_at`
- `last_seen_at`
- `agent_version`
- `node_name`
- `timezone`
- `decoder`
- `summary_hash`
- `channel_name`
- `alias_list_name`
- `wacn`
- `system_id`
- `nac`
- `rfss`
- `site`
- `lra`
- `payload_json`

Use `jsonb` for `payload_json` on PostgreSQL. Use the platform equivalent on other databases.

Store the full received JSON payload, even when indexed fields are extracted into columns. This lets the SDRTrunk client/server contract evolve without a database migration for every newly exposed field.

## Dedupe

SDRTrunk will compute `summaryHash` from the typed RF snapshot while excluding volatile fields such as observation time.

Recommended dedupe key:

```text
(node_id, summary_hash)
```

Behavior:

- If `(node_id, summary_hash)` does not exist, insert a new row.
- If it already exists, update `last_seen_at`, and optionally update `observed_at`, `agent_version`, `node_name`, `timezone`, and `payload_json`.
- Return `2xx` for both insert and duplicate/update paths.

Do not dedupe globally by `summaryHash` alone. Two receiver nodes may observe the same system/site state independently.

## Response Body

A minimal response is enough:

```json
{
  "ok": true,
  "status": "accepted",
  "duplicate": false
}
```

For duplicate/update:

```json
{
  "ok": true,
  "status": "seen",
  "duplicate": true
}
```

The SDRTrunk client only needs success versus failure for v1, but a small structured response helps debugging.

## Error Handling

Use structured error responses when practical:

```json
{
  "ok": false,
  "error": "invalid_payload",
  "message": "summaryHash is required"
}
```

Do not include secrets in error messages.

For auth errors, keep messages generic:

```json
{
  "ok": false,
  "error": "unauthorized"
}
```

## API Compatibility

This endpoint should be additive. It should not change existing call upload behavior:

```http
POST /api/node/upload-call
```

It should also not change the node test endpoint:

```http
GET /api/node/test
```

The same API key should work for all three node endpoints.

## Implementation Checklist

1. Add authenticated route `POST /api/node/rf-state`.
2. Reuse node bearer-token middleware/auth helper.
3. Add JSON body parsing with a strict size limit.
4. Validate required fields and schema version.
5. Resolve authenticated node from API key.
6. Extract indexed fields from the JSON.
7. Insert or upsert by `(node_id, summary_hash)`.
8. Store full payload JSON.
9. Return `2xx` for accepted inserts and duplicates.
10. Add logging that includes node id and summary hash, not API key.

## Tests

Add tests for:

- Valid payload inserts a row.
- Duplicate `(node_id, summary_hash)` updates `last_seen_at` instead of inserting a second row.
- Same `summaryHash` from a different node inserts a separate row.
- Missing bearer token returns `401`.
- Invalid bearer token returns `401` or `403`.
- Missing `summaryHash` returns `400`.
- Invalid JSON returns `400`.
- Unsupported `schemaVersion` returns `400`.
- Oversized body returns `413`.
- Unknown extra fields are accepted and stored in `payload_json`.
- `Authorization` header/API key is redacted from logs.

## Notes For The Implementing LLM

- Do not parse SDRTrunk activity-summary text.
- Do not infer node identity from `nodeName`; use the authenticated API key.
- Do not require every nested P25 field. SDRTrunk may not have learned every value yet.
- Keep the endpoint boring and conventional: normal bearer auth, JSON validation, upsert, and simple status response.
- Prefer storing both indexed columns and the raw JSON payload.
