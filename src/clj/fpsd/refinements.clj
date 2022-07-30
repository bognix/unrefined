(ns fpsd.refinements
  (:require [manifold.stream :as s]
            [fpsd.estimator :as estimator]))

(def default-settings {:max-points-delta 3
                       :voting-style :linear ;; or :fibonacci
                       :max-rediscussions 1
                       :suggestion-strategy :majority})

(def refinements_ (atom {}))

(comment
  (identity @refinements_)
  (identity (get @refinements_ "OKCAVG"))
  ,)

(defn details
  [code]
  (get @refinements_ code))

(defn ticket-details
  [code ticket-id]
  (-> code details :tickets (get ticket-id)))

(defn gen-random-code [length]
  (loop []
    (let [code (apply str (take length (repeatedly #(char (+ (rand 26) 65)))))]
      (if (get @refinements_ code)
        (recur)
        code))))

(defn user-connected
  "Every refinement session have an event-sink (a stream in manifold,
   or chan in core.async);
   once users land to a refinement page a stream is created to send them events,
   this stream is connected to the event-synk so, every message sent to it will
   be dispatched to user streams."
  [code]
  (let [user-stream (s/stream)
        {event-sink :event-sink} (details code)]
    (s/connect event-sink user-stream)
    user-stream))

;; refinements code
(defn create!
  "Return a new refinement with its own unique code, the provided
   settings, the owner name and id; the newly created refinement
   is atomically added to the global list of active refinements."
  [owner-id settings]
  (let [code (gen-random-code 6)
        refinement {:code code
                    :settings (merge default-settings settings)
                    :tickets {}
                    :owner owner-id
                    :participants {}
                    :event-sink (s/stream)}]
    (swap! refinements_ assoc code refinement)
    refinement))

(defn new-empty-session
  []
  {:status :open
   :result nil
   :votes {}
   :skips #{}})

(defn new-ticket
  [ticket-id ticket-url]
  {:id ticket-id
   :status :unrefined
   :result nil
   :current-session (new-empty-session)
   :sessions []
   :link-to-original ticket-url})

(defn add-ticket!
  [code ticket]
  (swap! refinements_ update-in [code :tickets] assoc (:id ticket) ticket)
  ticket)

(defn add-new-ticket!
  [code ticket-id ticket-url]
  (add-ticket! code (new-ticket ticket-id ticket-url)))

(defn set-participant
  [code user-id name]
  (swap! refinements_ update-in [code :participants] assoc user-id name))

(comment
  (add-ticket! "OKCAVG" "PE-1234")
  (identity (get @refinements_ "OKCAVG"))
  ,)

(defn count-voted
  [ticket]
  (count (-> ticket :current-session :votes)))

(defn count-skipped
  [ticket]
  (count (-> ticket :current-session :skips)))

(defn vote-ticket
  "Store that a user voted and send an event accordingly"
  [code ticket-id user-id vote]
  (swap! refinements_
         update-in [code :tickets ticket-id :current-session]
         (fn [session]
           (-> session
               (update :skips disj user-id)
               (update :votes assoc user-id vote)))))

(defn skip-ticket
  "Store that a user skipped voting and send an event accordingly"
  [code ticket-id user-id]
  (swap! refinements_
         update-in [code :tickets ticket-id :current-session]
         (fn [session]
           (-> session
               (update :skips conj user-id)
               (update :votes dissoc user-id)))))

(defn new-estimation-for-ticket
  "Given a ticket, move the current estimation to the :sessions list
   and set :current-session to an empty session.
   Returns the updated ticket."
  [ticket]
  (-> ticket
      (update :sessions conj (:current-session ticket))
      (assoc :current-session (new-empty-session))))

(defn re-estimate-ticket
  "Updates the ticket to start a new estimation and send an event to clients"
  [code ticket-id]
  (swap! refinements_ update-in [code :tickets ticket-id]
         new-estimation-for-ticket))
