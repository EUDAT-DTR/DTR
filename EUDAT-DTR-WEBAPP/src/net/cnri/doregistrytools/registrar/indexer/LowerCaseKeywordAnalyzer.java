/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.indexer;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;

public class LowerCaseKeywordAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
        return new TokenStreamComponents(new LowerCaseKeywordTokenizer(reader));
    }
    
    @Override
    public int getPositionIncrementGap(String fieldName) {
        return 10000;
    }
}
