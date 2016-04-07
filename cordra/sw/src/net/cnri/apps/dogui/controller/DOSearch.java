/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.dogui.controller;

import net.cnri.util.StringUtils;
import net.cnri.util.ThreadSafeDateFormat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Calendar;
import java.util.Date;

/** DOSearch represents a set of search terms that can include complete phrases,
  * individual keywords, and attributed keywords such as att:author:dickens. 
  * A DOSearch can parse and format the search terms without losing the meaning
  * of the search. */
public class DOSearch {
  private ArrayList keywords = new ArrayList();
  private ArrayList phrases = new ArrayList();
  private ArrayList attributes = new ArrayList();
  
  /** Initialize an empty search object */
  public DOSearch() {
  }
  
  /** Initialize a new search object using the given query */
  public DOSearch(String searchForm) {
    parseSearch(searchForm);
  }
  
  public void addKeyword(String keyword) {
    keywords.add(keyword);
  }
  
  public void addPhrase(String phrase) {
    phrases.add(phrase);
  }
  
  public void addAttribute(String name, String value) {
    attributes.add(new SearchAttribute(name, value));
  }
  
  public void addAttribute(String name) {
    attributes.add(new SearchAttribute(name));
  }
  
  /** Return the value of the first attribute, if any, that is associated with the given name */
  public String getFirstAttribute(String name) {
    for(Iterator it=getAttributes(); it.hasNext(); ) {
      SearchAttribute att = (SearchAttribute)it.next();
      if(att.getName().equals(name))
        return att.getValue();
    }
    return null;
  }
  
  /** Return the numeric range value for an attribute.  The numeric range will
   * be represented as either null (if the attribute doesn't exist or is invalid)
   * or a long array with length of two.  */
  public long[] getNumericRange(String attributeName) {
    for(Iterator it=getAttributes(); it.hasNext(); ) {
      SearchAttribute att = (SearchAttribute)it.next();
      if(!att.getName().equals(attributeName)) continue;
      if(!att.isRange()) continue;
      try {
        return att.getNumericRange();
      } catch (Exception e) {
        System.err.println("Invalid range: "+att+"; error: "+e);
      }
    }
    
    return null;
  }
  
  
  /** Return the date range value for an attribute.  The date range will
   * be represented as either null (if the attribute doesn't exist or is invalid)
   * or a Date array with length of two.  */
  public Date[] getDateRange(String attributeName) {
    for(Iterator it=getAttributes(); it.hasNext(); ) {
      SearchAttribute att = (SearchAttribute)it.next();
      if(!att.getName().equals(attributeName)) continue;
      if(!att.isRange()) continue;
      try {
        return att.getDateRange();
      } catch (Exception e) {
        System.err.println("Invalid range: "+att+"; error: "+e);
      }
    }
    
    return null;
  }
  
  
  /** Set a numeric range attribute */
  public void addRangeAttribute(String name, long minval, long maxval) {
    addAttribute(name, "["+minval+" TO "+maxval+"]");
  }
  
  /** Set a date range attribute */
  public void addRangeAttribute(String name, Date minval, Date maxval) {
    addAttribute(name, "["+formatDate(minval)+" TO "+formatDate(maxval)+"]");
  }
  
  /** Return an iterator of SearchAttribute objects */
  public Iterator getAttributes() {
    return attributes.iterator();
  }
  
  /** Return an iterator of String objects representing keywords */
  public Iterator getKeywords() {
    return keywords.iterator();
  }
  
  /** Return an iterator of String objects representing phrases */
  public Iterator getPhrases() {
    return phrases.iterator();
  }
  
  /** Replace the query parameters with the given query */
  public void parseSearch(String str) {
    keywords.clear();
    phrases.clear();
    attributes.clear();
    
    StringTokenizer st = new StringTokenizer(str);
    while(st.hasMoreTokens()) {
      String token = st.nextToken();
      if(token==null) break;
      boolean isPhrase = false;
      if(token.startsWith("\"")) {
        isPhrase = true;
        token = token.substring(1);
        // beginning of quoted terms, read the rest of the terms
        while(st.hasMoreTokens() && !token.endsWith("\"")) {
          token = token + " " + st.nextToken();
        }
        if(token.endsWith("\"")) {
          token = token.substring(0, token.length()-1);
        }
      }
      
      if(token.indexOf(':')>=0) {
        attributes.add(attributeFromString(token));
      } else if(isPhrase) {
        phrases.add(token);
      } else {
        keywords.add(token);
      }
    }
  }
  
