(function(){
"use strict";

test("decodeJsonPointerSegment", function () {
    equal(JsonUtil.decodeJsonPointerSegment("foo"), "foo");
    equal(JsonUtil.decodeJsonPointerSegment("foo~1bar"), "foo/bar");
    equal(JsonUtil.decodeJsonPointerSegment("foo~0bar"), "foo~bar");
    equal(JsonUtil.decodeJsonPointerSegment("foo~1~0bar"), "foo/~bar");
    equal(JsonUtil.decodeJsonPointerSegment("foo~0~1bar"), "foo~/bar");
    equal(JsonUtil.decodeJsonPointerSegment("foo~00bar"), "foo~0bar");
    equal(JsonUtil.decodeJsonPointerSegment("foo~01bar"), "foo~1bar");
});

test("encodeJsonPointerSegment", function () {
    equal(JsonUtil.encodeJsonPointerSegment("foo"), "foo");
    equal(JsonUtil.encodeJsonPointerSegment("foo/bar"), "foo~1bar");
    equal(JsonUtil.encodeJsonPointerSegment("foo~bar"), "foo~0bar");
    equal(JsonUtil.encodeJsonPointerSegment("foo/~bar"), "foo~1~0bar");
    equal(JsonUtil.encodeJsonPointerSegment("foo~/bar"), "foo~0~1bar");
    equal(JsonUtil.encodeJsonPointerSegment("foo~0bar"), "foo~00bar");
    equal(JsonUtil.encodeJsonPointerSegment("foo~1bar"), "foo~01bar");
});

test("getJsonAtPointer", function () {
    var obj = { "foo" : "bar", "baz" : [ "qux", "quux" ] };
    equal(JsonUtil.getJsonAtPointer(obj, "/foo"), "bar");
    equal(JsonUtil.getJsonAtPointer(obj, "/baz/1"), "quux");
});

test("replaceJsonAtPointer", function () {
    var obj = { "foo" : "bar", "baz" : [ "qux", "quux" ] };
    equal(obj["foo"], "bar");
    JsonUtil.replaceJsonAtPointer(obj, "/foo", "rab");
    equal(obj["foo"], "rab");
    equal(obj["baz"][1], "quux");
    JsonUtil.replaceJsonAtPointer(obj, "/baz/1", "xuuq");
    equal(obj["baz"][1], "xuuq");
});

var schema = {
        "properties": {
            "" : { "type": "string" },
            "a'b]<\"p" : { "type": "string" },
            "a][b" : { "type": "string" },
            "a.b" : { "type" : "string" },
            "a" : { "type": "object", "properties": {
                "b" : { "type": "string" }
            }},
            "." : { "type": "object", "properties": {
                "" : { "type": "object", "properties": {
                    "." : { "type": "string" }
                }},
                "." : { "type": "object", "properties": {
                    "" : { "type": "string" }
                }}
            }},
            "~/": { "type": "string" },
            "array": {
                "type": "array",
                "items": {
                    "oneOf": [
                              { "type": "string" },
                              { "type": "object", "properties": { "prop": { "type": "string" }}}
                              ]
                }
            },
            "textarea": {
                "type": "string",
                "format": "textarea"
            },
            "enum": {
                "type": "string",
                "enum": ["12"]
            }
        }        
};
var json = {
        "" : "1",
        "a'b]<\"p": "2",
        "a][b": "3",
        "a.b": "4",
        "a": { "b": "5" },
        ".": { "" : { ".": "6" }, ".": { "": "7" }},
        "~/": "8",
        "array": [ "9", { "prop": "10" } ],
        "textarea": "11",
        "enum": "12"
};

test("inputElementForJsonPointer", function () {
    var fixture = $("#qunit-fixture");
    var div = $('<div/>');
    fixture.append(div);
    var jsonEditor = new JSONEditor(div[0], { schema: schema });
    jsonEditor.setValue(json);
    var input = JsonUtil.inputElementForJsonPointer(div, "/");
    equal(input.val(), "1");
    input = JsonUtil.inputElementForJsonPointer(div, "/a'b]<\"p");
    equal(input.val(), "2");
    input = JsonUtil.inputElementForJsonPointer(div, "/a][b");
    equal(input.val(), "3");
    input = JsonUtil.inputElementForJsonPointer(div, "/a.b");
    equal(input.val(), "4");
    input = JsonUtil.inputElementForJsonPointer(div, "/a/b");
    equal(input.val(), "5");
    input = JsonUtil.inputElementForJsonPointer(div, "/.//.");
    equal(input.val(), "6");
    input = JsonUtil.inputElementForJsonPointer(div, "/././");
    equal(input.val(), "7");
    input = JsonUtil.inputElementForJsonPointer(div, "/~0~1");
    equal(input.val(), "8");
    input = JsonUtil.inputElementForJsonPointer(div, "/array/0");
    equal(input.val(), "9");
    input = JsonUtil.inputElementForJsonPointer(div, "/array/1/prop");
    equal(input.val(), "10");
    input = JsonUtil.inputElementForJsonPointer(div, "/textarea");
    equal(input.val(), "11");
    input = JsonUtil.inputElementForJsonPointer(div, "/enum");
    equal(input.val(), "12");
});

test("jsonPointerForElement", function () {
    var fixture = $("#qunit-fixture");
    var div = $('<div/>');
    fixture.append(div);
    var jsonEditor = new JSONEditor(div[0], { schema: schema });
    jsonEditor.setValue(json);
    var input = JsonUtil.inputElementForJsonPointer(div, "/");
    equal(JsonUtil.jsonPointerForElement(input), "/");
    input = JsonUtil.inputElementForJsonPointer(div, "/a'b]<\"p");
    equal(JsonUtil.jsonPointerForElement(input), "/a'b]<\"p");
    input = JsonUtil.inputElementForJsonPointer(div, "/a][b");
    equal(JsonUtil.jsonPointerForElement(input), "/a][b");
    input = JsonUtil.inputElementForJsonPointer(div, "/a.b");
    equal(JsonUtil.jsonPointerForElement(input), "/a.b");
    input = JsonUtil.inputElementForJsonPointer(div, "/a/b");
    equal(JsonUtil.jsonPointerForElement(input), "/a/b");
    input = JsonUtil.inputElementForJsonPointer(div, "/.//.");
    equal(JsonUtil.jsonPointerForElement(input), "/.//.");
    input = JsonUtil.inputElementForJsonPointer(div, "/././");
    equal(JsonUtil.jsonPointerForElement(input), "/././");
    input = JsonUtil.inputElementForJsonPointer(div, "/~0~1");
    equal(JsonUtil.jsonPointerForElement(input), "/~0~1");
    input = JsonUtil.inputElementForJsonPointer(div, "/array/0");
    equal(JsonUtil.jsonPointerForElement(input), "/array/0");
    input = JsonUtil.inputElementForJsonPointer(div, "/array/1/prop");
    equal(JsonUtil.jsonPointerForElement(input), "/array/1/prop");
    input = JsonUtil.inputElementForJsonPointer(div, "/textarea");
    equal(JsonUtil.jsonPointerForElement(input), "/textarea");
    input = JsonUtil.inputElementForJsonPointer(div, "/enum");
    equal(JsonUtil.jsonPointerForElement(input), "/enum");
});

test("getDeepProperty", function () {
    var jsonNode = {
            "a": {
                "b": {
                    "c": {
                        "d": "hi"
                    }
                }
            },
            "array": [
                {
                    "something": {
                        "else": true
                    }
                }
            ]
    };
    var atNothing = JsonUtil.getDeepProperty(jsonNode);
    equal(atNothing, jsonNode);
    var atMissing = JsonUtil.getDeepProperty(jsonNode, "bleh");
    equal(atMissing, null);
    var atMissingDeep = JsonUtil.getDeepProperty(jsonNode, "a", "b", "bleh");
    equal(atMissingDeep, null);
    var works = JsonUtil.getDeepProperty(jsonNode, "a", "b", "c", "d");
    equal(works, "hi");
    var array = JsonUtil.getDeepProperty(jsonNode, "array", "0", "something");
    console.log(array);
    deepEqual(array, { "else" : true });
});

})();