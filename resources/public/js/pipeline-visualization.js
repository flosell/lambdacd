var triggerManualStep = function(triggerId){
  $.ajax({url:"/api/dynamic/"+triggerId, type:"POST"}).done(function(data) {
    alert("triggered");
  });
}

var buildToDisplay = function(all) {
    var queryString = window.location.search;
    var offset = queryString.indexOf("build");
    if (offset > -1) {
        return queryString.substr(offset+"build=".length);
    } else {
        return Object.keys(all).sort().reverse()[0];
    }
}

var updateServerState = function() {
  $.ajax({url:"/api/pipeline-state"}).done(function(all) {
    // TODO: clear all status
    var buildtodisplay = buildToDisplay(all);
    var data = all[buildtodisplay];
    
    Object.keys(data).forEach(function(stepid) {
      var stepResult = data[stepid]
      var stepElem = findByStepId(stepid);
      stepElem.data("status",stepResult.status);
      stepElem.data("output",stepResult.out);
      stepElem.data("stepid",stepid);
      stepElem.data("build",buildtodisplay);

      var triggerId = stepResult["trigger-id"];
      if (triggerId) {
        stepElem.off();
        stepElem.on("click",function() {
          triggerManualStep(triggerId);
        })
      }
    })
    var builds = $("#builds ul");
    builds.children().remove();
    Object.keys(all).forEach(function(buildNumber) {
        builds.append("<li><a href=\"?build="+buildNumber+"\">Build #"+buildNumber+"</a></li>")
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
