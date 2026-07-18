(ns divecoord.advisor
  "Dive Operation Scheduling/Logistics Coordination Advisor — the
  advisor named in this repository's README, proposing a
  dive-operation scheduling/logistics coordination operation (log a
  work record, schedule a crew/dive-team operation, flag a safety
  concern, coordinate a supply order) from a dive-operation roster,
  diver roster and dive schedule. Swappable mock/llm; the advisor
  ONLY proposes — `divecoord.governor` checks dive-operation/diver
  registration, the closed op-allowlist and scope-exclusion
  independently, and always escalates safety-concern flags,
  above-threshold supply orders and low-confidence proposals. This
  actor coordinates DIVE-OPERATION SCHEDULING/LOGISTICS ONLY — it
  never performs diving work and never proposes to authorize a dive
  to proceed, finalize a dive-execution decision, or override a dive
  supervisor's/dive-safety-officer's judgment. Modeled on
  cloud-itonami-isco-7232's aerocoord.advisor.

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :dive-operation-id str :diver-id str?
               :cost number? :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake dive-operation-id diver-id cost task materials
                             concern-type severity description time-window
                             progress-notes defect-report]
                      :as request}]
  (cond-> {:op op
           :effect :propose
           :dive-operation-id dive-operation-id
           :stake (or stake :low)
           :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
           :rationale (str "proposed " (name op) " for dive-operation " dive-operation-id)}
    diver-id (assoc :diver-id diver-id)
    (some? cost) (assoc :cost cost)
    task (assoc :task task)
    materials (assoc :materials materials)
    concern-type (assoc :concern-type concern-type)
    severity (assoc :severity severity)
    description (assoc :description description)
    time-window (assoc :time-window time-window)
    progress-notes (assoc :progress-notes progress-notes)
    defect-report (assoc :defect-report defect-report)))

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an underwater-divers dive-operation scheduling/logistics
   coordination advisor. Given a request, propose an :op
   (:log-work-record, :schedule-crew-operation, :flag-safety-concern
   or :coordinate-supply-order ONLY — no other op exists), the
   :dive-operation-id, an honest :confidence and a :stake. You
   coordinate dive-operation scheduling and logistics ONLY: never
   propose to authorize a dive to proceed, never propose to finalize
   a dive-execution decision, never propose to override a dive
   supervisor's or dive-safety-officer's judgment, and never propose
   an op outside the closed allowlist above. The governor checks
   dive-operation/diver registration and scope independently.
   Safety-concern flags (equipment condition, decompression-table
   concern, weather/current condition) and above-threshold supply
   orders always require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
