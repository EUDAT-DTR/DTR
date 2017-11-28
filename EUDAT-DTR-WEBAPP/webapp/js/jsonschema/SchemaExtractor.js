/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var pointers = undefined;

function SchemaExtractor() {
    var self = this;
    
    function constructor() {
        
    }
    
    function extract(json, schema) {
        pointers = {};
        tv4.validate(json, schema);
        return pointers;
        
    }
    self.extract = extract;

    constructor();
}

var schemaExtractor = new SchemaExtractor();

window.SchemaExtractorFactory = {};

window.SchemaExtractorFactory.get = function () {
    return schemaExtractor;
};

function keywordHandler(data, value, schema, jsonPointer) {
    pointers[jsonPointer] = schema;
}

tv4.defineKeyword(Constants.REPOSITORY_SCHEMA_KEYWORD, keywordHandler);

//function fileHandler(data, schema, jsonPointer) {
//    pointers[jsonPointer] = schema;
//}
//
//tv4.addFormat("file", fileHandler);

})();