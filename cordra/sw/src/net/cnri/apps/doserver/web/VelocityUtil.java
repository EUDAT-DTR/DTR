/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.web;

import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.*;

import net.cnri.dobj.DOConstants;
import net.cnri.dobj.HeaderItem;
import net.cnri.util.StringUtils;

public class VelocityUtil {
    public static String extraDateString(HeaderItem item) {
        try {
            if(DOConstants.DATE_CREATED_ATTRIBUTE.equals(item.getName()) ||
                    DOConstants.DATE_MODIFIED_ATTRIBUTE.equals(item.getName())) {
                long millis = Long.parseLong(item.getValue());
                Calendar cal = new GregorianCalendar();
                cal.setTimeInMillis(millis);
                return "(" + cal.get(Calendar.YEAR) + "-" + MetadataUtil.pad(cal.get(Calendar.MONTH)+1) + "-" + MetadataUtil.pad(cal.get(Calendar.DAY_OF_MONTH)) + ")";
            }
        }
        catch(Throwable t) {
        }
        return "";
    }
    
    public static String encodeURLComponent(String s) {
      if(s==null) return "";
      return StringUtils.encodeURLComponent(s);
    }
    
    public static String cgiEscape(String s) {
      if(s==null) return "";
      return StringUtils.cgiEscape(s);
    }


    public static String stringFromStream(InputStream in)
      throws IOException
    {
      InputStreamReader rdr = new InputStreamReader(in, "UTF8");
      StringBuilder sb = new StringBuilder();
      int r;
      char buf[] = new char[256];
      while((r=rdr.read(buf))>=0) sb.append(buf, 0, r);
      return sb.toString();
    }

    public static String csvDateEncoding(String value){
        try{
            long timevalue = Long.parseLong(value);
            Date date = new Date(timevalue);
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.format(date);
        }
        catch(Exception e)
        {
            return "Invalid Time Infomation "+e.getMessage();
        }
    }
}
