/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.cnri.util.StringUtils;
import net.handle.hdllib.*;

import java.io.*;
import java.util.*;

/**
 * HeaderSet objects manage, parse, and format a set of key-value pairs
 * that are used with DOConnection messages.
 */
public class HeaderSet implements Iterable<HeaderItem> {
  private ArrayList<HeaderItem> headers = new ArrayList<HeaderItem>();
  private String msgType = "misc";

  private static final char HEX_VALUES[] = {'0','1','2','3','4','5','6','7',
                                            '8','9','A','B','C','D','E','F'};
  /**
   * Construct a HeaderSet with the given message type  
   */
  public HeaderSet(String msgType) {
    this.msgType = msgType;
  }
  
  /**
   * Construct a HeaderSet with the given message type  
   */
  public HeaderSet() {
    this("misc");
  }
  
  /** Copy constructor */
  public HeaderSet(HeaderSet orig) {
      this();
      headers.addAll(orig.headers);
  }
  
  /** Get the message type for this HeaderSet.  The message type is the label
    * that comes before the first colon in a formatted HeaderSet. */
  public String getMessageType() {
    return this.msgType;
  }
  
  public void setMessageType(String newMessageType) {
    if(newMessageType==null) newMessageType = "";
    this.msgType = newMessageType;
  }
  
  /** Adds a header to the header set */
  public void addHeader(String name, String value) {
    headers.add(new HeaderItem(name, value));
  }
  
  /** Adds a string array header to the header set */
  public void addHeader(String name, String value[]) {
    if(value==null) value = new String[0];
    StringBuffer sb = new StringBuffer();
    for(int i=0; i<value.length; i++) {
      if(i!=0) sb.append(',');
      sb.append(StringUtils.backslash(value[i], ","));
    }
    addHeader(name, sb.toString());
  }
  
  /** Adds a sub-header-set to the header set */
  public void addHeader(String name, HeaderSet subHeaders) {
    if(subHeaders==null) return;
    name = name + ".";
    for(HeaderItem item : subHeaders) {
      if(item==null) continue;
      
      headers.add(new HeaderItem(name + item.name, item.value));
    }
  }
  
  /** Adds a header to the header set */
  public void addHeader(String name, long value) {
    headers.add(new HeaderItem(name, String.valueOf(value)));
  }
  
  /** Adds a header to the header set */
  public void addHeader(String name, int value) {
    headers.add(new HeaderItem(name, String.valueOf(value)));
  }
  
  /** Adds a header to the header set */
  public void addHeader(String name, boolean value) {
    headers.add(new HeaderItem(name, value?"1":"0"));
  }
  
  /** Adds a header to the header set */
  public void addHeader(String name, byte value[]) {
    headers.add(new HeaderItem(name, Util.decodeHexString(value, false)));
  }
  
  /** Removes all headers with the given key */
  public void removeHeadersWithKey(String name) {
    for(int i=headers.size()-1; i>=0; i--) {
      if(headers.get(i).name.equalsIgnoreCase(name))
        headers.remove(i);
    }
  }
  
  /** Removes all headers from the HeaderSet */
  public synchronized void removeAllHeaders() {
    headers.clear();
  }
  
  /** Returns an iterator that returns each HeaderItem */
  public Iterator iterator() {
    return headers.iterator();
  }
  
  /** Returns the number of headers */
  public int size() {
    return headers.size();
  }
  
  public HeaderItem get(int idx) {
    return headers.get(idx);
  }
  
  public void writeHeaders(OutputStream out)
    throws IOException
  {
    writeEscapedTxt(out, msgType);
    out.write(':');
    int idx = 0;
    for(HeaderItem item : headers) {
      if(idx>0) out.write((byte)'&');
      idx++;
      writeEscapedTxt(out, item.name);
      if(item.value!=null) {
        out.write((byte)'=');
        writeEscapedTxt(out, item.value);
      }
    }
    out.write((byte)'\n');
  }
  
