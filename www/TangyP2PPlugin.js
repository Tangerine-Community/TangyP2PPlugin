var exec = require('cordova/exec');


exports.getPermission = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'getPermission', [arg0]);
};
exports.init = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'init', [arg0]);
};
exports.startRegistration = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'startRegistration', [arg0]);
};
exports.discoverPeers = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'discoverPeers', [arg0]);
};
exports.transferTo = function (arg0, success, error) {
    exec(success, error, 'TangyP2PPlugin', 'transferTo', [arg0]);
};

