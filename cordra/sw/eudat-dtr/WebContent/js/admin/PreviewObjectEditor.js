/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function PreviewObjectEditor(containerDiv, schema, type, objectJson, objectId) {
    var self = this;
    var editor = null;
    var objectIdHeading = null;
    var editorDiv = null;
    var suffixDiv = null;
    var suffixInput = null;
    
    function constructor() {
        
        objectIdHeading = $('<h3></h3>');
        containerDiv.append(objectIdHeading);
        if (objectId != null) {
            var objectHeadingText = getObjectHeadingText();
            objectIdHeading.text("Object Id: " + objectId);
            objectIdHeading.text(objectHeadingText);
        }
        editorDiv = $('<div></div>');
        containerDiv.append(editorDiv);
        
        
        var options = {
                theme : "bootstrap3",
                schema : schema,
                startval : objectJson,
                disable_edit_json : true,
                disable_properties : true,
//                required_by_default : true,
                disable_collapse: true,
                disabled: false,
                ajax: true
        };
        JSONEditor.defaults.options.iconlib = 'bootstrap3';
        JSONEditor.defaults.editors.object.options.disable_properties = true;
        JSONEditor.defaults.editors.object.options.disable_edit_json = true;
        JSONEditor.defaults.editors.object.options.disable_collapse = false;
        
        editor = new JSONEditor(editorDiv[0], options);
        editor.on('change', onChange);
        
        var editorTitle = $("div[data-schemapath=root] > h3 > span:first-child");
        if (editorTitle.text() === "root") {
            editorTitle.parent().hide();
        }
    }
    
    function createSuffixInput() {
        suffixDiv = $('<div class=""></div>');
        containerDiv.append(suffixDiv);
        var suffixLabel = $('<label class="control-label">Handle</label>');
        suffixDiv.append(suffixLabel);
        
        var suffixAndPrefixDiv = $('<div></div>');
        suffixDiv.append(suffixAndPrefixDiv);
        
        var prefixLabel = $('<span style="display: inline-block"></span>');
        prefixLabel.text(APP.getServerPrefix() + "/");
        prefixLabel.append("&nbsp;");
        suffixAndPrefixDiv.append(prefixLabel);
        
        suffixInput = $('<input type="text" style="display: inline-block; width: auto" class="form-control" placeholder="Suffix (optional)"></input>');
        suffixAndPrefixDiv.append(suffixInput);
    }
    
    function getObjectHeadingText() {
        var searchResult = {
                id: objectId,
                json: objectJson,
                type: type
        };
        var previewData = ObjectPreviewUtil.getPreviewData(searchResult);
        var objectHeadingText = null;
        for (var jsonPointer in previewData) {
            var thisPreviewData = previewData[jsonPointer];
            var prettifiedPreviewData = ObjectPreviewUtil.prettifyPreviewJson(thisPreviewData.previewJson);
            if (!prettifiedPreviewData) continue;
            if (thisPreviewData.isPrimary) {
                objectHeadingText = prettifiedPreviewData;
                break;
            }
        }
        if (objectHeadingText == null) {
            objectHeadingText = "Object Id: " + objectId;
        }
        return objectHeadingText;
    }
    
    function onChange() {
        fixButtonGroupCss();
    }
    
    function fixButtonGroupCss() {
        $('.cnri-round-left').removeClass('cnri-round-left');
        $('.cnri-round-right').removeClass('cnri-round-right');
        editorDiv.find(".btn-group").each(function (_, div) {
            var $div = $(div);
            var firstChild = $div.children().first();
            if (!firstChild.is(':visible')) {
                var firstDisplayedChild = $div.children(':visible').first();
                firstDisplayedChild.addClass('cnri-round-left');
            }
            var lastChild = $div.children().last();
            if (!lastChild.is(':visible')) {
                var lastDisplayedChild = $div.children(':visible').last();
                lastDisplayedChild.addClass('cnri-round-right');
            }
        });
    }
    
    function ensureFileInputsAreEmptyString(jsonObject) {
        var allFileInputs = getAllFileInputs();
        allFileInputs.each(function (index, element) {
            var jsonPointer = JsonUtil.jsonPointerForElement(element);
            JsonUtil.replaceJsonAtPointer(jsonObject, jsonPointer, "");
        }); 
    }
    
    function getAllFileInputs() {
        return containerDiv.find('input[type=file]');
    }
    
    function getObjectIdFromLocation(location) {
        var path = "/objects/";
        return location.substring(path.length);
    }
    
    constructor();
}
window.PreviewObjectEditor = PreviewObjectEditor;

})();