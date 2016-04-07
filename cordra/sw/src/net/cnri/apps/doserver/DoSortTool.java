/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.do_api.DigitalObject;
import org.apache.velocity.tools.generic.SortTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DoSortTool extends SortTool {
  private static Logger logger = LoggerFactory.getLogger(AgentSignerThread.class);

  public DoSortTool() {
    super();
  }

  @Override
  protected Collection internalSort(List list, List properties) {
    //System.out.println("Sort called");
    try {
      if (properties == null) {
        Collections.sort(list);
      } else {
        Collections.sort(list, new DoPropertiesComparator(properties));
      }

      return list;
    } catch (Exception e) {
      logger.error("Error in internalSort",e);  
      return null;
    }
  }

  public class DoPropertiesComparator extends PropertiesComparator {
    private static final int TYPE_ASCENDING = 1;
    private static final int TYPE_DESCENDING = -1;

    List properties;
    int[] sortTypes;
    
    public DoPropertiesComparator(List properties) {
      super(properties);
      this.properties = properties;
      // determine ascending/descending
      sortTypes = new int[properties.size()];

      for (int i = 0; i < properties.size(); i++)
      {
        if (properties.get(i) == null)
        {
          throw new IllegalArgumentException("Property " + i
            + "is null, sort properties may not be null.");
        }

        // determine if the property contains a sort type
        // // e.g "Name:asc" means sort by property Name ascending
        String prop = properties.get(i).toString();
        int colonIndex = prop.indexOf(':');
        if (colonIndex != -1)
        {
          String sortType = prop.substring(colonIndex + 1);
          properties.set(i, prop.substring(0, colonIndex));

          if (TYPE_ASCENDING_SHORT.equalsIgnoreCase(sortType))
          {
            sortTypes[i] = TYPE_ASCENDING;
          }
          else if (TYPE_DESCENDING_SHORT.equalsIgnoreCase(sortType))
          {
            sortTypes[i] = TYPE_DESCENDING;
          }
          else
          {
            //FIXME: log this
            // invalide property sort type. use default instead.
            sortTypes[i] = TYPE_ASCENDING;
          }
        }
        else
        {
          // default sort type is ascending.
          sortTypes[i] = TYPE_ASCENDING;
        }
      }
    }

    @Override
    public int compare(Object lhs, Object rhs){
      for (int i = 0; i < properties.size(); i++) {
        int comparison = 0;
        String property = (String)properties.get(i);
        // properties must be comparable
        Comparable left = null;
        Comparable right = null;
        if(lhs instanceof DigitalObject && rhs instanceof DigitalObject) {
          //System.out.println("Using new getComparable()");
          //Here is the overloading so we can refer to properties appropriately
          left = getComparable((DigitalObject) lhs, property);
          right = getComparable((DigitalObject) rhs, property);
        } else {
          //System.out.println("Using default getComparable()");
          left = SortTool.getComparable(lhs, property);
          right = SortTool.getComparable(rhs, property);
        }
        if(left == null && right == null) {
          return 0;
        } else if (left == null && right != null) {
          // find out how right feels about left being null
          comparison = right.compareTo(left);
          // and reverse that (if it works)
          comparison *= -1;
        } else if (left instanceof String) {
          //TODO: make it optional whether or not case is ignored
          comparison = ((String)left).compareToIgnoreCase((String)right);
        } else {
          //System.out.println("Left:"+left+" Right:"+right);
          comparison = left.compareTo(right);
        }
        // return the first difference we find
        if (comparison != 0) {
          // multiplied by the sort direction, of course
          return comparison * sortTypes[i];
        }
      }
      return 0;
    }
  }
  
  protected static Comparable getComparable(DigitalObject object, String property) {
    //System.out.println("getComparable() called");
    //This overloads the static method for use with DigitalObjects
    try {
      return (Comparable) object.getAttribute(property, "");
    } catch (Exception e) {
      logger.error("Could not retrieve comparable value for '"
        + property + "' from " + object,e);
      throw new IllegalArgumentException("Could not retrieve comparable value for '"
        + property + "' from " + object + ": " + e);
    }
  }
}
