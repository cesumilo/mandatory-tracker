# Project Memory

> Bootstrapped on 2026-04-23 from PLAN.md.
> This file is the agent's long-term memory. See AGENTS.md § Long-Term Memory Management.

## 📌 Current State Summary

- Project phase: **Phase 0 — Project Scaffolding** (all tasks pending)
- Implementation status: No code written yet.
- Next task: Initialize KMP project with `shared`, `androidApp`, and `iosApp` targets

## 🏛️ Architectural Decisions

- Use Kotlin Multiplatform (KMP) for shared logic layer (commonMain)
- Android widget: Jetpack Glance
- iOS widget: WidgetKit (SwiftUI)
- vlr.orlandomm.net API for match data

## 🔍 Discoveries & Learnings

_No discoveries recorded yet._

## 📜 Task Log

- **2026-04-23** — Bootstrapped MEMORY.md from PLAN.md — ⏳ Pending — `MEMORY.md`

## ❓ Open Questions & Follow-ups

- Does `vlresports` emit `ETag` / `Last-Modified`? (Phase 1 probe)
- Is `Match.time` absolute or relative? (Phase 1 probe)
- Tap target: companion app or vlr.gg match URL?
- Package naming and bundle IDs not yet decided