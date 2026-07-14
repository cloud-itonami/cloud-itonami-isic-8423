# cloud-itonami-isic-8423

Open Occupation Blueprint for **ISIC Rev.5 8423**: Public Order and Safety Administrative Operations.

This repository designs a forkable OSS business for a public safety agency's administrative back-office: a document-handling and verification robot performs incident intake, records request processing, resource scheduling, and statistical anomaly flagging under a governor-gated actor, so a public safety agency keeps its own administrative records and audit trail instead of renting a closed back-office SaaS.

## IMPORTANT: SCOPE BOUNDARIES

**This actor is EXPLICITLY NOT an operational system and has NO authority over enforcement, investigation, or tactical decisions.**

### What this actor DOES

- Administrative and back-office operations only:
  - Incident report intake and logging (clerical, not investigation)
  - Public records request processing
  - Non-tactical resource scheduling (facilities, equipment)
  - Statistical anomaly flagging (always escalates to human analyst)
  - Audit trail and compliance documentation

### What this actor DOES NOT (hard boundaries, permanently out of scope)

These operations are **permanently forbidden** — they are not gated by risk level or approval hierarchy, they cannot be escalated for human override, and the actor's proposal vocabulary has no path to construct them. A closed allowlist enforces this at the governance layer:

- **Arrest decisions or detention authority** — the actor has no notion of criminal procedure, arrest warrants, or custody decisions
- **Use-of-force authorization or direction** — no authority to approve or direct any form of physical force or restraint
- **Investigation direction or oversight** — no authority to direct investigative actions, obtain warrants, or supervise investigations
- **Evidence handling or chain-of-custody** — no access to evidence records or authority over evidentiary material
- **Tactical dispatch or operational command** — no authority to dispatch units, direct real-time operations, or issue tactical instructions
- **Intelligence or classified operations** — no access to classified intelligence or operational plans
- **Lethal or kinetic decisions** — no authority to approve or direct any action whose effect is loss of life or destruction

These are not "high-risk operations requiring escalation" — they are entirely outside the actor's design vocabulary. The governor will **permanently :hold** any proposal that touches these categories (it is not a matter of confidence, approval chain, or budget threshold).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs the physical domain work**. Here a document-handling and verification robot performs incident intake, records request processing, resource scheduling, and anomaly flagging under an actor that proposes actions and an independent **Public Safety Governor** that gates them. The governor never dispatches a robot action itself; administrative actions that are not time-critical (records requests, resource scheduling) require human sign-off. Escalated anomaly flags always route to a human analyst.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
request (incident report, records request, resource, anomaly)
        |
        v
Public Safety Advisor -> Public Safety Governor -> incident record, records approval, scheduling plan, or human approval
        |
        v
robot actions (gated) + administrative record + audit ledger
```

No automated advice can commit a record the governor refuses, process a request outside its registered scope, or log an incident without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `8423`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Advisor and Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/safety/store.cljc` — `Store` protocol + `MemStore`:
  registered requesters/cases, committed administrative records, an append-only audit ledger.
- `src/safety/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes an administrative operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/safety/governor.cljc` — `PublicSafetyGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered requester, a proposal whose `:effect` isn't `:propose`, any
  proposal touching arrest/use-of-force/investigation/evidence/dispatch) always
  route to `:hold`. Escalation invariants (records requests, anomaly flags,
  or low advisor confidence) always route to `:request-approval` — an
  `interrupt-before` node that the graph checkpoints and only resumes on
  explicit human approval (`actor/approve!`).
- `src/safety/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry).

## License

AGPL-3.0-or-later.
