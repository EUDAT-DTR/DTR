/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import java.util.ArrayList;
import java.util.List;

import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.search.BooleanClause;
import net.cnri.repository.search.BooleanQuery;
import net.cnri.repository.search.ElementAttributeQuery;
import net.cnri.repository.search.MatchAllObjectsQuery;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryVisitor;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.util.StringEncoding;
import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;

public class ParcelableQuery implements Parcelable {
    static final int ATTRIBUTE_QUERY = 1;
    static final int ELEMENT_ATTRIBUTE_QUERY = 2;
    static final int BOOLEAN_QUERY = 3;
    static final int MATCH_ALL_OBJECTS_QUERY = 4;
    static final int RAW_QUERY = 5;

    static BooleanClause.Occur[] BooleanClauseOccurValues = BooleanClause.Occur.values();

    private final Query query;
    
    public ParcelableQuery(Query query) {
        this.query = query;
    }
    
    public Query getQuery() {
        return query;
    }
    
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeQueryToParcel(query,dest);
    }

    static class WriteToParcelQueryVisitor implements QueryVisitor<Void> {
        private final Parcel dest;
        
        public WriteToParcelQueryVisitor(Parcel dest) {
            this.dest = dest;
        }
        
        @Override
        public Void visitAttributeQuery(AttributeQuery query) throws RepositoryException {
            dest.writeInt(ATTRIBUTE_QUERY);
            dest.writeString(query.getAttributeName());
            dest.writeString(query.getValue());
            return null;
        }
        
        @Override
        public Void visitElementAttributeQuery(ElementAttributeQuery query) throws RepositoryException {
            dest.writeInt(ELEMENT_ATTRIBUTE_QUERY);
            dest.writeString(query.getElementName());
            dest.writeString(query.getAttributeName());
            dest.writeString(query.getValue());
            return null;
        }
        
        @Override
        public Void visitBooleanQuery(BooleanQuery query) throws RepositoryException {
            dest.writeInt(BOOLEAN_QUERY);
            dest.writeInt(query.clauses().size());
            for(BooleanClause clause : query.clauses()) {
                dest.writeInt(clause.getOccur().ordinal());
                clause.getQuery().accept(this);
            }
            return null;
        }
        
        @Override
        public Void visitMatchAllObjectsQuery(MatchAllObjectsQuery query) throws RepositoryException {
            dest.writeInt(MATCH_ALL_OBJECTS_QUERY);
            return null;
        }
        
        @Override
        public Void visitRawQuery(RawQuery query) throws RepositoryException {
            dest.writeInt(RAW_QUERY);
            dest.writeString(query.getQueryString());
            return null;
        }
    }

    public static Parcelable.Creator<ParcelableQuery> CREATOR = new Parcelable.Creator<ParcelableQuery>() {
        @Override
        public ParcelableQuery createFromParcel(Parcel in) {
            return new ParcelableQuery(readQueryFromParcel(in));
        }

        @Override
        public ParcelableQuery[] newArray(int size) {
            return new ParcelableQuery[size];
        }
    };
    
    public static Query readQueryFromParcel(Parcel in) {
        switch(in.readInt()) {
        case ATTRIBUTE_QUERY:
            return new AttributeQuery(in.readString(),in.readString());
        case ELEMENT_ATTRIBUTE_QUERY:
            return new ElementAttributeQuery(in.readString(),in.readString(),in.readString());
        case BOOLEAN_QUERY:
            int size = in.readInt();
            List<BooleanClause> clauses = new ArrayList<BooleanClause>(size);
            for(int i = 0; i < size; i++) {
                BooleanClause.Occur occur = BooleanClauseOccurValues[in.readInt()];
                clauses.add(new BooleanClause(readQueryFromParcel(in),occur));
            }
            return new BooleanQuery(clauses);
        case MATCH_ALL_OBJECTS_QUERY:
            return new MatchAllObjectsQuery();
        case RAW_QUERY:
            return new RawQuery(in.readString());
        default:
            throw new BadParcelableException("Unknown Query parcel key");
        }
    }
    
    public static void writeQueryToParcel(Query query, Parcel dest) {
        try {
            query.accept(new WriteToParcelQueryVisitor(dest));
        }
        catch(RepositoryException e) {
            throw new AssertionError(e);
        }
    }
    
    public static String writeQueryToString(Query query) {
        Parcel parcel = Parcel.obtain();
        writeQueryToParcel(query,parcel);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return StringEncoding.decodeUTF16BE(bytes);
    }
    
    public static Query readQueryFromString(String s) {
        Parcel parcel = Parcel.obtain();
        byte[] bytes = StringEncoding.encodeUTF16BE(s);
        parcel.unmarshall(bytes,0,bytes.length);
        parcel.setDataPosition(0);
        Query res = readQueryFromParcel(parcel);
        parcel.recycle();
        return res;
    }
}
