(ns lambdaci.git)

(defn git [giturl]
  (fn []
    (println (str "Checking out " giturl))
    { :changed true
      :artifacts [ "/tmp/workspace/**/*"]}))
