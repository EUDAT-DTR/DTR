/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view.resources;

import java.util.*;
import java.io.*;

import net.cnri.util.StreamTable;


public class Resources
  extends ResourceBundle
{
  private static final String DEFAULT_DICT = "/net/cnri/apps/dogui/view/resources/english.dict";

  private StreamTable resourceTable;
  private StreamTable backupResourceTable;
  private Locale thisLocale = null;


  /** Default constructor that loads the English dictionary.
      Subclasses for other locales should call the Resources(String)
      constructor. */
  public Resources() {
    this(DEFAULT_DICT);
  }
  
  public Resources(String resourceName) {
    resourceTable = new StreamTable();
    try {
      Reader rdr = new InputStreamReader(getClass().getResourceAsStream(resourceName),
                                         "UTF8");
      resourceTable.readFrom(rdr);
    } catch (Exception e) {
      System.err.println("Error reading resources: "+e);
      e.printStackTrace(System.err);
    }

    if(DEFAULT_DICT.equals(resourceName)) {
      backupResourceTable = resourceTable;
    } else {
      backupResourceTable = new StreamTable();
      try {
        Reader rdr = new InputStreamReader(getClass().getResourceAsStream(DEFAULT_DICT),
                                           "UTF8");
        backupResourceTable.readFrom(rdr);
      } catch (Exception e) {
        System.err.println("Error reading backup (english) resources: "+e);
        e.printStackTrace(System.err);
      }
    }
  }

  public Enumeration getKeys() {
    return resourceTable.keys();
  }

  protected Object handleGetObject(String key)
    throws MissingResourceException
  {
    Object o = resourceTable.get(key);
    if(o==null) o = backupResourceTable.get(key);
    if(o==null) return "??"+key+"??";
    return o;
  }
  
  public boolean containsKey(String key) {
    return resourceTable.containsKey(key);
  }
  
}
