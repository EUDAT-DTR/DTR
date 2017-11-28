(function(){
"use strict";

test("getJsonPointerFromEditor", function () {
    var fixture = $("#qunit-fixture");
    var div = $('<div/>');
    fixture.append(div);
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
    
    var savedDefaults = $.extend(true, {}, JSONEditor.defaults);

    var jsonPointerToInputMap = {};
    
    JSONEditor.defaults.editors.myString = JSONEditor.defaults.editors.string.extend({
        build: function() {
            this._super();
            var editor = this;
            var textInput = $(this.input);
            jsonPointerToInputMap[FileEditor.Testing.getJsonPointerFromEditor(editor)] = textInput;
            console.log(jsonPointerToInputMap);
        }
    });

    JSONEditor.defaults.resolvers.unshift(function(schema) {
        if (schema.type === 'string') {
            return "myString";
        }
    });

    
    var jsonEditor = new JSONEditor(div[0], { schema: schema });
    jsonEditor.setValue(json);
    
    JSONEditor.defaults = savedDefaults;
    
    var input = jsonPointerToInputMap["/"];
    equal(input.val(), "1");
    input = jsonPointerToInputMap["/a'b]<\"p"];
    equal(input.val(), "2");
    input = jsonPointerToInputMap["/a][b"];
    equal(input.val(), "3");
    input = jsonPointerToInputMap["/a.b"];
    equal(input.val(), "4");
    input = jsonPointerToInputMap["/a/b"];
    equal(input.val(), "5");
    input = jsonPointerToInputMap["/.//."];
    equal(input.val(), "6");
    input = jsonPointerToInputMap["/././"];
    equal(input.val(), "7");
    input = jsonPointerToInputMap["/~0~1"];
    equal(input.val(), "8");
    input = jsonPointerToInputMap["/array/0"];
    equal(input.val(), "9");
    input = jsonPointerToInputMap["/array/1/prop"];
    equal(input.val(), "10");
    input = jsonPointerToInputMap["/textarea"];
    equal(input.val(), "11");
    input = jsonPointerToInputMap["/enum"];
    equal(input.val(), "12");
});

})();