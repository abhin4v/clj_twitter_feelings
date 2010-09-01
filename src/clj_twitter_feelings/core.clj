(ns clj-twitter-feelings.core
  (:import [java.io File BufferedReader]
           [clojure.lang PersistentQueue]
           [org.apache.http HttpException]
           [org.apache.http.auth AuthScope UsernamePasswordCredentials]
           [org.apache.http.client.methods HttpGet]
           [org.apache.http.client ResponseHandler HttpClient]
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

(def adjective-files ["negative" "neutral" "positive"])

(defn adjectives []
  (->> adjective-files
    (map #(str "clj_twitter_adjectives/adjectives/" % ".txt"))
    (map resource)
    (reduce
      (fn [acc ^java.net.URL url]
        (let [adjective-type
                (-> url .toString (.split "/") last (.split "\\.") first)]
          (reduce
            (fn [acc word]
              (assoc! acc word adjective-type))
            acc
            (read-lines url))))
      (transient {}))
    (persistent!)))

(defn safe-divide [n d] (if (zero? d) 0 (float (/ n d))))

(def split-pattern (re-pattern "[\\p{Z}\\p{C}\\p{P}]+"))

(defn tokenize-line [line]
  (->> line (split split-pattern) (filter (complement empty?))))

(defprotocol Processor
  (process [this tweet]))

(defn twitter-stream-client [username password]
  (doto (DefaultHttpClient.)
    (.. getCredentialsProvider
      (setCredentials
        (AuthScope. "stream.twitter.com" 80)
        (UsernamePasswordCredentials. username password)))))

(defn tweet-stream [^HttpClient client method & params]
  (let [read-line (fn this [^BufferedReader rdr]
                    (lazy-seq
                     (if-let [line (.readLine rdr)]
                       (cons line (this rdr))
                       (.close rdr))))
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

(defn process-tweet-stream [stream processors]
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
  (let [states (atom (PersistentQueue/EMPTY))]
    (reify Processor
      (process [this tweet]
        (let [adj-typs
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
                  (if (<= (count @states) *tweet-window-size*)
                    (do (swap! states conj current-state)
                      (merge-with + state current-state))
                    (let [old-state (peek @states)]
                      (swap! states #(conj (pop %) current-state))
                      (merge-with #(max 0 (- %1 %2))
                        (merge-with + state current-state)
                        old-state))))))))))))
