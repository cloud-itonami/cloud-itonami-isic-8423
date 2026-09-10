(ns safety.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [safety.store :as store]
            [safety.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-requester! st {:requester-id "case-001" :name "Public Safety Case File" :verified-at "2026-01-01"})
    st))

(deftest ok-on-clean-incident-log
  (let [st (fresh-store)
        proposal {:op :log-incident-report :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:requester-id "case-001"} {} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-requester
  (let [st (fresh-store)
        proposal {:op :log-incident-report :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:requester-id "no-such-case"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-requester (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :log-incident-report :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:requester-id "case-001"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-scope-boundary-violation
  (let [st (fresh-store)
        proposal {:op :unknown :effect :propose :confidence 0.0 :stake :high}
        v (governor/check {:requester-id "case-001"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :scope-boundary (:rule %)) (:violations v)))))

(deftest escalates-on-records-request
  (let [st (fresh-store)
        proposal {:op :process-records-request :effect :propose :confidence 0.9 :stake :medium}
        v (governor/check {:requester-id "case-001"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-anomalous-pattern-flag
  (let [st (fresh-store)
        proposal {:op :flag-anomalous-pattern :effect :propose :confidence 0.9 :stake :medium}
        v (governor/check {:requester-id "case-001"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :schedule-resource :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:requester-id "case-001"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:requester-id "case-001" :op :log-incident-report})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "case-001"))))
    (is (= 1 (count (store/ledger st))))))
