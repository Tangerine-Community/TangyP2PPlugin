var exec = require('cordova/exec');


exports.getPermission = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'getPermission', [arg0]);
};
exports.startAdvertising = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'startAdvertising', [arg0]);
};
exports.startDiscovery = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'startDiscovery', [arg0]);
};
exports.transferData = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'transferData', [arg0]);
};


