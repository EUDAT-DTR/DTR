/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function SearchResultsPagination(containerDiv, numResults, pageOffSet, pageSize, onPageClick) {
    var self = this;
    var showTotalResultsLabel = true;
    var showDownloadJson = true;
    
    function constructor() {
        containerDiv.css("margin-bottom", "10px");
        
        var paginationContainer = $('<div></div>');
        containerDiv.append(paginationContainer);
        
//        if (showTotalResultsLabel) {
//            var totalLabel = null;
//            
//            var rangeText = getRangeTextForPage();
//            
//            if (numResults == 1) {
//                totalLabel = $('<span>' + numResults + ' result </span>');
//            } else if (numResults == -1) {
//                totalLabel = $('<span>' + 'max results ' + '</span>');
//                numResults = 16384; //Repository max results
//            } else {
//                totalLabel = $('<span>'+ rangeText + " of " + numResults + ' results </span>'); 
//            }
//            if (totalLabel != null) {
//                totalLabel.css("vertical-align", "middle");
//                paginationContainer.append(totalLabel);
//            } 
//        }
        
        var nav = $('<ul class="pagination cnri-pagination-small">');
        nav.css("vertical-align", "middle");
        nav.css("margin-bottom", "0px;");
        paginationContainer.append(nav);
        
        
        var size = pageSize;
        var totalNumOfPages 
        if (numResults === -1) {
            totalNumOfPages = -1;
        } else {
            totalNumOfPages = (numResults/size); 
        }
                
        var roundedTotalNumOfPages = Math.ceil(totalNumOfPages);
        var currentPageNumber = pageOffSet;
        var startingNumber = getStartPaginationNumberForCurrent(currentPageNumber, totalNumOfPages);
        var endingNumber = getEndPaginationNumberForCurrent(currentPageNumber, roundedTotalNumOfPages);
        
        if (totalNumOfPages > 1 || totalNumOfPages === -1) {

//            var firstPageLi = $('<li>');
//            if (currentPageNumber == 0) {
//                firstPageLi.addClass("active");
//            }
//            var firstPage = $('<a id="pageNext" href="#">First</a>');
//            firstPage.data("pageNumber", 0);
//            firstPage.click(onPageClick);
//            firstPageLi.append(firstPage);
//            nav.append(firstPageLi);
            
            var prevLi = $('<li>');
            var prevPage = $('<a id="pagePrev" href="#">Prev</a>');
            if (currentPageNumber == 0) {
                prevLi.addClass("disabled");
                prevPage.data("pageNumber", currentPageNumber);
            } else {
                prevPage.data("pageNumber", currentPageNumber-1);
            }
            prevPage.click(onPageClick);
            prevLi.append(prevPage);
            nav.append(prevLi);

            for(var i = startingNumber; i < endingNumber; i++) {
                var pageNumForDisplay = i+1;
                var li = $('<li>');
                var page = $('<a href="#"></a>').attr('id', 'page' + pageNumForDisplay).text(pageNumForDisplay);
                if (currentPageNumber == i) {
                    li.addClass("active");
                }
                page.data("pageNumber", i);
                page.click(onPageClick);
                li.append(page);
                nav.append(li);
            }

            var nextLi = $('<li>');
            var nextPage = $('<a id="pageNext" href="#">Next</a>');
            if (currentPageNumber == roundedTotalNumOfPages-1) {
                nextLi.addClass("disabled");
                nextPage.data("pageNumber", currentPageNumber);
            } else {
                nextPage.data("pageNumber", currentPageNumber+1);
            }
            nextPage.click(onPageClick);
            nextLi.append(nextPage);
            nav.append(nextLi);

//            var lastPageLi = $('<li>');
//            if (currentPageNumber == roundedTotalNumOfPages-1) {
//                lastPageLi.addClass("active");
//            }
//            var lastPage = $('<a id="pageNext" href="#">Last</a>');
//            lastPage.data("pageNumber", roundedTotalNumOfPages-1);
//            lastPage.click(onPageClick);
//            lastPageLi.append(lastPage);
//            nav.append(lastPageLi);
            
        } 
        
        
        if (showTotalResultsLabel) {
            paginationContainer.append(" ");
            var totalLabel = null;
            var rangeText = getRangeTextForPage();
            if (numResults == 1) {
                totalLabel = $('<span>' + numResults + ' result </span>');
            } else if (numResults == -1) {
                totalLabel = $('<span>'+ rangeText + ' of many results</span>'); 
                
                //totalLabel = $('<span>' + 'max results ' + '</span>');
                //numResults = 16384; //Repository max results
            } else {
                totalLabel = $('<span>'+ rangeText + " of " + numResults + ' results </span>'); 
            }
            if (totalLabel != null) {
                totalLabel.css("vertical-align", "middle");
                paginationContainer.append(totalLabel);
            } 
        }
    }
    
    function getRangeTextForPage() {
        var firstResultOnPageNumber = (pageOffSet * pageSize) + 1;
        var lastResultOnPageNumber = (pageOffSet + 1) * pageSize;
        if (numResults != -1 && lastResultOnPageNumber > numResults) lastResultOnPageNumber = numResults;
        return firstResultOnPageNumber + " to " + lastResultOnPageNumber;
    }

    function getStartPaginationNumberForCurrent(currentPageNumber, totalNumOfPages) {
        var roundedTotalNumOfPages = Math.ceil(totalNumOfPages); 
        var result = 0;
        if (currentPageNumber < 6) {
            result = 0;
        } else {
            result = currentPageNumber - 5;
        }
        if ((roundedTotalNumOfPages - result) < 10) {
            result = roundedTotalNumOfPages -10;
        }
        if (result < 0) {
            result = 0;
        }
        return result;
    }    
    
    function getEndPaginationNumberForCurrent(currentPageNumber, totalNumOfPages) {
        var result = null;
        if (currentPageNumber >= 6) {
            result = currentPageNumber+4;
        } else {
            result = 9;
        }
        if (result > totalNumOfPages) {
            result = totalNumOfPages;
        }
        return result;
    }
    
    constructor();
}