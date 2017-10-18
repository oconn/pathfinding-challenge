(ns main
  (:require [clojure.data.json :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Arguments
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def state
  (json/read-str (slurp "resources/arena.json")
                 :key-fn keyword))

(def time-left 3000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pathfinding
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-in-arena
  "pulls the cell contents out of an arena at given coords"
  {:added "1.0"}
  [[x y] arena]
  (get-in arena [y x]))

(defn add-self
  "Adds local choords to the current view of the arean"
  [{:keys [local-coords arena] :as enriched-state}]
  (let [self (get-in-arena local-coords arena)]
    (assoc enriched-state :self self)))

(defn to-global-coords
  "Converts local coordinates passed by the partially occluded arena
  to their corresponding global coordinates"
  {:added "1.0"}
  [{[origin-x origin-y] :local-coords
    [global-x global-y] :global-coords
    [dim-x dim-y] :global-dimensions}]
  (fn [[target-x target-y]]
    (let [delta-x (- target-x origin-x)
          delta-y (- target-y origin-y)
          new-x (mod (+ global-x delta-x) dim-x)
          new-y (mod (+ global-y delta-y) dim-y)]
      [new-x new-y])))

(defn get-first-frontier
  [{:keys [local-coords arena]}]
  (let [{{orientation-str :orientation
          uuid :uuid} :contents} (get-in-arena local-coords arena)]
    {:coords local-coords
     :orientation (keyword orientation-str)
     :uuid uuid
     :weight 0
     :action-sequence []}))

(defn modify-orientation
  "Return a new orientation based off a provided orientation and the direction
  you want to turn"
  {:added "1.0"
   :defined "wombats.game.utils"}
  [current-orientation modifier]

  (def ^:private orientations [:n :e :s :w])

  (let [current-idx (.indexOf orientations current-orientation)]
    (if (not= current-idx -1)
      (condp = modifier
        :right (get orientations (mod (inc current-idx) 4))
        :left (get orientations (mod (dec current-idx) 4))
        :about-face (get orientations (mod (+ 2 current-idx) 4))
        current-orientation)
      current-orientation)))

(defn calculate-turn-frontiers
  [{:keys [orientation coords weight action-sequence]}]
  (map (fn [next-direction]
         {:orientation (modify-orientation orientation next-direction)
          :coords coords
          :weight (inc weight)
          :action-sequence (conj action-sequence {:action :turn
                                                  :metadata {:direction next-direction}})})
       [:right :left :about-face]))

(defn get-move-coords
  "Gets the updated coords for moving.
  :Note wrapping not assumed."
  {:added "1.0"}
  [[x y] orientation]
  (case (keyword orientation)
    :n [x (dec y)]
    :e [(inc x) y]
    :s [x (inc y)]
    :w [(dec x) y]))

(defn get-move-frontier-coords
  "Returns the coords from the move command"
  {:added "1.0"}
  ([coords orientation dimensions]
   (get-move-frontier-coords coords orientation dimensions false))
  ([[x y] orientation [dim-x dim-y] wrap?]
   (let [new-coords (get-move-coords [x y] orientation)
         [new-x new-y] new-coords]

     (if wrap?
       (case (keyword orientation)
         (:n :s) [new-x (mod new-y dim-y)]
         (:e :w) [(mod new-x dim-x) new-y])
       (case (keyword orientation)
         :n (if (< new-y 0) nil new-coords)
         :w (if (< new-x 0) nil new-coords)
         :e (if (> new-x (dec dim-x)) nil new-coords)
         :s (if (> new-y (dec dim-y)) nil new-coords))))))

(defn calculate-move-frontier
  [{:keys [orientation coords weight action-sequence]}
   arena-dimensions
   wrap?]
  (let [coords (get-move-frontier-coords coords orientation arena-dimensions wrap?)]
    (when coords
      {:orientation orientation
       :coords coords
       :weight (inc weight)
       :action-sequence (conj action-sequence {:action :move})})))

(defn get-arena-dimensions
  "returns the dimensions of a given arena (NOTE: NOT 0 indexed)"
  {:added "1.0"
   :defined-in "wombats.arena.utils"}
  [arena]
  (let [x ((comp count first) arena)
        y (count arena)]
    [x y]))

(defn can-safely-occupy-space?
    "Predicate used to determine what cells can pass as frontiers"
    {:added "1.0"}
    [cell]
    (not (contains? #{"wood-barrier" "steel-barrier" "fog"}
                    (get-in cell [:contents :type]))))

(defn filter-frontiers
    "Filters all the possible frontiers, returning only explore-able frontiers"
    {:added "1.0"}
    [frontiers arena explored]
    (filter (fn [{coords :coords}]
              (if (nil? coords) false
                  (let [cell (get-in-arena coords arena)
                        uuid (get-in cell [:contents :uuid])]
                    (and (nil? (get explored uuid))
                         (can-safely-occupy-space? cell))))) frontiers))

(defn calculate-frontier
  "Caclulates the new frontier set based off of the provided frontier."
  {:added "1.0"}
  ([frontier arena explored]
   ;; Default to false because this is currently only used for the partial arena
   ;; which has no notion of wrapping.
   (calculate-frontier frontier arena explored false))
  ([frontier arena explored wrap?]
   (filter-frontiers
    (conj (calculate-turn-frontiers frontier)
          (calculate-move-frontier frontier
                                   (get-arena-dimensions arena)
                                   wrap?))
    arena
    explored)))

(defn add-to-sorted-arena
    "Adds a frontier node to the sorted arena"
    {:added "1.0"}
    [sorted-arena
     {{type :type
       uuid :uuid} :contents}
     {weight :weight
      coords :coords
      action-sequence :action-sequence}]
    (let [formatted-frontier {:weight weight
                              :uuid uuid
                              :coords coords
                              :action-sequence action-sequence}]
      (update-in sorted-arena
                 [weight (keyword type)]
                 (fn [coll]
                   (if (not (nil? coll))
                     (conj coll formatted-frontier)
                     [formatted-frontier])))))

(defn sort-arena-by-distance-then-type
  "sorts an arena by distance then type"
  {:added "1.0"}
  [{:keys [arena] :as enriched-state}]
  (let [update-global-coords-fn (to-global-coords enriched-state)]
    (loop [frontier [(get-first-frontier enriched-state)]
           explored {}
           sorted-arena []]

      (if (empty? frontier)
        ;; All frontiers have been explored, break out with sorted-arena
        ;; attached to state.
        (assoc enriched-state :sorted-arena sorted-arena)

        (let [frontier-node (first frontier)
              cell (get-in-arena (:coords frontier-node) arena)
              next-frontier (calculate-frontier frontier-node
                                                arena
                                                explored)]
          (recur (vec (concat (rest frontier) next-frontier))
                 (merge explored {(get-in cell [:contents :uuid]) true})
                 (add-to-sorted-arena sorted-arena
                                      cell
                                      (update frontier-node
                                              :coords
                                              update-global-coords-fn))))))))

(defn enrich-state
  "Adds additional information to the given state used to improve
   the decision-making process"
  {:added "1.0"}
  [state]
  (-> state
      (add-self)
      (sort-arena-by-distance-then-type)))

(defn main-fn
  [state time-left]
  (-> state
      (enrich-state)))
