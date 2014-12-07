'use strict';

module.exports = function (task, msg, taskOptions) {
  if (task && msg !== false) {
    msg = (typeof msg === 'string') ? msg : '';
    if (taskOptions.aliases && taskOptions.aliases.length > 0) {
      if (msg.length > 0) {
        msg += ' ';
      }
      msg += 'Aliases: ' + taskOptions.aliases.join(', ');
    }

    task.help = {
      message: msg,
      options: taskOptions.options || {}
    };
  }
};