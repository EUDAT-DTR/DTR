/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class DOImages {
  public static final String DOCUMENT = "/net/cnri/apps/dogui/view/images/document.png";
  public static final String DEPOSIT_WELL = "/net/cnri/apps/dogui/view/images/DropWell.png";
  public static final String INFO = "/net/cnri/apps/dogui/view/images/i_in_circle.png";
  public static final String UP_TRIANGLE = "/net/cnri/apps/dogui/view/images/up_triangle.png";
  
  public static final String ADD = "/net/cnri/apps/dogui/view/images/add.png";
  public static final String REMOVE = "/net/cnri/apps/dogui/view/images/remove.png";
  
  private HashMap images = new HashMap();
  private HashMap icons = new HashMap();
  
  DOImages() {
  }
  
  
  /** Get an icon with the given path as a resource. */
  public synchronized Icon getIcon(String imgPath) {
    Icon icon = (Icon)icons.get(imgPath);
    if(icon!=null) return icon;
    
    Image img = getImage(imgPath);
    if(img==null) return null;
    
    ImageIcon imgIcon = new ImageIcon(img);
    icon = imgIcon;
    while(imgIcon.getImageLoadStatus()==MediaTracker.LOADING) {
      try { Thread.yield(); } catch (Exception e) {}
    }
    icons.put(imgPath, icon);
    return icon;
  }
  
  
  /** Get an image with the given path as a resource.  This uses a cache to
    * avoid reloading images numerous times. */
  public synchronized Image getImage(String imgPath) {
    Image img = (Image)images.get(imgPath);
    if(img!=null) return img;
    
    try {
      java.net.URL url = getClass().getResource(imgPath);
      if(url==null) return null;
      
      img = Toolkit.getDefaultToolkit().getImage(url);
      if(img==null) return null;
      
      images.put(imgPath, img);
      return img;
    } catch (Exception e) {
      System.err.println("Error loading image: '"+imgPath+"' error="+e);
    }
    return null;
  }
  
  
}

