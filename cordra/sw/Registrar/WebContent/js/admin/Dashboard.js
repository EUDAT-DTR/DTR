/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function Dashboard(containerDiv) {

    var self = this;
    var typeDonutChart = null;
    var typeDonutChartDiv = null;
    var objectTimelineDiv = null;
    var objectTimeline = null;

    
    var isFirstTime = true;
    
    function constructor() {
        var title = $('<h4>Dashboard</h4>');
        containerDiv.append(title);
        
        typeDonutChartDiv = $('<div></div>');
        containerDiv.append(typeDonutChartDiv);
        
//        objectTimelineDiv = $('<div></div>');
//        containerDiv.append(objectTimelineDiv);
    }
    
    function show() {
        if (isFirstTime) {
            //wait until the Dashboard is visible before constructing the chart or it will have a size of zero
            typeDonutChart = new TypeDonutChart(typeDonutChartDiv);
            //objectTimeline = new ObjectTimeline(objectTimelineDiv);
            isFirstTime = false;
        }
    }
    self.show = show;
    
    constructor();
}