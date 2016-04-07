/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function TypeDonutChart(containerDiv) {
    var self = this;
    
   // var colors = ["#98abc5", "#8a89a6", "#7b6888", "#6b486b", "#a05d56", "#d0743c", "#ff8c00"];
    
    var typesDiv = null;
    var usersDiv = null;
    
    var colors = [
    "#9E0041",
    "#C32F4B",
    "#E1514B",
    "#F47245",
    "#FB9F59",
    "#FEC574",
    "#FAE38C",
    "#EAF195",
    "#C7E89E",
    "#9CD6A4",
    "#6CC4A4",
    "#4D9DB4",
    "#4776B4",
    "#5E4EA1"];
    
    function constructor() {
        containerDiv.addClass("row");
        
        typesDiv = $('<div class="col-md-6" style="display: inline-block;"></div>');
        containerDiv.append(typesDiv);
        
        usersDiv = $('<div class="col-md-6" style="display: inline-block;"></div>');
        containerDiv.append(usersDiv);

        
//        var title = $('<h4>Types</h4>');
//        containerDiv.append(title);
        
        APP.getStatus(onGotStatusSuccess, onGotStatusError);
        APP.getCountByUser(onGotCountByUserSuccess, onGotCountByUserError);
    }
    
    function onGotStatusSuccess(response) {
        var data = [];
        var colorIndex = 0;
        for (var type in response.typeCount) {
            var typeCount = response.typeCount[type];
            var extraLabel = type + "(" + typeCount + ")";
            var typeData = {
                    value : typeCount,
                    color: colors[colorIndex],
                    highlight: colorLuminance(colors[colorIndex], 0.1),
                    label: type,
                    extraLabel : extraLabel
            };
            data.push(typeData);
            colorIndex++;
            if (colorIndex > colors.length -1) {
                colorIndex = 0;
            }
        }
        buildTypeChart(data);
    }
    
    function onGotCountByUserSuccess(response) {
        var data = [];
        var colorIndex = 0;
        for (var i = 0; i < response.length; i++) {
            var userData = response[i];
            if (userData.createCount > 0) {
                var item = {
                        value : userData.createCount,
                        color: colors[colorIndex],
                        highlight: colorLuminance(colors[colorIndex], 0.1),
                        label: userData.username  
                };
                data.push(item);
                colorIndex++;
                if (colorIndex > colors.length -1) {
                    colorIndex = 0;
                }
            }
        }
        buildCountByUserChart2(data);
    }
    
    function buildCountByUserChart(data) {
        var countChartDiv = $('<canvas id="polar" width="400" height="400" style="width: 400px; height: 400px; display: block; vertical-align:top;"></canvas>');
        usersDiv.append(countChartDiv);
        
        var options = {
            scaleShowLabelBackdrop : true,
            scaleBackdropColor : "rgba(255,255,255,0.75)",
            scaleBeginAtZero : true,
            scaleBackdropPaddingY : 2,
            scaleBackdropPaddingX : 2,
            scaleShowLine : true,
            segmentShowStroke : true,
            segmentStrokeColor : "#fff",
            segmentStrokeWidth : 2,
            animationSteps : 100,
            animationEasing : "easeOutQuart",
            animateRotate : true,
            animateScale : false,
        };

        
        
        var ctx = countChartDiv.get(0).getContext("2d");
        var countChart = new Chart(ctx).PolarArea(data, options);
        
        var legendDiv = $('<div style="display: inline-block; vertical-align:top;"></div>');
        usersDiv.append(legendDiv);
        var legend = countChart.generateLegend();
        legendDiv.append(legend); 
        
        var label = $('<p>Number of digital objects created by users.</p>');
        usersDiv.append(label);
    }
    
    function buildCountByUserChart2(data) {
        var label = $('<p>Number of digital objects created by users.</p>');
        usersDiv.append(label);
        
        var countChartDiv = $('<canvas id="polar" width="400" height="400" style="width: 400px; height: 400px; display: block; vertical-align:top;"></canvas>');
        usersDiv.append(countChartDiv);
        
        var options = {
                segmentShowStroke : true,
                segmentStrokeColor : "#fff",
                segmentStrokeWidth : 2,
                percentageInnerCutout : 50, // This is 0 for Pie charts
                animationSteps : 100,
                animationEasing : "easeOutQuart",
                animateRotate : true,
                animateScale : false,
                legendTemplate : "<ul class=\"<%=name.toLowerCase()%>-legend\"><% for (var i=0; i<segments.length; i++){%><li><span style=\"background-color:<%=segments[i].fillColor%>\"></span><%if(segments[i].label){%><%=segments[i].label + ' (' + segments[i].value + ')'%><%}%></li><%}%></ul>"
            };

        
        
        var ctx = countChartDiv.get(0).getContext("2d");
        var countChart = new Chart(ctx).Doughnut(data, options);
        
        var legendDiv = $('<div style="display: inline-block; vertical-align:top;"></div>');
        usersDiv.append(legendDiv);
        var legend = countChart.generateLegend();
        legendDiv.append(legend); 
        

    }    
    
    
    function onGotStatusError(response) {
        
    }
    
    function onGotCountByUserError(response) {

    }
    
    function buildTypeChart(data) {
        var options = {
                segmentShowStroke : true,
                segmentStrokeColor : "#fff",
                segmentStrokeWidth : 2,
                percentageInnerCutout : 50, // This is 0 for Pie charts
                animationSteps : 100,
                animationEasing : "easeOutQuart",
                animateRotate : true,
                animateScale : false,
                legendTemplate : "<ul class=\"<%=name.toLowerCase()%>-legend\"><% for (var i=0; i<segments.length; i++){%><li><span style=\"background-color:<%=segments[i].fillColor%>\"></span><%if(segments[i].label){%><%=segments[i].label + ' (' + segments[i].value + ')'%><%}%></li><%}%></ul>"
            };
        
        var label = $('<p>Number of digital objects.</p>');
        typesDiv.append(label);
        
        var chartDiv = $('<canvas id="donut" width="400" height="400" style="width: 400px; height: 400px; display: block; vertical-align:top;"></canvas>');
        typesDiv.append(chartDiv);
        
        var ctx = chartDiv.get(0).getContext("2d");
        var doughnutChart = new Chart(ctx).Doughnut(data, options);
        
        var legendDiv = $('<div style="display: inline-block; vertical-align:top;"></div>');
        typesDiv.append(legendDiv);
        var legend = doughnutChart.generateLegend();
        legendDiv.append(legend);   
        
        
        
        chartDiv.click(function(evt){
            var activePoints = doughnutChart.getSegmentsAtEvent(evt);           
            var type = activePoints[0].label;
            var query = "type:"+type;
            var url = getUrlOfRegistrar();
            url = url + "#objects/?query=" + query;
            window.open(url);
        });
    }
    
    function getUrlOfRegistrar() {
        var origin = window.location.origin;
        var path = window.location.pathname;
        var end = path.indexOf("admin.html");
        var suffix = path.substring(0, end);
        var url = origin + suffix;
        return url;
    }
    
    function colorLuminance(hex, lum) {

        // validate hex string
        hex = String(hex).replace(/[^0-9a-f]/gi, '');
        if (hex.length < 6) {
            hex = hex[0]+hex[0]+hex[1]+hex[1]+hex[2]+hex[2];
        }
        lum = lum || 0;

        // convert to decimal and change luminosity
        var rgb = "#", c, i;
        for (i = 0; i < 3; i++) {
            c = parseInt(hex.substr(i*2,2), 16);
            c = Math.round(Math.min(Math.max(0, c + (c * lum)), 255)).toString(16);
            rgb += ("00"+c).substr(c.length);
        }

        return rgb;
    }
    
    
    constructor();
}