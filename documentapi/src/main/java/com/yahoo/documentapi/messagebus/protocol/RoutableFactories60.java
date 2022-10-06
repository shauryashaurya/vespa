// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.messagebus.Routable;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.VisitorStatistics;
import com.yahoo.vespa.objects.Deserializer;

import java.util.Map;

import static com.yahoo.documentapi.messagebus.protocol.AbstractRoutableFactory.decodeString;
import static com.yahoo.documentapi.messagebus.protocol.AbstractRoutableFactory.encodeString;


/**
 * @author Vegard Sjonfjell
 * This class encapsulates all the {@link RoutableFactory} classes needed to implement serialization for the document
 * protocol. When adding new factories to this class, please KEEP THE THEM ORDERED alphabetically like they are now.
 */
public abstract class RoutableFactories60 {

    /**
     * Implements the shared factory logic required for {@link DocumentMessage} objects, and it offers a more convenient
     * interface for implementing {@link RoutableFactory}.
     *
     * @author Simon Thoresen Hult
     */
    public static abstract class DocumentMessageFactory extends AbstractRoutableFactory {

        /**
         * This method encodes the given message using the given serializer. You are guaranteed to only receive messages
         * of the type that this factory was registered for.
         * <p>
         * This method is NOT exception safe. Return false to
         * signal failure.
         *
         * @param msg        The message to encode.
         * @param serializer The serializer to use for encoding.
         * @return True if the message was encoded.
         */
        protected abstract boolean doEncode(DocumentMessage msg, DocumentSerializer serializer);

        /**
         * This method decodes a message from the given deserializer. You are guaranteed to only receive byte buffers
         * generated by a previous call to {@link #doEncode(DocumentMessage, DocumentSerializer)}.
         * <p>
         * This method is NOT exception safe. Return null to signal failure.
         *
         * @param deserializer The deserializer to use for decoding.
         * @return The decoded message.
         */
        protected abstract DocumentMessage doDecode(DocumentDeserializer deserializer);

        @SuppressWarnings("removal") // TODO: Remove on Vespa 9
        public boolean encode(Routable obj, DocumentSerializer out) {
            if (!(obj instanceof DocumentMessage)) {
                throw new AssertionError(
                        "Document message factory (" + getClass().getName() + ") registered for incompatible " +
                        "routable type " + obj.getType() + "(" + obj.getClass().getName() + ").");
            }
            DocumentMessage msg = (DocumentMessage)obj;
            out.putByte(null, (byte)(msg.getPriority().getValue())); // TODO: encode default value on Vespa 9
            out.putInt(null, 0); // Ignored load type. 0 is legacy "default" load type ID.
            return doEncode(msg, out);
        }

        @SuppressWarnings("removal") // TODO: Remove on Vespa 9
        public Routable decode(DocumentDeserializer in) {
            byte pri = in.getByte(null); // TODO: ignore on Vespa 9
            in.getInt(null); // Ignored load type
            DocumentMessage msg = doDecode(in);
            if (msg != null) {
                msg.setPriority(DocumentProtocol.getPriority(pri));
            }
            return msg;
        }
    }

    /**
     * Implements the shared factory logic required for {@link DocumentReply} objects, and it offers a more convenient
     * interface for implementing {@link RoutableFactory}.
     *
     * @author Simon Thoresen Hult
     */
    public static abstract class DocumentReplyFactory extends AbstractRoutableFactory {

        /**
         * This method encodes the given reply into the given byte buffer. You are guaranteed to only receive replies of
         * the type that this factory was registered for.
         * <p>
         * This method is NOT exception safe. Return false to signal
         * failure.
         *
         * @param reply The reply to encode.
         * @param buf   The byte buffer to write to.
         * @return True if the message was encoded.
         */
        protected abstract boolean doEncode(DocumentReply reply, DocumentSerializer buf);

        /**
         * This method decodes a reply from the given byte buffer. You are guaranteed to only receive byte buffers
         * generated by a previous call to {@link #doEncode(DocumentReply, com.yahoo.document.serialization.DocumentSerializer)}.
         *
         * <p>
         * This method is NOT exception safe. Return null to signal failure.
         *
         * @param buf The byte buffer to read from.
         * @return The decoded reply.
         */
        protected abstract DocumentReply doDecode(DocumentDeserializer buf);

