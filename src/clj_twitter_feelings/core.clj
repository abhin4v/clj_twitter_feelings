(ns clj-twitter-feelings.core
  (:import [java.io File BufferedReader]
           [clojure.lang PersistentQueue]
           [org.apache.http HttpException]
           [org.apache.http.auth AuthScope UsernamePasswordCredentials]
           [org.apache.http.client.methods HttpGet]
           [org.apache.http.client HttpClient]
           [org.apache.http.impl.client DefaultHttpClient]
           [org.apache.http.params BasicHttpParams HttpParams])
  (:use [clojure.java.io :only (reader resource as-file)]
        [clojure.contrib
          [core :only (-?>)]
          [string :only (split lower-case)]
          [duck-streams :only (read-lines)]
          [json :only (read-json)]]))

(defmulti trace (fn [_ f _] f))
(defmethod trace :l [msg _ arg] (do (println msg ":" arg) arg))
(defmethod trace :f [arg _ msg] (do (println msg ":" arg) arg))

;names of adjective files
(def adjective-files ["negative" "neutral" "positive"])

(defn adjectives
  "Loads the adjective files and returns a map of adjective to its type."
  []
  (->> adjective-files
    (map #(str "clj_twitter_feelings/adjectives/" % ".txt"))
    (map resource)
    (reduce
      (fn [acc ^java.net.URL url]
        (let [adjective-type
                (-> url .toString (.split "/") last (.split "\\.") first)]
          (reduce
            (fn [acc word] (assoc! acc word adjective-type))
            acc
            (read-lines url))))
      (transient {}))
    (persistent!)))

(defn safe-divide
  "If denominator is 0 returns 0 else returns numerator divided by denominator."
  [n d] (if (zero? d) 0 (float (/ n d))))

(def split-pattern (re-pattern "[\\p{Z}\\p{C}\\p{P}]+"))

(defn tokenize-line
  "Tokenizes the line on split-pattern and returns a lazy seq of non-empty
  tokens."
  [line]
  (->> line (split split-pattern) (filter (complement empty?))))

(defprotocol Processor
  (process [this tweet]))

(defn twitter-stream-client
  "Creates an HttpClient for stream.twitter.com using the credentials provided."
  [username password]
  (doto (DefaultHttpClient.)
    (.. getCredentialsProvider
      (setCredentials
        (AuthScope. "stream.twitter.com" 80)
        (UsernamePasswordCredentials. username password)))))

(defn tweet-stream
  "Creates a lazy stream of live tweets."
  [^HttpClient client method & params]
  (let [read-line (fn this [^BufferedReader rdr]
                    (lazy-seq
                     (if-let [line (.readLine rdr)]
                       (cons line (this rdr))
                       (do (.close rdr)
                        (.. client getConnectionManager shutdown)))))
        baseurl "http://stream.twitter.com/1/statuses/"
        url (str baseurl method ".json")
        http-params
          (reduce (fn [^HttpParams hp [k v]] (.setParameter hp (name k) v))
            (BasicHttpParams.) (partition 2 params))
        request (doto (HttpGet. url) (.setParams http-params))
        response (.execute client request)
        status-code (.. response getStatusLine getStatusCode)]
    (if (= status-code 200)
      (if-let [rdr (-?> response .getEntity .getContent reader)]
        (map #(read-json % true) (read-line rdr)))
      (throw (HttpException.
                (str "Invalid Status code: " status-code))))))

(defn process-tweet-stream
  "Processes the tweet stream with the provided processors."
  [stream processors]
  (doseq [tweet stream]
    (future (doseq [p processors] (process p tweet)))))

(def status-seen (atom nil))

(defn status-processor []
  (reify Processor
    (process [this tweet]
      (reset! status-seen
        (str (-> tweet :user :screen_name) ": " (:text tweet))))))

(def adjective-type-count (atom {}))

(def adjective-seen (atom nil))

(def *tweet-window-size* 25)

(defn adjective-processor [adjective-map]
  (let [state-window (atom (PersistentQueue/EMPTY))]
    (reify Processor
      (process [this tweet]
        (let [adj-typs                  ;list of types of adjective in the tweet
                (->> tweet :text
                  tokenize-line
                  (map lower-case)
                  (map #(vector % (adjective-map %)))
                  (filter #(-> % second nil? not))
                  ;(map #(do (println %) %))
                  (map #(do (reset! adjective-seen (first %)) %))
                  (map second))]
          (when-not (empty? adj-typs)
            (let [current-state
                    (reduce #(assoc %1 %2 (inc (get %1 %2 0))) {} adj-typs)]
              (swap! adjective-type-count
                (fn [state]
                  ;If the count of states in the state-window is less than
                  ;*tweet-window-size*, push the current-state in the
                  ;state-window and return merge result: (state + current-state).
                  ;Else, pop the oldest state from the state-window, push the
                  ;current-state in the state-window and return merge result:
                  ;(state + current-state - popped out oldest state).
                  (if (<= (count @state-window) *tweet-window-size*)
                    (do (swap! state-window conj current-state)
                      (merge-with + state current-state))
                    (let [old-state (peek @state-window)]
                      (swap! state-window #(conj (pop %) current-state))
                      (merge-with #(max 0 (- %1 %2))
                        (merge-with + state current-state)
                        old-state))))))))))))
