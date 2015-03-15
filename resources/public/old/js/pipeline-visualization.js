var triggerManualStep = function(triggerId,parameters){
  $.ajax({
      url:"api/dynamic/"+triggerId,
      type:"POST",
      contentType: "application/json",
      dataType: "json",
      processData: false,
      data: JSON.stringify(parameters)
    }).done(function(data) {
      alert("triggered");
    });
}

var buildToDisplay = function(all) {
    var queryString = window.location.search;
    var offset = queryString.indexOf("build");
    if (offset > -1) {
        return queryString.substr(offset+"build=".length);
    } else {
        var mostRecentBuild = Object.keys(all).sort().reverse()[0]
        window.location.search="build="+mostRecentBuild;
        return mostRecentBuild;
    }
}

var updateServerState = function() {
  $.ajax({url:"api/pipeline-state"}).done(function(all) {
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
        var triggerElem = stepElem.find(".trigger");
        triggerElem.off();
        triggerElem.on("click",function() {
          var parameters = stepResult["parameters"];
          var parameterValues = {};
          if (parameters !== undefined) {
            $.each(parameters,function(parametername,config) {
              parameterValues[parametername] = prompt("Pleaser enter a value for "+parametername+":"+config.desc)
            });
          }
          triggerManualStep(triggerId,parameterValues);
        })
      }

      var retriggerElem = stepElem.find(".retrigger");
      retriggerElem.off();
      retriggerElem.on("click",function() {
        var stepIdParts = stepid
                            .replace("(","")
                            .replace(")","")
                            .split(" ");

        if (stepIdParts.length>1) {
          alert("nested steps cant be retriggered yet");
        } else if (stepIdParts.length == 0) {
          alert("no step selected")
        } else if (buildtodisplay === undefined) {
          alert("build not finished yet")
        } else {
          $.ajax({url:"api/builds/"+buildtodisplay+"/"+stepIdParts[0]+"/retrigger", type:"POST"}).done(function(data) {
              alert("retriggered");
          });
        }
      });
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
      stepResult = '<span>'+step.name+'</span><i class="fa fa-play trigger"></i><i class="fa fa-repeat retrigger"></i>'
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
