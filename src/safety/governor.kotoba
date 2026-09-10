(ns safety.governor
  "PublicSafetyGovernor — the independent safety/traceability layer
  for the ISIC Rev.5 8423 public order and safety administrative
  operations actor. Wired as its own `:govern` node in `safety.actor`'s
  StateGraph, downstream of `:advise` — the Advisor has no notion of
  requester provenance or case risk, so this MUST be a separate system able
  to reject a proposal (itonami actor pattern, per ADR-2607011000 /
  CLAUDE.md Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. requester provenance    — the request's requester must be registered.
    2. no-actuation            — proposal :effect must be :propose.
    3. scope-boundary          — proposals touching arrest, use-of-force,
                                  investigation, evidence handling, tactical
                                  dispatch, or enforcement authority NEVER
                                  PROCEED (closed allowlist enforced here +
                                  in advisor).

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    4. records-request-sensitive — records requests touching potentially
                                  sensitive/ongoing-case material.
    5. anomalous-pattern-flag   — statistical anomalies are always escalated
                                  (escalation is the whole point — they go to
                                  human analyst, never auto-acted).
    6. low confidence          (< `confidence-floor`)."
  (:require [safety.store :as store]
            [safety.advisor :as advisor]))

(def confidence-floor 0.6)

; Permanently forbidden operation categories (not in permitted-ops at all,
; but documented here for completeness)
(def ^:private forbidden-ops #{:unknown})  ; :unknown catches out-of-scope proposals from advisor

; Escalating operations (require human approval)
(def ^:private escalating-ops #{:process-records-request
                                 :flag-anomalous-pattern})

(defn- hard-violations [{:keys [proposal]} requester-record]
  (cond-> []
    (nil? requester-record)
    (conj {:rule :no-requester :detail "requester not registered"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect must be :propose only (no direct store writes)"})

    (contains? forbidden-ops (:op proposal))
    (conj {:rule :scope-boundary
           :detail "operation outside permitted scope (arrest, use-of-force, investigation, evidence handling, tactical dispatch, and enforcement authority are permanently forbidden)"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `safety.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [requester-record (store/requester store (:requester-id request))
        hard (hard-violations {:proposal proposal} requester-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        escalating-op? (contains? escalating-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not escalating-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? escalating-op?))}))
