/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.util.HashMap;
import java.util.Iterator;


/** Storage class for metadata about a digital object. */
public class DOMetadata {
  private String objectID = null;
  private long dateCreated = 0;
  private long dateDeleted = 0;
  private HashMap fields = null;
  
  public DOMetadata() { }

  /** Resets the contents of the metadata */
  public synchronized void resetFields() {
    this.objectID = null;
    this.dateCreated = 0;
    this.dateDeleted = 0;
    this.fields = null;
  }
  
  /** Returns the identifier for the object to which this metadata applies. */
  public String getObjectID() {
    return this.objectID;
  }
  
  /** Sets the identifier for the object to which this metadata applies. */
  public void setObjectID(String newObjectID) {
    this.objectID = newObjectID;
  }

  /** Returns the value of the given tag.  If the tag is not associated with a
    * value in this object, return the given defaultValue. */
  public synchronized String getTag(String tagName, String defaultValue) {
    if(fields==null) return defaultValue;
    if(fields.containsKey(tagName))
      return (String)fields.get(tagName);
    return defaultValue;
  }
  
  /** Stores the given key-value pair as a tag along with this object.  Providing
    * a null tagValue will remove any value that already exists with the given
    * tagName. */
  public synchronized void setTag(String tagName, String tagValue) {
    if(tagValue==null) { // delete the tag
      if(fields==null) return;
      if(fields.containsKey(tagName))
        fields.remove(tagName);
    } else { // add the tag
      if(fields==null) fields = new HashMap();
      fields.put(tagName, tagValue);
    }
  }

  /** Returns an iterator containing the names of all tags associated with this
    * object. */
  public synchronized Iterator getTagNames() {
    if(fields==null) return new Iterator() {
      public boolean hasNext() { return false; }
      public Object next() { throw new java.util.NoSuchElementException(); }
      public void remove() { throw new UnsupportedOperationException(); }
    };
    return fields.keySet().iterator();
  }
  
  
  /** Removes all tags from the metadata */
  public synchronized void clearTags() {
    fields = null;
  }
  
  /** Returns true if the object exists */
  public boolean objectExists() {
    return ( dateCreated > 0 ) && ( dateDeleted <= dateCreated );
  }
  
  /** Stores the date that the DO was most recently created. */
  public void setDateCreated(long newDateCreated) {
    this.dateCreated = newDateCreated;
  }
  
  /** Returns the date that the DO was most recently created. */
  public long getDateCreated() {
    return this.dateCreated;
  }
  
  /** Stores the date that the DO was most recently deleted. */
  public void setDateDeleted(long newDateDeleted) {
    this.dateDeleted = newDateDeleted;
  }
  
  /** Returns the date that the DO was most recently deleted. */
  public long getDateDeleted() {
    return this.dateDeleted;
  }
  
  public String toString() {
    return ""+objectID+" dtcreated="+dateCreated+
    "; dtdeleted="+dateDeleted+
    "; fields:"+String.valueOf(fields);
  }
  
  public synchronized void updateModification(long timestamp) {
      String oldTimestampString = this.getTag("objmodified","0");
      long oldTimestamp = 0;
      try {
          oldTimestamp = Long.parseLong(oldTimestampString);
      }
      catch(NumberFormatException e) {}
      
      if(timestamp > oldTimestamp) this.setTag("objmodified",String.valueOf(timestamp));
  }
}

