/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function HandleRefSearchSelector(textInput, editor) {
    
    var self = this;
    var types = null;
    var remoteRepository = null;
    var lastQuery = 0;
    var pendingQueryTimeoutId = null;
    var lastResults = null;
    var link = null;
    
    function constructor() {
        types = JsonUtil.getDeepProperty(editor.schema, Constants.REPOSITORY_SCHEMA_KEYWORD, 'type', 'handleReference', 'types');
        remoteRepository = JsonUtil.getDeepProperty(editor.schema, Constants.REPOSITORY_SCHEMA_KEYWORD, 'type', 'handleReference', 'remoteRepository');
        textInput.attr("placeholder", placeholderForTypes(types));
        textInput.keyup(onChange);
        textInput.popover({
            content: popoverContent,
            placement: "bottom",
            html: true,
            trigger: 'manual'
        });
        textInput.on('shown.bs.popover', onShownPopover);
        textInput.blur(onBlur);
        link = $('<a></a>');
        $(textInput).after(link);
        editor.jsoneditor.watch(editor.path, getTargetObject);
    }
    
    function popoverContent() {
        if (lastResults == null) return 'No results.';
        var outerDiv = $('<div></div>');
        var div = $('<ul class="search-results-list small"></ul>');
        outerDiv.append(div);
        var results = lastResults.results;
        if (results.length === 0) return 'No results.';
        for (var i = 0; i < results.length; i++) {
            var resultsDiv = ObjectPreviewUtil.elementForSearchResult(results[i], null, remoteRepository);
            div.append(resultsDiv);
        }
        return outerDiv.html();
    }
    
    function onShownPopover() {
        textInput.next('.popover').find('a').click(function (e) {
            e.preventDefault();
            textInput.val('');
            var link = $(this);
            var handle = link.attr('data-handle');
            editor.setValue(handle);
            textInput.val(handle);
            textInput.popover('hide');
        });
    }
    
    function getTargetObject() {
        link.text("");
        link.attr('href', "#");
        var targetObjectId = textInput.val();
        if (targetObjectId) {
            resolveHandle(targetObjectId);
        } 
    }
    
    function resolveHandle(targetObjectId) {
        if (remoteRepository) {
            APP.getObjectRemotely(remoteRepository, targetObjectId, onGotTargetObjectObject, onGotTargetObjectError);
        } else {
            APP.getObjectLocally(targetObjectId, onGotTargetObjectObject, onGotTargetObjectError);
        }
    }
    
    function onGotTargetObjectError(res, status, xhr) {
        var message = JSON.parse(res.responseText);
        console.log(res);
    }
    
    function onGotTargetObjectObject(res, status, xhr) {
        var location = xhr.getResponseHeader("Location");
        var objectId = getObjectIdFromLocation(location);
        var type = xhr.getResponseHeader("X-Schema");
        renderTargetObjectPreview(res, type, objectId);
    }
    
    function getObjectIdFromLocation(location) {
        var path = "/objects/";
        return location.substring(path.length);
    }
    
    function renderTargetObjectPreview(targetObject, type, objectId) {
        var targetObjectSearchResult = {
                id : objectId,
                type : type,
                json : targetObject
        };
        var previewData = ObjectPreviewUtil.getPreviewData(targetObjectSearchResult, remoteRepository);
        var linkText = objectId;
        for (var jsonPointer in previewData) {
            var previewDataItem = previewData[jsonPointer];
            if (previewDataItem.isPrimary) {
                linkText = previewDataItem.title + ": " + previewDataItem.previewJson;
                if (remoteRepository) {
                    linkText = linkText + ", Located at: " + remoteRepository;
                }
            }
        }
        link.text(linkText);
        if (remoteRepository) {
            link.attr('href', remoteRepository + "#objects/" + objectId);
            link.attr('target', "_blank");
        } else {
            link.attr('href', "#objects/" + objectId);
        }
    }

    function onBlur() {
        textInput.popover('hide');
    }
    
    function onChange() {
        var now = Date.now();
        if (now - lastQuery >= 500) {
            doQuery();
        } else {
            if (pendingQueryTimeoutId !== null) clearTimeout(pendingQueryTimeoutId);
            pendingQueryTimeoutId = setTimeout(doQuery, 500 - (now - lastQuery));
        }
    }
    
    function doQuery() {
        var text = textInput.val();
        if (text === "") {
            return;
        }
        //var query = "type:" + type + " AND internal.all:(" + text + ")";
        var query = queryForTypes(types) + " AND internal.all:(" + text + ")";
        if (remoteRepository) {
            APP.searchRemoteRepository(remoteRepository, query, 0, 10, null, onSuccess, onError);
        } else {
            APP.search(query, 0, 10, null, onSuccess, onError);
        }
        
        lastQuery = Date.now();
        pendingQueryTimeoutId = null;
    }
    
    function onSuccess(response) {
        lastResults = response;
        textInput.popover('show');
    }
    
    function onError(response) {
        console.log(response);
    }
    
    function placeholderForTypes(types) {
        if (typeof types === 'string') {
            return types;
        } else {
            var res = '';
            for (var i = 0; i < types.length; i++) {
                if (i > 0) res += ', ';
                res += types[i];
            }
            return res;
        }
    }
    
    function queryForTypes(types) {
        var query = "(";
        for (var i = 0; i < types.length; i++) {
            if (i > 0) query += ' OR ';
            query += "type:" + types[i];
        }
        query += ")";
        return query;
    }
    
    constructor();
}

window.HandleRefSearchSelector = HandleRefSearchSelector;

})();