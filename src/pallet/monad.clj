(ns pallet.monad
  "Pallet monads"
  (:use
   [clojure.algo.monads
    :only [domonad maybe-m monad state-t state-m with-monad]]
   [clojure.tools.macro :only [symbol-macrolet]]
   [pallet.context :only [with-context]]
   [pallet.phase :only [check-session]]))

(defn state-checking-t
  "Monad transformer that transforms a state monad m into a monad that check its
  state."
  [m checker]
  (monad [m-result (with-monad m
                     m-result)
          m-bind   (with-monad m
                     (fn m-bind-state-checking-t [stm f]
                       (fn state-checking-t-mv [s]
                         (checker s)
                         (let [[_ ss :as r] ((m-bind stm f) s)]
                           (checker ss)
                           r))))
          m-zero   (with-monad m
                     (if (= ::undefined m-zero)
                       ::undefined
                       (fn [s]
                         m-zero)))
          m-plus   (with-monad m
                     (if (= ::undefined m-plus)
                       ::undefined
                       (fn [& stms]
                         (fn [s]
                           (apply m-plus (map #(% s) stms))))))]))

(def
  ^{:doc
    "The pallet session monad. This is fundamentally a state monad, where
the state is the pallet session map."}
  session-m ;; (state-t maybe-m)
  (state-checking-t state-m check-session))

;; (def
;;   ^{:doc "The pallet session sequence monad"}
;;   session-seq-m
;;   (sequence-t state-m))

;;; state accessors
(defn update-in-state
  "Return a state-monad function that replaces the current state by the result
of f applied to the current state and that returns the old state."
  [ks f & args]
  (fn [s] [s (apply update-in s ks f args)]))

(defn assoc-state
  "Return a state-monad function that replaces the current state by the result
of assoc'ing the specified kw-value-pairs onto the current state, and that
returns the old state."
  [& kw-value-pairs]
  (fn [s]
    {:pre [(map? s)]}
    [s (apply assoc s kw-value-pairs)]))

(defn dissoc-state
  "Return a state-monad function that removes the specified keys from the
current state, and returns the old state"
  [& keys]
  (fn [s] [s (apply dissoc s keys)]))

(defn get-state
  "Return a state-monad function that gets the specified key from the current
state."
  ([k default]
     (fn [s] [(get s k default) s]))
  ([k]
     (get-state k nil)))

(defn get-in-state
  "Return a state-monad function that gets the specified key from the current
state."
  ([ks default]
     (fn [s] [(get-in s ks default) s]))
  ([ks]
     (get-in-state ks nil)))

(defn get-session
  "Return a state-monad function that gets the current sessin."
  []
  (fn [s] [s s]))


;;; comprehensions
(defmacro let-s
  "A monadic comprehension using the session monad."
  [& body]
  `(symbol-macrolet [~'update-in update-in-state
                     ~'assoc assoc-state
                     ~'get get-state
                     ~'get-in get-in-state
                     ~'dissoc dissoc-state]
     (domonad session-m ~@body)))

(defmacro chain-s
  "Defines a monadic comprehension under the session monad, where return value
  bindings can be dropped . Any vector in the arguments is expected to be of the
  form [symbol expr] and becomes part of the generated monad comprehension."
  [& args]
  (letfn [(gen-step [f]
            (if (vector? f)
              f
              [(gensym "_") f]))]
    `(let-s
      [~@(mapcat gen-step args)]
      nil)))

(defmacro wrap-pipeline
  "Wraps a pipeline with one or more wrapping froms"
  [[arg] & wrappings-and-pipeline]
  `(fn [~arg]
     ~(reduce
       #(concat %2 [%1])
       (list (last wrappings-and-pipeline) arg)
       (reverse (drop-last wrappings-and-pipeline)))))

(defmacro session-pipeline-fn
  "Defines a session pipeline. This composes the body functions under the
  session-m monad. Any vector in the arguments is expected to be of the form
  [symbol expr] and becomes part of the generated monad comprehension."
  [name & args]
  `(wrap-pipeline [session#]
    (with-context :session-pipeline ~name)
    (chain-s ~@args)))

(defmacro session-pipeline
  "Build and call a session pipeline"
  [name session & args]
  `(let [name# ~name
         session# ~session]
     (second ((session-pipeline-fn ~name ~@args) session#))))


;; (defmacro for-s
;;   "A for comprehension, for use within the session monad."
;;   [& body]
;;   `(monads/domonad session-seq-m ~@body))

;;; helpers
(defn as-session-pipeline-fn
  "Converts a function of session -> session and makes it a monadic value under
  the state monad"
  [f] #(vector nil (f %)))

(defmacro session-peek-fn
  "Create a state-m monadic value function that examines the session, and
  returns nil."
  [[sym] & body]
  `(fn [~sym] ~@body [nil ~sym]))
