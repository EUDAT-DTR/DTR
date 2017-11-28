/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function HandleMintingConfigEditor(containerDiv, handleMintingConfig) {
    var self = this;
    var baseUriInput = null;
    var advancedToggleButton = null;
    var basicToggleButton = null;
    var editor = null;
    var editorDiv = null;
    var form = null;
    var isAdvanced = false;
    
    var updateProgressDiv = null;
    var updateProgressWidget = null;
    
    function constructor() {
        
        var title = $('<h4>Handle Records</h4>');
        containerDiv.append(title);
        
        var description = $("<p>If appropriately configured, the DO Repository will create handle records for each new object created. The handle records consist of a value redirecting the resolver to the Repository's API and/or user interface. The DO Repository needs to be configured with what base URI, ending with a slash, should be used for the URLs in the new handle records. If the base URI is not configured, handle records will not be created.</p>");
        containerDiv.append(description);
        
        var toolBarDiv = $('<div class="object-editor-toolbar"></div>');
        containerDiv.append(toolBarDiv);
        
        var saveButton = $('<button class="btn btn-sm btn-default">Save</button>');
        toolBarDiv.append(saveButton);
        saveButton.click(save);
        
        form = $('<form class="form-horizontal"></form>');
        containerDiv.append(form);
        
        var group = $('<div class="form-group"></div>');
        form.append(group);
        
        var label = $('<label for="baseUriInput" class="col-sm-2 control-label">Base URI</label>');
        group.append(label);

        var div = $('<div class="col-sm-10"></div>');
        group.append(div);
        
        baseUriInput = $('<input class="form-control" id="baseUriInput" placeholder="Base URI"></input>');
        div.append(baseUriInput);
        baseUriInput.val(handleMintingConfig.baseUri);
        
        advancedToggleButton = $('<button class="btn btn-sm btn-default">Advanced</button>');
        toolBarDiv.append(advancedToggleButton);
        advancedToggleButton.click(showAdvanced);
        
        basicToggleButton = $('<button class="btn btn-sm btn-default" style="display:none;">Basic</button>');
        toolBarDiv.append(basicToggleButton);
        basicToggleButton.click(showBasic);
        
        editorDiv = $('<div style="height:500px; display:none;"></div>');
        containerDiv.append(editorDiv);
        
        var container = editorDiv[0];
        var options = {
                mode: 'code',
                modes: ['code', 'tree'], // allowed modes
                error: function (err) {
                  alert(err.toString());
                }
              };
        editor = new JsonEditorOnline(container, options, handleMintingConfig);
        
        containerDiv.append($('<br/>'));
        
        var updateWell = $('<div class="well well-sm"></div>');
        containerDiv.append(updateWell);
        
        var updateToolBar = $('<div></div>');
        updateWell.append(updateToolBar);
        
        var updateHandlesButton = $('<button class="btn btn-sm btn-default">Update All Handles</button>');
        updateToolBar.append(updateHandlesButton);
        updateHandlesButton.click(updateHandles);
        
        updateProgressDiv = $('<div style="display:none"></div>');
        updateWell.append(updateProgressDiv);
        
        updateProgressWidget = new UpdateAllHandlesStatusWidget(updateProgressDiv);
        
        pollUpdateStatus();
    }
    
    function showAdvanced() {
        advancedToggleButton.hide();
        basicToggleButton.show();
        form.hide();
        editorDiv.show();
        isAdvanced = true;
    }
    
    function showBasic() {
        advancedToggleButton.show();
        basicToggleButton.hide();
        form.show();
        editorDiv.hide();
        isAdvanced = false;
    }
    
    function save() {
        var handleMintingConfigUpdate = editor.get();
        if (!isAdvanced) {
            var newBaseUri = baseUriInput.val();
            handleMintingConfigUpdate.baseUri = newBaseUri;
        }
        APP.saveHandleMintingConfig(handleMintingConfigUpdate);
    }
    
    function updateHandles() {
        var dialog = new ModalYesNoDialog("Updating all handles can take a long time. Are you sure you want to start this process?", yesUpdateHandles, noUpdateHandles, self);
        dialog.show();
    }
    
    function yesUpdateHandles() {
        updateProgressWidget.clear();
        APP.updateAllHandles(pollUpdateStatus);
    }
    
    function pollUpdateStatus() {
        APP.getHandleUpdateStatus(function (status) {
            if (!status.inProgress && status.total == 0) return; 
            updateProgressWidget.setStatus(status);
            //updateProgressDiv.text(JSON.stringify(status));
            updateProgressDiv.show();
            if (status.inProgress) {
                setTimeout(pollUpdateStatus, 100);
            } else {
                //complete
            }
        });
    }
    
    function noUpdateHandles() {
      //no-op
    }
    
    constructor();
}