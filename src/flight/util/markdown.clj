(ns flight.util.markdown
  (:use hiccup.core)
  (:require
   [markdown.core :as md]
   [clojure.string :as s]
   [flight.util.image :as image]))

(defn image-transform [text state]
  (loop [out []
         tokens (seq text)]
    (if (empty? tokens)
      [(s/join out) state]
      (let [[head xs]   (split-with (partial not= \[) tokens)
            [title ys]  (split-with (partial not= \]) xs)
            [dud zs]    (split-with (partial not= \() ys)
            [link tail] (split-with (partial not= \)) zs)]
        (if (or (< (count link) 2)
                (< (count tail) 1)
                (> (count dud) 1))
          (recur (concat out head title dud link) tail)
          (recur
           (into out
                 (let [alt (s/join (rest title))
                       [url title] (split-with (partial not= \space) (rest link))
                       title (s/join (rest title))]
                   (concat (butlast head) (image/img (s/join url) (s/join title) alt))))
           (rest tail)))))))

(defn radio [label text selected]
  [:label [:input (conj {:name label :type "radio"} (when selected {:checked "checked"}))] (s/trim text)])

(defn checkbox [label text selected]
  [:label [:input (conj {:name label :type "checkbox"} (when selected {:checked "checked"}))] (s/trim text)])

;;range is exclusive
(defn dob [label]
  (list [:select {:name "month"} (map #(vector :option {:value %} %) (range 1 13))]
        "-"
        [:select {:name "day"} (map #(vector :option {:value %} %) (range 1 32))]
        "-"
        [:select {:name "year"} (map #(vector :option {:value %} %) (range 1980 1994))]))

(defn height [label]
  (list [:select {:name "feet"} (map #(vector :option {:value %} %) (range 4 8))]
        [:select {:name "inches"} (map #(vector :option {:value %} %) (range 0 13))]))

(defn option [text]
  (let [options (s/split text #"-&gt;")
        value (s/trim (first options))
        selected (and (= (first value) \() (= (last value) \)))
        label (or (second options) value)]
    [:option (conj {:value (if selected (s/join (rest (drop-last value))) value) :label label} (when selected {:selected "selected"}))]))

(defn input-text [label text]
  (let [placeholder (second (s/split text #"\#"))]
    [:label label [:br] [:input (conj {:name label :type "text"} (when (not (empty? placeholder)) {:placeholder placeholder}))]]))

(defn weight [label]
  (list [:select {:name "weight"} (map #(vector :option {:value %} %) (range 80 321))]))

(defn picture [label]
  (list [:input {:type "file" :accept "image/jpeg" :name label}]))

(defn trim-fl [string]
  {:pre [(string? string)]}
  (s/join (rest (drop-last string))))

(defonce fns {"DOB" dob "height" height "weight" weight "picture" picture})

(defn input-transform [text state]
  (let [[label tail] (split-with (partial not= \=) (seq text))
        tail (rest tail)
        tails (s/trim (s/join tail))
        label (s/trim (s/join label))]
    (if (empty? label)
      [text state]
      (cond
       (= (count (re-seq #"\_" tails)) 3) [(html (input-text label tails)) state]
       (> (count (re-seq #"\( \)|\(x\)+" tails)) 1)
         [(html (map #(if (not (empty? %)) (if (= (take 3 %) (seq "(x)")) (radio label (s/join (drop 3 %)) true) (radio label % false))) (s/split tails #"\( \)"))) state]
       (> (count (re-seq #"\[ \]|\[x\]+" tails)) 1)
         [(html (map #(if (not (empty? %)) (if (= (take 3 %) (seq "[x]")) (checkbox label (s/join (drop 3 %)) true) (checkbox label % false))) (s/split tails #"\[ \]"))) state]
       (and (= (first tails) \{) (= (last tails) \})) [(html [:label label [:select {:name label} (map #(option %) (s/split (trim-fl tails)  #","))]]) state]
       (and (= (first tails) \[) (= (last tails) \]) (not (nil? (fns (trim-fl tails))))) [(html [:label label ((fns (trim-fl tails)) label)]) state]
       :else [text state]
       )
      )
    ))

(defn md->html
    "reads a markdown string and returns the html"
    [string]
  (md/md-to-html-string string :custom-transformers [input-transform
                                                     image-transform]))
