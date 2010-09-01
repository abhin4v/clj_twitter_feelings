# clj-twitter-feelings

Shows how people on twitter are feeling, in real-time.
Meant to be an example of a swing app in Clojure.

## Usage

Download the standalone jar and run it like:

    java -jar clj-twitter-feelings-1.0.0-standalone.jar

## How it works

* Access the twitter sample tweet stream 
* Find the feeling related adjectives in the tweet status and find their type
(Positive, Neutral, Negative)
* Keep the count of the adjective types in a sliding windows of tweets
* Show the count on the UI
    
## License

Copyright (C) 2010 Abhinav Sarkar <abhinav@abhinavsarkar.net>

Distributed under the Eclipse Public License, the same as Clojure.
