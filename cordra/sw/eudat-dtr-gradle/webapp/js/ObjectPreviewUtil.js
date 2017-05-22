/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

function elementForSearchResult(searchResult, onHandleClick, remoteRepository, uiConfig) {
    var searchResultsConfig = {};
    if (uiConfig) {
        if (uiConfig.searchResults) {
            searchResultsConfig = uiConfig.searchResults;
        }
    }
    var handleString = searchResult.id;
    var div = $('<li/>');
    var previewData = getPreviewData(searchResult, remoteRepository);
    
    var topLine = $('<span/>');
    var link = $('<a class="list-handles-link" style="display:inline-block" target="_blank">').attr('href', 'objects/' + handleString).text(handleString);
    link.attr('data-handle', handleString);
    var ul = $('<ul class="search-result-properties"></ul>');
    for (var jsonPointer in previewData) {
        var thisPreviewData = previewData[jsonPointer];
        var prettifiedPreviewData = prettifyPreviewJson(thisPreviewData.previewJson);
        if (!prettifiedPreviewData) continue;
        
        if (thisPreviewData.isPrimary) {
            link = $('<a class="list-handles-link" style="display:inline-block" target="_blank">').attr('href', 'objects/' + handleString).text(prettifiedPreviewData);
            link.attr('data-handle', handleString);
//            topLine.append(document.createTextNode(" [Id:\u00A0" + handleString + "]"));
        } else {
            var li = $('<li/>');
            if (thisPreviewData.excludeTitle) {
                if (thisPreviewData.isUri) {
                    var link = $('<a style="color:green" target="_blank"></a>');
                    link.text(prettifiedPreviewData);
                    link.attr('href', prettifiedPreviewData);
                    li.append(link);
                } else {
                    li.text(prettifiedPreviewData);
                }
            } else {
                if (thisPreviewData.isUri) {
                    var link = $('<a style="color:green" target="_blank"></a>');
                    link.text(prettifiedPreviewData);
                    link.attr('href', prettifiedPreviewData);
                    li.text(thisPreviewData.title + ": ");
                    li.append(link);
                } else {
                    li.text(thisPreviewData.title + ": " + prettifiedPreviewData);
                }
            }
            ul.append(li);
        }
    }
    topLine.prepend(link);
    if (onHandleClick != null) { 
        link.click(onHandleClick);
    }
    
    //If there are multiple schemas in the registrar include the type in the search results
    if (APP.getSchemaCount() > 1) {
        var schema = APP.getSchema(searchResult.type, remoteRepository);
        var typeTitle = searchResult.type;
        if (schema && schema.title) {
            typeTitle = schema.title;
        }
        if (searchResultsConfig.includeType) {
            topLine.append(document.createTextNode(" \u00A0 [Type:\u00A0" + typeTitle +"]"));
        }
    }
    
    var dateLi = $('<li class="search-results-dates"/>');
    if (searchResult.metadata.createdOn) {
        if (searchResultsConfig.includeCreatedDate) {
            dateLi.append(document.createTextNode("[Created:\u00A0" + new Date(searchResult.metadata.createdOn).toISOString() + "]"));
        }
    }
    if (searchResult.metadata.modifiedOn) {
        if (searchResultsConfig.includeModifiedDate) {
            if (searchResult.metadata.createdOn) dateLi.append(" "); 
    	    dateLi.append(document.createTextNode("[Modified:\u00A0" + new Date(searchResult.metadata.modifiedOn).toISOString() + "]"));
        }
    }
    if (searchResult.metadata.createdOn || searchResult.metadata.modifiedOn) ul.append(dateLi);
    
    div.append(topLine);
    
    if (searchResult.payloads) {
        if (searchResult.payloads.length > 0) {
            var attachmentIcon = $('<i class="fa fa-paperclip"></i>');
            div.append(" &nbsp; ");
            div.append(attachmentIcon);
        }
    } 
    if (ul.children().length > 0) div.append(ul);
    return div;
} 

function prettifyPreviewJson(previewJson, maxLength) {
    var result = null;
    if (typeof previewJson === 'string') {
        result = previewJson;
    } else {
        result = JSON.stringify(previewJson);
    }        
    if (maxLength != null && maxLength != undefined) {
        if (result.length > maxLength) {
            result = result.substring(0, maxLength) + "...";
        }
    }
    return result;
}

function getPreviewData(searchResult, remoteRepository) {
    var res = {};
    var schema = APP.getSchema(searchResult.type, remoteRepository);
    if (!schema) return res;
    var content = searchResult.content;
    if (!content) content = searchResult.json; // old-style search result
    var pointerToSchemaMap = SchemaExtractorFactory.get().extract(content, schema);
    var foundPrimary = false;
    for (var jsonPointer in pointerToSchemaMap) {
        var subSchema = pointerToSchemaMap[jsonPointer];
        var registrarSchema = subSchema[Constants.REPOSITORY_SCHEMA_KEYWORD];
        if (registrarSchema === undefined) continue;
        var previewNode = registrarSchema.preview;
        if (previewNode === undefined) continue;
        var showInPreview = previewNode['showInPreview'];
        var isPrimary = previewNode['isPrimary'];
        var excludeTitle = previewNode['excludeTitle'];
        if (!showInPreview) continue;
        var title = subSchema["title"];
        if (!title) title = jsonPointer;
        var previewJson = JsonUtil.getJsonAtPointer(content, jsonPointer);
        var data = { "title": title, "previewJson": previewJson };
        if (subSchema.format === 'uri') {
            data.isUri = true;
        }
        if (isPrimary && !foundPrimary) {
            data.isPrimary = true;
            foundPrimary = true;
        }
        if (excludeTitle) {
            data.excludeTitle = true;
        } 
        res[jsonPointer] = data;
    }
    return res;
}

var ObjectPreviewUtil = {};
ObjectPreviewUtil.elementForSearchResult = elementForSearchResult;
ObjectPreviewUtil.getPreviewData = getPreviewData;
ObjectPreviewUtil.prettifyPreviewJson = prettifyPreviewJson;
window.ObjectPreviewUtil = ObjectPreviewUtil;

})();