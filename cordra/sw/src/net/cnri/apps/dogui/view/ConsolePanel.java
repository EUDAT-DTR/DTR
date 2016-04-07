/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import net.handle.awt.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.datatransfer.StringSelection;

public class ConsolePanel
  extends JPanel
  implements AdjustmentListener,
             Runnable
{
  public boolean debug = false;
  private int rowHeight = 18;
  private ArrayList lines;
  private JScrollBar scrollBar;
  private ConsoleCanvas canvas;
  private int firstIdx = 0;
  private Font textFont;

  public ConsolePanel() {
    super(new GridBagLayout());
    setBorder(new EtchedBorder());
    FontMetrics fm = null;
    Graphics graphics = this.getGraphics();
    if(graphics!=null) {
        textFont = new Font("Monospaced", Font.PLAIN, 10);
        fm = graphics.getFontMetrics(textFont);
    }
    if(fm!=null) {
      int lineHeight = fm.getHeight();
      if(lineHeight>0)
        rowHeight = lineHeight;
    }
    
    canvas = new ConsoleCanvas();
    lines = new ArrayList();
    lines.add("");
    scrollBar = new JScrollBar(JScrollBar.VERTICAL, 0, 0, 0, 0);
    add(canvas,
        AwtUtil.getConstraints(0,0,1,1,1,1,true,true));
    add(scrollBar,
        AwtUtil.getConstraints(1,0,0,1,1,1,true,true));
    
    scrollBar.addAdjustmentListener(this);
  }

  public void writeConsoleContents(Writer w)
    throws IOException
  {
    for(int i=0; i<lines.size(); i++) {
      w.write(String.valueOf(lines.get(i)));
      w.write("\n");
    }
  }
  
  public void copyContentsToClipboard() {
    StringBuffer sb = new StringBuffer();
    for(Iterator it=lines.iterator(); it.hasNext(); ) {
      sb.append(String.valueOf(it.next()));
      sb.append('\n');
    }
    StringSelection ss = new StringSelection(sb.toString());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, ss);
  }
  
  
  public synchronized void addText(String text) {
    try {
      if(text==null) return;
      boolean isAtEnd = (scrollBar.getValue()+scrollBar.getVisibleAmount())>=scrollBar.getMaximum();
      boolean newline = false;
      do {
        int nlIdx = text.indexOf('\n');
        int lastLineNum = lines.size()-1;
        if(nlIdx<0) {
          lines.set(lastLineNum, ((String)lines.get(lastLineNum) + text));
          text = "";
        } else {
          lines.add(text.substring(0, nlIdx));
          text = text.substring(nlIdx+1);
          if(isAtEnd) {
            firstIdx = Math.max(0, lines.size()-canvas.getNumRows());
          }
          newline = true;
        }
      } while(text.length()>0);
      
      if(newline) {
        if(isAtEnd) {
          if(debug) {
            System.err.println("set (end) values"+
                               "; first="+Math.max(firstIdx, lines.size()-canvas.getNumRows())+
                               "; extent="+Math.min(lines.size(), canvas.getNumRows())+
                               "; min="+0+
                               "; max="+lines.size());
          }
          scrollBar.setValues(Math.max(firstIdx, lines.size()-canvas.getNumRows()),
                              Math.min(lines.size(), canvas.getNumRows()),
                              0,
                              lines.size());
        } else {
          if(debug) {
            System.err.println("set values"+
                               "; first="+firstIdx+
                               "; extent="+Math.min(lines.size(), canvas.getNumRows())+
                               "; min="+0+
                               "; max="+lines.size());
          }
          scrollBar.setValues(firstIdx,
                              Math.min(lines.size(), canvas.getNumRows()),
                              0,
                              lines.size());
        }
      }
      SwingUtilities.invokeLater(this);
    } catch (Throwable t) {
      //t.printStackTrace(System.out);
    }
  }

  public synchronized void clear() {
    lines.clear();
    addText("\n");
    repaint();
  }

  public void run() {
    canvas.repaint();
  }
  
  public void adjustmentValueChanged(AdjustmentEvent evt) {
    firstIdx = evt.getValue();
    canvas.repaint();
  }

  
  private ConsoleStream outputStream = null;
  public synchronized OutputStream getOutputStream() {
    if(outputStream==null) {
      outputStream = new ConsoleStream();
    }
    return outputStream;
  }

  private class ConsoleStream
    extends OutputStream
  {
    int MAX_CHARS = 32*1024;  // 32k

    public void write(byte buf[]) throws IOException {
      addText(new String(buf));
    }
    public void write(byte buf[], int offset, int length) throws IOException {
      addText(new String(buf, offset, length));
    } 
    
    public void write(int i) throws IOException {
      addText(String.valueOf((char)i));
    }
  }
  

  

  private class ConsoleCanvas
    extends JComponent
  {
    int w = 0;
    int h = 0;

    public void setBounds(int x, int y, int w, int h) {
      this.w = w;
      this.h = h;
      super.setBounds(x, y, w, h);
    }

    int getNumRows() {
      return h/rowHeight;
    }

    public void paint(Graphics g) {
      update(g);
    }

    public void update(Graphics g) {
      g.setFont(textFont);
      g.clearRect(0, 0, w, h);
      int x = 4;
      int y = rowHeight;
      for(int i=firstIdx; y<h+rowHeight && i<lines.size(); i++) {
        g.drawString((String)lines.get(i), x, y);
        y += rowHeight;
      }
    }
    
  }

}
