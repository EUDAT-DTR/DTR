/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;
import net.cnri.util.StringUtils;

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines an object that can record operations on
 * digital objects in a registry server.
 */
public class AuditLog {
  static final Logger logger = LoggerFactory.getLogger(AuditLog.class);

  private static final int TXN_ID_PATH_LEN = 20;
  private static final int SEGMENT_SIZE = 4;
  private int TXN_LOG_DEPTH = TXN_ID_PATH_LEN/SEGMENT_SIZE;

  static final String OP_INFO_FILE = "op";
  static final String INPUT_FILE = "in";
  static final String OUTPUT_FILE = "out";
  static final String PROGRESS_FILE = "inprogress";
  
  private Main main = null;
  private FileWriter catalogWriter = null;
  private AuditLogArchiver archiver = null;
  private File catalogFile = null;
  private File auditDir = null;
  private File nextIDFile = null;
  private long nextTxnID = 0;
  
  AuditLog(Main m, File auditDir)
    throws Exception
  {
    this.main = m;
    this.auditDir = auditDir;

    auditDir.mkdirs();
    
    archiver = new AuditLogArchiver(this);
    archiver.compressLogs(auditDir, true);
    
    nextIDFile = new File(auditDir, "nextid");
    if(nextIDFile.exists()) {
      BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(nextIDFile),"UTF-8"));
      String line = rdr.readLine();
      rdr.close();
      if(line==null)
        throw new Exception("Invalid transaction ID '"+line+"' in audit file "+
                            nextIDFile.getAbsolutePath());
      nextTxnID = Long.parseLong(line.trim(), 16);
    } else {
      nextTxnID = 1;
    }

    catalogFile = new File(auditDir, "catalog");
    catalogWriter = new FileWriter(catalogFile.getAbsolutePath(), true);
    catalogWriter.write("\n<info>\taudit log started: "+(new java.util.Date()));
  }

  /** This returns the next transaction ID without modifying it. */
  //synchronized long peekAtNextTxnID() {
  //  return nextTxnID+1;
  //}
  
  
  /** This calculates and returns the next transaction ID while recording an
    * entry in the 'catalog'.
    */
  private synchronized long getNextTxnID(String objectID, String opID, String callerID)
    throws Exception
  {
    // TODO:  Make this gracefully handle filesystem-full errors
    
    // find and increment the next transaction ID
    FileWriter fw = null;
    long txnID;
    try {
      fw = new FileWriter(nextIDFile);
      fw.write(Long.toHexString(nextTxnID+1));
      fw.close();
      nextTxnID++;
      txnID = nextTxnID;
    } finally {
      if(fw!=null) try { fw.close(); } catch (Throwable t) {}
    }
    
    // log this transaction in the catalog
    catalogWriter.write("\n<txn>\t"+txnID+                  // log type ID and txn ID
                        "\t"+System.currentTimeMillis()+    // current date/time
                        "\t"+StringUtils.encode(objectID)+  // encoded object ID
                        "\t"+StringUtils.encode(opID)+      // encoded operation ID
                        "\t"+StringUtils.encode(callerID)); // encoded caller ID
    catalogWriter.flush();
    
    // return the transaction ID
    return txnID;
  }
  
  
  /**
   * This initiates logging of the operation specified in the given requestHeaders,
   * along with the bytes sent over the input and output streams.
   */
  public StreamPair recordOperation(HeaderSet requestHeaders, InputStream in,
                                    OutputStream out)
    throws DOException
  {
    AuditedInvocation invocation = null;
    File logDir = null;
    try {
      logDir = 
      calculateLogDir(getNextTxnID(requestHeaders.getStringHeader("objectid", null),
                                   requestHeaders.getStringHeader("operationid", null),
                                   requestHeaders.getStringHeader("callerid", null)));
      if(!logDir.mkdirs()) {
        throw new DOException(DOException.STORAGE_ERROR, "Unable to log transaction");
      }
      
      invocation = new AuditedInvocation(requestHeaders, logDir, in, out);
    } catch (Exception e) {
      if(e instanceof DOException)
        throw (DOException)e;
      else
        throw new DOException(DOException.STORAGE_ERROR, 
                              "Unable to log transaction: "+e);
    }
    
    try {
      invocation.record();
    } catch (Exception e) {
      if(e instanceof DOException)
        throw (DOException)e;
      else
        throw new DOException(DOException.STORAGE_ERROR, 
                              "Unable to log transaction: "+e);
    }

    try {
      return invocation.getStreamPair();
    } catch (Exception e) {
      if(e instanceof DOException)
        throw (DOException)e;
      else
        throw new DOException(DOException.STORAGE_ERROR, 
                              "Unable to log transaction: "+e);
    }
  }


  private File calculateLogDir(long txnID) {
    return calculateLogDir(String.valueOf(txnID));
  }
  
  private File calculateLogDir(String txnID) {
    txnID = txnID.toLowerCase();
    
    StringBuffer sb = new StringBuffer();
    // since we're building a path, make sure no scary non-digit/non-letter
    // characters were given to us (ie '../../../etc/passwd')
    int idLen = txnID.length();
    for(int i=0; i<idLen; i++) {
      char ch = txnID.charAt(i);
      if("0123456789abcdefghijklmnopqrstuvwxyz".indexOf(ch)>=0)
        sb.append(ch);
    }
    
    while(sb.length()<TXN_ID_PATH_LEN) {
      sb.insert(0, '0');
    }

    int i = SEGMENT_SIZE;
    while(i+1 < sb.length()) {
      sb.insert(i, File.separatorChar);
      i += SEGMENT_SIZE + 1;
    }
    sb.append('t');
    
    if(sb.charAt(sb.length()-1)!=File.separatorChar)
      sb.append(File.separatorChar);
    
    return new File(auditDir, sb.toString());
  }

  
  /**
   * This will write a \n-delimited list of transaction IDs for which the given
   * operation was performed on the given object.  If the objectID and/or
   * operationID are null, they are considered wildcards and will match all
   * transactions.  For the start and end time parameters the value zero indicates
   * that transactions not be limited to start and end times.
   */
  public void listTransactionsIDs(String objectID, String operationID, String callerID,
                                  long gmtStartTime, long gmtEndTime,
                                  Writer txnWriter)
    throws Exception
  {
    BufferedReader catRdr = new BufferedReader(new InputStreamReader(new FileInputStream(catalogFile),"UTF-8"));
    String line;
    long counter = 0;
    while((line=catRdr.readLine())!=null) {
      counter++;
      line = line.trim();
      if(!line.startsWith("<txn>")) continue;
      long txnDt;
      try {
        txnDt = Long.parseLong(StringUtils.fieldIndex(line, '\t', 2));
        if(gmtStartTime!=0 && txnDt < gmtStartTime) continue;
        if(gmtEndTime!=0 && txnDt > gmtEndTime) continue;

        if(objectID!=null &&
           !objectID.equalsIgnoreCase(StringUtils.fieldIndex(line, '\t', 3)))
          continue;

        if(operationID!=null &&
           !operationID.equalsIgnoreCase(StringUtils.fieldIndex(line, '\t', 4)))
          continue;
        
        if(callerID!=null &&
           !callerID.equalsIgnoreCase(StringUtils.fieldIndex(line, '\t', 5)))
          continue;

        // if we got to here, the transaction matches, so write the ID
        txnWriter.write(StringUtils.fieldIndex(line, '\t', 1));
        txnWriter.write("\n");
      } catch (Exception e) {
        logger.error("Error parsing line "+counter+
                           " of transaction log: "+line,e);
      }
    }
  }

    
  /**
   * This will return the HeaderSet that was used when invoking the transaction
   * with the given identifier.  If the given transactionID is invalid this
   * will return null.
   */
  public HeaderSet getTransactionHeaders(String txnID)
    throws Exception
  {
    InputStream in = archiver.getAuditLogEntry(calculateLogDir(txnID), 
                                               OP_INFO_FILE, TXN_LOG_DEPTH);
    HeaderSet headers = new HeaderSet();
    headers.readHeaders(in);
    return headers;
  }

  /**
   * This will return the 64 bit value representing the date/time that the
   * request with the given identifier occurred.  If the given transactionID
   * is invalid this will return zero.
   */
  public long getTransactionTime(String txnID)
    throws Exception
  {
    if(txnID==null) return 0;
    txnID = txnID.trim();
    if(txnID.length()<=0) return 0;
    
    BufferedReader catRdr = new BufferedReader(new InputStreamReader(new FileInputStream(catalogFile),"UTF-8"));
    String line;
    long counter = 0;
    while((line=catRdr.readLine())!=null) {
      counter++;
      if(!line.startsWith("<txn>")) continue;
      try {
        if(txnID.equals(StringUtils.fieldIndex(line, '\t', 1)))
          return Long.parseLong(StringUtils.fieldIndex(line, '\t', 2));
      } catch (Exception e) {
        logger.error("Error parsing line "+counter+
                           " of transaction log:"+line,e);
      }
    }
    return 0;
  }

  /**
   * This will return the stream of bytes that were used as input for the
   * operation associated with the given transaction ID.  If the given
   * transactionID is invalid this will return null.
   */
  public java.io.InputStream getTransactionInput(String txnID)
    throws Exception
  {
    return archiver.getAuditLogEntry(calculateLogDir(txnID), 
                                     INPUT_FILE, TXN_LOG_DEPTH);
  }

  /**
   * This will return the stream of bytes that were generated as output
   * from the operation associated with the given transaction ID.  If the
   * given transactionID is invalid this will return null.
   */
  public java.io.InputStream getTransactionOutput(String txnID)
    throws Exception
  {
    return archiver.getAuditLogEntry(calculateLogDir(txnID), 
                                     OUTPUT_FILE, TXN_LOG_DEPTH);
  }



  private class AuditedInvocation {
    private InputStream in = null;
    private OutputStream out = null;
    private StreamPair streams = null;
    private HeaderSet requestHeaders = null;
    private File auditDir;
    private File lockFile;
    
    AuditedInvocation(HeaderSet op, File auditDir,
                      InputStream in, OutputStream out) 
      throws IOException
    {
      this.in = in;
      this.out = out;
      this.auditDir = auditDir;
      this.requestHeaders = op;

      // create the file that indicates that this operation is in progress
      this.lockFile = new File(auditDir, PROGRESS_FILE);
      new FileOutputStream(this.lockFile).close();
      //lockFile.deleteOnExit();
      
      getStreamPair();
    }

    void record()
      throws Exception
    {
      File infoFile = new File(auditDir, OP_INFO_FILE);
      FileOutputStream fout = new FileOutputStream(infoFile);
      requestHeaders.writeHeaders(fout);
      fout.close();
    }

    synchronized StreamPair getStreamPair() 
      throws IOException
    {
      if(streams!=null) return streams;
      
      File inLogFile = new File(auditDir, INPUT_FILE);
      File outLogFile = new File(auditDir, OUTPUT_FILE);
      streams = 
        new StreamPair(new AuditInputStream(in, new FileOutputStream(inLogFile)),
                       new AuditOutputStream(out, new FileOutputStream(outLogFile)));
      return streams;
    }    

    private void streamClosed() {
      if(in==null && out==null) {
        finishAndArchive();
      }
    }
    
    private boolean archived = false;
    synchronized void finishAndArchive() {
      // the transaction has already been archived
      if(archived) return;
      
      try { if(in!=null) in.close(); } catch (Throwable t) {
        logger.error("Error closing operation input log in "+
                           auditDir.getAbsolutePath(),t);
      }
      in = null;
      try { if(out!=null) out.close(); } catch (Throwable t) {
        logger.error("Error closing operation output log in "+
                           auditDir.getAbsolutePath(),t);
      }
      out = null;

      try {
      if(lockFile!=null)
        lockFile.delete();
      } catch (Throwable t) {
        logger.error("Error removing in-progress indicator file: "+
                           lockFile.getAbsolutePath(),t);
      }
      archiver.compressOperationFolder(auditDir, true);
      archived = true;
    }
    
    
    
    
    /** This class provides a wrapper around an input stream that logs all
      * read bytes to another stream.
      */
    private class AuditInputStream
      extends InputStream
    {
      private InputStream actualIn;
      private OutputStream auditOut;
      
      public AuditInputStream(InputStream in, OutputStream auditOut) {
        this.actualIn = in;
        this.auditOut = auditOut;
      }
      
      public int read()
        throws IOException
      {
        int b = actualIn.read();
        if(b!=-1) {
          auditOut.write((byte)b);
        } else {
          auditOut.close();
        }
        return b;
      }
      
      public void close() 
        throws IOException
      {
        IOException ex = null;
        try {
          auditOut.close();
        } catch (IOException e) {
          ex = e;
        }
        actualIn.close();
        if(ex!=null) throw ex;

        in = null;
        streamClosed();
      }
    }
    

    
    
    /**
      * This class provides a wrapper around an output stream that logs all
     * written bytes to another stream.
     */
    public class AuditOutputStream
      extends OutputStream
    {
      private OutputStream actualOut;
      private OutputStream auditOut;
      
      public AuditOutputStream(OutputStream out, OutputStream auditOut) {
        this.actualOut = out;
        this.auditOut = auditOut;
      }
      
      public void write(int b)
        throws IOException
      {
        auditOut.write(b);
        actualOut.write(b);
      }
      
      public void write(byte b[])
        throws IOException
      {
        auditOut.write(b);
        actualOut.write(b);
      }
      
      public void write(byte b[], int offset, int len)
        throws IOException
      {
        auditOut.write(b, offset, len);
        actualOut.write(b, offset, len);
      }
      
      public void close()
        throws IOException
      {
        IOException ex = null;
        try {
          auditOut.close();
        } catch (IOException e) {
          ex = e;
        }
        actualOut.close();
        out = null;
        streamClosed();
        if(ex!=null) throw ex;
      }
      
      public void flush()
        throws IOException
      {
        actualOut.flush();
        auditOut.flush();
      }
    }
    
    
    
  }
}




