/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.Version;

import java.io.Reader;

public class AlphanumericLowerCaseTokenizer extends CharTokenizer {
    public AlphanumericLowerCaseTokenizer(Reader in) {
        super(Version.LUCENE_47, in);
    }

    @Override
    protected int normalize(int c) {
        return Character.toLowerCase(c);
    }

    @Override
    protected boolean isTokenChar(int c) {
        return Character.isLetterOrDigit(c);
    }
}