  /** 
   * This method will populate the set of headers with data from
   * the given InputStream.  The stream will be read using the UTF8
   * character encoding.  Returns false if a newline or EOF was reached
   * before any headers were found.
   */
  public boolean readHeaders(InputStream in)
    throws IOException
  {
    headers.clear();
    
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    while(true) {  // read until hitting a newline
      int inByte = in.read();
      if(inByte<0 || inByte=='\n') {
        break;
      }
      bout.write(inByte);
    }
    byte[] bytes = bout.toByteArray();
    if (bytes.length == 0) return false;
    return readHeadersFromString(Util.decodeString(bytes));
  }
  
  public boolean readHeadersFromBytes(byte[] bytes) {
      headers.clear();
      if (bytes.length == 0 || bytes[0] == '\n') return false;
      int index = 0;
      while (index < bytes.length) {
          byte b = bytes[index];
          if (b == '\n') {
              msgType = Util.decodeString(bytes, 0, index);
              return true;
          } else if (b == ':') {
              msgType = Util.decodeString(bytes, 0, index);
              break;
          }
          index++;
      }
      index++;
      int start = index;
      boolean scanningValue = false;
      boolean sawPercent = false;
      String key = null;
      while (index < bytes.length) {
          byte b = bytes[index];
          if (b == '\n' || b == '&') {
              if (scanningValue) {
                  String value;
                  if (sawPercent) value = unescapeURLTxt(bytes, start, index - start);
                  else value = Util.decodeString(bytes, start, index - start);
                  HeaderItem newItem = new HeaderItem(key, value);
                  headers.add(newItem);
                  scanningValue = false;
              } else {
                  if (sawPercent) key = unescapeURLTxt(bytes, start, index - start);
                  else key = Util.decodeString(bytes, start, index - start);
                  HeaderItem newItem = new HeaderItem(key, null);
                  headers.add(newItem);
              }
              if (b == '\n') return true;
              start = index + 1;
              sawPercent = false;
          } else if (!scanningValue && b == '=') {
              scanningValue = true;
              if (sawPercent) key = unescapeURLTxt(bytes, start, index - start);
              else key = Util.decodeString(bytes, start, index - start);
              start = index + 1;
              sawPercent = false;
          } else if (b == '%') {
              sawPercent = true;
          }
          index++;
      }
      return true;
  }
  
  /** 
    * This method will populate the set of headers with data from the given encoded 
    * String.  Returns true.
    */
  public boolean readHeadersFromString(String str) {
    headers.clear();
    
    int startIdx = -1;
    int endIdx = str.indexOf(':');
    if(endIdx>=0) {
      msgType = str.substring(0, endIdx);
    } else {
      msgType = str;
    }
    startIdx = endIdx;
    
    while(endIdx>=0) {
      endIdx = str.indexOf('&', startIdx+1);
      String segment = 
        endIdx<0 ? str.substring(startIdx+1) : str.substring(startIdx+1, endIdx);
      
      int eqIdx = segment.indexOf('=');
      HeaderItem newItem = null;
      if(eqIdx<0)
        newItem = new HeaderItem(unescapeURLTxt(segment), null);
      else
        newItem = new HeaderItem(unescapeURLTxt(segment.substring(0, eqIdx)),
                                 unescapeURLTxt(segment.substring(eqIdx+1)));
      headers.add(newItem);
      
      if(endIdx<0) break;  // this was the last segment
      startIdx = endIdx;
    }
    return true;
  }
  
  public static final void writeEscapedTxt(OutputStream out, String str)
    throws IOException
  {
    byte utf8Buf[] = null;
    try {
      utf8Buf = str.getBytes("UTF8");
    } catch (UnsupportedEncodingException e) {
      System.err.println("error encoding string in UTF8: "+e);
      try {
        utf8Buf = str.getBytes("ASCII");
      } catch (UnsupportedEncodingException e2) {
        utf8Buf = str.getBytes();
      }
    }
    
    byte ch;
    for(int i=0; i<utf8Buf.length; i++) {
      ch = utf8Buf[i];
      switch(ch) {
        case '\r':
        case '\n':
        case '=':
        case '&':
        case '%':
        case '?':
        case ';':
          out.write('%');
          out.write(HEX_VALUES[ ( ch&0xF0 ) >>> 4 ] );
          out.write(HEX_VALUES[ ( ch&0xF ) ] );
          break;
        default:
          out.write(utf8Buf[i]);
      }
    }
  }