        public boolean encode(Routable obj, DocumentSerializer out) {
            if (!(obj instanceof DocumentReply)) {
                throw new AssertionError(
                        "Document reply factory (" + getClass().getName() + ") registered for incompatible " +
                        "routable type " + obj.getType() + "(" + obj.getClass().getName() + ").");
            }
            DocumentReply reply = (DocumentReply)obj;
            out.putByte(null, (byte)(reply.getPriority().getValue()));
            return doEncode(reply, out);
        }

        public Routable decode(DocumentDeserializer in) {
            byte pri = in.getByte(null);
            DocumentReply reply = doDecode(in);
            if (reply != null) {
                reply.setPriority(DocumentProtocol.getPriority(pri));
            }
            return reply;
        }
    }

    public static class CreateVisitorMessageFactory extends DocumentMessageFactory {

        protected String decodeBucketSpace(Deserializer deserializer) {
            return decodeString(deserializer);
        }

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            CreateVisitorMessage msg = new CreateVisitorMessage();
            msg.setLibraryName(decodeString(buf));
            msg.setInstanceId(decodeString(buf));
            msg.setControlDestination(decodeString(buf));
            msg.setDataDestination(decodeString(buf));
            msg.setDocumentSelection(decodeString(buf));
            msg.setMaxPendingReplyCount(buf.getInt(null));

            int size = buf.getInt(null);
            for (int i = 0; i < size; i++) {
                long reversed = buf.getLong(null);
                long rawid = ((reversed >>> 56) & 0x00000000000000FFl) | ((reversed >>> 40) & 0x000000000000FF00l) |
                        ((reversed >>> 24) & 0x0000000000FF0000l) | ((reversed >>> 8) & 0x00000000FF000000l) |
                        ((reversed << 8) & 0x000000FF00000000l) | ((reversed << 24) & 0x0000FF0000000000l) |
                        ((reversed << 40) & 0x00FF000000000000l) | ((reversed << 56) & 0xFF00000000000000l);
                msg.getBuckets().add(new BucketId(rawid));
            }

            msg.setFromTimestamp(buf.getLong(null));
            msg.setToTimestamp(buf.getLong(null));
            msg.setVisitRemoves(buf.getByte(null) == (byte)1);
            msg.setFieldSet(decodeString(buf));
            msg.setVisitInconsistentBuckets(buf.getByte(null) == (byte)1);

            size = buf.getInt(null);
            for (int i = 0; i < size; i++) {
                String key = decodeString(buf);
                int sz = buf.getInt(null);
                msg.getParameters().put(key, buf.getBytes(null, sz));
            }

            buf.getInt(null); // unused ordering spec
            msg.setMaxBucketsPerVisitor(buf.getInt(null));
            msg.setBucketSpace(decodeBucketSpace(buf));
            return msg;
        }

