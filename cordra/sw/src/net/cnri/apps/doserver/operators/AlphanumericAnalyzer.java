/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;

public class AlphanumericAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
        return new TokenStreamComponents(new AlphanumericLowerCaseTokenizer(reader));
    }
    
    @Override
    public int getPositionIncrementGap(String fieldName) {
        return 10000;
    }
}
