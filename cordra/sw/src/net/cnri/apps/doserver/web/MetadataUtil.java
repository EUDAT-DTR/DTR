/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import net.cnri.util.StringUtils;
import net.cnri.util.ThreadSafeDateFormat;

public class MetadataUtil {
//    private static FileTypeMap fileTypeMap = null;
//    
//    static {
//        try {
//            InputStream in = MetadataUtil.class.getResourceAsStream("/net/cnri/apps/emrui/web/util/mime.types");
//            fileTypeMap = new MimetypesFileTypeMap(in);
//            FileTypeMap.setDefaultFileTypeMap(fileTypeMap);
//        } catch (Exception e) {
//            System.err.println("Error initializing mime type map: "+e);
//        }
//    }
//    
//    public static String getContentType(String name) {
//        return fileTypeMap.getContentType(name);
//    }

    public static String makeCamelCase(String s) {
        StringBuilder sb = new StringBuilder(s);
        for(int i = 0; i < s.length(); i++) {
            if(i==0 || s.charAt(i-1)==' ' || s.charAt(i-1)=='-' || s.charAt(i-1)=='/') {
                sb.setCharAt(i,Character.toUpperCase(s.charAt(i)));
            }
        }
        int i = 0;
        while(i < sb.length()) {
            if(sb.charAt(i)==' ' || sb.charAt(i)=='-' || s.charAt(i)=='/') sb.deleteCharAt(i);
            else i++;
        }
        return sb.toString();
    }
    
    public static String pad(int i) {
        if(i<10) return "0" + i;
        return "" + i;
    }
    
    @SuppressWarnings("boxing")
    public static String relaxedDate(String s) throws IOException {
        Scanner scan = new Scanner(s);
        scan.useDelimiter("[^A-Za-z0-9]+");
        List<Integer> tokens = new ArrayList<Integer>();
        Integer month = null;
        while(scan.hasNext()) {
            String tok = scan.next().toLowerCase();
            try {
                tokens.add(Integer.valueOf(tok));
            }
            catch (NumberFormatException e) {
                if(tok.startsWith("jan")) month = 1;
                else if(tok.startsWith("feb")) month = 2;
                else if(tok.startsWith("mar")) month = 3;
                else if(tok.startsWith("apr")) month = 4;
                else if(tok.startsWith("may")) month = 5;
                else if(tok.startsWith("jun")) month = 6;
                else if(tok.startsWith("jul")) month = 7;
                else if(tok.startsWith("aug")) month = 8;
                else if(tok.startsWith("sep")) month = 9;
                else if(tok.startsWith("oct")) month = 10;
                else if(tok.startsWith("nov")) month = 11;
                else if(tok.startsWith("dec")) month = 12;
            }
        }
        if(tokens.size()>3 || tokens.isEmpty() || (month!=null && tokens.size()>2)) {
            throw new IOException("Couldn't parse date: " + s);
        }
        
        // can have 1, 2, or 3 tokens (with extra specified month possible for 1 or 2)
        
        if(tokens.size()==1 && month==null) {
            int n = tokens.get(0);
            if(n<10000000 || n>99999999) throw new IOException("Couldn't parse date: " + s);
            tokens.clear();
            int y = n/1000;
            int m = (n - y*1000)/100;
            int d = (n - y*1000 - m*100);
            if(y<1300 || m < 1 || m > 12 || d < 1 || d > 31) throw new IOException("Couldn't parse date: " + s);
            return "" + y + "-" + pad(m) + "-" + pad(d);
        }

        // can have 1 + month, 2, 2 + month, 3
        
        Integer year = null;
        for(Integer i : tokens) {
            if(i > 31) {
                year = i;
                tokens.remove(i);
                if(year<100) {
                    if(year>=70) year += 1900;
                    else year += 2000;
                }
                break;
            }
        }
        
        // now, either year is null, or we have removed one token
        // now can have year + month, 1 + month, 1 + year, 1 + month + year, 2, 2 + month, 2 + year, 3
        if(year==null) {
            if(tokens.size() == 3 || (month!=null && tokens.size()==2)) {
                year = tokens.get(tokens.size()-1);
                tokens.remove(year);
                year += 2000;
            }
            else {
                year = new GregorianCalendar().get(Calendar.YEAR);
            }
        }
        // can have year + month, 1 + month + year, 1 + year, (1 + month + year), 2 + year, (1 + month + year), (2 + year), (2 + year)
        if(month==null) {
            month = tokens.get(0);
            tokens.remove(month);
        }
        int day = tokens.isEmpty() ? 1 : tokens.get(0);
        
        if(year<1300 || month < 1 || month > 12 || day < 1 || day > 31) throw new IOException("Couldn't parse date: " + s);
        return "" + year + "-" + pad(month) + "-" + pad(day);
    }
    
    public static Map<String,String> parseQueryParameters(String query) {
        Map<String,String> res = new HashMap<String,String>();
        if(query==null || query.isEmpty()) return res;
        
        String[] pairs = query.split("[&;]");
        for(String pair : pairs) {
            int equals = pair.indexOf('=');
            if(equals<0) res.put(StringUtils.decodeURLIgnorePlus(pair),"");
            else res.put(StringUtils.decodeURLIgnorePlus(pair.substring(0,equals)),StringUtils.decodeURLIgnorePlus(pair.substring(equals+1)));
        }
        return res;
    }
    
    private static ThreadSafeDateFormat standardDateFormatter = new ThreadSafeDateFormat("yyyy-MM-dd");

    public static String formatDate(Date date) {
        return standardDateFormatter.format(date);
    }
}
