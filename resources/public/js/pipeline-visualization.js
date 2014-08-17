

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
