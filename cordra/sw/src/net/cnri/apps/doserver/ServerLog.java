/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
  * Object that writes information to access and error log files.
  */
public class ServerLog {
  static final Logger logger = LoggerFactory.getLogger(ServerLog.class);
  
  public static final int LOG_BUFFER_SIZE = 2000;
  public static final int ROTATE_NEVER = 0;
  public static final int ROTATE_WEEKLY = 1;
  public static final int ROTATE_MONTHLY = 2;
  public static final int ROTATE_DAILY = 3;
  
  private File logDirectory = null;
  private String baseFileName;
  private Writer logWriter = null;
  private boolean redirectStdErr = false;
  
  private boolean continuing = true; // Signal to keep going...or not
  
  private final Object LOG_LOCK = new Object();
  private final Calendar logCal;

  private Thread rotaterThread = null;
  
  /** 
    * Construct a log handler to write to a log file with the given name
    * under the specified directory.
    */
  public ServerLog(File logDir, String baseFileName, 
                   boolean redirectStdErr, int rotation)
    throws Exception
  {
    this.logDirectory = logDir;
    this.baseFileName = baseFileName;
    this.redirectStdErr = redirectStdErr;
    this.logCal = Calendar.getInstance();
    
    LogRotater logRotater = null;
    
    switch(rotation) {
      case ROTATE_WEEKLY:
        logRotater = new WeeklyRotater(Calendar.SUNDAY);
        break;
      case ROTATE_DAILY:
        logRotater = new DailyRotater();
        break;
      case ROTATE_MONTHLY:
        logRotater = new MonthlyRotater();
        break;
      case ROTATE_NEVER:
        logRotater = new NonRotater();
        break;
      default:
        throw new Exception("Invalid log rotation: "+rotation);
    }
    
    // make sure the log directory exists and is a directory
    if (logDirectory.exists()) {
      if (!logDirectory.isDirectory())
        throw new Exception("\"" + logDirectory.getAbsolutePath() + "\" is not a directory.");
    } else {
      logDirectory.mkdirs();
    }
    
    // allow the rotater to initialize itself now that all the settings are loaded
    logRotater.init();
    
    // kick off the rotater
    rotaterThread = new Thread(logRotater);
    rotaterThread.setPriority(Thread.MIN_PRIORITY);
    rotaterThread.setDaemon(true);
    rotaterThread.start();

    // wait for the log rotater to get started
    while(!logRotater.initialized()) {
      try { Thread.sleep(500); } catch (Throwable t) {}
    }
    
    log("Started new run at "+new Date());
  }


  /**
   * Write a message to the log
   */
  public void log(String logEntry) 
    throws Exception
  {
    synchronized(LOG_LOCK) {
      if (logWriter == null) {
        throw new Exception("Cannot write to unopened log");
      } else {
        logWriter.write(logEntry);
        logWriter.write('\n');
        logWriter.flush();
      }
    }
  }
  
  /** 
    * Sets the file where access log entries will be written.  If the file 
    * already exists then new entries will be appended to the file.
    */
  private void setLogFile(File newLogFile)
    throws IOException
  {
    synchronized(LOG_LOCK) {
      // close the old access log
      if(logWriter!=null) {
        //try {
        //  logWriter.write("info:logging finished: "+(new Date()));
        //} catch (Exception e) {}
        logWriter.flush();
        logWriter.close();
        logWriter = null;
      }
      
      // open the new log, without buffering if we are taking over stderr
      if(redirectStdErr) {
        PrintStream stream = new PrintStream(new FileOutputStream(newLogFile, true));
        logWriter = new OutputStreamWriter(stream, "UTF8");
        System.setErr(stream);
      } else {
        logWriter = new BufferedWriter(new FileWriter(newLogFile.getAbsolutePath(), true),
                                       LOG_BUFFER_SIZE);
      }
      //logWriter.write("logging started: "+(new Date()));
    }
  }
  
  
  /**
   * Stop the flusher thread and close the logs.
   */
  public void shutdown() {
    continuing = false;              // Tell flusher thread to quit
    synchronized(LOG_LOCK) {         // Wake the flusher thread, if it's asleep
      try { rotaterThread.interrupt(); } catch (Exception e) { /* Ignore */ }
    }
    
    if (logWriter != null) {
      try { logWriter.close(); } catch (Exception e) { /* Ignore */ }
    }
  }


