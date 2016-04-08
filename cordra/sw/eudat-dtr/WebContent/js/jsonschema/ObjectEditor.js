/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function ObjectEditor(containerDiv, options) {
    var schema = $.extend(true, {}, options.schema); //The editor might modify the schema when expanding $ref so we need to deep clone it here
    var type = options.type;
    var objectJson = options.objectJson;
    var objectId = options.objectId;
    var relationshipsButtonText = options.relationshipsButtonText;
    var disabled = options.disabled;
    var allowEdits = options.allowEdits;
    var remoteRepository = options.remoteRepository;
    var allowClone = options.allowClone;
    var contentPlusMeta = options.contentPlusMeta;
    
    var self = this;
    var editor = null;
    var objectIdHeading = null;
    var editorDiv = null;
    var toolBarDiv = null;
    var editJsonDiv = null;
    var editJsonTextDiv = null;
    var jsonEditorOnline = null;
    var aclEditorDiv = null;
    var aclEditorChildDiv = null;
    var aclEditor = null;
    var versionsEditorDiv = null;
    var versionsEditor = null;
    var suffixDiv = null;
    var suffixInput = null;
    var allowAdvancedView = true;
    var advancedDiv = null;
    var detailedObjectViewer = null;
    var saveButtonBottom = null;
    var saveButton = null;
    var payloadsEditorDiv = null;
    var payloadsEditor = null;
    
    function constructor() {
        if (!allowEdits) {
            disabled = true;
        }

        var closeButton = $('<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>');
        containerDiv.append(closeButton);
        closeButton.click(onCloseClick);
        
        objectIdHeading = $('<h3 class="editorTitle"></h3>');
        
        containerDiv.append(objectIdHeading);
        if (objectId != null) {
            var objectHeadingText = getObjectHeadingText();
            objectIdHeading.text("Object Id: " + objectId);
            objectIdHeading.text(objectHeadingText);
        }
        
        if (!APP.getUiConfig().hideTypeInObjectEditor) {
            var typeText = $('<p></p>');
            typeText.text("Type: " + type);
            containerDiv.append(typeText);
        }
        
        if (contentPlusMeta.metadata.isVersion) {
            var newerVersionText = $('<p>There is a newer version of this object </p>');
            containerDiv.append(newerVersionText);
            var versionOf = contentPlusMeta.metadata.versionOf;
            var link = $('<a style="display:inline-block" target="_blank">').attr('href', 'objects/' + versionOf).text(versionOf);
            link.attr('data-handle', versionOf);
            link.click(onNewerVersionClick);
            newerVersionText.append(link);
        }
        
        if (remoteRepository) {
            var originLabel = $('<p></p>');
            originLabel.text("Origin: " + remoteRepository);
            containerDiv.append(originLabel);
        }

        toolBarDiv = $('<div class="object-editor-toolbar"></div>');
        createToolBar();
        containerDiv.append(toolBarDiv);

        advancedDiv = $('<div style="display:none;"></div>');
        containerDiv.append(advancedDiv);
        versionsEditorDiv = $('<div style="display:none;"></div>');
        containerDiv.append(versionsEditorDiv);
        aclEditorDiv = $('<div style="display:none;"></div>');
        containerDiv.append(aclEditorDiv);

        if (objectId == null) {
            if (APP.getUiConfig().allowUserToSpecifySuffixOnCreate) {
                createSuffixInput();
            }
        }
        
        createEditJsonDiv();
        
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
                disabled: disabled
        };
        JSONEditor.defaults.options.iconlib = 'bootstrap3';
        JSONEditor.defaults.editors.object.options.disable_properties = true;
        JSONEditor.defaults.editors.object.options.disable_edit_json = true;
        JSONEditor.defaults.editors.object.options.disable_collapse = false;
        
        editor = new JSONEditor(editorDiv[0], options);
        editor.on('change', onChange);
        if (disabled) {
            editor.disable();
            editorDiv.addClass("hidden-buttons");
            editorDiv.addClass("view-mode");
        }
        
        if (objectId != null) {
            APP.getRelationships(objectId, onGotRelationshipsSuccess, onGotRelationshipsError);
        }
        
        var editorTitle = $("div[data-schemapath=root] > h3 > span:first-child");
        if (editorTitle.text() === "root") {
            editorTitle.parent().hide();
        }
        
        if (!disabled || (contentPlusMeta.payloads && contentPlusMeta.payloads.length > 0)) {
            payloadsEditorDiv = $('<div></div>');
            containerDiv.append(payloadsEditorDiv);
            payloadsEditor = new PayloadsEditor(payloadsEditorDiv, contentPlusMeta.payloads, disabled);
       }
 
        if (!disabled) {
            if (allowEdits) {
                saveButtonBottom = $('<button class="btn btn-sm btn-success" data-loading-text="Saving...">Save Object</button>');
                containerDiv.append(saveButtonBottom);
                saveButtonBottom.click(save);
            }
        }
    }
    
    function createToolBar() {
        if (disabled) {
            if (allowEdits) {
                var editButton = $('<button class="btn btn-sm btn-default">Edit Object</button>');
                toolBarDiv.append(editButton);
                editButton.click(APP.editCurrentObject);
            }

            if (allowAdvancedView) {
                toolBarDiv.append(" ");
                var advancedButton = $('<button class="btn btn-sm btn-default">Digital Object View</button>');
                toolBarDiv.append(advancedButton);
                advancedButton.click(onAdvancedButtonClick);
            }
            
            if (objectId != null) {
                toolBarDiv.append(" ");
                var jsonForm = $('<form style="display:none" method="POST" target="_blank"/>');
                jsonForm.attr('action', "objects/" + objectId);
                var csrfInput = $('<input type="hidden" name="csrfToken"/>');
                csrfInput.val(APP.getCsrfCookieToken());
                jsonForm.append(csrfInput);
                var jsonButton = $('<a class="btn btn-sm btn-default">JSON View</a>');
                jsonButton.click(function (event) {
                    event.preventDefault();
                    jsonForm.submit();
                });
                toolBarDiv.append(jsonForm);
                toolBarDiv.append(jsonButton); 
                
                toolBarDiv.append(" ");
                var versionsButton = $('<button class="btn btn-sm btn-primary">Versions View</button>');
                toolBarDiv.append(versionsButton);
                versionsButton.click(onShowVersionsClick);
                
//                toolBarDiv.append(" ");
//                var referrersButton = $('<button class="btn btn-sm btn-default">Referrers List</button>');
//                toolBarDiv.append(referrersButton);
//                referrersButton.click(onReferrersButtonClick);
                
                if (allowEdits) {
                    toolBarDiv.append(" ");
                    var shareButton = $('<button class="btn btn-sm btn-primary">Share Object</button>');
                    toolBarDiv.append(shareButton);
                    shareButton.click(onShareButtonClick);

                    if (allowClone) {
                        toolBarDiv.append(" ");
                        var cloneButton = $('<button class="btn btn-sm btn-default">Clone Object</button>');
                        toolBarDiv.append(cloneButton);
                        cloneButton.click(onClone);
                    }
                }
            }
        } else {
            saveButton = $('<button class="btn btn-sm btn-success" data-loading-text="Saving...">Save Object</button>');
            toolBarDiv.append(saveButton);
            saveButton.click(save);

            toolBarDiv.append(" ");
            var editJsonButton = $('<button class="btn btn-sm btn-default">Edit JSON</button>');
            toolBarDiv.append(editJsonButton);
            editJsonButton.click(onEditJson);
            
            if (objectId != null) {
                toolBarDiv.append(" ");
                var revertButton = $('<button class="btn btn-sm btn-warning">Revert</button>');
                toolBarDiv.append(revertButton);
                revertButton.click(onRevert);

                toolBarDiv.append(" ");
                var deleteButton = $('<button class="btn btn-sm btn-danger">Delete Object</button>');
                toolBarDiv.append(deleteButton);
                deleteButton.click(onDelete);
            }
        }
    }
    
    function onNewerVersionClick(e) {
        e.preventDefault();
        var link = $(this);
        var handle = link.attr('data-handle');
        APP.resolveHandle(handle);
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
    
    function onAdvancedButtonClick() {
        advancedDiv.empty();
        detailedObjectViewer = new DetailedObjectViewer(advancedDiv, contentPlusMeta);
        advancedDiv.show(300);
    }
    
//    function onReferrersButtonClick() {
//        var referrersQuery = "internal.pointsAt:" + objectId;
//        APP.performSearchWidgetSearch(referrersQuery);
//    }
    
    function onShareButtonClick() {
        APP.getAclForCurrentObject(onGotAclSuccess);
    }
    
    function onShowVersionsClick() {
        APP.getVersionsFor(objectId, onGotVersionsSuccess);
    }
    
    function onGotVersionsSuccess(versions) {
        //if (versionsEditor) versionsEditor.destroy();
        versionsEditorDiv.empty();
        versionsEditor = new ObjectVersions(versionsEditorDiv, versions, objectId, allowEdits);
        versionsEditorDiv.show(300);
    }
    
    function onGotAclSuccess(res, status, xhr) {
        if (aclEditor) aclEditor.destroy();
        aclEditorDiv.empty();
        aclEditor = new ObjectAclEditor(aclEditorDiv, res, objectId);
        aclEditorDiv.show(300);
    }
    
    function createEditJsonDiv() {
        editJsonDiv = $('<div style="display:none;"></div>');
        containerDiv.append(editJsonDiv);
        
        var applyButton = $('<button class="btn btn-sm btn-default">Apply</button>');
        editJsonDiv.append(applyButton);
        applyButton.click(applyEditJsonClick);
        
        editJsonDiv.append(" ");
        
        var cancelButton = $('<button class="btn btn-sm btn-default">Cancel</button>');
        editJsonDiv.append(cancelButton);
        cancelButton.click(cancelEditJsonClick);
        
        var editJsonTextDiv = $('<div style="height: 500px; display:block; width:100%;"></div>');
        //editJsonTextArea = $('<textarea style="height: 500px; display:block; width:100%; resize:vertical;"></textarea>');
        editJsonDiv.append(editJsonTextDiv);
        
        var container = editJsonTextDiv[0];
        var options = {
                mode: 'code',
                modes: ['code', 'tree'], // allowed modes
                error: function (err) {
                  APP.notifications.alertError(err.toString());
                }
              };
        jsonEditorOnline = new JsonEditorOnline(container, options);
    }
    
    function onGotRelationshipsSuccess(res, status, xhr) {
        //if (res.nodes.length > 1) {
        if (disabled) {
            toolBarDiv.append(" ");
            var showRelationshipsButton = $('<button class="btn btn-sm btn-default"></button>');
            showRelationshipsButton.text(relationshipsButtonText);
            toolBarDiv.append(showRelationshipsButton);
            showRelationshipsButton.click(onShowRelationshipsClick); 
        }
           
        //}
    }
    
    function onGotRelationshipsError(res, status, xhr) {
        
    }
    
    function onCloseClick() {
        var showSearch = true;
        APP.hideObjectEditor(showSearch);
    }
    
    function destroy() {
        if (aclEditor) aclEditor.destroy();
        editor.destroy();
    }
    self.destroy = destroy;
    
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
    
    function getJsonFromEditor() {
        var jsonObject = editor.getValue();
        ensureFileInputsAreEmptyString(jsonObject);
        return jsonObject;
    }
    self.getJsonFromEditor = getJsonFromEditor;
    
    function getContentPlusMeta() {
        var contentPlusMetaCopy = jQuery.extend(true, {}, contentPlusMeta);
        contentPlusMetaCopy.content = getJsonFromEditor();
        return contentPlusMetaCopy;
    }
    self.getContentPlusMeta = getContentPlusMeta;
    
    function getType() {
        return type;
    }
    self.getType = getType;
    
    function getRemoteRepository() {
        return remoteRepository;
    }
    self.getRemoteRepository = getRemoteRepository;
    
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
    
    function onEditJson() {
        var jsonObject = editor.getValue();
        //var jsonString = JSON.stringify(jsonObject, undefined, 4);
        
        jsonEditorOnline.set(jsonObject);
        //editJsonTextArea.val(jsonString);
        editJsonDiv.show();
    }
    
    function applyEditJsonClick() {
//        var jsonString = editJsonTextArea.val();
//        var jsonObject = JSON.parse(jsonString);
        var jsonObject = jsonEditorOnline.get();
        editor.setValue(jsonObject);
        editJsonDiv.hide();
    }
    
    function cancelEditJsonClick() {
        jsonEditorOnline.set({});
        //editJsonTextArea.val("");
        editJsonDiv.hide();
    }
    
    function save() {
        saveButtonBottom.button('loading');
        saveButton.button('loading');
        var formData = buildFormData();
        if (objectId == null) {
            var suffix = null;
            if (APP.getUiConfig().allowUserToSpecifySuffixOnCreate) {
                suffix = suffixInput.val();
                if (suffix === "") {
                    suffix = null;
                }
            }
            APP.createObject(formData, type, suffix, onSaveError);
        } else {
            APP.saveObject(formData, objectId, onSaveError);
        }
    }
    
    function onSaveError() {
        saveButtonBottom.button('reset');
        saveButton.button('reset');
    }
    
    function onDelete() {
        APP.deleteObject(objectId);
    }
    
    function onRevert() {
        APP.resolveHandle(objectId);
    }
    
    function onClone() {
        APP.cloneCurrentObject();
    }
    
    
    function onShowRelationshipsClick() {
        APP.showRelationshipsGraph(objectId);
    }
    
    function buildFormData() {
        var formData = new FormData();
        var jsonString = JSON.stringify(getJsonFromEditor(), undefined, 2);
        formData.append("json", jsonString);
        payloadsEditor.appendFormData(formData);
        return formData;
    }
    
    function getObjectIdFromLocation(location) {
        var path = "/objects/";
        return location.substring(path.length);
    }
    
    function getObjectId() {
        return objectId;
    }
    self.getObjectId = getObjectId;

    constructor();
}
window.ObjectEditor = ObjectEditor;

})();