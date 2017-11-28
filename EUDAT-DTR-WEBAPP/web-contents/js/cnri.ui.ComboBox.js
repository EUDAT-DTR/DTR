/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

// Simple text-input to combo-box; depends on Twitter Bootstrap
(function(){
"use strict";

window.cnri = window.cnri || {};
cnri.ui = cnri.ui || {};

cnri.ui.ComboBox = function (textInput, items) {
    var self = this;
    textInput = $(textInput);
    var div = null;
    
    function constructor() {
        div = $('<div class="dropdown" style="display: inline-block; line-height: 30px"/>');
        textInput.before(div);
        textInput.detach();
        var inputGroup = $('<div class="input-group" data-toggle="dropdown"/>');
        div.append(inputGroup);
        inputGroup.append(textInput);
        var inputGroupBtnSpan = $('<span class="input-group-btn"/>');
        inputGroup.append(inputGroupBtnSpan);
        var button = $('<button type="button" class="btn btn-default"/>');
        button.append($('<span class="caret"/>'));
        inputGroupBtnSpan.append(button);
        var ul = $('<ul class="dropdown-menu"/>');
        ul.on('click','a',onClick);
        div.append(ul);
        for(var i = 0; i < items.length; i++) {
            var li = $('<li>');
            var a = $('<a>').attr('href', '#').text(items[i]);
            li.append(a);
            ul.append(li);
        }
    }
    
    function hide() {
        div.hide();
    }
    self.hide = hide;
    
    function show() {
        div.css("display", "inline-block");
    }
    self.show = show;

    function enable() {
        
    }
    self.enable = enable;

    function disable() {
        
    }
    self.disable = disable;
    
    function onClick(event) {
        event.preventDefault();
        textInput.val($(event.target).parent().text());
        textInput.trigger('change');
    }

    constructor();
};

/*end*/})();
