var triggerManualStep = function(triggerId){
  $.ajax({url:"/api/dynamic/"+triggerId, type:"POST"}).done(function(data) {
    alert("triggered");
  });
}

var updateServerState = function() {
  $.ajax({url:"/api/pipeline-state"}).done(function(data) {
    // TODO: clear all status

    Object.keys(data).forEach(function(stepid) {
      var stepResult = data[stepid]
      var stepElem = findByStepId(stepid);
      stepElem.data("status",stepResult.status);
      stepElem.data("output",stepResult.out);
      var triggerId = stepResult["trigger-id"];
      if (triggerId) {
        stepElem.off();
        stepElem.on("click",function() {
          triggerManualStep(triggerId);
        })
      }
    })
    setTimeout(updateServerState,500);
  })
}

var stepIdToArray = function(id) {
  return id.replace("(","").replace(")","").split(" ");
}

var findByStepId = (function() {
  return function(idString) {
    var stepid = stepIdToArray(idString);
    var curPos = $("#pipeline");
    do {
      var idx = stepid.pop()-1;
      curPos = $(curPos.children("ol, ul").children("li").get(idx))
    } while (stepid.length > 0)
    return curPos;
  }
})()

var pipelineHtml = (function(){

  var renderStep = function(step) {
    var stepResult = "";
    if (step.type === "parallel") {
      stepResult = step.name+"<ul>"+step.children.map(renderStep).join("")+"</ul>";
    } else if (step.type === "container") {
      stepResult = step.name+"<ol>"+step.children.map(renderStep).join("")+"</ol>";
    } else {
      stepResult = step.name
    }

    return "<li>"+stepResult+"</li>";
  }

  return function(pipeline) {
    var result = "<ol>";

    result += pipeline.map(renderStep).join("");

    result+="</ol>";
    return result;
  }
})()
