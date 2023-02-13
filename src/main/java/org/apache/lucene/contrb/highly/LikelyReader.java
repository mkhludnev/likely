package org.apache.lucene.contrb.highly;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.CompositeReader;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.ImpactsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafMetaData;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SlowImpactsEnum;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.VectorValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

/**
 *
 * */
public class LikelyReader extends LeafReader {
    private final IndexReader origin;

    /**
     * Construct a FilterLeafReader based on the specified base reader.
     *
     * <p>Note that base reader is closed if this FilterLeafReader is closed.
     *
     * @param in specified base reader.
     */
    public LikelyReader(IndexReader in)
    {
        this.origin = in;
        // TODO heh.. how to pass same maxdoc? is it done already?
    }

    @Override
    public Terms terms(String field) throws IOException {
        final Terms terms = MultiTerms.getTerms(origin, field);
        if (terms==null) {
            return null;
        }
        return new FilterLeafReader.FilterTerms(terms) {
            @Override
            public TermsEnum iterator() throws IOException {
                final TermsEnum origin = super.iterator();
                if (origin==null) {
                    return null;
                }
                // seeks, next, terms, postings
                return new FilterLeafReader.FilterTermsEnum(origin) {
                    @Override
                    public PostingsEnum postings(PostingsEnum reuse, int flags) throws IOException {
                        final DocIdSetIterator aDoc = DocIdSetIterator.all(1);
                        final DocIdSetIterator aPos = DocIdSetIterator.all(1);
                        int tf = estimateMaxTf(in.totalTermFreq(), in.docFreq(), maxDoc());
                        //final PostingsEnum postings = origin.postings(reuse, flags);
                        return new PostingsEnum() {
                            @Override
                            public int freq() throws IOException {
                                return tf;
                            }

                            @Override
                            public int nextPosition() throws IOException {
                                return aPos.nextDoc();
                            }

                            @Override
                            public int startOffset() throws IOException {
                                return 0;
                            }

                            @Override
                            public int endOffset() throws IOException {
                                return 1;
                            }

                            @Override
                            public BytesRef getPayload() throws IOException {
                                return null;
                            }

                            @Override
                            public int docID() {
                                return aDoc.docID();
                            }

                            @Override
                            public int nextDoc() throws IOException {
                                return aDoc.nextDoc();
                            }

                            @Override
                            public int advance(int target) throws IOException {
                                return aDoc.advance(target);
                            }

                            @Override
                            public long cost() {
                                return aDoc.cost();
                            }
                        };
                    }

                    @Override
                    public ImpactsEnum impacts(int flags) throws IOException {
                        //final ImpactsEnum impacts = super.impacts(flags);
                        return new SlowImpactsEnum(postings(null,flags));
                    }
                };
            }
        };
    }

    private static int estimateMaxTf(long totalTermFreq, int docFreq, int maxDoc) {
        int tf=0;
        for (; docFreq > 0 && maxDoc > 0 && totalTermFreq > docFreq; tf+=1 ){
            totalTermFreq -= docFreq;
            int newDiv= docFreq * docFreq / maxDoc;
            maxDoc = docFreq;//+1 ???
            docFreq = newDiv;
        }
        tf += totalTermFreq;
        return tf;
    }

    @Override
    public NumericDocValues getNumericDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public BinaryDocValues getBinaryDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public SortedDocValues getSortedDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
        return null;
    }

    @Override
    public NumericDocValues getNormValues(String field) throws IOException {
        return null;
    }

    @Override
    public VectorValues getVectorValues(String field) throws IOException {
        return null;
    }

    @Override
    public TopDocs searchNearestVectors(String field, float[] target, int k, Bits acceptDocs, int visitedLimit) throws IOException {
        return null;
    }

    @Override
    public FieldInfos getFieldInfos() {
        return checkFirst(LeafReader::getFieldInfos);
    }

    private <R> R  checkFirst(Function<LeafReader, R> getFieldInfos) {
        final Optional<LeafReader> reader;
        if (origin instanceof LeafReader) {
            reader = Optional.of((LeafReader) origin);
        } else {
            if (origin instanceof CompositeReader) {
                reader = Optional.of(((CompositeReader) origin).getContext().leaves().get(0).reader());
            } else {
                reader = Optional.empty();
            }
        }

        return reader.map(getFieldInfos).orElse(null);
    }

    @Override
    public Bits getLiveDocs() {
        return new Bits() {
            @Override
            public boolean get(int index) {
                return true;
            }

            @Override
            public int length() {
                return origin.maxDoc();
            }
        };
    }

    @Override
    public PointValues getPointValues(String field) throws IOException {
        return checkFirst(Function.identity()).getPointValues(field);
    }

    @Override
    public void checkIntegrity() throws IOException {

    }

    @Override
    public LeafMetaData getMetaData() {
        return null;
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
        return null;
    }

    @Override
    public Fields getTermVectors(int docID) throws IOException {
        return origin.getTermVectors(docID);
    }

    @Override
    public int numDocs() {
        return origin.numDocs();
    }

    @Override
    public int maxDoc() {
        return origin.maxDoc();
    }

    @Override
    public void document(int docID, StoredFieldVisitor visitor) throws IOException {

    }

    @Override
    public CacheHelper getReaderCacheHelper() {
        return null;
    }


    @Override
    public boolean hasDeletions() {
        return false;
    }

    @Override
    protected void doClose() throws IOException {

    }
}
