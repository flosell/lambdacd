# Changelog 

## 0.1.0-alpha3

* parameters for steps changed. the second argument is now a context-map that contains the step-id and 
  other low-level information
    * previously ```(defn some-step [args step-id])```
    * now ```(defn some-step [args {:keys [step-id] :as ctx}])```
    
