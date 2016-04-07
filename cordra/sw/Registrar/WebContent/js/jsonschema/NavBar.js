/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function NavBar(navBarElement, navBarConfig, schemas, alienSchemas) {
    var self = this;
    
    function constructor() {
        for (var i = 0; i < navBarConfig.length; i++) {
            var navLink = navBarConfig[i];
            if (navLink.type === "document") {
                addDocumentLink(navLink);
            } else if (navLink.type === "url") {
                addUrlLink(navLink);
            } else if (navLink.type === "query") {
                addQueryLink(navLink);
            } else if (navLink.type === "typeDropdown") {
                addQueryTypeDropDown(navLink);
            }
        }
        
    }
    
    function addDocumentLink(navLink) {
        var li = $('<li></li>');
        var link = $('<a target="_self"></a>');
        link.text(navLink.title);
        link.attr('href', "#pages/" + navLink.dataElementName);
        navBarElement.append(li);
        li.append(link);
    }
    
    function addUrlLink(navLink) {
        var li = $('<li></li>');
        var link = $('<a target="_self"></a>');
        link.text(navLink.title);
        link.attr('href', "#urls/" + navLink.url);
        navBarElement.append(li);
        li.append(link);
    }    
    
    function addQueryTypeDropDown(navLink) {
        var dropdownLi = $('<li class="dropdown"></li>');
        navBarElement.append(dropdownLi);
        
        var dropdownToggle = $('<a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false"> </a>');
        dropdownLi.append(dropdownToggle);
        
        var title = "Types";
        if (navLink.title) {
            title = navLink.title;
        }
        dropdownToggle.text(title + " ");

        var caret = $('<span class="caret"></span>');
        dropdownToggle.append(caret);
        
        var dropdownList = $('<ul class="dropdown-menu" role="menu"></ul>');
        dropdownLi.append(dropdownList);
        
        var deduplicatedSchemas = getDeduplicatedSchemas();
        for (var schemaName in deduplicatedSchemas) {
            var schema = deduplicatedSchemas[schemaName];
            var linkName = schemaName;
            if (schema.title) {
                linkName = schema.title;
            }
            var menuItem = $('<li></li>');
            dropdownList.append(menuItem);
            
            var itemLink = $('<a target="_self"></a>');
            itemLink.text(linkName);
            itemLink.attr('href', '#objects/?query=type:' + encodeURIComponent('"' + schemaName + '"'));
            
            menuItem.append(itemLink);
        }  
    }
    
    function getDeduplicatedSchemas() {
        var res = {};
        for (var schemaName in schemas) {
            res[schemaName] = schemas[schemaName];
        }
        if (alienSchemas) {
            for (var uri in alienSchemas) {
                for (var schemaName in alienSchemas[uri]) {
                    if (!res[schemaName]) {
                        res[schemaName] = alienSchemas[uri][schemaName];
                    }
                }
            }            
        }
        return res;
    }
    
    function addQueryLink(navLink) {
        var li = $('<li></li>');
        var link = $('<a href=# ></a>');
        link.text(navLink.title);
        link.attr('data-query', navLink.query);
        if (navLink.sortFields) {
            link.attr('data-sort-fields', navLink.sortFields);
        }
        link.click(onQueryLinkClick);
        navBarElement.append(li);
        li.append(link);
    }
    
    function onQueryLinkClick(e) {
        e.preventDefault();
        var link = $(this);
        var query = link.attr('data-query');
        var sortFields = link.attr('data-sort-fields');
        APP.performSearchWidgetSearch(query, sortFields);
    }
    
    constructor();
}

window.NavBar = NavBar;

})();