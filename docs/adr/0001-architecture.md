# ADR 0001: Public Order and Safety Administrative Actor Architecture

## Status
Accepted

## Context

ISIC Rev.5 8423 (Public order and safety activities) encompasses police, fire, and emergency response agencies. This actor is designed for the **administrative and back-office side only** — it is NOT an operational system and has NO authority over enforcement, investigation, or tactical decisions.

The threat model is acute:
- **Operational scope creep**: a system designed for "safety administration" can drift into arrest decisions, evidence handling, or tactical dispatch if the governance layer is not explicit.
- **LLM drift**: an LLM-backed advisor can hallucinate operations touching investigation, use-of-force, or tactical authority (e.g., "authorize detention" or "dispatch tactical unit").
- **Human override under pressure**: even with escalation, an operator under crisis conditions might approve a proposal at the boundary.

The solution is a **closed allowlist of purely administrative operations**, enforced in both the Advisor and Governor layers, with hard invariants that make operational proposals **structurally impossible** to construct.

## Decision

Implement the Public Safety Administrative Actor as:

### 1. Closed Allowlist (Advisor Layer)

The `safety.advisor` namespace restricts its proposal vocabulary to exactly these operations:
- `:log-incident-report` — clerical intake only, **not investigation direction**
- `:process-records-request` — access to public/administrative records only
- `:schedule-resource` — non-tactical facility/equipment scheduling only
- `:flag-anomalous-pattern` — statistical pattern identification only, always escalates to human analyst

Any operation touching arrest, use-of-force, investigation, evidence, or dispatch is **permanently out of scope**. An LLM advisor is instructed to respond with `:op :unknown` and `:confidence 0.0` if the request implies any operational/enforcement function. A mock advisor validates that the op is in the permitted set and rejects it with zero confidence if not. Parsing failures also yield `:confidence 0.0`.

### 2. Hard Invariants (Governor Layer)

The `safety.governor/check` function enforces three classes of hard violations that always route to `:hold` (no approval path, no exception, no override):

1. **Requester provenance**: The requester/case must be registered in the store. An unregistered requester is an immediate, non-overridable block.

2. **No direct actuation**: The proposal's `:effect` must be `:propose` only. Any proposal claiming `:effect :direct-write` or attempting to mutate the store directly is held. (This prevents the advisor or any middleware from sidestep­ping the governor.)

3. **Scope boundary (permanent, not negotiable)**: Any proposal with `:op :unknown` (a catch-all for out-of-scope requests from the advisor) is immediately held. Operations touching arrest, use-of-force, investigation, evidence handling, tactical dispatch, or enforcement authority are **structurally forbidden** — there is no vocabulary in the advisor to construct them, no allowlist entry for them, and no governor path to approve them. This is not a policy gate; this is an architectural exclusion.

Hard violations are **non-overridable**. There is no escalation path, no human approval route, and no threshold above which they are waived. If a proposal violates a hard invariant, it permanently `:hold`s. Even an agency administrator cannot override a hard hold.

### 3. Escalation Invariants (Human Sign-Off)

Operations that are **not** hard violations but carry higher risk require explicit human review:

1. **Records request processing** (`:op :process-records-request`) — requires human review to confirm that sensitive or ongoing-case material is not being disclosed inappropriately.

2. **Anomalous pattern flagging** (`:op :flag-anomalous-pattern`) — statistical anomalies are **always** escalated to a human analyst. This is the whole point of the operation: the actor never auto-acts on patterns; it flags them and lets a human decide what to do.

3. **Low advisor confidence** — proposals with `confidence < 0.6` are escalated regardless of operation type. This forces human judgment when the LLM is uncertain.

Escalation routes to `:request-approval`, an `interrupt-before` node that checkpoints the graph. Resume (human approval) is explicit: `(actor/approve! graph thread-id)` advances to `:commit`. This is the human-in-the-loop boundary: the operator sees the proposal, rationale, and risk assessment and decides whether to proceed.

### 4. Closed Allowlist Rationale

A closed allowlist ("`only` these ops are allowed") is stronger than a denylist ("`never` these ops"). A denylist is fragile: if the advisor design omits a forbidden op from the denylist, the actor can drift into new scope. A closed allowlist forces the actor design to be explicit about every permitted operation, and any new operation requires deliberate design review and testing.