        protected boolean encodeBucketSpace(String bucketSpace, DocumentSerializer buf) {
            encodeString(bucketSpace, buf);
            return true;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            CreateVisitorMessage msg = (CreateVisitorMessage)obj;
            encodeString(msg.getLibraryName(), buf);
            encodeString(msg.getInstanceId(), buf);
            encodeString(msg.getControlDestination(), buf);
            encodeString(msg.getDataDestination(), buf);
            encodeString(msg.getDocumentSelection(), buf);
            buf.putInt(null, msg.getMaxPendingReplyCount());

            buf.putInt(null, msg.getBuckets().size());
            for (BucketId id : msg.getBuckets()) {
                long rawid = id.getRawId();
                long reversed = ((rawid >>> 56) & 0x00000000000000FFL) | ((rawid >>> 40) & 0x000000000000FF00L) |
                                ((rawid >>> 24) & 0x0000000000FF0000L) | ((rawid >>> 8) & 0x00000000FF000000L) |
                                ((rawid << 8) & 0x000000FF00000000L) | ((rawid << 24) & 0x0000FF0000000000L) |
                                ((rawid << 40) & 0x00FF000000000000L) | ((rawid << 56) & 0xFF00000000000000L);
                buf.putLong(null, reversed);
            }

            buf.putLong(null, msg.getFromTimestamp());
            buf.putLong(null, msg.getToTimestamp());
            buf.putByte(null, msg.getVisitRemoves() ? (byte)1 : (byte)0);
            encodeString(msg.getFieldSet(), buf);
            buf.putByte(null, msg.getVisitInconsistentBuckets() ? (byte)1 : (byte)0);

            buf.putInt(null, msg.getParameters().size());
            for (Map.Entry<String, byte[]> pairs : msg.getParameters().entrySet()) {
                encodeString(pairs.getKey(), buf);
                byte[] b = pairs.getValue();
                buf.putInt(null, b.length);
                buf.put(null, b);
            }

            buf.putInt(null, 0); // unused ordering spec
            buf.putInt(null, msg.getMaxBucketsPerVisitor());
            return encodeBucketSpace(msg.getBucketSpace(), buf);
        }
    }

    public static class CreateVisitorReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            CreateVisitorReply reply = new CreateVisitorReply(DocumentProtocol.REPLY_CREATEVISITOR);
            reply.setLastBucket(new BucketId(buf.getLong(null)));

            VisitorStatistics vs = new VisitorStatistics();
            vs.setBucketsVisited(buf.getInt(null));
            vs.setDocumentsVisited(buf.getLong(null));
            vs.setBytesVisited(buf.getLong(null));
            vs.setDocumentsReturned(buf.getLong(null));
            vs.setBytesReturned(buf.getLong(null));
            buf.getLong(null); // unused
            buf.getLong(null); // unused
            reply.setVisitorStatistics(vs);
            return reply;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            CreateVisitorReply reply = (CreateVisitorReply)obj;
            buf.putLong(null, reply.getLastBucket().getRawId());
            buf.putInt(null, reply.getVisitorStatistics().getBucketsVisited());
            buf.putLong(null, reply.getVisitorStatistics().getDocumentsVisited());
            buf.putLong(null, reply.getVisitorStatistics().getBytesVisited());
            buf.putLong(null, reply.getVisitorStatistics().getDocumentsReturned());
            buf.putLong(null, reply.getVisitorStatistics().getBytesReturned());
            buf.putLong(null, 0); // was SecondPassDocumentsReturned
            buf.putLong(null, 0); // was SecondPassBytesReturned
            return true;
        }
    }

    public static class DestroyVisitorMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            DestroyVisitorMessage msg = new DestroyVisitorMessage();
            msg.setInstanceId(decodeString(buf));
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            DestroyVisitorMessage msg = (DestroyVisitorMessage)obj;
            encodeString(msg.getInstanceId(), buf);
            return true;
        }
    }

    public static class DestroyVisitorReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new VisitorReply(DocumentProtocol.REPLY_DESTROYVISITOR);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class DocumentIgnoredReplyFactory extends DocumentReplyFactory {
        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new DocumentIgnoredReply();
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class DocumentListMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            DocumentListMessage msg = new DocumentListMessage();
            msg.setBucketId(new BucketId(buf.getLong(null)));
            int len = buf.getInt(null);
            for (int i = 0; i < len; i++) {
                msg.getDocuments().add(new DocumentListEntry(buf));
            }
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            DocumentListMessage msg = (DocumentListMessage)obj;
            buf.putLong(null, msg.getBucketId().getRawId());
            buf.putInt(null, msg.getDocuments().size());

            for (int i = 0; i < msg.getDocuments().size(); i++) {
                msg.getDocuments().get(i).serialize(buf);
            }
            return true;
        }
    }

    public static class DocumentListReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new VisitorReply(DocumentProtocol.REPLY_DOCUMENTLIST);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class DocumentSummaryMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            DocumentSummaryMessage msg = new DocumentSummaryMessage();
            msg.setDocumentSummary(new DocumentSummary(buf));
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            return false; // not supported
        }
    }

    public static class DocumentSummaryReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new VisitorReply(DocumentProtocol.REPLY_DOCUMENTSUMMARY);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class EmptyBucketsMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            EmptyBucketsMessage msg = new EmptyBucketsMessage();
            int size = buf.getInt(null);
            for (int i = 0; i < size; ++i) {
                msg.getBucketIds().add(new BucketId(buf.getLong(null)));
            }
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            EmptyBucketsMessage msg = (EmptyBucketsMessage)obj;
            buf.putInt(null, msg.getBucketIds().size());
            for (BucketId bid : msg.getBucketIds()) {
                buf.putLong(null, bid.getRawId());
            }
            return true;
        }
    }

    public static class EmptyBucketsReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new VisitorReply(DocumentProtocol.REPLY_EMPTYBUCKETS);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class GetBucketListMessageFactory extends DocumentMessageFactory {

        protected String decodeBucketSpace(Deserializer deserializer) {
            return decodeString(deserializer);
        }

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            GetBucketListMessage msg = new GetBucketListMessage();
            msg.setBucketId(new BucketId(buf.getLong(null)));
            msg.setBucketSpace(decodeBucketSpace(buf));
            return msg;
        }

        protected boolean encodeBucketSpace(String bucketSpace, DocumentSerializer buf) {
            encodeString(bucketSpace, buf);
            return true;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            GetBucketListMessage msg = (GetBucketListMessage)obj;
            buf.putLong(null, msg.getBucketId().getRawId());
            return encodeBucketSpace(msg.getBucketSpace(), buf);
        }
    }

    public static class GetBucketListReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            GetBucketListReply reply = new GetBucketListReply();
            int len = buf.getInt(null);
            for (int i = 0; i < len; i++) {
                GetBucketListReply.BucketInfo info = new GetBucketListReply.BucketInfo();
                info.bucket = new BucketId(buf.getLong(null));
                info.bucketInformation = decodeString(buf);
                reply.getBuckets().add(info);
            }
            return reply;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            GetBucketListReply reply = (GetBucketListReply)obj;
            buf.putInt(null, reply.getBuckets().size());
            for (GetBucketListReply.BucketInfo info : reply.getBuckets()) {
                buf.putLong(null, info.bucket.getRawId());
                encodeString(info.bucketInformation, buf);
            }
            return true;
        }
    }

    public static class GetBucketStateMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            GetBucketStateMessage msg = new GetBucketStateMessage();
            msg.setBucketId(new BucketId(buf.getLong(null)));
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            GetBucketStateMessage msg = (GetBucketStateMessage)obj;
            buf.putLong(null, msg.getBucketId().getRawId());
            return true;
        }
    }

    public static class GetBucketStateReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            GetBucketStateReply reply = new GetBucketStateReply();
            int size = buf.getInt(null);
            for (int i = 0; i < size; i++) {
                reply.getBucketState().add(new DocumentState(buf));
            }
            return reply;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            GetBucketStateReply reply = (GetBucketStateReply)obj;
            buf.putInt(null, reply.getBucketState().size());
            for (DocumentState stat : reply.getBucketState()) {
                stat.serialize(buf);
            }
            return true;
        }
    }

    public static class GetDocumentMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            return new GetDocumentMessage(new DocumentId(buf), decodeString(buf));
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            GetDocumentMessage msg = (GetDocumentMessage)obj;
            msg.getDocumentId().serialize(buf);
            encodeString(msg.getFieldSet(), buf);
            return true;
        }
    }

    public static class GetDocumentReplyFactory extends DocumentReplyFactory {

        private final LazyDecoder decoder = new LazyDecoder() {

            public void decode(Routable obj, DocumentDeserializer buf) {
                GetDocumentReply reply = (GetDocumentReply)obj;

                Document doc = null;
                byte flag = buf.getByte(null);
                if (flag != 0) {
                    doc = Document.createDocument(buf);
                    reply.setDocument(doc);
                }
                long lastModified = buf.getLong(null);
                reply.setLastModified(lastModified);
                if (doc != null) {
                    doc.setLastModified(lastModified);
                }
            }
        };

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            GetDocumentReply reply = new GetDocumentReply(decoder, buf);

            return reply;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            GetDocumentReply reply = (GetDocumentReply)obj;
            if (reply.getSerializedBuffer() != null) {
                buf.put(null, reply.getSerializedBuffer());
            } else {
                Document document = reply.getDocument();
                buf.putByte(null, (byte)(document == null ? 0 : 1));
                if (document != null) {
                    document.serialize(buf);
                }
                buf.putLong(null, reply.getLastModified());
            }
            return true;
        }
    }

    public static class MapVisitorMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            MapVisitorMessage msg = new MapVisitorMessage();
            int size = buf.getInt(null);
            for (int i = 0; i < size; i++) {
                String key = decodeString(buf);
                String value = decodeString(buf);
                msg.getData().put(key, value);
            }
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            MapVisitorMessage msg = (MapVisitorMessage)obj;
            buf.putInt(null, msg.getData().size());
            for (Map.Entry<String, String> pairs : msg.getData().entrySet()) {
                encodeString(pairs.getKey(), buf);
                encodeString(pairs.getValue(), buf);
            }
            return true;
        }
    }

    public static class MapVisitorReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new VisitorReply(DocumentProtocol.REPLY_MAPVISITOR);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class PutDocumentMessageFactory extends DocumentMessageFactory {
        protected void decodeInto(PutDocumentMessage msg, DocumentDeserializer buf) {
            msg.setDocumentPut(new DocumentPut(Document.createDocument(buf)));
            msg.setTimestamp(buf.getLong(null));
            decodeTasCondition(msg, buf);
        }

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buffer) {
            final LazyDecoder decoder = (obj, buf) -> {
                decodeInto((PutDocumentMessage) obj, buf);
            };

            return new PutDocumentMessage(decoder, buffer);
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            PutDocumentMessage msg = (PutDocumentMessage)obj;
            if (msg.getSerializedBuffer() != null) {
                buf.put(null, msg.getSerializedBuffer());
            } else {
                msg.getDocumentPut().getDocument().serialize(buf);
                buf.putLong(null, msg.getTimestamp());
                encodeTasCondition(buf, (TestAndSetMessage) obj);
            }
            return true;
        }
    }

    public static class PutDocumentReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            WriteDocumentReply rep = new WriteDocumentReply(DocumentProtocol.REPLY_PUTDOCUMENT);
            rep.setHighestModificationTimestamp(buf.getLong(null));
            return rep;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            WriteDocumentReply rep = (WriteDocumentReply)obj;
            buf.putLong(null, rep.getHighestModificationTimestamp());
            return true;
        }
    }

    public static class RemoveDocumentMessageFactory extends DocumentMessageFactory {
        protected void decodeInto(RemoveDocumentMessage msg, DocumentDeserializer buf) {
            msg.setDocumentId(new DocumentId(buf));
            decodeTasCondition(msg, buf);
        }

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            RemoveDocumentMessage msg = new RemoveDocumentMessage();
            decodeInto(msg, buf);
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            RemoveDocumentMessage msg = (RemoveDocumentMessage)obj;
            msg.getDocumentId().serialize(buf);
            encodeTasCondition(buf, (TestAndSetMessage) obj);
            return true;
        }
    }

    public static class RemoveDocumentReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            RemoveDocumentReply reply = new RemoveDocumentReply();
            byte flag = buf.getByte(null);
            reply.setWasFound(flag != 0);
            reply.setHighestModificationTimestamp(buf.getLong(null));
            return reply;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            RemoveDocumentReply reply = (RemoveDocumentReply)obj;
            buf.putByte(null, (byte)(reply.wasFound() ? 1 : 0));
            buf.putLong(null, reply.getHighestModificationTimestamp());
            return true;
        }
    }

    public static class RemoveLocationMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            return new RemoveLocationMessage(decodeString(buf));
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            RemoveLocationMessage msg = (RemoveLocationMessage)obj;
            encodeString(msg.getDocumentSelection(), buf);
            return true;
        }
    }

    public static class RemoveLocationReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new DocumentReply(DocumentProtocol.REPLY_REMOVELOCATION);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class SearchResultMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            SearchResultMessage msg = new SearchResultMessage();
            msg.setSearchResult(new SearchResult(buf));
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            return false; // not supported
        }
    }

    public static class QueryResultMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            QueryResultMessage msg = new QueryResultMessage();
            msg.setSearchResult(new SearchResult(buf));
            msg.setSummary(new DocumentSummary(buf));
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            return false; // not supported
        }
    }

    public static class SearchResultReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new VisitorReply(DocumentProtocol.REPLY_SEARCHRESULT);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class QueryResultReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new VisitorReply(DocumentProtocol.REPLY_QUERYRESULT);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class StatBucketMessageFactory extends DocumentMessageFactory {

        protected String decodeBucketSpace(Deserializer deserializer) {
            return decodeString(deserializer);
        }

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            StatBucketMessage msg = new StatBucketMessage();
            msg.setBucketId(new BucketId(buf.getLong(null)));
            msg.setDocumentSelection(decodeString(buf));
            msg.setBucketSpace(decodeBucketSpace(buf));
            return msg;
        }

        protected boolean encodeBucketSpace(String bucketSpace, DocumentSerializer buf) {
            encodeString(bucketSpace, buf);
            return true;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            StatBucketMessage msg = (StatBucketMessage)obj;
            buf.putLong(null, msg.getBucketId().getRawId());
            encodeString(msg.getDocumentSelection(), buf);
            return encodeBucketSpace(msg.getBucketSpace(), buf);
        }
    }

    public static class StatBucketReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            StatBucketReply reply = new StatBucketReply();
            reply.setResults(decodeString(buf));
            return reply;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            StatBucketReply reply = (StatBucketReply)obj;
            encodeString(reply.getResults(), buf);
            return true;
        }
    }

    public static class UpdateDocumentMessageFactory extends DocumentMessageFactory {
        protected void decodeInto(UpdateDocumentMessage msg, DocumentDeserializer buf) {
            msg.setDocumentUpdate(new DocumentUpdate(buf));
            msg.setOldTimestamp(buf.getLong(null));
            msg.setNewTimestamp(buf.getLong(null));
            decodeTasCondition(msg, buf);
        }

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buffer) {
            final LazyDecoder decoder = (obj, buf) -> {
                decodeInto((UpdateDocumentMessage) obj, buf);
            };

            return new UpdateDocumentMessage(decoder, buffer);
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            UpdateDocumentMessage msg = (UpdateDocumentMessage)obj;
            if (msg.getSerializedBuffer() != null) {
                buf.put(null, msg.getSerializedBuffer());
            } else {
                msg.getDocumentUpdate().serialize(buf);
                buf.putLong(null, msg.getOldTimestamp());
                buf.putLong(null, msg.getNewTimestamp());
                encodeTasCondition(buf, (TestAndSetMessage) obj);
            }
            return true;
        }
    }

    public static class UpdateDocumentReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            UpdateDocumentReply rep = new UpdateDocumentReply();
            byte flag = buf.getByte(null);
            rep.setWasFound(flag != 0);
            rep.setHighestModificationTimestamp(buf.getLong(null));
            return rep;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            UpdateDocumentReply rep = (UpdateDocumentReply)obj;
            buf.putByte(null, (byte)(rep.wasFound() ? 1 : 0));
            buf.putLong(null, rep.getHighestModificationTimestamp());
            return true;
        }
    }

    public static class VisitorInfoMessageFactory extends DocumentMessageFactory {

        @Override
        protected DocumentMessage doDecode(DocumentDeserializer buf) {
            VisitorInfoMessage msg = new VisitorInfoMessage();
            int size = buf.getInt(null);
            for (int i = 0; i < size; i++) {
                long reversed = buf.getLong(null);
                long rawid = ((reversed >>> 56) & 0x00000000000000FFl) | ((reversed >>> 40) & 0x000000000000FF00l) |
                             ((reversed >>> 24) & 0x0000000000FF0000l) | ((reversed >>> 8) & 0x00000000FF000000l) |
                             ((reversed << 8) & 0x000000FF00000000l) | ((reversed << 24) & 0x0000FF0000000000l) |
                             ((reversed << 40) & 0x00FF000000000000l) | ((reversed << 56) & 0xFF00000000000000l);
                msg.getFinishedBuckets().add(new BucketId(rawid));
            }

            msg.setErrorMessage(decodeString(buf));
            return msg;
        }

        @Override
        protected boolean doEncode(DocumentMessage obj, DocumentSerializer buf) {
            VisitorInfoMessage msg = (VisitorInfoMessage)obj;
            buf.putInt(null, msg.getFinishedBuckets().size());
            for (BucketId id : msg.getFinishedBuckets()) {
                long rawid = id.getRawId();
                long reversed = ((rawid >>> 56) & 0x00000000000000FFl) | ((rawid >>> 40) & 0x000000000000FF00l) |
                                ((rawid >>> 24) & 0x0000000000FF0000l) | ((rawid >>> 8) & 0x00000000FF000000l) |
                                ((rawid << 8) & 0x000000FF00000000l) | ((rawid << 24) & 0x0000FF0000000000l) |
                                ((rawid << 40) & 0x00FF000000000000l) | ((rawid << 56) & 0xFF00000000000000l);
                buf.putLong(null, reversed);
            }
            encodeString(msg.getErrorMessage(), buf);
            return true;
        }
    }

    public static class VisitorInfoReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            return new VisitorReply(DocumentProtocol.REPLY_VISITORINFO);
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            return true;
        }
    }

    public static class WrongDistributionReplyFactory extends DocumentReplyFactory {

        @Override
        protected DocumentReply doDecode(DocumentDeserializer buf) {
            WrongDistributionReply reply = new WrongDistributionReply();
            reply.setSystemState(decodeString(buf));
            return reply;
        }

        @Override
        protected boolean doEncode(DocumentReply obj, DocumentSerializer buf) {
            WrongDistributionReply reply = (WrongDistributionReply)obj;
            encodeString(reply.getSystemState(), buf);
            return true;
        }
    }
    static void decodeTasCondition(TestAndSetMessage msg, DocumentDeserializer buf) {
        msg.setCondition(new TestAndSetCondition(decodeString(buf)));
    }

    static void encodeTasCondition(DocumentSerializer buf, TestAndSetMessage msg) {
        encodeString(msg.getCondition().getSelection(), buf);
    }
}
