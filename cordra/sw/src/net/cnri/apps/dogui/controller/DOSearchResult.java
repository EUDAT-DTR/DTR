/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.dogui.controller;

import net.cnri.dobj.DOConstants;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.HeaderItem;
import net.cnri.do_api.DOKeyRing;
import java.util.Iterator;


/** A DOSearchResults object contains the results of executing a DOSearch.
  */
public class DOSearchResult {
  private HeaderSet info;
  
  /** Initialize an empty set of search results */
  public DOSearchResult(HeaderSet info) {
    this.info = info;
  }
  
  /** Return the key-value set of information that contains the information behind
    * this search result. */
  HeaderSet getInfo() {
    return info;
  }
  
  /** Return the date that the object was created or 0 if the creation
    * date is not known. */
  public long getDateCreated() {
    return info.getLongHeader("field:objatt_"+DOConstants.DATE_CREATED_ATTRIBUTE, 0);
  }
  
  /** Return the date that the object was created or 0 if the creation
    * date is not known. */
  public long getDateModified() {
    return info.getLongHeader("field:objatt_"+DOConstants.DATE_MODIFIED_ATTRIBUTE, 0);
  }
  
  /** Return the title of this item, or null if there is no title */
  public String getTitle() {
    return info.getStringHeader("field:objatt_"+DOConstants.TITLE_ATTRIBUTE,
                                info.getStringHeader("field:eltitle_content", null));
  }
  
  /** Returns a label that describes this item.  Will not return null. */
  public String getLabel() {
    String title = getTitle();
    if(title!=null) {
      title = title.trim();
      if(title.length()>0) return title;
    }
    title = getContentFileName();
    if(title!=null) {
      title = title.trim();
      if(title.length()>0) return title;
    }
    
    title = getID();
    if(title==null || title.trim().length()<=0) {
      title = toString();
    }
    return title;
  }
  
  /** Return the owner of the object, or null if the owner is not known */
  public String getOwner() {
    return info.getStringHeader("field:objatt_"+DOConstants.OWNER_ATTRIBUTE, null);
  }
  
  /** Return the identifier of the object */
  public String getID() {
    return info.getStringHeader("objectid", null);
  }
  
  public String getRepositoryID() {
    return info.getStringHeader("repoid", null);
  }
  
  /** Return any note that was in the index for this object */
  public String getNotes() {
    return info.getStringHeader("field:objatt_"+DOConstants.NOTES_ATTRIBUTE, null);
  }
  
  /** Return the mime type that was associated with the content */
  public String getContentMimeType() {
    return info.getStringHeader("field:elatt_content_"+DOConstants.MIME_TYPE_ATTRIBUTE,
                                info.getStringHeader("eltype_content", null));
  }
  
  /** Return the file name for the object */
  public String getContentFileName() {
    return info.getStringHeader("field:elatt_content_"+DOConstants.FILE_NAME_ATTRIBUTE, null);
  }
  
  
  /** Return the length of the content or -1 if the length is not known */
  public long getContentSize() {
    return info.getLongHeader("field:elatt_content_"+DOConstants.SIZE_ATTRIBUTE, -1);
  }
  
  
  /** Return the fingerprint of the key that was used to encrypt the content element */
  public String getContentEncryptionKeyID() {
    return info.getStringHeader("field:elatt_content_"+DOKeyRing.KEY_ID_ATTRIBUTE, null);
  }
  
  /** Return the encryption algorithm that was used to encrypt the content element */
  public String getContentEncryptionAlgorithm() {
    return info.getStringHeader("field:elatt_content_"+DOKeyRing.CIPHER_ALG_ATTRIBUTE, null);
  }
  
  /** Return the date the content was last modified or 0 if not known */
  public long getContentDateModified() {
    return info.getLongHeader("field:elatt_content_"+DOConstants.DATE_MODIFIED_ATTRIBUTE, 0);
  }
  
  /** Return the folder that applies to this object */
  public String getFolder() {
    return info.getStringHeader("field:objatt_"+DOConstants.FOLDER_ATTRIBUTE, null);
  }
  
  
  /** Merge the information in the given search result with the info for this result.
    * If overwrite is true then the values in the otherInfo parameter will take
    * precedence over existing values in this result's information. */
  public void mergeValues(DOSearchResult otherInfo, boolean overwrite) {
    HeaderSet otherSet = otherInfo.getInfo();
    for(Iterator it=otherSet.iterator(); it.hasNext(); ) {
      HeaderItem item = (HeaderItem)it.next();
      String name = item.getName();
      if(info.hasHeader(name)) {
        if(overwrite) {
          info.removeHeadersWithKey(name);
          info.addHeader(name, item.getValue());
        }
      } else {
        info.addHeader(name, item.getValue());
      }
      
    }
  }
  
  public String toString() {
    return String.valueOf(info);
  }
  
}
