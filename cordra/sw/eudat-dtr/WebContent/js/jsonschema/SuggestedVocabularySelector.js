/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function SuggestedVocabularySelector(textInput, editor) {
    
    var self = this;
    
    function constructor() {
        var items = JsonUtil.getDeepProperty(editor.schema, Constants.REPOSITORY_SCHEMA_KEYWORD, 'type', 'suggestedVocabulary');
        var combo = new cnri.ui.ComboBox(textInput, items);
        textInput.change(onChange);
    }
    
    function onChange() {
        var value = textInput.val();
        textInput.val(editor.getValue());
        editor.setValue(value);
    }

    function enable() {
        
    }
    self.enable = enable;

    function disable() {
        // consider disable in ComboBox
    }
    self.disable = disable;

    constructor();
}

window.SuggestedVocabularySelector = SuggestedVocabularySelector;

})();