/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.do_api;

import net.cnri.dobj.*;

import java.io.*;


/** This is a high level interface to a data element for a digital object. */
public class DataElement {
  private DigitalObject dobj;    // the object itself
  private String elementID;    // the name of the data element within the object
  
  DataElement(DigitalObject dobj, String elementID, HeaderSet attributes) {
    this.dobj = dobj;
    this.elementID = elementID;
  }
  
  DataElement(DigitalObject dobj, String elementID) 
    throws DOException, IOException
  {
    this.dobj = dobj;
    this.elementID = elementID;
  }
  
  public String toString() {
    return "("+dobj+" -> "+elementID+")";
  }
  
  /** Return the DigitalObject of which this element is a part */
  public DigitalObject getDigitalObject() {
    return dobj;
  }
  
  public String getObjectID() {
      return dobj.getObjectID();
  }
  
  public Repository getRepository() {
      return dobj.getRepository();
  }
  
  /** Returns the data element's identifier within the object */
  public String getDataElementID() {
    return elementID;
  }

  /** Returns an InputStream from which the element's data can be read */
  public java.io.InputStream read() 
    throws DOException, IOException
  {
    HeaderSet params = new HeaderSet();
    params.addHeader(DOConstants.PARAM_ELEMENT_ID, elementID);
    StreamPair io = dobj.performOperation(DOConstants.GET_DATA_OP_ID, params);
    io.getOutputStream().close();
    return io.getInputStream();
  }

  
  /** Appends the bytes from the given InputStream to the data element, returning
    * the total number of bytes written.  Note:  It is possible to write enough
    * bytes to overflow the 64 bit return value, so if you are writing more than
    * about 9,223,372,036,854,775,808 bytes then don't count on the return value
    * to be accurate. */
  public long append(InputStream in) 
    throws DOException, IOException
  {
      return write(in,true);
  }  

  
  public long write(InputStream in) throws DOException, IOException {
      return write(in,false);
  }
  
  /** Writes the bytes from the given InputStream to the data element, returning
    * the total number of bytes written.  Note:  It is possible to write enough
    * bytes to overflow the 64 bit return value, so if you are writing more than
    * about 9,223,372,036,854,775,808 bytes then don't count on the return value
    * to be accurate. */
  public long write(InputStream in, boolean append) 
    throws DOException, IOException
  {
    HeaderSet params = new HeaderSet();
    params.addHeader("elementid", elementID);
    if(append) params.addHeader("append",true);
    StreamPair io = null;
    long numBytes = 0;
    try {
      io = dobj.performOperation(DOConstants.STORE_DATA_OP_ID, params);
      OutputStream out = io.getOutputStream();
      
      // write the bytes to the data element
      byte buf[] = new byte[1024];
      int r;
      while((r=in.read(buf))>=0) {
        out.write(buf, 0, r);
        numBytes += r;
      }
      out.close();
      
      // read the response to get confirmation that the element was completely written
      DigitalObject.checkStatus(io.getInputStream());
    } finally {
      try { io.close(); } catch (Throwable t) {}
    }
    dobj.clearAttributes(); // presumably have changed size and date_modified
    return numBytes;
  }  
  
  /** Return the date that this object was created */
  public java.util.Date getDateCreated() 
  throws DOException, IOException
  {
      return dobj.getDateCreated(elementID);
  }

  /** Return the date that this object was last modified */
  public java.util.Date getDateLastModified() 
  throws DOException, IOException
  {
      return dobj.getDateLastModified(elementID);
  }

  /** Gets the size in bytes of the data element, not including attributes */
  public long getSize()  throws DOException, IOException {
      return dobj.getSize(elementID);
  }
  
  /** Associates the given attributeValue with the given attributeName for this
   * object.  */
  public void setAttribute(String attributeName, String attributeValue)
  throws DOException, IOException
  {
      dobj.setAttribute(elementID,attributeName,attributeValue);
  }

  /** Atomically set more than one attribute */
  public void setAttributes(HeaderSet atts)
  throws DOException, IOException
  {
      dobj.setAttributes(elementID,atts);
  }

  /** Delete the given attributeName for this
   * object.  */
  public void deleteAttribute(String attributeName)
  throws DOException, IOException
  {
      dobj.deleteAttribute(elementID,attributeName);
  }

  /** Atomically delete more than one attribute */
  public void deleteAttributes(String[] atts)
  throws DOException, IOException
  {
      dobj.deleteAttributes(elementID,atts);
  }


  /** Associates the given attributeValue with the given attributeName for this
   * object.  */
  public String getAttribute(String attributeName, String defaultValue)
  throws DOException, IOException
  {
      return dobj.getAttribute(elementID,attributeName,defaultValue);
  }

  /** Return all attributes */
  public HeaderSet getAttributes() 
  throws DOException, IOException
  {
      return dobj.getAttributes(elementID);
  }

  /** Return the list of attribute names for this object */
  public String[] listAttributes() 
  throws DOException, IOException
  {
      return dobj.listAttributes(elementID);
  }
}
