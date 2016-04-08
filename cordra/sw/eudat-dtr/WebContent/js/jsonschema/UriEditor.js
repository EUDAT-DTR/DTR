(function(){
"use strict";

var window = window || self;

function UriEditor(textInput, editor) {
    var self = this;
    var link = null;
    
    function constructor() {
        textInput.on('input', updateLink);
        link = $('<a style="display:block" target="_blank"></a>');
        textInput.after(link);
        editor.jsoneditor.watch(editor.path, updateLink);
    }
    
    function updateLink() {
        link.attr('href', textInput.val());
        link.text(textInput.val());
    }
    
    function enable() {
        textInput.show();
    }
    self.enable = enable;

    function disable() {
        textInput.hide();
    }
    self.disable = disable;

    
    constructor();
}

window.UriEditor = UriEditor;

})();