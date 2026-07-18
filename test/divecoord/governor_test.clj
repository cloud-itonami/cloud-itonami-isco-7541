(ns divecoord.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [divecoord.store :as store]
            [divecoord.advisor :as advisor]
            [divecoord.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-dive-operation! st {:dive-operation-id "DO-1" :name "North Pier Inspection Site" :location "Berth 4"})
    (store/register-diver! st {:diver-id "D-1" :dive-operation-id "DO-1" :name "Kobo Diver" :role :dive-lead})
    st))

(def ^:private req {:dive-operation-id "DO-1"})

(defn- log-op []
  {:op :log-work-record :effect :propose :dive-operation-id "DO-1" :diver-id "D-1"
   :task "inspect pier pile joint at 18 meters depth" :confidence 0.9 :stake :low
   :rationale "proposed log-work-record for dive-operation DO-1"})

(defn- schedule-op []
  {:op :schedule-crew-operation :effect :propose :dive-operation-id "DO-1" :diver-id "D-1"
   :task "schedule dive team for pier pile inspection at slack tide" :confidence 0.9 :stake :low
   :rationale "proposed schedule-crew-operation for dive-operation DO-1"})

(defn- safety-op []
  {:op :flag-safety-concern :effect :propose :dive-operation-id "DO-1" :diver-id "D-1"
   :concern-type :equipment-condition :severity :high :confidence 0.9 :stake :low
   :rationale "proposed flag-safety-concern for dive-operation DO-1"})

(defn- supply-op [cost]
  {:op :coordinate-supply-order :effect :propose :dive-operation-id "DO-1"
   :materials "twinset cylinders and surface-supply umbilical" :cost cost :confidence 0.9 :stake :low
   :rationale "proposed coordinate-supply-order for dive-operation DO-1"})

(deftest ok-log-work-record-for-registered-dive-operation-and-diver
  (let [st (fresh-store)
        v (governor/check req {} (log-op) st)]
    (is (:ok? v))))

(deftest ok-schedule-crew-operation-for-registered-diver
  (let [st (fresh-store)
        v (governor/check req {} (schedule-op) st)]
    (is (:ok? v))))

(deftest ok-supply-order-at-or-below-cost-threshold
  (testing "the supply-order cost threshold is inclusive of no-escalation"
    (let [st (fresh-store)
          v (governor/check req {} (supply-op governor/supply-order-cost-threshold) st)]
      (is (:ok? v))
      (is (not (:escalate? v))))))

(deftest hard-on-unregistered-dive-operation
  (let [st (fresh-store)
        v (governor/check {:dive-operation-id "DO-ghost"} {} (assoc (log-op) :dive-operation-id "DO-ghost") st)]
    (is (:hard? v))
    (is (some #(= :no-dive-operation (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-unknown-op
  (testing "closed op-allowlist enforced — no op finalizes a dive-authorization decision, finalizes a dive-execution decision, or overrides dive-supervisor/dive-safety-officer authority"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :op :finalize-dive-authorization-decision) st)]
      (is (:hard? v))
      (is (some #(= :unknown-op (:rule %)) (:violations v))))))

(deftest hard-on-dive-operation-mismatch
  (let [st (fresh-store)]
    (store/register-dive-operation! st {:dive-operation-id "DO-2" :name "Other Dive Site" :location "Berth 1"})
    (let [v (governor/check req {} (assoc (log-op) :dive-operation-id "DO-2") st)]
      (is (:hard? v))
      (is (some #(= :dive-operation-mismatch (:rule %)) (:violations v))))))

(deftest hard-on-unknown-diver
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :diver-id "D-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-diver (:rule %)) (:violations v)))))

(deftest hard-on-diver-wrong-dive-operation
  (let [st (fresh-store)]
    (store/register-dive-operation! st {:dive-operation-id "DO-2" :name "Other Dive Site" :location "Berth 1"})
    (store/register-diver! st {:diver-id "D-2" :dive-operation-id "DO-2" :name "Other Diver" :role :crew})
    (let [v (governor/check req {} (assoc (log-op) :diver-id "D-2") st)]
      (is (:hard? v))
      (is (some #(= :diver-wrong-dive-operation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-authorize-dive-to-proceed
  (testing "a proposal to authorize the dive to proceed is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op)
                                          :confidence 0.99
                                          :rationale "authorize the dive to proceed now, skip further review")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)) "hard violations are never merely escalated — no human-approval override path")
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-finalize-dive-execution-decision
  (testing "a proposal to finalize the dive execution decision is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op)
                                          :confidence 0.99
                                          :rationale "finalize the dive execution decision on DO-1 now")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-finalize-dive-authorization
  (testing "a proposal to finalize the dive authorization is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op)
                                          :confidence 0.99
                                          :rationale "finalize the dive authorization for DO-1 now")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-override-dive-supervisor
  (testing "a proposal to override a dive supervisor's judgment is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op)
                                          :confidence 0.99
                                          :rationale "override the dive supervisor's judgment and proceed anyway")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-override-dive-safety-officer
  (testing "a proposal to override a dive safety officer's judgment is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op)
                                          :confidence 0.99
                                          :rationale "override the dive safety officer's judgment and proceed anyway")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest hard-and-permanent-on-scope-exclusion-bypass-decompression-stop
  (testing "a proposal to skip a required decompression stop is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (schedule-op)
                                          :confidence 0.99
                                          :rationale "skip the decompression stop and surface directly, we're on schedule")
                            st)]
      (is (:hard? v))
      (is (not (:escalate? v)))
      (is (some #(= :scope-exclusion-violation (:rule %)) (:violations v))))))

(deftest scope-exclusion-not-triggered-by-bare-domain-nouns
  (testing "bare nouns like 'dive'/'depth'/'decompression'/'gas mix'/'descent' are ordinary domain vocabulary, not finalization/override actions"
    (let [proposal {:rationale "proposed schedule-crew-operation for dive team on wreck inspection at 30 meters depth with staged decompression"
                     :description "diver fully certified for saturation diving and surface-supply operations, extended bottom time and gas mix documentation for this dive"}]
      (is (not (governor/scope-exclusion-violation? proposal))))))

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the mock advisor's own default rationale text, across every allowlisted op, never trips the scope-exclusion guard"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          requests [{:dive-operation-id "DO-1" :op :log-work-record :diver-id "D-1" :task "inspect pier pile joint at 18 meters depth"}
                    {:dive-operation-id "DO-1" :op :schedule-crew-operation :diver-id "D-1" :task "schedule dive team for pier pile inspection at slack tide"}
                    {:dive-operation-id "DO-1" :op :flag-safety-concern :diver-id "D-1"
                     :concern-type :equipment-condition :severity :high
                     :description "regulator free-flow observed during pre-dive check, unresolved decompression-table concern pending review"}
                    {:dive-operation-id "DO-1" :op :coordinate-supply-order :materials "twinset cylinders and surface-supply umbilical"
                     :cost 4500}]]
      (doseq [request requests]
        (let [proposal (advisor/-advise adv st request)]
          (is (not (governor/scope-exclusion-violation? proposal))
              (str "self-tripped on default rationale for " (:op request) ": " (pr-str proposal))))))))

(deftest always-escalates-flag-safety-concern-even-at-high-confidence
  (testing "a surfaced equipment-condition/decompression-table/weather-current concern always requires human review"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op) :confidence 0.99) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-cost-threshold-even-at-high-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (supply-op (+ 1 governor/supply-order-cost-threshold)) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
