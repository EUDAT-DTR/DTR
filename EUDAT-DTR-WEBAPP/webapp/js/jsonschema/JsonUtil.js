/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

var JsonUtil = {};
window.JsonUtil = JsonUtil;

function getJsonAtPointer(jsonObject, jsonPointer) {
    if (jsonPointer === "") return jsonObject;
    var segments = jsonPointer.split('/').slice(1);
    var node = jsonObject;
    var decodedSegment;
    for (var i = 0; i < segments.length; i++) {
        decodedSegment = decodeJsonPointerSegment(segments[i]);
        node = node[decodedSegment];
        if (node == undefined) return undefined;
    }
    return node;
}
JsonUtil.getJsonAtPointer = getJsonAtPointer;

function replaceJsonAtPointer(jsonObject, jsonPointer, replacement) {
    if (jsonPointer === "") return;
    var segments = jsonPointer.split('/').slice(1);
    var parent = null;
    var child = jsonObject;
    var decodedSegment = undefined;
    for (var i = 0; i < segments.length; i++) {
        decodedSegment = decodeJsonPointerSegment(segments[i]);
        parent = child;
        child = parent[decodedSegment];
        if (child == undefined) return;
    }
    parent[decodedSegment] = replacement;
}
JsonUtil.replaceJsonAtPointer = replaceJsonAtPointer;

//Recursively retrieves all properties and sub propterties with the given property name.
function getPropertiesNamed(targetPropertyName, jsonObject) {
    var result = [];
    if (typeof jsonObject === 'object') {
        for (var propertyName in jsonObject) {
            var property = jsonObject[propertyName];
            if (targetPropertyName === propertyName) {
                result.push(property);
            } else {
                if (typeof property === 'object' || typeof property === 'array') {
                    var subResult = getPropertiesNamed(targetPropertyName, property);
                    arrayPushAll(result, subResult);
                }
            }
        }
    } else if (typeof jsonObject === 'array') {
        for (var i = 0; i < jsonObject.length; i++) {
            var element = jsonObject[i];
            if (typeof property === 'object' || typeof property === 'array') {
                var subResult = getPropertiesNamed(targetPropertyName, element);
                arrayPushAll(result, subResult);
            }
        }
    }
    return result;
}
JsonUtil.getPropertiesNamed = getPropertiesNamed;

function arrayPushAll(a, b) {
    for (var i = 0; i < b.length; i++) {
        var item = b[i];
        a.push(item);
    }
}

function decodeJsonPointerSegment(segment) {
    return segment.replace(/~1/g, "/").replace(/~0/g, "~");
}
JsonUtil.decodeJsonPointerSegment = decodeJsonPointerSegment;

function jsonPointerToJsonEditorDataSchemaPath(jsonPointer) {
    return "root" + jsonPointer.replace(/\//g, ".").replace(/~1/g, "/").replace(/~0/g, "~");
}
JsonUtil.jsonPointerToJsonEditorDataSchemaPath = jsonPointerToJsonEditorDataSchemaPath;

function jsonPointerToJsonEditorFormName(jsonPointer) {
    return "root" + jsonPointer.replace(/\//g, ".").replace(/~1/g, "/").replace(/~0/g, "~").replace(/\.([^.]+)/g,'[$1]');    
}
JsonUtil.jsonPointerToJsonEditorFormName = jsonPointerToJsonEditorFormName;

function escapeForJQuerySelector(str) {
    if (!str) return str;
    return str.replace(/([ #;?%&,.+*~\':"!^$[\]()=>|\/@])/g, '\\$1');      
}
JsonUtil.escapeForJQuerySelector = escapeForJQuerySelector;

function inputElementForJsonPointer(container, jsonPointer) {
    var selector = '[name="' + escapeForJQuerySelector(jsonPointerToJsonEditorFormName(jsonPointer)) + '"]';
    var res = container.find(selector);
    while (res.length >= 2) {
        selector = '[data-schemapath="' + escapeForJQuerySelector(jsonPointerToJsonEditorDataSchemaPath(jsonPointer)) + '"] ' + selector;
        res = container.find(selector);
        if (res.length <= 1 || jsonPointer.length === 0) break;
        jsonPointer = jsonPointer.replace(/\/[^\/]*$/, '');
    }
    return res;
}
JsonUtil.inputElementForJsonPointer = inputElementForJsonPointer;

function encodeJsonPointerSegment(segment) {
    return segment.replace(/~/g, "~0").replace(/\//g, "~1");
}
JsonUtil.encodeJsonPointerSegment = encodeJsonPointerSegment;

function jsonPointerForElement(element) {
    var container = $(element).closest('[data-schemapath]');
    var path = container.attr('data-schemapath');
    if (path === 'root' || !path) return '';
    var parent = container.parent().closest('[data-schemapath]');
    var parentPath = parent.attr('data-schemapath');
    if (path === parentPath) return jsonPointerForElement(parent);
    var key = path.substring(parentPath.length + 1);
    return jsonPointerForElement(parent) + '/' + JsonUtil.encodeJsonPointerSegment(key);
}
JsonUtil.jsonPointerForElement = jsonPointerForElement;

function getDeepProperty(obj) {
    for (var i = 1; i < arguments.length; i++) {
        if (typeof obj === 'object') {
            obj = obj[arguments[i]];
        } else {
            return null;
        }
    }
    return obj;
}
JsonUtil.getDeepProperty = getDeepProperty;

})();