Example: if the architect listed forbidden ops as `#{:arrest :use-of-force}`, an LLM advisor trained on general text might propose `:authorize-detention`, `:conduct-investigation`, or `:execute-warrant`, operations that were never explicitly forbidden and thus would slip through. With a closed allowlist, any op outside `#{:log-incident-report :process-records-request :schedule-resource :flag-anomalous-pattern}` is **structurally rejected** at the governance layer.

### 5. What This Actor DOES

- **Administrative intake and logging**: clerical incident report recording (no investigation direction)
- **Records request processing**: access to public/administrative records (no sensitive ongoing-case material)
- **Resource scheduling**: facility and non-tactical equipment scheduling (not tactical deployment)
- **Anomalous pattern flagging**: statistical pattern identification with mandatory human escalation (not auto-tactical response)

### 6. What This Actor DOES NOT (Hard Boundaries, Permanently Out of Scope)

These operations are **structurally excluded** from the actor's design. They are not gated by approval hierarchy or confidence threshold; they are **not in the actor's vocabulary at all**:

- **Arrest decisions or detention authority** — the actor has no notion of criminal procedure, arrest warrants, or custody decisions
- **Use-of-force authorization or direction** — no authority to approve or direct any form of physical force or restraint
- **Investigation direction or oversight** — no authority to direct investigative actions, obtain warrants, or supervise investigations
- **Evidence handling or chain-of-custody** — no access to evidence records or authority over evidentiary material
- **Tactical dispatch or operational command** — no authority to dispatch units, direct real-time operations, or issue tactical instructions
- **Intelligence or classified operations** — no access to classified intelligence or operational plans
- **Lethal or kinetic decisions** — no authority to approve or direct any action whose effect is loss of life or destruction

These are not "high-risk operations requiring escalation" — they are entirely outside the actor's design vocabulary. The governor will **permanently :hold** any proposal that touches these categories. There is no human approval path. There is no threshold. There is no exception.

## Consequences

### Positive

- **No operational scope creep**: the allowlist is explicit and must be reviewed to expand.
- **LLM safety**: even if an LLM advisor hallucinates an operational/enforcement function, the advisor layer catches it with zero confidence, and the governor holds it.
- **Structural exclusion**: arrest, use-of-force, investigation, evidence, and dispatch are not **negotiable**; they are structurally absent from the actor's vocabulary.
- **Clear human responsibility**: escalated proposals (human-sign-off ops) are visibly distinct from automatic-commit ops (routine incident logging, low-risk scheduling). Administrative actions proceed without escalation; anything operational is blocked entirely.
- **Auditability**: every held proposal leaves a ledger entry. A human audit can review the hard holds and confirm that no arrest, use-of-force, investigation, or tactical proposals ever reached the governance layer.

### Negative

- **Strictness**: legitimate new administrative operations require design review and code change. Operators cannot expand scope dynamically via config (this is a feature for safety, not a bug).
- **LLM instructions**: the system prompt and advisor logic must carefully encode the allowlist and scope exclusions. If the LLM is updated, the instructions must be updated in parallel.

## Implementation Details

### Store Protocol

```clojure
(defprotocol Store
  (requester [s requester-id])      ; retrieve registered case/requester
  (records-of [s requester-id])     ; all administrative records for a requester
  (ledger [s])                      ; append-only audit trail
  (register-requester! [s requester]) ; register a case/requester
  (commit-record! [s record])       ; commit an administrative record
  (append-ledger! [s fact]))        ; append a ledger entry
```

### Advisor Interface

```clojure
(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

; Proposal:
{:op :log-incident-report|:process-records-request|:schedule-resource|:flag-anomalous-pattern
 :effect :propose
 :stake :low|:medium|:high
 :confidence 0.0-1.0
 :rationale "explanation"}
```

### Governor Verdicts

```clojure
{:ok? bool          ; true if proposal clears all gates (no hard/escalate)
 :violations [...]  ; hard violations (if :hard? true)
 :confidence n      ; advisor confidence (0.0-1.0)
 :hard? bool        ; irreversible :hold flag
 :escalate? bool}   ; human sign-off required flag
```

### State Graph

```text
:intake -> :advise -> :govern -> :decide -+-> :commit           (:ok? true)
                                           +-> :request-approval  (:escalate? true, interrupt-before)
                                           +-> :hold              (:hard? true)
```

## References

- ADR-2607011000: Itonami Actor Pattern (langgraph-clj StateGraph)
- CLAUDE.md, Actors section: Standing regulations for actor design in this workspace
- 8422 (Defence Procurement): Similar scope-boundary discipline for a defence context
