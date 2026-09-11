(ns divecoord.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [divecoord.actor :as actor]
            [divecoord.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-dive-operation! st {:dive-operation-id "DO-1" :name "North Pier Inspection Site" :location "Berth 4"})
    (store/register-diver! st {:diver-id "D-1" :dive-operation-id "DO-1" :name "Kobo Diver" :role :dive-lead})
    st))

(deftest commits-a-registered-diver-log-work-record
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:dive-operation-id "DO-1" :op :log-work-record :stake :low
                 :diver-id "D-1" :task "inspect pier pile joint at 18 meters depth"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "DO-1"))))))

(deftest commits-a-crew-scheduling-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:dive-operation-id "DO-1" :op :schedule-crew-operation :stake :low
                 :diver-id "D-1" :task "schedule dive team for pier pile inspection at slack tide"}
        result (actor/run-request! graph request {} "thread-sched")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "DO-1"))))))

(deftest holds-an-unregistered-dive-operation-request
  (testing "the dive-operation/dive-team record must be independently verified/registered before any action"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:dive-operation-id "DO-ghost" :op :log-work-record :stake :low
                   :diver-id "D-1" :task "inspect pier pile joint at 18 meters depth"}
          result (actor/run-request! graph request {} "thread-2")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "DO-ghost"))))))

(deftest holds-a-scope-excluded-proposal-with-no-interrupt-path
  (testing "a proposal to authorize a dive to proceed is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:dive-operation-id "DO-1" :op :log-work-record :stake :low
                   :diver-id "D-1" :task "inspect pier pile joint at 18 meters depth"
                   :description "authorize the dive to proceed now, skip further review"}
          result (actor/run-request! graph request {} "thread-scope")]
      (is (= :done (:status result))
          "hard :hold is a finish point, not an interrupt — the advisor can never park a scope-excluded proposal awaiting human override")
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "DO-1"))))))

(deftest holds-a-finalize-dive-execution-proposal-with-no-interrupt-path
  (testing "a proposal to finalize the dive execution decision is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:dive-operation-id "DO-1" :op :schedule-crew-operation :stake :low
                   :diver-id "D-1" :task "schedule dive team for pier pile inspection at slack tide"
                   :description "finalize the dive execution decision on DO-1 now"}
          result (actor/run-request! graph request {} "thread-finalize-execution")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "DO-1"))))))

(deftest holds-an-override-dive-supervisor-proposal-with-no-interrupt-path
  (testing "a proposal to override a dive supervisor's judgment is a hard, permanent block — never routed through :request-approval"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:dive-operation-id "DO-1" :op :flag-safety-concern :stake :low
                   :diver-id "D-1" :concern-type :equipment-condition :severity :high
                   :description "override the dive supervisor's judgment and proceed anyway"}
          result (actor/run-request! graph request {} "thread-override")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (nil? (get-in result [:state :record])))
      (is (empty? (store/records-of st "DO-1"))))))

(deftest interrupts-then-approves-a-safety-concern-flag-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:dive-operation-id "DO-1" :op :flag-safety-concern :stake :low
                 :diver-id "D-1" :concern-type :equipment-condition :severity :high}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "DO-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "DO-1")))))))

(deftest interrupts-then-approves-an-above-threshold-supply-order-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:dive-operation-id "DO-1" :op :coordinate-supply-order :stake :low
                 :materials "twinset cylinders and surface-supply umbilical" :cost 25000}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "DO-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "DO-1")))))))
