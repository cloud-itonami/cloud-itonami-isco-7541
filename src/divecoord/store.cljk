(ns divecoord.store
  "SSoT for the ISCO-08 7541 underwater divers dive-operation
  scheduling/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a dive-operation scheduling/logistics coordination robot
  proposes crew/dive-team scheduling, work-record logging,
  safety-concern flags and dive-equipment/gas-supply order
  coordination under this advisor/governor pair, which never
  dispatches hardware itself, never performs diving work, and never
  finalizes a dive-authorization decision, a dive-execution decision,
  or overrides a dive supervisor's/dive-safety-officer's judgment).
  Modeled on cloud-itonami-isco-7232's aerocoord.store.

  Domain:

    dive-operation — a registered dive operation/site under
               coordination (:dive-operation-id, :name, :location).
    diver    — a registered certified diver
               {:diver-id :dive-operation-id :name :role}, belonging
               to exactly one registered dive-operation (the
               dive-operation/site currently assigned to this diver
               for this engagement).
    record   — a committed operating record (a logged work record,
               scheduling proposal, safety-concern flag or supply-
               order coordination entry) — written ONLY via
               commit-record!. This actor coordinates dive-operation
               scheduling/logistics ONLY — a `record` is a
               coordination artifact, never a dive-execution act, a
               dive-authorization decision, or a dive-supervisor's-/
               dive-safety-officer's-judgment override.
    ledger   — append-only audit trail, commit or hold.")

(defprotocol Store
  (dive-operation [s dive-operation-id])
  (diver [s diver-id])
  (records-of [s dive-operation-id])
  (ledger [s])
  (register-dive-operation! [s op])
  (register-diver! [s d])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (dive-operation [_ dive-operation-id] (get-in @a [:dive-operations dive-operation-id]))
  (diver [_ diver-id] (get-in @a [:divers diver-id]))
  (records-of [_ dive-operation-id] (filter #(= dive-operation-id (:dive-operation-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-dive-operation! [s op]
    (swap! a assoc-in [:dive-operations (:dive-operation-id op)] op) s)
  (register-diver! [s d]
    (swap! a assoc-in [:divers (:diver-id d)] d) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:dive-operations {} :divers {} :records [] :ledger []}
                                   seed)))))
