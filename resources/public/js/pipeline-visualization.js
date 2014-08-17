
var updateServerState = function() {
  $.ajax({url:"/api/pipeline-state"}).done(function(data) {
    data.running.forEach(function(stepid) {
      findByStepId(stepid).addClass("running");
    })

    data.finished.forEach(function(stepid) {
      findByStepId(stepid).addClass("finished");
    })


    setTimeout(updateServerState,500);
  })
}

var findByStepId = (function() {
  return function(stepid) {
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