  /** Parse the given string into a SearchAttribute */
  private SearchAttribute attributeFromString(String str) {
    int colIdx = str.lastIndexOf(':');
    if(colIdx<0) return new SearchAttribute(str);
    else return new SearchAttribute(str.substring(0, colIdx),
                                    str.substring(colIdx+1));
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    for(Iterator it=phrases.iterator(); it.hasNext(); ) {
      if(sb.length()>0) sb.append(' ');
      sb.append('\"');
      sb.append((String)it.next());
      sb.append('\"');
    }
    for(Iterator it=keywords.iterator(); it.hasNext(); ) {
      if(sb.length()>0) sb.append(' ');
      sb.append((String)it.next());
    }
    for(Iterator it=attributes.iterator(); it.hasNext(); ) {
      if(sb.length()>0) sb.append(' ');
      sb.append(it.next());
    }
    return sb.toString();
  }
  
  
  public class SearchAttribute {
    private String name;
    private String val;
    
    SearchAttribute(String name, String value) {
      this.name = name;
      this.val = decodeValue(value);
    }
    
    private String decodeValue(String val) {
      return val; // val.replace("\\/", "/");
    }
    
    SearchAttribute(String name) {
      this.name = name;
      this.val = null;
    }
    
    public String getName() {
      return name;
    }
    
    public String getValue() {
      return val;
    }
    
    public boolean isRange() {
      return val!=null && val.startsWith("[") && val.endsWith("]");
    }

    public long[] getNumericRange() 
      throws Exception
    {
      String val = this.val;
      if(val==null || !val.startsWith("[") || !val.endsWith("]") || val.indexOf(" TO ")<=0) {
        throw new Exception("Invalid range: "+val+" for attribute "+name);
      }
      val = val.substring(1, val.length()-1);
      return new long[] {
        Long.parseLong(StringUtils.fieldIndex(val, ' ', 0)),
        Long.parseLong(StringUtils.fieldIndex(val, ' ', 2))
      };
    }
    
    
    public Date[] getDateRange() 
      throws Exception
    {
      String val = this.val;
      if(val==null || !val.startsWith("[") || !val.endsWith("]") || val.indexOf(" TO ")<=0) {
        throw new Exception("Invalid range: "+val+" for attribute "+name);
      }
      val = val.substring(1, val.length()-1);
      return new Date[] {
        parseDate(StringUtils.fieldIndex(val, ' ', 0)),
        parseDate(StringUtils.fieldIndex(val, ' ', 2))
      };
    }
    
    
    public String toString() {
      StringBuffer sb = new StringBuffer();
      String n = name;
      String v = val;
      sb.append(n);
      sb.append(':');
      if(v!=null) {
        // we replace forward slashes with a single character wildcard because
        // escaping doesn't seem to work
        //v = v.replace('/', '?');
        sb.append(v);
      }
      
      if(sb.indexOf(" ")>=0 && !isRange()) {
        sb.insert(0, '\"');
        sb.append('\"');
      }
      return sb.toString();
    }
    
  }
  
  
  private static ThreadSafeDateFormat fullDateFmt =
    new ThreadSafeDateFormat("yyyyMMddHHmmssSSS");
  
  /** Format a java.util.Date object into a string representation suitable for
    * date ranges in searches.  The result will be formatted as yyyyMMddHHmmssSSS */
  public static final String formatDate(Date dt) {
    return fullDateFmt.format(dt);
  }
  
  public static final Date parseDate(String dtStr) {
    dtStr = dtStr.trim();
    Calendar cal = Calendar.getInstance();
    int now_era = cal.get(Calendar.ERA);
    int now_year = cal.get(Calendar.YEAR);
    int now_month = cal.get(Calendar.MONTH);
    int now_day = cal.get(Calendar.DAY_OF_MONTH);
    int now_day_of_week = cal.get(Calendar.DAY_OF_WEEK);
    int now_millis = cal.get(Calendar.MILLISECOND);
    
    int year = 0;
    int month = 1;
    int day = 0;
    int hour = 0;
    int minute = 0;
    int seconds = 0;
    int millisecs = 0;
    
    // parse the basic part of the date
    try {
      if(dtStr.length()>0) {
        year = Integer.parseInt(dtStr.substring(0,Math.min(dtStr.length(),4)));
        if(dtStr.length()>=6) {
          month = Integer.parseInt(dtStr.substring(4,6));
          if(dtStr.length()>=8) {
            day = Integer.parseInt(dtStr.substring(6,8));
            if(dtStr.length()>=10) {
              hour = Integer.parseInt(dtStr.substring(8,10));
              if(dtStr.length()>=12) {
                minute = Integer.parseInt(dtStr.substring(10,12));
                if(dtStr.length()>=14) {
                  seconds = Integer.parseInt(dtStr.substring(12,14));
                  if(dtStr.length()>15) {
                    millisecs = Integer.parseInt(dtStr.substring(15));
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      System.err.println("Invalid date: "+dtStr+"; error: "+e);
    }
      
    cal.set(Calendar.YEAR, year);
    cal.set(Calendar.MONTH, month-1);
    cal.set(Calendar.DAY_OF_MONTH, day);
    cal.set(Calendar.HOUR_OF_DAY, hour);
    cal.set(Calendar.MINUTE, minute);
    cal.set(Calendar.SECOND, seconds);
    cal.set(Calendar.MILLISECOND, millisecs);
    return cal.getTime();
  }
  
}
