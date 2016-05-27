/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function HtmlPageViewer(containerDiv, options) {
    var self = this;
    
    function constructor() {
        containerDiv.empty();
        var closeButton = $('<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>');
        containerDiv.append(closeButton);
        closeButton.click(onCloseClick);
        var contentUrl = "";
        if (options.type == "document") {
            contentUrl = "design?elementName=" + elementName;
        } else {
            contentUrl = options.url;
        }
                    
//        var base = $('<base target="_blank" />');
//        containerDiv.append(base);
        
        var iframe = $('<iframe style="width:100%; min-height:217px; max-height:1200px" frameborder="0" scrolling="no" marginheight="0" marginwidth="0" src="' + contentUrl + '"></iframe>');
        iframe.load(function () {
            if (iframe[0].contentDocument) {
                iframe.height(iframe[0].contentDocument.body.scrollHeight);
            }
            if (217 === iframe.height()) {
                iframe.css('min-height', '900px');
            }
        });
        containerDiv.append(iframe);
    }
    
    function onCloseClick() {
        APP.hideHtmlContent();
    }
    
    constructor();
}

window.HtmlPageViewer = HtmlPageViewer;

})();
