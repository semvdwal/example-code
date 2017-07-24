package mbp.common.db;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.wwk.meubelplan.common.logger.Logger;
import org.bson.Document;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Created by sem on 13-10-16.
 */
public class DatabaseResult<T extends Model> implements Iterable<T> {

    private static final Logger logger = Logger.getLogger(DatabaseResult.class);

    private Class<T> type;
    private FindIterable<Document> documentFindIterable;

    public DatabaseResult(Class<T> type, FindIterable<Document> documentFindIterable){
        this.type = type;
        this.documentFindIterable = documentFindIterable;
    }

    @Override
    public Iterator<T> iterator() {
        return new DatabaseResultIterator();
    }

    @Override
    public void forEach(Consumer action) {
        // Not implemented
    }

    @Override
    public Spliterator spliterator() {
        // Not implemented
        return null;
    }

    public T first(){
        Document document = documentFindIterable.first();
        if(document != null){
            try {
                T first = type.newInstance();
                first.setDocument(document);
                return first;
            } catch (Exception e) {
                logger.warning("Could not create new instance of "+type.getSimpleName()+", reason: "+e.getMessage());
            }
        }
        return null;
    }

    public DatabaseResult<T> sort(Document document){
        documentFindIterable.sort(document);
        return this;
    }

    public DatabaseResult<T> limit(int i){
        documentFindIterable.limit(i);
        return this;
    }

    public DatabaseResult<T> skip(int i){
        documentFindIterable.skip(i);
        return this;
    }

    public DatabaseResult<T> projection(Document projection){
        documentFindIterable.projection(projection);
        return this;
    }

    private class DatabaseResultIterator implements Iterator<T> {

        private MongoCursor<Document> cursor;

        public DatabaseResultIterator() {
            this.cursor = documentFindIterable.iterator();
        }

        @Override
        public boolean hasNext() {
            return cursor.hasNext();
        }

        @Override
        public T next() {
            if(cursor.hasNext()) {
                Document document = cursor.next();
                if(document != null){
                    try {
                        T next = type.newInstance();
                        next.setDocument(document);
                        return next;
                    } catch (Exception e) {
                        logger.warning("Could not create new instance of "+type.getSimpleName()+", reason: "+e.getMessage());
                    }
                }
            }
            return null;
        }

        @Override
        public void remove() {
            cursor.remove();
        }

        @Override
        public void forEachRemaining(Consumer action) {
            // Not implemented
        }

    }

}
