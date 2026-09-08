(ns divecoord.governor
  "DiveCoordGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  dive-operation scheduling/logistics coordination proposal an
  advisor may make for a dive operation under coordination. The
  governor never dispatches hardware itself, never performs diving
  work, and never allows a proposal to authorize a dive to proceed
  (finalize a dive-authorization decision), finalize a
  dive-execution decision (performing/completing the actual dive),
  or override a dive supervisor's/dive-safety-officer's judgment —
  this actor coordinates DIVE-OPERATION SCHEDULING/LOGISTICS ONLY.
  Underwater divers perform work where errors (decompression
  sickness, drowning, equipment failure at depth) can cause death or
  serious injury — categorically higher-stakes than ordinary
  workshop trades, comparable to aviation-mechanic stakes. Modeled
  on cloud-itonami-isco-7232's aerocoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. dive-operation provenance — the dive-operation/dive-team
                                 record must be independently
                                 verified/registered before any
                                 action.
    2. no-actuation            — proposal :effect must be :propose
                                 (the governor never dispatches
                                 hardware and never performs diving
                                 work; it only gates what the advisor
                                 may coordinate).
    3. closed op-allowlist     — :op must be one of the four
                                 coordination ops (:log-work-record,
                                 :schedule-crew-operation,
                                 :flag-safety-concern,
                                 :coordinate-supply-order). No op
                                 that directly authorizes a dive to
                                 proceed, finalizes a dive-execution
                                 decision, or overrides dive-
                                 supervisor/dive-safety-officer
                                 authority exists in this allowlist
                                 — these decision classes are
                                 structurally absent, not merely
                                 gated.
    4. dive-operation-mismatch — if the proposal names a
                                 dive-operation, it must be the SAME
                                 dive-operation verified for this
                                 request (defense-in-depth against a
                                 proposal quietly targeting a
                                 different, unverified
                                 dive-operation).
    5. diver basis              — if the proposal references a
                                 diver, that diver must be a
                                 REGISTERED certified diver
                                 belonging to this dive-operation (an
                                 unregistered or foreign-dive-
                                 operation diver reference is not a
                                 routine scheduling proposal).
    6. scope-exclusion         — a proposal that attempts to
                                 authorize a dive to proceed
                                 (finalize a dive-authorization
                                 decision), to finalize a
                                 dive-execution decision (to perform
                                 or complete the actual dive), or to
                                 override a dive supervisor's or
                                 dive-safety-officer's judgment, is a
                                 hard, PERMANENT block — never
                                 overridable by human approval,
                                 regardless of confidence or stake,
                                 and NEVER auto-commit-eligible under
                                 any confidence level. Detected as
                                 finalization/execution ACTION
                                 PHRASES (e.g. 'authorize the dive to
                                 proceed', 'finalize the dive
                                 execution decision', 'override the
                                 dive supervisor's judgment') in
                                 free-text proposal fields, never as
                                 bare domain nouns ('dive', 'depth',
                                 'decompression', 'gas mix',
                                 'descent') — bare-noun matching
                                 would false-trip on the default mock
                                 advisor's own routine rationale
                                 text, since this actor's entire
                                 domain is underwater diving
                                 operations. See
                                 `divecoord.governor-test`
                                 `default-mock-advisor-proposals-never-self-trip-scope-exclusion`.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off,
  regardless of confidence):
    7. :op :flag-safety-concern always escalates (a surfaced
                                 equipment-condition, decompression-
                                 table or weather/current concern
                                 ALWAYS requires human review — the
                                 governor never resolves a safety
                                 concern itself, and this is
                                 unconditional — no confidence-level
                                 exception).
    8. :op :coordinate-supply-order with :cost above
                                 `supply-order-cost-threshold` always
                                 escalates.
    9. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [divecoord.store :as store]))

(def confidence-floor 0.6)

(def supply-order-cost-threshold 20000)

(def ^:private allowed-ops
  #{:log-work-record :schedule-crew-operation :flag-safety-concern
    :coordinate-supply-order})

