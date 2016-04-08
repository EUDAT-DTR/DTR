/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function SubscriptionBanner(containerDiv, prefixParam) {
    var self = this;
    var prefix = null;
    var ONE_DAY = 1000 * 60 * 60 * 24; 
    
    function constructor() {
        if (!startsWith(prefixParam, "0.NA/") && !startsWith(prefixParam, "0.na/")) {
            prefix = "0.NA/" + prefixParam;
        } else {
            prefix = prefixParam;
        }
        
        var url = "http://hdl.handle.net/api/handles/" + prefix;
        $.getJSON(url).done(function (handleRecord) {
            var subscriptionInfoHandleValue = getValueByType(handleRecord, "PREFIX_SUBSCRIPTION_INFO");
            if (subscriptionInfoHandleValue) {
                var subscriptionInfoJson = subscriptionInfoHandleValue.data.value;
                var subscriptionInfo = JSON.parse(subscriptionInfoJson);
                displaySubscriptionBanner(subscriptionInfo);
            } else {
                //no subscription info, do not display banner
            }
        }).fail(function (response) {
            
        });
    }
    
    function displaySubscriptionBanner(subscriptionInfo) {
        var closeButton = $('<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>');
        containerDiv.append(closeButton);
        closeButton.click(onCloseClick);
        
        var expirationDateISO8601 = subscriptionInfo.expirationDate;
        var expirationDate = new Date(expirationDateISO8601);
        console.log(expirationDate);
        var duration = getTimeUntilExpiration(expirationDate);
        var daysLeft = msToDays(duration);
        var message = null;
        if (daysLeft > 1) {
            message = $('<p>Handle Prefix '+prefix+' will expire in '+ daysLeft +' days. Click <a href="http://handle.net">here</a> to convert the prefix to operational status.</p>');       
        } else if (daysLeft == 1) {
            message = $('<p>Handle Prefix '+prefix+' will expire in '+ daysLeft +' day. Click <a href="http://handle.net">here</a> to convert the prefix to operational status.</p>');       
        } else if (daysLeft < 1) {
            message = $('<p>Handle Prefix '+prefix+' has expired. Click <a href="http://handle.net">here</a> to convert the prefix to operational status.</p>');       
        }
        containerDiv.append(message);
        containerDiv.show();
    }
    
    function onCloseClick() {
        containerDiv.hide();
    }
    
    function msToDays(duration) {
        return Math.round(duration / ONE_DAY);
    }
    
    function getTimeUntilExpiration(expirationDate) {
        var expiresTimestamp = expirationDate.getTime();
        var now = new Date().getTime();
        var delta = expiresTimestamp - now;
        return delta;
    }
    
    function getValueByType(handleRecord, type) {
        for (var i = 0; i < handleRecord.values.length; i++) {
            var value = handleRecord.values[i];
            if (type === value.type) return value; 
        }
        return null;
    }
    
    function startsWith(s, start) {
        return s.indexOf(start) === 0;
    }
    
    constructor();
}