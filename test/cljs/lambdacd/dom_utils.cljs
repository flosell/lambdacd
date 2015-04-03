(ns lambdacd.dom-utils
  (:require
    [dommy.core :as dommy]
    [dommy.core :refer-macros [sel sel1 by-tag]]))

(defn fire!
  "Creates an event of type `event-type`, optionally having
   `update-event!` mutate and return an updated event object,
   and fires it on `node`.
   Only works when `node` is in the DOM"
  [node event-type & [update-event!]]
  (let [update-event! (or update-event! identity)]
    (if (.-createEvent js/document)
      (let [event (.createEvent js/document "Event")]
        (.initEvent event (name event-type) true true)
        (.dispatchEvent node (update-event! event)))
      (.fireEvent node (str "on" (name event-type))
                  (update-event! (.createEventObject js/document))))))



(defn found-in [div re]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))
(defn not-found-in [div re]
  (let [res (.-innerHTML div)]
    (if (not (re-find re res))
      true
      (do (println "found: " res)
          false))))

(defn having-class [classname elem]
  (if (dommy/has-class? elem classname)
    true
    (do (println "expected " elem " to have class " classname)
        false)))

(defn containing-link-to [div href]
  (= href (first (map #(dommy/attr % :href) (by-tag div :a)))))

(defn containing-ordered-list [elem]
  (not (empty? (sel elem :ol))))

(defn containing-unordered-list [elem]
  (not (empty? (sel elem :ul))))

(defn having-data [name value elem]
  (= value (dommy/attr elem (str "data-" name))))

(defn after-click [atom elem]
  (fire! elem :click)
  @atom)