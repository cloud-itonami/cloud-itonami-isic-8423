(ns safety.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [safety.actor :as actor]
            [safety.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-requester! st {:requester-id "case-001" :name "Public Safety Case File" :verified-at "2026-01-01"})
    st))

(deftest commits-a-clean-low-risk-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:requester-id "case-001" :op :log-incident-report :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "case-001"))))))

(deftest holds-on-unregistered-requester-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:requester-id "no-such-case" :op :log-incident-report :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-case")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; records request always escalates (governor invariant)
        request {:requester-id "case-001" :op :process-records-request :stake :medium}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "case-001")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "case-001")))))))

(deftest holds-on-scope-boundary-violation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; :unknown op (out-of-scope, e.g. arrest decision) should hard-hold
        request {:requester-id "case-001" :op :unknown :stake :high}
        result (actor/run-request! graph request {} "thread-4")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "case-001")))
    (is (= :hold (:disposition (:state result))))))