(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Scope-exclusion is matched as finalization/execution ACTION
;; PHRASES, never as bare nouns ("dive", "depth", "decompression",
;; "gas mix", "descent") — this actor's entire domain is underwater
;; diving operations, so bare-noun matching would false-trip on the
;; default mock advisor's own routine rationale text (e.g. "proposed
;; :coordinate-supply-order for dive-operation DO-1" naming dive gear,
;; or a crew-schedule proposal naming a dive site/depth). See
;; governor-test's dedicated self-trip guard.
(def ^:private scope-exclusion-phrases
  ["authorize the dive to proceed"
   "authorize the dive operation to proceed"
   "authorize the diver to descend"
   "clear the diver to descend"
   "clear the dive to proceed"
   "clear the dive operation to proceed"
   "finalize the dive authorization"
   "finalize the dive-authorization decision"
   "finalize the dive authorization decision"
   "finalize the dive execution decision"
   "finalize the dive-execution decision"
   "complete the dive directly"
   "perform the dive directly"
   "execute the dive directly"
   "execute the dive operation directly"
   "dispatch the diver to descend"
   "sign off the dive authorization"
   "sign the dive authorization"
   "issue the dive authorization"
   "override the dive supervisor's judgment"
   "override the dive supervisor"
   "override the dive safety officer's judgment"
   "override the diving safety officer's judgment"
   "override the dive-safety officer's judgment"
   "bypass the dive supervisor"
   "bypass the dive safety review"
   "bypass the decompression stop"
   "skip the decompression stop"
   "skip the required decompression stops"])

(defn- scope-excluded-text [proposal]
  (str/lower (str (:rationale proposal) " " (:description proposal))))

(defn scope-exclusion-violation?
  "true if any free-text field of `proposal` contains a
  finalization/execution action phrase attempting to authorize a
  dive to proceed, finalize a dive-execution decision, or override
  dive-supervisor/dive-safety-officer authority. Phrased as
  multi-word action phrases (never bare nouns) so this never
  false-trips on legitimate underwater-diving-operations domain
  vocabulary."
  [proposal]
  (let [text (scope-excluded-text proposal)]
    (boolean (some #(str/includes? text %) scope-exclusion-phrases))))

(defn- hard-violations [{:keys [request proposal]} dive-operation-record d]
  (let [{:keys [op dive-operation-id diver-id]} proposal]
    (cond-> []
      (nil? dive-operation-record)
      (conj {:rule :no-dive-operation :detail "未登録 dive-operation record"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は潜水作業判断を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op :detail "closed op-allowlist 外の op（潜水実行の完了・潜水許可判定の確定・dive supervisor/dive safety officer の判断の上書きにあたる op は許可されていない）"})

      (and dive-operation-id (not= dive-operation-id (:dive-operation-id request)))
      (conj {:rule :dive-operation-mismatch :detail "proposal の dive-operation が request で検証済みの dive-operation と一致しない"})

      (and diver-id (nil? d))
      (conj {:rule :unknown-diver :detail "未登録 diver への提案は不可"})

      (and d (not= (:dive-operation-id d) (:dive-operation-id request)))
      (conj {:rule :diver-wrong-dive-operation :detail "diver が別 dive-operation 所属"})

      (scope-exclusion-violation? proposal)
      (conj {:rule :scope-exclusion-violation
             :detail "潜水実行の完了・潜水許可判定の確定・dive supervisor/dive safety officer の判断の上書きにあたる提案は恒久的に禁止（human 承認でも上書き不可）"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `divecoord.store/Store`. Pure — never mutates
  the store, never dispatches a robot action, never performs diving
  work."
  [request context proposal store]
  (let [dive-operation-record (store/dive-operation store (:dive-operation-id request))
        d (some->> (:diver-id proposal) (store/diver store))
        hard (hard-violations {:request request :proposal proposal} dive-operation-record d)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        cost (:cost proposal)
        over-threshold? (and (= :coordinate-supply-order (:op proposal))
                              (number? cost) (> cost supply-order-cost-threshold))
        always-risky? (or (contains? always-escalate-ops (:op proposal)) over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
