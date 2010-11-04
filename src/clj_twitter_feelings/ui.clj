(ns clj-twitter-feelings.ui
  (:import [java.awt Dimension Color Font]
           [java.awt.event KeyEvent KeyAdapter]
           [javax.imageio ImageIO]
           [javax.swing JPanel JFrame JLabel JDialog JTextField JPasswordField
                        JButton JOptionPane Timer WindowConstants UIManager]
           [org.jfree.chart ChartFactory ChartPanel]
           [org.jfree.chart.labels StandardPieSectionLabelGenerator]
           [org.jfree.chart.plot PiePlot]
           [org.jfree.data.general DefaultPieDataset]
           [org.jfree.data.time Millisecond TimeSeries TimeSeriesCollection]
           [org.jfree.ui RefineryUtilities])
  (:use [clj-twitter-feelings.core]
        [clojure.java.io :only (resource)]
        [clojure.contrib
          [miglayout :only (miglayout)]
          [swing-utils :only (add-action-listener add-key-typed-listener
                              do-swing)]])
  (:gen-class))

(JFrame/setDefaultLookAndFeelDecorated true)
(JDialog/setDefaultLookAndFeelDecorated true)
(UIManager/setLookAndFeel
  "org.jvnet.substance.skin.SubstanceModerateLookAndFeel")

(let [message-type {
        :error    JOptionPane/ERROR_MESSAGE
        :info     JOptionPane/INFORMATION_MESSAGE
        :warn     JOptionPane/WARNING_MESSAGE
        :question JOptionPane/QUESTION_MESSAGE
        :plain    JOptionPane/PLAIN_MESSAGE
     }]
  (defn show-message [frame message title type]
    (JOptionPane/showMessageDialog frame message title (type message-type))))

(defn exit-app [^JFrame frame]
  (doto frame (.setVisible false) (.dispose))
  (System/exit 0))

(defn create-auth-input-dialog
  "Creates a JDialog to take the input of username and password from the user.
  Supports the <ENTER> and <ESCAPE> keys for OKing and Cancelling the dialog
  respectively.
  Returns the dialog.

  Arguments are:

    parent: the parent frame
    dialog-title: the title of the dialog
    dialog-message: the message shown in the dialog
    dialog-width : the width of the dialog
    dialog-height: the height of the dialog
    username-lbl-text: the text shown on the username label
    password-lbl-text: the text shown on the password label
    input-field-size: the size of the username and password input fields
    ok-btn-text: the text shown on the ok button
    cancel-btn-text: the text shown on the cancel button
    validation-fn: a function which is called to validate the user input when
      the user presses the ok button.
      the function is called with arguments: username, password, this dialog.
      if the function return a string, it is shown on the dialog as the error
      message and the dialog remains visible. otherwise ok-fn is called.
      this function is called in the swing event thread.
    ok-fn: a function which is called when the user presses ok button and the
      input is valid as per the call to validation-fn.
      the function is called with arguments: username, password, this dialog.
      the dialog is hidden before the call.
      this function is called in the swing event thread.
    cancel-fn: a function which is called when the user presses cancel button.
      the function is called with arguments: this dialog.
      the dialog is hidden before the call.
      this function is called in the swing event thread.
  "
  [^JFrame parent
   ^String dialog-title ^String dialog-message dialog-width dialog-height
   ^String username-lbl-text ^String password-lbl-text input-field-size
   ^String ok-btn-text ^String cancel-btn-text
   validation-fn ok-fn cancel-fn]
  (let [dialog (JDialog. parent dialog-title true)
        username-input (JTextField. (int input-field-size))
        password-input (JPasswordField. (int input-field-size))
        validation-msg-lbl (JLabel. " ")
        ok-btn
          (doto (JButton. ok-btn-text)
            (add-action-listener
              (fn [_]
                (let [username (.getText username-input)
                      password (.getText password-input)]
                  (if-let [validation-msg
                            (validation-fn username password dialog)]
                    (.setText validation-msg-lbl validation-msg)
                    (do (.setText validation-msg-lbl " ")
                      (.setVisible dialog false)
                      (ok-fn username password dialog)))))))
        cancel-btn
          (doto (JButton. cancel-btn-text)
            (add-action-listener
              (fn [_]
                (.setVisible dialog false)
                (cancel-fn dialog))))]
    (doseq [^JTextField in [username-input password-input]]
      (.addKeyListener in
        (proxy [KeyAdapter] []
          (keyTyped [^KeyEvent e]
            (when (= (.getKeyChar e) \newline)
              (.doClick ok-btn)))
          (keyPressed [^KeyEvent e]
            (when (= (.getKeyCode e) KeyEvent/VK_ESCAPE)
              (.doClick cancel-btn))))))
    (doto dialog
      (.setDefaultCloseOperation JDialog/DO_NOTHING_ON_CLOSE)
      (.setContentPane
        (miglayout (JPanel.)
          :layout {:wrap 2}
          (JLabel. dialog-message) {:span 2 :align "center"}
          (JLabel. username-lbl-text) username-input
          (JLabel. password-lbl-text) password-input
          validation-msg-lbl {:span 2 :align "center"}
          (miglayout (JPanel.) ok-btn cancel-btn) {:span 2 :align "center"}
      ))
      (.setSize dialog-width dialog-height))))

