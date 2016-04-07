/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.dogui.controller;

/** Miscellaneous utility functions */
public class DOUtil {
  
  private static java.text.NumberFormat numFmt = new java.text.DecimalFormat("0.0");

  /** Return a human-readable description of the number of bytes given */
  public static String niceSize(long numBytes) {
    if(numBytes>=100000000)
      return numFmt.format(numBytes/1000000000.0)+" GB";
    if(numBytes>=100000)
      return numFmt.format(numBytes/1000000.0)+" MB";
    if(numBytes>=100)
      return numFmt.format(numBytes/1000.0)+" KB";
    return String.valueOf(numBytes)+" bytes";
  }
  
}
