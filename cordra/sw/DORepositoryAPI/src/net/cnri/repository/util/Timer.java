/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

public class Timer {
	private long startTime = System.nanoTime();
	private long endTime;
	private long duration = 0; 
	private boolean isRunning = false;
	private String name = "";

	public Timer() {}
	
	public Timer(String name) {
		this.name = name;
	}
	
	public void start() {
		if (!isRunning) {
			startTime = System.nanoTime();
			isRunning = true;
		}
	}
	
	public void stop() {
		if (isRunning) { 
			endTime = System.nanoTime();
			duration += endTime - startTime;
			isRunning = false;
		}
	}
	
	public void reset() {
		stop();
		duration = 0;
	}
	
	public String getResult() {
		if (isRunning) stop();
		return name +":Process took " + duration + " nano seconds (" + duration/1000000000d + " seconds)";
	}
	
	public String toString() {
		return getResult();
	}
	
	public long getDuration() { 
	    if(isRunning) return duration + System.nanoTime() - startTime;
	    else return duration; 
	}
}
