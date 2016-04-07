/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.debug;

import java.io.*;

public final class TimeHelper {
  private static final int MAX_TIMES = 200;
  private long times[];
  private String timeDescs[];
  private int timeIdx = 0;
  private String timerLabel = "Event Timer";

  public TimeHelper(String label) {
    this.timerLabel = label;
    times = new long[MAX_TIMES];
    timeDescs = new String[MAX_TIMES];
    recordTick("Start Time");
  }

  public TimeHelper() {
    this("Event Timer");
  }
  
  public synchronized final void recordTick(String desc) {
    if(timeIdx>=MAX_TIMES) return;
    
    times[timeIdx] = System.currentTimeMillis();
    timeDescs[timeIdx++] = desc;
  }

  public synchronized void printTimes(PrintStream out) {
    out.println(timerLabel+":");
    for(int i=1; i<timeIdx; i++) {
      out.println("  "+timeDescs[i]+": "+(times[i]-times[i-1])+" ms");
    }
    out.println("--total time: "+(times[timeIdx-1]-times[0]));
    
  }
  
}
  
