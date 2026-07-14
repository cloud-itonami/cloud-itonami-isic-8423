# Operator Guide

## Running the Actor

Use the `safety.actor` StateGraph to process administrative requests.

```clojure
(require '[safety.actor :as actor]
         '[safety.store :as store])

; Build the graph
(def st (store/mem-store))
(def graph (actor/build-graph {:store st}))

; Process a request
(def result (actor/run-request! 
  graph 
  {:requester-id "case-001" :op :log-incident-report}
  {}
  "thread-1"))

; Check result
(println (:status result))  ; :done or :interrupted

; If interrupted, review and approve
(actor/approve! graph "thread-1")
```

## Checkpointing and Resume

The graph checkpoints at the `:request-approval` interrupt point. When a proposal requires human approval, the actor pauses and waits for explicit approval via `actor/approve!`.

## Hard Holds

If a proposal violates a hard invariant (unregistered requester, scope boundary, no-actuation), it immediately routes to `:hold` with no escalation path. Review the `:verdict` in the result for details.

## Audit Trail

Every proposal, verdict, and disposition is logged in the store's append-only ledger. Use `(store/ledger st)` to access the full trail.