  public static String unescapeURLTxt(byte[] bytes, int off, int len) {
      byte utf8Buf[] = new byte[len];
      int utf8Loc = 0;
      int strLoc = off;
      while(strLoc < off + len) {
        byte ch = bytes[strLoc++];
        if(ch=='%' && strLoc+2 <= off + len) {
          utf8Buf[utf8Loc++] = decodeHexByte(bytes[strLoc++], bytes[strLoc++]);
        } else {
          utf8Buf[utf8Loc++] = ch;
        }
      }
      return Util.decodeString(utf8Buf, 0, utf8Loc);
  }
  
  public static final String unescapeURLTxt(String str) {
      byte[] bytes = Util.encodeString(str);
      return unescapeURLTxt(bytes, 0, bytes.length);
  }

  public static final byte decodeHexByte(char ch1, char ch2) {
    ch1 = (char) ((ch1>='0' && ch1<='9') ? ch1-'0' : ((ch1>='a' && ch1<='z') ? ch1-'a'+10 : ch1-'A'+10));
    ch2 = (char) ((ch2>='0' && ch2<='9') ? ch2-'0' : ((ch2>='a' && ch2<='z') ? ch2-'a'+10 : ch2-'A'+10));
    return (byte)(ch1<<4 | ch2);
  }

  public static final byte decodeHexByte(byte ch1, byte ch2) {
      int n1 = ((ch1>='0' && ch1<='9') ? ch1-'0' : ((ch1>='a' && ch1<='z') ? ch1-'a'+10 : ch1-'A'+10));
      int n2 = ((ch2>='0' && ch2<='9') ? ch2-'0' : ((ch2>='a' && ch2<='z') ? ch2-'a'+10 : ch2-'A'+10));
      return (byte)(n1<<4 | n2);
    }

  
  /**
   * Returns a new HeaderSet with the key-value pairs for which the keys
   * have the given prefix.  This adds all of the key-value pairs, with the
   * prefix removed from the keys, to a new HeaderSet and returns it.
   */
  public HeaderSet getHeaderSubset(String prefix) {
    prefix = prefix + ".";
    HeaderSet subset = new HeaderSet(msgType);
    for(int i=headers.size()-1; i>=0; i--) {
      HeaderItem hdr = (HeaderItem)headers.get(i);
      if(hdr.name.startsWith(prefix)) {
        subset.addHeader(hdr.name.substring(prefix.length()), hdr.value);
      }
    }
    return subset;
  }
  
  /** 
    * Returns the integer value for the last header with the given name.
    * If no header with the given name is found or if there is a formatting
    * error, defaultValue is returned.
    */
  public int getIntHeader(String headerName, int defaultValue) {
    for(int i=headers.size()-1; i>=0; i--) {
      HeaderItem hdr = (HeaderItem)headers.get(i);
      if(hdr.name.equalsIgnoreCase(headerName)) {
        try {
          return Integer.parseInt(hdr.value);
        } catch (Exception e) {
          System.err.println("Invalid integer formatting: "+hdr.value);
          return defaultValue;
        }
      }
    }
    return defaultValue;
  }

  /** 
    * Returns the long (64 bit integer) value for the last header with the given name.
    * If no header with the given name is found or if there is a formatting
    * error, defaultValue is returned.
    */
  public long getLongHeader(String headerName, long defaultValue) {
    for(int i=headers.size()-1; i>=0; i--) {
      HeaderItem hdr = (HeaderItem)headers.get(i);
      if(hdr.name.equalsIgnoreCase(headerName)) {
        try {
          return Long.parseLong(hdr.value);
        } catch (Exception e) {
          System.err.println("Invalid integer formatting: "+hdr.value);
          return defaultValue;
        }
      }
    }
    return defaultValue;
  }
  
  
  /**
   * Returns true iff this HeaderSet contains a header with the given name.
   * (as always, case insensitive)
   */
  public boolean hasHeader(String headerName) {
    for(int i=headers.size()-1; i>=0; i--) {
      HeaderItem hdr = ((HeaderItem)headers.get(i)); 
      if(hdr.name.equalsIgnoreCase(headerName))
        return true;
    }
    return false;
  }
  
