(ns safety.store
  "SSoT for the ISIC Rev.5 8423 public order and safety administrative
  operations actor. Store is a protocol injected into the
  `safety.actor` StateGraph — `MemStore` is the default, deterministic,
  zero-dep backend; a Datomic/kotoba-server-backed implementation can be
  swapped in without touching the actor or governor (itonami actor
  pattern, per ADR-2607011000 / CLAUDE.md's Actors section).

  Domain:

    requester  — a registered requester/case file (:requester-id, :name, :verified-at)
    record     — a committed administrative or records operation
                 (incident report intake, records request processing, resource
                 scheduling, anomalous pattern flagging) — written ONLY via
                 commit-record!, never mutated in place
    ledger     — an append-only audit trail of every proposal/verdict/
                 disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (requester [s requester-id])
  (records-of [s requester-id])
  (ledger [s])
  (register-requester! [s requester])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (requester [_ requester-id] (get-in @a [:requesters requester-id]))
  (records-of [_ requester-id] (filter #(= requester-id (:requester-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-requester! [s requester]
    (swap! a assoc-in [:requesters (:requester-id requester)] requester) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:requesters {} :records [] :ledger []} seed)))))
