/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
    "use strict";

    var window = window || self;

    var BatchObjectRetriever = {};
    window.BatchObjectRetriever = BatchObjectRetriever;

    BatchObjectRetriever.retrieve = function (objectsToRetrieve, allRetrievedCallback, errorCallback) {
        var promises = [];
        for (var i = 0; i < objectsToRetrieve.length; i++) {
            var objectToRetrieve = objectsToRetrieve[i];
            var baseUri = objectToRetrieve.remoteRepository;
            var objectId = objectToRetrieve.id;
            var query = "id:"+ objectId;
            var url = baseUri + "objects/?query=" + encodeURIComponent(query) + "&pageNum=" + 0 + "&pageSize=" + 1;
            var promise = $.getJSON(url);
            promises.push(promise);
        }
        $.when.apply($, promises).done(function (responses) {
            var results = {};
            for (var i = 0; i < responses.length; i++) {
                var response = responses[i];
                if (response.results.length > 0) {
                    var result = response.results[0];
                    results[result.id] = result;
                }
            }
            allRetrievedCallback(results);
        }).fail(errorCallback);
    };
})();