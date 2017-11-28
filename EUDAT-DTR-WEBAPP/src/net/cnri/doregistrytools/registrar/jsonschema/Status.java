/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

public class Status {
	public Map<String, Integer> typeCount = new HashMap<String, Integer>();
}