(defn init-gui [adjective-map]
  (let [frame (doto (JFrame. "Twitter Feelings")
                (.setIconImage
                  (ImageIO/read (resource "clj_twitter_feelings/favicon.jpg")))
                (.setDefaultCloseOperation WindowConstants/EXIT_ON_CLOSE)
                (.setResizable false))

        adjective-types (sort (keys @adjective-type-count))

        ^DefaultPieDataset pie-dataset
          (reduce
            (fn [^DefaultPieDataset ds ^String key] (doto ds (.setValue key 0)))
            (DefaultPieDataset.) adjective-types)
        pie-chart (ChartFactory/createPieChart
                    "Distribution" pie-dataset true false false)
        pie-chart-panel (doto (ChartPanel. pie-chart)
                          (.setPreferredSize (Dimension. 500 400)))

        time-series-map
          (into (sorted-map)
            (map #(vector % (TimeSeries. % Millisecond)) adjective-types))
        time-series-dataset
          (reduce #(doto ^TimeSeriesCollection %1 (.addSeries %2))
            (TimeSeriesCollection.) (vals time-series-map))
        time-series-chart
          (ChartFactory/createTimeSeriesChart
            "History" "Time" "Percentage" time-series-dataset
            true false false)
        time-series-chart-panel
          (doto (ChartPanel. time-series-chart)
            (.setPreferredSize (Dimension. 550 400)))

        adjective-lbl (JLabel. "<html><h2>...</h2></html>")
        status-lbl (doto (JLabel. " ")
                      (.setFont (Font/getFont "Arial Unicode MS")))

        timer (doto (Timer. 1000 nil)
                (add-action-listener
                  (fn [_]
                    (let [a-count @adjective-type-count
                          total (reduce + 0 (vals a-count))]
                      (doseq [[^String k v] a-count]
                        (.setValue pie-dataset k (double v))
                        (.add ^TimeSeries (time-series-map k)
                          (Millisecond.) (* 100 (safe-divide v total))))))))

        ^JDialog auth-input-dialog
          (create-auth-input-dialog frame
            "Credentials" "Input your Twitter credentials" 250 150
            "Screen Name" "Password" 20
            "OK" "Cancel"
            ;validation-fn
            (fn [uname pass _]
              (when (or (empty? uname) (empty? pass))
                (str "Screen Name and Password cannot be empty!")))
            ;ok-fn
            (fn [uname pass ^JDialog dialog]
              (future
                (try
                  (process-tweet-stream
                    (tweet-stream (twitter-stream-client uname pass) "sample")
                    [(adjective-processor adjective-map) (status-processor)])
                  (catch Exception e
                    (do-swing
                      (.stop timer)
                      (show-message frame
                        (str "Error happened: " (.getMessage e)
                          ".\nPlease restart.")
                        "Error" :error)
                      (.setVisible dialog true)))))
              (.start timer))
            ;cancel-fn
            (fn [_] (exit-app frame)))]

    ;add watches
    (add-watch adjective-seen :adjective-lbl
      (fn [_ _ _ adj]
        (do-swing
          (.setText adjective-lbl (str "<html><h2>" adj "</h2></html>")))))
    (add-watch status-seen :status-lbl
      (fn [_ _ _ sts] (do-swing (.setText status-lbl sts))))

    ;do some configuration of the plots
    (doto ^PiePlot (.getPlot pie-chart)
      (.setNoDataMessage "No data available")
      (.setLabelGenerator (StandardPieSectionLabelGenerator. "{0} {2}"))
      (.setBackgroundPaint (Color. 238 238 238)))
    (doto (.getXYPlot time-series-chart)
      (.setBackgroundPaint (Color. 238 238 238)))
    (doto (.. time-series-chart getXYPlot getDomainAxis)
      (.setAutoRange true)
      (.setFixedAutoRange 120000.0))
    (doto (.. time-series-chart getXYPlot getRangeAxis)
      (.setRange 0.0 100.0))

    ;layout the content in the frame and make it visible
    (doto frame
      (.setContentPane
        (miglayout (JPanel.)
          :layout {:wrap 1}
          (miglayout (JPanel.)
            :layout {:wrap 2}
            (JLabel. "<html><h1>How is Twitter feeling now?</h1></html>")
              {:align "left"}
            adjective-lbl {:align "right"}
            pie-chart-panel time-series-chart-panel)
          status-lbl {:align "left"}))
      (.pack)
      (.setVisible true)
      (RefineryUtilities/centerFrameOnScreen))

    ;show the auth-input-dialog
    (doto auth-input-dialog
      (RefineryUtilities/centerFrameOnScreen)
      (.setVisible true))))

(defn -main [& args]
  (let [adjective-map (adjectives)]
    (reset! adjective-type-count
      (zipmap (distinct (vals adjective-map)) (repeat 0)))
    (do-swing (init-gui adjective-map))))