 /** 
  * Returns the boolean value for the last header with the given name.
  * If no header with the given name is found or if there is a formatting
  * error, defaultValue is returned.
  */
 public boolean getBooleanHeader(String headerName, boolean defaultValue) {
   for(int i=headers.size()-1; i>=0; i--) {
     HeaderItem hdr = headers.get(i); 
     if(hdr.name.equalsIgnoreCase(headerName)) {
       if(hdr.value==null) return defaultValue;
       if(hdr.value.equals("1") || hdr.value.equals("true") || hdr.value.equals("yes")) return true;
       else if(hdr.value.equals("0") || hdr.value.equals("false") || hdr.value.equals("no")) return false;
       else return defaultValue;
     }
   }
   return defaultValue;
 }
  
  /** 
    * Returns the String value for the last header with the given name.
    * If no header with the given name is found, defaultValue is returned.
    */
  public String getStringHeader(String headerName, String defaultValue) {
    for(int i=headers.size()-1; i>=0; i--) {
      HeaderItem hdr = headers.get(i); 
      if(hdr.name.equalsIgnoreCase(headerName)) {
        return hdr.value;
      }
    }
    return defaultValue;
  }
  
  /** 
    * Returns the String array value for the last header with the given name.
    * If no header with the given name is found, defaultValue is returned.
    */
  public String[] getStringArrayHeader(String headerName, String[] defaultValue) {
    String val = null;
    for(int i=headers.size()-1; i>=0; i--) {
      HeaderItem hdr = headers.get(i); 
      if(hdr.name.equalsIgnoreCase(headerName)) {
        val = hdr.value;
        break;
      }
    }
    if(val==null) {
      return defaultValue;
    }
    String splitStr[] = splitOnUnbackslashedCommas(val);
    for(int i=splitStr.length-1; i>=0; i--) {
      splitStr[i] = StringUtils.unbackslash(splitStr[i]);
    }
    return splitStr;
  }
  
  public static final String[] splitOnUnbackslashedCommas(String str) {
      if(str==null) return new String[0];
      int numFields = 1;
      int strLen = str.length();
      for(int i=0; i<strLen; i++) {
        if(str.charAt(i)=='\\') {
            i++;
        }
        else if(str.charAt(i)==',') {
          numFields++;
        }
      }
      String result[] = new String[numFields];
      int strNum = 0;
      int idx = 0;
      for(int i=0; i<strLen; i++) {
          if(str.charAt(i)=='\\') {
              i++;
          }
          else if(str.charAt(i)==',') {
          result[strNum++] = str.substring(idx, i);
          idx = i+1;
        }
      }
      result[strNum] = str.substring(idx);
      return result;
    }


  /** 
   * Returns the byte array value for the last header with the given name.
   * If no header with the given name is found, defaultValue is returned.
   */
  public byte[] getHexByteArrayHeader(String headerName, byte defaultValue[]) {
    for(int i=headers.size()-1; i>=0; i--) {
      HeaderItem hdr = headers.get(i); 
      if(hdr.name.equalsIgnoreCase(headerName)) {
        return Util.encodeHexString(hdr.value);
      }
    }
    return defaultValue;
  }
  
  public void copyInto(HeaderSet destination) {
    for(HeaderItem item : headers) {
      destination.addHeader(item.getName(), item.getValue());
    }
  }
  
  
  public String toString() {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    try {
      writeHeaders(bout);
    } catch (IOException e) {}
    return Util.decodeString(bout.toByteArray()).trim();
  }

  @Override
  public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((headers == null) ? 0 : headers.hashCode());
      result = prime * result + ((msgType == null) ? 0 : msgType.hashCode());
      return result;
  }

  @Override
  public boolean equals(Object obj) {
      if (this == obj)
          return true;
      if (obj == null)
          return false;
      if (getClass() != obj.getClass())
          return false;
      HeaderSet other = (HeaderSet) obj;
      if (headers == null) {
          if (other.headers != null)
              return false;
      } else if (!headers.equals(other.headers))
          return false;
      if (msgType == null) {
          if (other.msgType != null)
              return false;
      } else if (!msgType.equals(other.msgType))
          return false;
      return true;
  }
}

