// Shim module to support `require('tree-sitter-c/grammar')` when grammars are vendored
// under short directory names like `grammars/c`.

module.exports = require('../c/grammar');

