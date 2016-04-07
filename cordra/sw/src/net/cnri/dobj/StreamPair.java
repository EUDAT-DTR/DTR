/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.io.*;

/** A StreamPair is a simple container that holds an associated InputStream 
  * and OutputStream */
public class StreamPair {
  int channelID;
  private InputStream inputStream;
  private OutputStream outputStream;
  private String name = "";
  
  public StreamPair(InputStream in, OutputStream out) {
    this(-1, in, out);
  }
  
  public StreamPair(int channelID, InputStream in, OutputStream out) {
    this.channelID = channelID;
    this.inputStream = in;
    this.outputStream = out;
  }
  
  public void setName(String newName) {
    this.name = newName;
  }
  
  public InputStream getInputStream() {
    return this.inputStream;
  }
  
  public OutputStream getOutputStream() {
    return this.outputStream;
  }
  
  public void close() 
    throws IOException
  {
    inputStream.close();
    outputStream.close();
  }
  
  public String toString() {
    return "iochannel:"+channelID+":"+name;
  }
  
}

