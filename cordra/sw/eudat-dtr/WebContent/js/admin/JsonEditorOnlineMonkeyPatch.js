/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

//The Json Editor from https://github.com/josdejong/jsoneditor/
//has exactly the same variable name as the one from https://github.com/jdorn/json-editor
//The following is used to rename the first so that both can be used on the same page.

window.JsonEditorOnline = window.JSONEditor;
delete window.JSONEditor;

})();