  private abstract class LogRotater
    implements Runnable
  {
    private boolean isInitialized = false;
    
    /**
     * Initialize the log rotater
     */
    public void init() {}
    
    /**
     * Return the next time that the logs should be rotated in milliseconds
     */
    public abstract long getNextRotationTime(long currentTime);
    
    /**
     * Return the suffix that should be appended to access/error log file names
     * for the time period containing the given time.
     */
    public abstract String getLogFileSuffix(long currentTime);
    
    public boolean initialized() {
      return isInitialized;
    }
    
    public void run() {
      while(continuing) {
        long now = System.currentTimeMillis();
        try {
          // get the name of the log file for the current period
          String suffix = getLogFileSuffix(now);
          setLogFile(new File(logDirectory, baseFileName + suffix));
        } catch (Throwable t) {
          logger.error("Error setting log files",t);
        }
        isInitialized = true;
        
        // wait until the next rotation time
        long nextRotationTime = getNextRotationTime(now);
        while(continuing && nextRotationTime > System.currentTimeMillis()) {
          try {
            long waitTime = Math.max(1000, nextRotationTime - System.currentTimeMillis());
            Thread.sleep(waitTime);
          } catch (Throwable t) { }
        }
      }
    }

    /**
     * Return a date-stamp for the given date.
     */
    protected String getSuffixForDate(Calendar cal) {
      return "-"+String.valueOf(cal.get(Calendar.YEAR)*10000 +
                                (cal.get(Calendar.MONTH)+1)*100 +
                                cal.get(Calendar.DAY_OF_MONTH));
    }
  }


  class WeeklyRotater
    extends LogRotater
  {
    protected Calendar cal = Calendar.getInstance();
    private int dayOfRotation;

    WeeklyRotater(int dayOfRotation) {
      this.dayOfRotation = dayOfRotation;
    }
    
    /**
     * Return the next time that the logs should be rotated in milliseconds
     */
    public long getNextRotationTime(long currentTime) {
      cal.setTime(new Date(currentTime));
      do {
        cal.add(Calendar.DATE, 1);
      } while(cal.get(Calendar.DAY_OF_WEEK)!=dayOfRotation);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 1);
      cal.set(Calendar.MILLISECOND, 0);
      return cal.getTime().getTime();
    }
    
    /**
     * Return the suffix that should be appended to access/error log file names
     * for the time period containing the given time.
     */
    public String getLogFileSuffix(long currentTime) {
      cal.setTime(new Date(currentTime));
      while(cal.get(Calendar.DAY_OF_WEEK)!=dayOfRotation)
        cal.add(Calendar.DATE, -1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 1);
      return getSuffixForDate(cal);
    }
  }
    
    
  class MonthlyRotater
    extends LogRotater
  {
    protected Calendar cal = Calendar.getInstance();

    /**
     * Return the next time that the logs should be rotated in milliseconds
     */
    public long getNextRotationTime(long currentTime) {
      cal.setTime(new Date(currentTime));
      int thisMonth = cal.get(Calendar.MONTH);
      while(cal.get(Calendar.MONTH)==thisMonth)
        cal.add(Calendar.DATE, 1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 1);
      cal.set(Calendar.MILLISECOND, 0);
      return cal.getTime().getTime();
    }
    
    /**
     * Return the suffix that should be appended to access/error log file names
     * for the time period containing the given time.
     */
    public String getLogFileSuffix(long currentTime) {
      cal.setTime(new Date(currentTime));
      while(cal.get(Calendar.DAY_OF_MONTH)!=1)
        cal.add(Calendar.DATE, -1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 1);

      return "-"+String.valueOf(cal.get(Calendar.YEAR)*100 +
                                (cal.get(Calendar.MONTH)+1));
    }
  }
    

  class DailyRotater
    extends LogRotater
  {
    protected Calendar cal = Calendar.getInstance();
    
    /**
     * Return the next time that the logs should be rotated in milliseconds
     */
    public long getNextRotationTime(long currentTime) {
      cal.setTime(new Date(currentTime));
      cal.add(Calendar.DATE, 1);
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 1);
      cal.set(Calendar.MILLISECOND, 0);
      return cal.getTime().getTime();
    }
    
    /**
     * Return the suffix that should be appended to access/error log file names
     * for the time period containing the given time.
     */
    public String getLogFileSuffix(long currentTime) {
      cal.setTime(new Date(currentTime));
      cal.set(Calendar.HOUR_OF_DAY, 0);
      cal.set(Calendar.MINUTE, 0);
      cal.set(Calendar.SECOND, 1);
      return getSuffixForDate(cal);
    }
  }
    
  
  class NonRotater
    extends LogRotater
  {
    protected Calendar cal = Calendar.getInstance();
    
    /**
     * Return the next time that the logs should be rotated in milliseconds
     */
    public long getNextRotationTime(long currentTime) {
      return Long.MAX_VALUE;
    }
    
    /**
     * Return the suffix that should be appended to access/error log file names
     * for the time period containing the given time.
     */
    public String getLogFileSuffix(long currentTime) {
      return "";
    }
  }
    
  

  
  
}
