/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function SearchWidget(containerDiv, resultsContainerDiv, schemas, serverPrefix, allowCreate) {
    var self = this;
    var searchInput = null;
    var paginationDiv = null;
    var paginationBottomDiv = null;
    var resultsContainer = null;
    var resultsDiv = null;
    var pageSize = 10;
    var lastQueryResponse = null;
    var retrieveJsonForm = null;
    var createButtonSpan = null;
    
    function constructor() {
        
        var a = ["a", "b"];
        for (var i = 0; i < a.lenght; i++)  {
            
        }
        
        var searchBarDiv = $('<div class="container"></div>');
        containerDiv.append(searchBarDiv);

        var searchBox = $('<div id="dtr-search-box" class="row"></div>');
        searchBarDiv.append(searchBox);

        var column = $('<div class="col-sm-12"></div>');
        searchBox.append(column);

        var navBar = $('<div class="navbar navbar-default"></div>');
        column.append(navBar);

        var form = $('<form class="form-inline" role="form"></form>');
        navBar.append(form);
        form.submit(function(e) {return false;});

        var column = $('<div class="nav navbar-nav navbar-left navbar-form"></div>');
        form.append(column);

        var searchInputGroup = $('<div class="input-group" id="dtr-search-form-input-group"></div>');
        column.append(searchInputGroup);

        /* help tips button */
        var helpTips = $('\
                <span class="input-group-btn" style="width: 1%"> \
                  <a class="dropdown-toggle btn btn-default" rel="tooltip" title="" data-placement="bottom" data-delay="100" data-toggle="dropdown" role="button" href="#" data-original-title="Search Tips and Examples"> \
                    <i class="glyphicon glyphicon-question-sign"></i> \
                  </a> \
                  <ul class="searchexamples dropdown-menu"> \
                    <li> \
                      <a href="/help/search-tips"> \
                        <strong>Search Tips</strong> \
                        <i class="pull-right glyphicon glyphicon-info-sign"></i> \
                      </a> \
                    </li> \
                    <li> \
                      <a href="/help/search-guide"> \
                        <i class="pull-right glyphicon glyphicon-leaf"></i> \
                        <strong>Search Guide</strong> \
                      </a> \
                    </li> \
                  </ul> \
                </span>');

        searchInputGroup.append(helpTips);

        /* additional buttons for search bar (should use 'width: 1%' if enabled) */
        // var stuff = $('\
        //           <input autocomplete="off" data-items="4" name="p" class="form-control" type="text" tabindex="1" placeholder="Search 359 records for" value=""><span class="input-group-btn invenio-collapsable-tabs" data-toggle="buttons"> \
        //             <a href="#" class="btn btn-default right-not-rounded left-not-rounded" data-toggle="collapse" data-target="#navbar-bottom"> \
        //               <i class="glyphicon glyphicon-plus"></i> \
        //               <b class="caret"></b> \
        //             </a> \
        //             <a href="#" class="btn btn-default right-not-rounded left-not-rounded" data-toggle="collapse" data-target="#navbar-bottom2"> \
        //               <i class="glyphicon glyphicon-cog"></i> \
        //               <b class="caret"></b> \
        //             </a> \
        //           </span><span class="input-group-btn"> \
        //             <button name="action_search" type="submit" class="btn btn-primary btn-inline-icon-hide-sm"> \
        //               <i class="glyphicon glyphicon-search"></i><span>&nbsp;Search</span> \
        //             </button> \
        //           </span>');
        //searchInputGroup.append(stuff);

        searchInput = $('<input type="text" class="form-control" placeholder="Search">');
        searchInputGroup.append(searchInput);
        searchInput.keypress(function(event){
            if(event.keyCode == 13){ 
                event.preventDefault();
                onSearchButtonClick();
            }
        });

        var buttonSpan = $('<span class="input-group-btn" style="width:1%"></span>');
        searchInputGroup.append(buttonSpan);

        var searchButton = $('<button class="btn btn-primary cnri-search-button" type="button">\
                                <i class="glyphicon glyphicon-search"></i>\
                                <span>&nbsp;Search</span>\
                              </button>');
        buttonSpan.append(searchButton);
        searchButton.click(onSearchButtonClick);

        if (allowCreate) {
            createButtonSpan = $('<span class="input-group-btn" style="width:1%"></span>');
        } else {
            createButtonSpan = $('<span class="input-group-btn" style="width:1%; display:none"></span>');
        }
        
        searchInputGroup.append(createButtonSpan);
        
        if (Object.keys(schemas).length === 1) {
            buildSingleCreateButton(createButtonSpan);
        } else {
            var types = Object.keys(schemas);
            buildCreateDropdown(createButtonSpan, types);
        }
        
        resultsContainer = $('<div class="row well" style="display:none"><div/>');
        //containerDiv.append(resultsContainer);
        resultsContainerDiv.append(resultsContainer);
        
        var paginationContainerDiv = $('<div/>');
        resultsContainer.append(paginationContainerDiv);

        var closeButton = $('<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>');
        paginationContainerDiv.append(closeButton);
        closeButton.click(onCloseClick);

        retrieveJsonForm = $('<form style="display:none" method="POST" target="_blank"/>');
        var csrfInput = $('<input type="hidden" name="csrfToken"/>');
        csrfInput.val(APP.getCsrfCookieToken());
        retrieveJsonForm.append(csrfInput);
        paginationContainerDiv.append(retrieveJsonForm);
        
        var retrieveJsonButton = $('<a class="btn btn-sm btn-default pull-right">JSON View</a>');
        retrieveJsonButton.css("margin-right", "4px");
        retrieveJsonButton.click(function (event) {
            event.preventDefault();
            csrfInput.val(APP.getCsrfCookieToken());
            retrieveJsonForm.submit();
        });
        paginationContainerDiv.append(retrieveJsonButton);

        paginationDiv = $('<div class=""></div>');
        paginationContainerDiv.append(paginationDiv);

        resultsDiv = $('<div class=""></div>');
        resultsContainer.append(resultsDiv);
        
        var paginationContainerBottomDiv = $('<div/>');
        resultsContainer.append(paginationContainerBottomDiv);
        
        paginationBottomDiv = $('<div class=""></div>');
        paginationContainerBottomDiv.append(paginationBottomDiv);
    }
    
    function setAllowCreateTypes(types) {
        createButtonSpan.empty();
        if (types.length === 0) {
            createButtonSpan.hide();
            return;
        }
        if (types.length === 1) {
            buildSingleCreateButton(createButtonSpan, types[0]);
        } else {
            buildCreateDropdown(createButtonSpan, types);
        }
        createButtonSpan.show();
        
    } 
    self.setAllowCreateTypes = setAllowCreateTypes;
    
    function setAllowCreate(allowCreateParam) {
        allowCreate = allowCreateParam;
        if (allowCreate) {
            createButtonSpan.show();
        } else {
            createButtonSpan.hide();
        }
    }
    self.setAllowCreate = setAllowCreate;
    
    function buildSingleCreateButton(form, type) {
        var createButton = $('<button class="btn btn-danger cnri-create-button"></button>');
        createButton.data("type", type);
        if (type) {
            createButton.text("Create " + type);
        } else {
            createButton.text("Create");
        }
        form.append(createButton);
        createButton.click(onCreateButtonClicked);
    }
    
    function onCreateButtonClicked(e) {
        e.preventDefault();
        var createButton = $(this);
        var type = createButton.data("type");
        //var type = Object.keys(schemas)[0];
        APP.createNewObject(type);
    }
    
    function buildCreateDropdown(form, types) {
        var dropdownDiv = $('<div class="dropdown" style="display:inline-block;vertical-align:top"></div>');
        form.append(dropdownDiv);
        
        var dropdownButton = $('<button class="btn btn-danger dropdown-toggle cnri-create-button" type="button" id="dropdownMenu1" data-toggle="dropdown">Create <span class="caret"></span></button>');
        dropdownDiv.append(dropdownButton);
        
        var createList = $('<ul class="dropdown-menu" role="menu" aria-labelledby="dropdownMenu1"></ul>');
        dropdownDiv.append(createList);
        
        for (var i = 0; i < types.length; i++) {
            var objectType = types[i];
            var schema = schemas[objectType];
            var typeTitle = objectType;
            if (schema.title) {
                typeTitle = schema.title;
            }
            
            var menuItem = $('<li role="presentation"></li>');
            var menuLink = $('<a role="menuitem" tabindex="-1" href="#">'+typeTitle+'</a>');
            menuLink.attr("data-objectType", objectType);
            menuItem.append(menuLink);
            createList.append(menuItem);
            menuLink.click(onCreateClicked);
        }
    }
    
    function onCloseClick() {
        resultsContainer.hide();
    }
    
    function hideResults() {
        resultsContainer.hide(300);
    } 
    self.hideResults = hideResults;
    
    function showResults() {
        if (lastQueryResponse != null) {
            resultsContainer.show(300);
        }
    }
    self.showResults = showResults;
    
    function onCreateClicked(e) {
        e.preventDefault();
        var clickedItem = $(e.target);
        var objectType = clickedItem.attr("data-objectType");
        APP.createNewObject(objectType);
    }
    
    function onSearchButtonClick() {
        var query = searchInput.val();
        if ("" === query) {
            return;
        }
        if (isObjectId(query)) {
            query = "id:"+query;
        }
        var pageNum = 0;
        APP.search(query, pageNum, pageSize, null, function (response) { onSuccess(query, null, response); }, onError);
    }
    
    function search(query, sortFields) {
        if ("" === query) {
            return;
        }
        var pageNum = 0;
        APP.search(query, pageNum, pageSize, sortFields, function (response) { onSuccess(query, sortFields, response); }, onError);
    }
    self.search = search;
    
    function isObjectId(str) {
        if (startsWith(str, serverPrefix)) {
            var suffix = str.substring(serverPrefix);
            if (!containsSpaces(suffix)) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    
    function containsSpaces(str) {
        return str.lastIndexOf(" ", 0) === 0;
    }
    
    function startsWith(str, prefix) {
        return str.lastIndexOf(prefix, 0) === 0;
    }
    
    function onSuccess(query, sortFields, response) {
        APP.notifications.clear();
    	APP.setQueryInFragment(query);
        lastQueryResponse = response;
        paginationDiv.empty();
        paginationBottomDiv.empty();
        resultsDiv.empty();
        var results = response.results;
        if (response.size > pageSize || response.size === -1) {
            var pagination = new SearchResultsPagination(paginationDiv, response.size, response.pageNum, response.pageSize, function (ev) { onPageClick(query, sortFields, ev); }); 
            var paginationBottom = new SearchResultsPagination(paginationBottomDiv, response.size, response.pageNum, response.pageSize, function (ev) { onPageClick(query, sortFields, ev); }); 
        } else if (response.size === 0) {
            //no-op
        } 
        writeResultsToResultsDiv(results);
        retrieveJsonForm.attr("action", getRestApiUriFor(query, response.pageNum, response.pageSize, sortFields));
        resultsContainer.show();
    }
    
    function getRestApiUriFor(query, pageNum, pageSize, sortFields) {
        var url = "objects/?query=" + encodeURIComponent(query) + "&pageNum=" + pageNum + "&pageSize=" + pageSize;
        if (sortFields) {
            url = url + "&sortFields=" + sortFields;
        }
        return url;
    }
    
    function writeResultsToResultsDiv(results) {
        resultsDiv.empty();
        var list = $('<ul class="search-results-list"></ul>');
        resultsDiv.append(list);
        if (results.length === 0) {
            var noResultsLabel = $('<label>No Results</label>');
            resultsDiv.append(noResultsLabel);
        }
        for (var i = 0; i < results.length; i++) {
            var result = results[i];
            var li = ObjectPreviewUtil.elementForSearchResult(result, onHandleClick, result.metadata.remoteRepository, APP.getUiConfig());
            list.append(li);
        }
    }
    
    function onPageClick(query, sortFields, ev) {
        ev.preventDefault();
        var pageNum = $(ev.target).data("pageNumber");
        APP.search(query, pageNum, pageSize, sortFields, function (response) { onSuccess(query, sortFields, response); }, onError);
    }
    
    function onHandleClick(e) {
        e.preventDefault();
        var link = $(this);
        var handle = link.attr('data-handle');
        //var handle = link.text();
        APP.resolveHandle(handle);
    }
    
    function onError(response) {
        resultsDiv.empty();
        APP.notifications.alertError(response.message);
    }
    
    constructor();
}
window.SearchWidget = SearchWidget;

})();
