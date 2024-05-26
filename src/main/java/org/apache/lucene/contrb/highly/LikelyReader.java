package org.apache.lucene.contrb.highly;

import org.apache.lucene.index.*;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
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
                        return impacts(flags);
                    }

                    @Override
                    public ImpactsEnum impacts(int flags) throws IOException {
                        final DocIdSetIterator aDoc = DocIdSetIterator.all(1);
                        final DocIdSetIterator aPos = DocIdSetIterator.all(1);
                        long minNorm = Long.MAX_VALUE;
                        int maxFreq = -Integer.MAX_VALUE;
                        for(LeafReaderContext lrc:LikelyReader.this.origin.leaves()) {
                            Terms leafTerms = lrc.reader().terms(field);
                            if (leafTerms!=null) {
                                TermsEnum iterator = leafTerms.iterator();
                                if(iterator.seekExact(in.term())){
                                    ImpactsEnum impactsEnum = iterator.impacts(PostingsEnum.FREQS);
                                    impactsEnum.advanceShallow(lrc.reader().maxDoc()-1);
                                    Impacts impactsImpacts = impactsEnum.getImpacts();
                                    List<Impact> impactList = impactsImpacts.getImpacts(impactsImpacts.numLevels());
                                    minNorm = Long.min(impactList.get(0).norm, minNorm);
                                    maxFreq = Integer.max(impactList.get(impactList.size() - 1).freq, maxFreq);
                                }
                            }
                        }

                        int tf = maxFreq;
                        //final PostingsEnum postings = origin.postings(reuse, flags);
                        int finalMaxFreq = maxFreq;
                        long finalMinNorm = minNorm;
                        return new ImpactsEnum() {
                            @Override
                            public void advanceShallow(int target) throws IOException {

                            }

                            @Override
                            public Impacts getImpacts() throws IOException {
                                return new Impacts() {
                                    @Override
                                    public int numLevels() {
                                        return 1;
                                    }

                                    @Override
                                    public int getDocIdUpTo(int level) {
                                        return 1;
                                    }

                                    @Override
                                    public List<Impact> getImpacts(int level) {
                                        return List.of(new Impact(finalMaxFreq, finalMinNorm) );
                                    }
                                };
                            }

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
                };
            }
        };
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

        // TODO estimate norms from impact
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
