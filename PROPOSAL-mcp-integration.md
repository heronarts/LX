# Proposal: MCP integration for LX / Chromatik

> **Status**: proposal for review and discussion — no code changes in this PR. Looking for feedback on the LX-side architecture (the `mcp/` package, the global-engine vs per-project-modulator opt-in, and how `LXProjectWatcher` mirrors the existing package autoreload pattern). The MCP server itself ships in a separate Node/TypeScript repo (not included here).

---

## Context

LX is the engine library; [Chromatik](https://chromatik.co) is the GUI app built on it (formerly "LX Studio"). The use case is **pre-show authoring with Chromatik open** — Claude wires up channels, patterns, modulation knobs/buttons/triggers, modulation routings, and MIDI mappings; the operator watches the Chromatik UI update live. Not for live-performance control (some runtime state resets on reload is acceptable).

LX has [OSC](src/main/java/heronarts/lx/osc/LXOscEngine.java) + an [OSC Query](src/main/java/heronarts/lx/osc/LXOscQueryServer.java) HTTP introspection server, but neither is well-suited to *structural* edits (adding channels, creating modulators, wiring modulations). Project files are loaded once via [`openProject`](src/main/java/heronarts/lx/LX.java#L1131) and aren't watched for external changes.

**Strategy**: V1 = MCP edits the `.lxp` JSON on disk; LX adds a per-project watcher (toggled on via either a global sidebar control or a per-project modulator) that reloads it. V2 = LX exposes an HTTP backend on the same toggle, MCP swaps backends transparently. The MCP server's tool surface and Claude install flow stay identical across versions.

## Target UX

Both Claude Desktop and Claude Code, cross-platform (macOS/Linux/Windows), on the same machine as Chromatik for v1.

**Install (one-time, per Claude client):**
- **Claude Desktop**: drag `chromatik-mcp.mcpb` into the app. Claude Desktop spawns the bundled Node MCP via stdio whenever it's launched. No manual `node`/`npm` step.
- **Claude Code (CLI)**: `claude mcp add chromatik -- npx chromatik-mcp@latest --stdio` (or equivalent `.mcp.json` snippet).

**Enable inside Chromatik (either path):**
- **Global**: a status box in the Chromatik left sidebar, under the package controls, bound to a new `LXMcpEngine`. Toggle "MCP enabled". Persists in `LXPreferences` across sessions.
- **Per-project**: drop an `LXMcpModulator` into a project's modulation engine — opts that project in regardless of the global toggle.

Either path activates the project-file watcher + writes `~/.chromatik/current-project.json` for the MCP to discover.

## V1 — LX-side (Java)

New package `src/main/java/heronarts/lx/mcp/`, sibling to `osc/`, `midi/`, `modulation/`.

### `mcp/LXMcpEngine.java`
- Extends `LXComponent`. Sibling to [`LXOscEngine`](src/main/java/heronarts/lx/osc/LXOscEngine.java) etc. Constructed in [LXEngine near line 408](src/main/java/heronarts/lx/LXEngine.java#L408): `addChild("mcp", this.mcp = new LXMcpEngine(lx));`.
- Parameters (saved to `LXPreferences`, not project — global state):
  - `enabled` (`BooleanParameter`, default false)
  - `host` (`StringParameter`, default `"127.0.0.1"`)
  - `port` (`DiscreteParameter`, default 4774)
- Owns the `LXProjectWatcher` lifecycle and status-file writes. Active if `enabled.isOn() || attachedModulatorCount > 0`.
- Public listener API so the Chromatik UI can bind to enabled/status.
- `onSetProject(File, Change)` is called from `LX.setProject` so the watcher rebinds on OPEN, records `lastSavedMtime` on SAVE, and clears on NEW.

### `mcp/LXMcpModulator.java`
- Extends `LXModulator`. Marker modulator with empty compute body. Same shape as [`MacroKnobs`](src/main/java/heronarts/lx/modulator/MacroKnobs.java) but with no signal output.
- Parameters (saved to project): `enabled` (default true), `host`/`port` (default null = use global).
- `onAdded` → `lx.engine.mcp.attach(this)`. `dispose()` → `lx.engine.mcp.detach(this)`.
- Registered as a built-in modulator class in [LXRegistry](src/main/java/heronarts/lx/LXRegistry.java) so it appears in the modulator menu.

### `mcp/LXProjectWatcher.java`
- Owned by `LXMcpEngine`. Mirrors the package autoreload in [`LXRegistry.enableWatchService`](src/main/java/heronarts/lx/LXRegistry.java#L592) / [`runWatchService`](src/main/java/heronarts/lx/LXRegistry.java#L625):
  - Single `WatchService` on the project file's parent dir, filtered to that filename.
  - Polled from the engine loop — **no separate thread, no manual debounce**. The engine tick naturally coalesces.
  - Engine-loop hook: after [`registry.runWatchService()` at LXEngine.java#L1077](src/main/java/heronarts/lx/LXEngine.java#L1077), add `this.mcp.runWatchService();`.
- On a qualifying event, calls `lx.openProject(file)` directly (already on engine thread).
- **Echo suppression**: records `lastSavedMtime` on `Change.SAVE`; ignores events with mtime ≤ that value (avoids autosave-bounce loop).
- **Cross-platform note**: macOS `WatchService` is polling-based with ~10s default latency. If responsiveness is sluggish, fall back to mtime polling inside `runWatchService` (still no extra threads — engine loop drives it).

### `mcp/StatusFile.java`
- Writes `${user.home}/.chromatik/current-project.json`. Cross-platform: use `System.getProperty("user.home")` + `Path.of(home, ".chromatik", "current-project.json")`; create parent dir if absent. Atomic write (temp + `Files.move(ATOMIC_MOVE)`).
- Content:
  ```json
  { "path": "...", "version": "1.2.1", "pid": 12345,
    "host": "127.0.0.1", "port": 4774,
    "enabledBy": ["global"],
    "capabilities": ["fileEdit"],
    "timestamp": 1700000000000 }
  ```
  (`enabledBy` may be `["global"]`, `["project"]`, or `["global","project"]`.)
- Re-written on any change to enable state, host/port, or active project. Deleted when fully disabled and on shutdown.
- The `capabilities` list lets the v2 HTTP backend announce itself to the MCP without code changes on the MCP side.

### Edits to existing files
- [LX.java#L992](src/main/java/heronarts/lx/LX.java#L992) — call `this.engine.mcp.onSetProject(file, change)` at the end of `setProject`. Call `this.engine.mcp.dispose()` next to `registry.closeWatchService()` at [line 738](src/main/java/heronarts/lx/LX.java#L738).
- [LXEngine.java](src/main/java/heronarts/lx/LXEngine.java) — construct `LXMcpEngine` as a child engine near line 408; add `this.mcp.runWatchService()` after line 1077.
- [LXPreferences.java](src/main/java/heronarts/lx/LXPreferences.java) — persist `mcp.enabled`/`host`/`port` save/load. Mirror the `KEY_AUTO_RELOAD_PACKAGES` pattern at [line 251](src/main/java/heronarts/lx/LXPreferences.java#L251).
- [LXRegistry.java](src/main/java/heronarts/lx/LXRegistry.java) — register `LXMcpModulator.class` in the built-in modulator list.

### Test
- `src/test/java/heronarts/lx/mcp/LXMcpEngineTest.java`: enable via engine pref triggers watch + status file; enable via modulator triggers watch; both off = no watch + no status file; echo suppression for autosave; status-file paths resolve correctly on macOS + Linux + Windows path separators (use `Files.createTempDirectory`, not hard-coded `/tmp`).

## Chromatik UI (separate repo — planned, not in this PR)

In the Chromatik left sidebar, **under the package controls**, add a small status box bound to `lx.engine.mcp`:
- Shows: "MCP enabled / disabled", current `host:port`, count of attached project modulators.
- Provides: toggle for `enabled`, fields for `host`/`port`.

This is a Chromatik-repo change (the LX library has no UI). The LX-side `LXMcpEngine` exposes the standard parameter listeners Chromatik already uses for UI binding, so the panel implementation is mechanical. Filed as a follow-up; this proposal scopes only the LX-side engine + parameters.

## V1 — MCP server (Node/TS — separate repo, scoped here only for context)

New repo `chromatik-mcp`, scaffolded from the layout used by [touchdesigner-mcp](https://github.com/looking-glass-factory/touchdesigner-mcp).

### Layout — communication layer extracted as a swappable backend

```
chromatik-mcp/
  src/
    cli.ts                                # parses --transport, --status-file, --project
    server/chromatikServer.ts             # registers tools
    transport/                            # stdio + streamable-http
    features/tools/
      handlers/lxTools.ts                 # tool handlers — call ChromatikClient only
      metadata/, types.ts                 # Zod schemas
    chromatikClient/                      # === SWAPPABLE COMMUNICATION LAYER ===
      index.ts                            # interface ChromatikClient (all tool ops)
      backendFactory.ts                   # picks backend from status-file capabilities
      backends/
        fileBackend.ts                    # v1: read/write .lxp; watcher reloads
        httpBackend.ts                    # v2: POST to in-Chromatik HTTP server
        remoteBackend.ts                  # later: same HTTP, different host
      projectLocator.ts                   # reads ~/.chromatik/current-project.json
      schema.ts                           # typed view of .lxp JSON
      atomicWrite.ts
  mcpb/                                   # Claude Desktop bundle manifest
  install/
    claude-code-install.md                # one-line `claude mcp add ...` instructions
  README.md
```

**The `ChromatikClient` interface is the contract.** Tool handlers in `lxTools.ts` never touch files or HTTP directly; they call `client.addChannel(...)` etc. Backends declare capabilities; `backendFactory` picks the best one available per the status file's `capabilities` array. This means v1 → v2 → remote is a backend swap with zero changes to tools, transport, server, or install.

### Tool surface (intent-shaped, frozen across backend swaps)

**Mixer & patterns:**
- `lx_get_project` → `{ path, version, channelCount }`
- `lx_list_channels`, `lx_get_channel`
- `lx_add_channel`, `lx_remove_channel`
- `lx_list_patterns`, `lx_add_pattern`, `lx_set_active_pattern`

**Modulation side-panel controls:**
- `lx_list_modulators({ scope: "global" | { busId } })`
- `lx_add_macro_knobs({ scope, label? })` — [`MacroKnobs`](src/main/java/heronarts/lx/modulator/MacroKnobs.java) (8 knobs)
- `lx_add_macro_switches({ scope, label? })` — [`MacroSwitches`](src/main/java/heronarts/lx/modulator/MacroSwitches.java)
- `lx_add_macro_triggers({ scope, label? })` — [`MacroTriggers`](src/main/java/heronarts/lx/modulator/MacroTriggers.java)
- `lx_add_modulator({ scope, modulatorClass, label?, parameters? })` — generic LFO/envelope/etc.
- `lx_remove_modulator({ modulatorId })`
- `lx_set_modulator_parameter({ modulatorId, paramPath, value })`
- `lx_add_modulation({ scope, sourceId, sourceOutput?, targetPath, polarity?, range? })` — [`LXCompoundModulation`](src/main/java/heronarts/lx/modulation/LXCompoundModulation.java) (stored in `modulations` array)
- `lx_add_trigger_modulation({ scope, sourceId, targetPath })` — stored in `triggers` array per [LXModulationEngine.java#L432](src/main/java/heronarts/lx/modulation/LXModulationEngine.java#L432)
- `lx_remove_modulation({ modulationId })`

**MIDI mappings:**
- `lx_list_midi_mappings` — `"mapping"` array on [LXMidiEngine](src/main/java/heronarts/lx/midi/LXMidiEngine.java#L1386)
- `lx_add_midi_mapping({ channel, type: "note" | "control", number, targetPath })` — see [LXMidiMapping.save lines 142–149](src/main/java/heronarts/lx/midi/LXMidiMapping.java#L142)
- `lx_remove_midi_mapping({ mappingId })`

**Parameters & meta:**
- `lx_set_parameter({ path, value })` — `path` follows OSC convention `/lx/mixer/channel/1/fader` so v2 maps 1:1 to LX's existing OSC routing
- `lx_reload` (touches mtime to force watcher; debug aid)

### Project JSON shape (where the file backend reads/writes)
Per [`LXModulationEngine.save`](src/main/java/heronarts/lx/modulation/LXModulationEngine.java#L366):
```json
{ "modulators":  [ {"class": "...", "id": 1, "parameters": {} } ],
  "modulations": [ {"source": "...", "target": "...", "range": 1.0, "polarity": "unipolar"} ],
  "triggers":    [ {"source": "...", "target": "..."} ] }
```
Global engine at `engine.modulation`; per-bus engines under each channel's bus block. New IDs must exceed the project's max ID (`LX#getMaxId` recurses).

### File-backend edit pipeline
1. Locate `.lxp` via `~/.chromatik/current-project.json` (or `--project` flag).
2. Read fresh on every call (no in-memory cache — disk is source of truth).
3. Mutate against `schema.ts` typed view; preserve unknown keys; bump ID counters correctly.
4. Atomic write (temp file in same dir → `fs.rename`).
5. Poll the status file's `timestamp` (or sleep ~700ms) to confirm Chromatik picked up the reload, then re-read and return the updated entity.
6. In-process mutex to serialize concurrent tool calls.

### Cross-platform & cross-client
- Node 20+ on macOS / Linux / Windows. `os.homedir()` joins the status-file path; matches the Java side.
- `.mcpb` bundle for Claude Desktop install.
- `claude mcp add chromatik -- npx chromatik-mcp@latest --stdio` for Claude Code; `.mcp.json` snippet documented for committed-per-repo configs.
- No platform-specific APIs in either Node or Java sides.

## V1 → V2 migration

Only the **backend module** in `chromatikClient/backends/` swaps. Everything else (tools, transport, server, CLI, install, both Claude clients) is unchanged.

- V2 adds an HTTP listener inside `LXMcpEngine` bound on `host`/`port` that maps each tool to a Java method on the engine. The status file's `capabilities` array bumps to `["fileEdit", "http"]`.
- `backendFactory` sees `"http"` in capabilities and instantiates `httpBackend` instead of `fileBackend`. Live mutation, no `closeProject`, no undo-stack clobber.
- `remoteBackend` is the same HTTP, parameterized for a different host — opens the door to running Claude on a separate machine.

## Risks

- **Reload loop with autosave**: mitigated by `lastSavedMtime` echo suppression.
- **Reload kills undo stack / in-progress edits**: V1 reload calls `closeProject`. Opt-in (engine toggle or modulator) means the user accepts this; V2 (HTTP) avoids it.
- **Schema drift across LX versions**: pin `schema.ts` to a tested version range; reject mismatched versions using `lx_get_project`'s `version` field.
- **macOS WatchService latency**: fall back to mtime polling inside `runWatchService` if needed.
- **Multi-tool-call races**: in-process mutex in the file backend.
- **Cross-platform paths**: use `Path` APIs and `user.home` / `os.homedir()` consistently; no hard-coded separators.

## Critical files

LX (this repo):
- `src/main/java/heronarts/lx/mcp/LXMcpEngine.java` (new)
- `src/main/java/heronarts/lx/mcp/LXMcpModulator.java` (new)
- `src/main/java/heronarts/lx/mcp/LXProjectWatcher.java` (new) — modeled on [`LXRegistry`'s watch service](src/main/java/heronarts/lx/LXRegistry.java#L592)
- `src/main/java/heronarts/lx/mcp/StatusFile.java` (new)
- [LX.java#L992](src/main/java/heronarts/lx/LX.java#L992) — wire `onSetProject` + shutdown
- [LXEngine.java#L408](src/main/java/heronarts/lx/LXEngine.java#L408), [#L1077](src/main/java/heronarts/lx/LXEngine.java#L1077) — construct engine + add `runWatchService` call
- [LXPreferences.java](src/main/java/heronarts/lx/LXPreferences.java) — persist engine params
- [LXRegistry.java](src/main/java/heronarts/lx/LXRegistry.java) — register modulator class

Chromatik MCP (new repo):
- `chromatik-mcp/src/chromatikClient/index.ts` — the swappable interface
- `chromatik-mcp/src/chromatikClient/backends/fileBackend.ts`
- `chromatik-mcp/src/features/tools/handlers/lxTools.ts`

## Verification

1. **Manual end-to-end (macOS first, then Linux)**: build LX, launch Chromatik, open a sample `.lxp`. Toggle MCP on via the sidebar status box (or drop in an `LXMcpModulator`). Confirm `~/.chromatik/current-project.json` appears with `enabledBy: ["global"]` (or `["project"]`). Install `chromatik-mcp` in **Claude Desktop** via `.mcpb` and ask Claude to run `lx_add_macro_knobs`, `lx_add_modulation`, `lx_add_midi_mapping` — each should appear in the Chromatik UI within ~1s. Repeat with **Claude Code** via `claude mcp add` to confirm parity.
2. **Java unit**: `LXMcpEngineTest` covering toggle-on-via-pref, toggle-on-via-modulator, echo suppression, status-file lifecycle, cross-platform paths.
3. **TS unit**: snapshot tests in `fileBackend` against a fixture `.lxp` covering channels, modulators, modulations, and MIDI mappings.
4. **Migration smoke** (post-v2): re-run identical Claude prompts with `httpBackend` selected; outputs should match v1 byte-for-byte — proves the `ChromatikClient` contract held.

---

## Open questions for reviewers

1. Is `mcp/` the right package location, or would you prefer it nested under an existing one (e.g. `osc/mcp/`)?
2. Should `LXMcpEngine` parameters live in `LXPreferences` (global) or be promoted to a new top-level preferences section?
3. Status-file location: `~/.chromatik/current-project.json` — is there an existing convention I should match instead?
4. Is the modulator-as-opt-in pattern acceptable, or do you prefer a single global toggle?
5. The Node MCP server lives in a separate repo — does the LX team want it under the `heronarts` org, or is a third-party repo fine?
