---
name: gg
description: Load-test an HTTP API with Gopher Glide (gg): write and verify .http journeys, run and steer simulations, find the capacity ceiling, and compare snapshots for regressions. Use when asked to load test, stress test, benchmark an endpoint or check for performance regressions.
---

# gg

The `gg mcp` server must be configured (see `.mcp.json`). Its tools:

- `generate_config`
- `get_live_telemetry`
- `run_journey_dry_run`
- `snap_assert`
- `snap_capture`
- `snap_diff`
- `snap_list`
- `start_simulation`
- `steer_traffic`
- `stop_simulation`
- `validate_http_file`

Resources to read before you act:

- `gg://docs/http-syntax`: the .http file syntax; read it before writing a file
- `gg://schema/gg-yaml`: the YAML config schema
- `gg://profiles/catalog`: built-in traffic profiles

## Workflow

1. **Author**: write a .http file, fix every `validate_http_file` error, then prove it with `run_journey_dry_run`. The dry run sends real requests.
2. **Run**: `start_simulation` returns a session_id at once. For "how much can it take" pass `autopilot {mode: "find-ceiling"}` with no profile; to defend an SLO pass a profile and `autopilot {mode: "slo-guard"}`. Set `snap: true` to keep a snapshot.
3. **Watch**: `get_live_telemetry` with `wait_s` and `wake_on_p99_ms` / `wake_on_error_rate` instead of polling. Judge health on the window figures, not the cumulative ones.
4. **Steer**: `steer_traffic` (adjust_rps, set_rps, hold_rps, release_hold, mark). Not while the built-in autopilot owns the rate.
5. **Finish**: `stop_simulation` (add `report: "md"` for a shareable report), then `snap_list`, `snap_diff` and `snap_assert` to compare against a baseline.

Never leave a simulation running, and do not aim a dry run or load at systems you do not own.
