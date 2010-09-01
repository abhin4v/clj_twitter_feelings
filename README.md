# clj-twitter-feelings

Shows how people on twitter are feeling, in real-time.
Meant to be an example of a swing app in Clojure.

## Usage

Download [the standalone jar][1] and run it like:

    java -jar clj-twitter-feelings-1.0.0-standalone.jar

Or checkout the source and

* build using leiningen and run using Java:

    git clone git://github.com/abhin4v/clj_twitter_feelings.git clj_twitter_feelings
    cd clj_twitter_feelings
    lein deps && lein uberjar
    java -jar target/clj-twitter-feelings-1.0.0-standalone.jar

* OR run using leiningen-run plugin:

    git clone git://github.com/abhin4v/clj_twitter_feelings.git clj_twitter_feelings
    cd clj_twitter_feelings
    lein deps && lein run

## How it works

* Access the twitter sample tweet stream (the users need to input their twitter 
credentials for accessing the stream)
* Find the feeling related adjectives in each tweet's status text and lookup 
their type (Positive, Neutral, Negative) against the included list of adjectives 
in text files
* Keep the count of the adjective types in a sliding window of tweets
* Show the count on the UI

## License

Copyright (C) 2010 Abhinav Sarkar &lt;abhinav@abhinavsarkar.net&gt;

Distributed under the Eclipse Public License, the same as Clojure.

    [1] http://github.com/downloads/abhin4v/clj_twitter_feelings/clj-twitter-feelings-1.0.0-standalone.jar