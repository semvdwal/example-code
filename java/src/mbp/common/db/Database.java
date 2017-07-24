package mbp.common.db;

import com.mongodb.MongoClient;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import com.wwk.meubelplan.common.db.SelectionFilter;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;
import java.util.logging.Logger;

import static java.util.Arrays.asList;

/**
 * Created by sem on 05-04-14.
 */
public class Database {

    private static final Logger logger = Logger.getLogger("Database");
    private static int maxRetries = 5;

    private MongoClient mongoClient;
    private MongoDatabase db;

    private boolean batchMode = false;

    private String dbaseLocation = "remote:localhost/meubelplan-develop-2";
    private String username = "admin";
    private String password = "admin";

    private static ThreadLocal<Database> instance;

    /**
     * Saves current instance in
     */
    public Database(String username, String password){
        if(username != null && password != null) {
            this.username = username;
            this.password = password;
        }
    }

    /**
     * Gets a database instance
     * @return A database instance
     */
    public static Database getInstance(String username, String password){
        if(instance == null){
            instance = new ThreadLocal<>();
        }
        if(instance.get() == null){
            instance.set(new Database(username, password));
        }
        return instance.get();
    }

    public static Database getInstance(){
        return getInstance(null, null);
    }

    /**
     * Opens a new connection to the database
     */
    public void openDatabase(){
        openDatabase(false);
    }

    /**
     * Opens a new connection to the database
     */
    public void openDatabase(Boolean force){
        if(mongoClient == null){
            mongoClient = new MongoClient();
        }
        if(db == null || force) db = mongoClient.getDatabase("mbp");
    }

    /**
     * Start batch mode, database will not be closed until batch mode ends
     */
    public void startBatch(){
        openDatabase();
        batchMode = true;
    }

    /**
     * Ends batch mode, closing the database
     */
    public void endBatch(){
        batchMode = false;
        closeDatabase();
    }

    /**
     * Creates a new instance from an entity which can be used in the database
     * @param c         The class of the entity to find an instance from (use entities within com.wwk.webshopconnect.entities)
     * @return          An instance of the given class, enriched for use with the database
     */
    public <T extends Model> T newEntityInstance(Class<T> c){
        T result = null;
        try{
            openDatabase();
            result = c.newInstance();
        }catch (Exception e){
            logger.warning("Could not create new database entity instance: "+e.getMessage());
            e.printStackTrace();
        }finally {
            closeDatabase();
        }
        return result;
    }

    /**
     * Retrieves an object from the database based on
     * @param dbId The database id of the object which is requested
     * @return The request object
     */
    public <T extends Model> T get(String dbId, Class<T> c){
        try {
            return get(new ObjectId(dbId), c);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Retrieves an object from the database based on
     * @param objectId The database recordId of the object which is requested
     * @return The request object
     */
    public <T extends Model> T get(ObjectId objectId, Class<T> c){
        if(objectId == null) return null;
        T result = null;
        try{
            openDatabase();
            FindIterable<Document> iterable = db.getCollection(c.getSimpleName()).find(new Document("_id", objectId)).limit(1);
            Document document = iterable.first();
            if(document != null){
                result = c.newInstance();
                result.setDocument(document);
            } else {
                logger.info("Could not find document of type "+c.getSimpleName()+" with objectId "+objectId.toHexString());
            }
        }catch (Exception e){
            logger.warning("Could not load object from database ("+e.getClass().getSimpleName()+"): " + e.getMessage());
        }finally{
            closeDatabase();
        }
        return result;
    }

    /**
     * Retrieves an document from the database
     * @param objectId The database recordId of the object which is requested
     * @return The requested document
     */
    public Document get(ObjectId objectId){
        logger.warning("Cannot get document without class / collection name");
        return null;
    }

    /**
     * Retrieves all objects for a specific class from the database
     * @param tClass    The class to find al objects for
     * @return          DatabaseIterator An iterator which can be used to iterate over the resulting objects
     */
    public <T extends Model> DatabaseResult<T> getALL(Class<T> tClass){
        try {
            openDatabase();
            return new DatabaseResult<>(tClass, db.getCollection(tClass.getSimpleName()).find());
        }catch (Exception e){
            logger.warning("Could not browse class in database: "+e.getMessage());
            e.printStackTrace();
        }finally{
            closeDatabase();
        }
        return null;
    }

    /**
     * Retrieves all objects matching a particular query.
     * @param tClass    The class for which to execute the query
     * @param filter    The filter to use for this query
     * @return          A list of objects matching the query, of the given class type
     */
    public <T extends Model> DatabaseResult<T> query(Class<T> tClass, Document filter){
        return query(tClass, filter, 0);
    }

    /**
     * Retrieves all objects matching a particular query.
     * @param tClass    The class for which to execute the query
     * @param filter    The filter to use for this query
     * @param retries   The number of times the function has already been called
     * @return          A list of objects matching the query, of the given class type
     */
    public <T extends Model> DatabaseResult<T> query(Class<T> tClass, Document filter, int retries){
        try {
            openDatabase();
            return new DatabaseResult<>(tClass, db.getCollection(tClass.getSimpleName()).find(filter));
        }catch (Exception e){
            logger.warning("Could not query database: "+e.getMessage());
            e.printStackTrace();
        }finally{
            closeDatabase();
        }
        return null;
    }

    /**
     * Aggregate query results
     * @param tClass    The class for which to execute the aggregation
     * @param group     The group selection for this aggregation
     * @param <T>       The class type
     * @return          Found documents
     */
    public <T extends Model> AggregateIterable<Document> aggregate(Class<T> tClass, Document group){
        return aggregate(tClass, group, null);
    }

    /**
     * Aggregate query results
     * @param tClass    The class for which to execute the aggregation
     * @param group     The group selection for this aggregation
     * @param filter    The filter for this aggregation
     * @param <T>       The class type
     * @return          Found documents
     */
    public <T extends Model> AggregateIterable<Document> aggregate(Class<T> tClass, Document group, Document filter){
        try {
            openDatabase();
            if(filter != null) {
                return db.getCollection(tClass.getSimpleName()).aggregate(asList(new Document("$match", filter), new Document("$group", group)));
            } else {
                return db.getCollection(tClass.getSimpleName()).aggregate(asList(new Document("$group", group)));
            }
        } catch (Exception e) {
            logger.warning("Could not query database: "+e.getMessage());
            e.printStackTrace();
        } finally {
            closeDatabase();
        }
        return null;
    }

    /**
     * Retrieves all objects matching a particular query.
     * @param filter    The filter to use for this query
     * @return          A list of objects matching the query, of the given class type
     */
    public FindIterable<Document> queryDocuments(Document filter, Class dClass){
        return queryDocuments(filter, dClass, 0);
    }

    /**
     * Retrieves all objects matching a particular query.
     * @param filter    The filter to use for this query
     * @param retries   The number of times the function has already been called
     * @return          A list of objects matching the query, of the given class type
     */
    public FindIterable<Document> queryDocuments(Document filter, Class dClass, int retries){
        try {
            openDatabase();
            return db.getCollection(dClass.getSimpleName()).find(filter);
        }catch (Exception e){
            logger.warning("Could not query (documents) database: "+e.getMessage());
            e.printStackTrace();
        }finally{
            closeDatabase();
        }
        return null;
    }

    /**
     * Retrieves all objects matching a query, using a specific selection (mostly used for aggregation functions)
     * @param filter        The filter to use
     * @param customSelect  The custom select string
     * @return              A list of documents resulting from the query
     */
    public FindIterable<Document> queryCustomSelect(SelectionFilter filter, String customSelect){
        return queryCustomSelect(filter, customSelect, 0);
    }

    /**
     * Retrieves all objects matching a query, using a specific selection (mostly used for aggregation functions)
     * @param filter        The filter to use
     * @param customSelect  The custom select string
     * @param retries       The number of times the function has already been called
     * @return              A list of documents resulting from the query
     */
    public FindIterable<Document> queryCustomSelect(SelectionFilter filter, String customSelect, int retries){
        logger.warning("queryCustomSelect no longer supported");
        return null;
    }

    /**
     * Create a new index with default type for the given class and property
     * @param className     The name of the class to add the index for
     * @param property      The property to add the index for
     */
    public void createIndex(String className, String property){
        createIndex(className, property, "notunique");
    }

    /**
     * Create a new index with specified type for the given class and property
     * @param className     The name of the class to add the index for
     * @param property      The property to add the index for
     * @param type          The type of index to create
     */
    public void createIndex(String className, String property, String type){
        try {
            openDatabase();
            // TODO Create index
        }catch (Exception e){
            logger.warning("Could not create index: "+e.getMessage());
            e.printStackTrace();
        }finally {
            closeDatabase();
        }
    }

    /**
     * Saves an object to the database. The object must be an instance, retrieved from the database, or created through
     * the method newEntityInstance.
     * @param dbObject  The object to save to the database
     * @return The bound object if successful or null if not successful
     */
    public <T extends Model> T save(T dbObject){
        if(dbObject == null || dbObject.getDocument() == null){
            logger.warning("Trying to store null!");
            return null;
        }

        dbObject.setUpdateDate(new Date());

        return save(dbObject, 0);
    }

    /**
     * Saves an object to the database. The object must be an instance, retrieved from the database, or created through
     * the method newEntityInstance.
     * @param dbObject  The object to save to the database
     * @param retries   The number of times the function has already been called
     * @return The bound object if successful or null if not successful
     */
    private <T extends Model> T save(T dbObject, int retries){
        Document result = null;

        if(dbObject == null || dbObject.getDocument() == null){
            logger.warning("Trying to store null!");
            return null;
        }

        if(!dbObject.validate(false)){
            // TODO: Handle validation error
        }

        if(dbObject.getObjectId() != null){
            T oldObject = null;
            try {
                oldObject = get(dbObject.getObjectId(), (Class<T>) dbObject.getClass());
            } catch (Exception e){
                logger.info("Could not get oldObject for comparison");
            }
            if(oldObject != null){
                dbObject.updateBeforeSave(oldObject);
            } else {
                dbObject.updateBeforeSave(null);
            }
        } else {
            dbObject.updateBeforeSave(null);
        }

        try {
            openDatabase();
            if(dbObject.getObjectId() != null){
                db.getCollection(dbObject.getClassName()).replaceOne(Filters.eq("_id", dbObject.getObjectId()), dbObject.getDocument());
            } else {
                db.getCollection(dbObject.getClassName()).insertOne(dbObject.getDocument());
            }
        }catch (Exception e){
            logger.warning("General exception while saving object ("+dbObject.getClass().getSimpleName()+") to database: "+e.getMessage());
            try {
                logger.warning("JSON: " + dbObject.getDocument().toJson());
            } catch (Exception e1){
                if(dbObject.getObjectId() != null) {
                    logger.info("ObjectId: " + dbObject.getObjectId().toHexString());
                }
            }
            e.printStackTrace();
            if(e.getCause() != null) {
                logger.warning("Caused by:");
                logger.warning(e.getCause().getMessage());
                e.getCause().printStackTrace();
            }
        }finally{
            closeDatabase();
        }

        return dbObject;
    }

    /**
     * Update a single document
     * @param tClass    The class to update (collection name)
     * @param filter    The filter to use to select documents to update
     * @param update    The fields with new values to update
     * @param <T>       tClass needs to extend Model
     * @return          The update count (1 if successful)
     */
    public <T extends Model> long updateOne(Class<T> tClass, Document filter, Document update){
        try {
            openDatabase();
            UpdateResult result = db.getCollection(tClass.getSimpleName()).updateOne(filter, update);
            return result.getModifiedCount();
        } catch (Exception e) {
            logger.info("Error on updateOne: "+e.getMessage());
        } finally {
            closeDatabase();
        }
        return 0L;
    }

    /**
     * Update many documents
     * @param tClass    The class to update (collection name)
     * @param filter    The filter to use to select documents to update
     * @param update    The fields with new values to update
     * @param <T>       tClass needs to extend Model
     * @return          The update count (higher than 0 if successful)
     */
    public <T extends Model> long updateMany(Class<T> tClass, Document filter, Document update){
        try {
            openDatabase();
            UpdateResult result = db.getCollection(tClass.getSimpleName()).updateMany(filter, update);
            return result.getModifiedCount();
        } catch (Exception e) {
            logger.info("Error on updateMany: "+e.getMessage());
        } finally {
            closeDatabase();
        }
        return 0L;
    }

    /**
     * Counts the number of records of the given class
     * @param tClass The class to count
     * @return int The number of records found
     */
    public long count(Class tClass){
        return count(tClass, 0);
    }

    /**
     * Counts the number of records of the given class
     * @param tClass    The class to count
     * @param retries   The number of times the function has already been called
     * @return int The number of records found
     */
    public long count(Class tClass, int retries){
        long result = 0;
        try{
            openDatabase();
            result = db.getCollection(tClass.getSimpleName()).count();
        }catch (Exception e){
            logger.warning("Could not count objects of class " + tClass.getSimpleName() + ", reason: " + e.getMessage());
        }finally{
            closeDatabase();
        }
        return result;
    }

    /**
     * Counts the number of records of the given class
     * @param tClass The class to count
     * @param filter The filter to use on this query count
     * @return int The number of records found
     */
    public long count(Class tClass, Document filter){
        return count(tClass, filter, 0);
    }

    /**
     * Counts the number of records of the given class
     * @param tClass    The class to count
     * @param filter    The filter to use on this query count
     * @param retries   The number of times the function has already been called
     * @return int The number of records found
     */
    public long count(Class tClass, Document filter, int retries){
        long result = 0;
        try{
            openDatabase();
            result = db.getCollection(tClass.getSimpleName()).count(filter);
        }catch (Exception e){
            logger.warning("Could not count objects of class " + tClass.getSimpleName() + ", reason: " + e.getMessage());
            logger.info("Cause: ");
            e.printStackTrace();
            if(e.getCause() != null) e.getCause().printStackTrace();
        }finally{
            closeDatabase();
        }
        return result;
    }

    /**
     * Deletes an object from the database.
     *
     * @param dbObject The object to remove from the database
     */
    public <T extends Model> void delete(T dbObject){
        delete(dbObject, 0);
    }

    /**
     * Deletes an object from the database.
     *
     * @param dbObject  The object to remove from the database
     * @param retries   The number of times the function has already been called
     */
    public <T extends Model> void delete(T dbObject, int retries){
        if(dbObject == null || dbObject.getDocument() == null){
            logger.warning("Trying to delete null document!");
            return;
        }
        try{
            openDatabase();
            db.getCollection(dbObject.getClassName()).deleteOne(dbObject.getDocument());
        }catch (Exception e){
            logger.warning("Could not delete object from database: "+e.getMessage());
            e.printStackTrace();
        }finally{
            closeDatabase();
        }
    }

    /**
     * Deletes documents from database that match given class and filter document
     * @param tClass    The class to delete documents for
     * @param filter    The filter to use to select documents which will be deleted
     * @param <T>       The class type has to be a Model
     */
    public <T extends Model> void delete(Class<T> tClass, Document filter) {
        delete(tClass, filter, 0);
    }

    /**
     * Deletes documents from database that match given class and filter document
     * @param tClass    The class to delete documents for
     * @param filter    The filter to use to select documents which will be deleted
     * @param retries   The number of retries
     * @param <T>       The class type has to be a Model
     */
    public <T extends Model> void delete(Class<T> tClass, Document filter, int retries) {
        if(filter == null) return;
        try{
            openDatabase();
            db.getCollection(tClass.getSimpleName()).deleteMany(filter);
        }catch (Exception e){
            logger.warning("Could not delete object from database (filter "+filter.toJson()+"): "+e.getMessage());
            e.printStackTrace();
        }finally{
            closeDatabase();
        }
    }

    /**
     * Closes the database instance
     */
    public void closeDatabase(){
        closeDatabase(false);
    }

    /**
     * Closes the database instance
     */
    public void closeDatabase(boolean force){
        if(force) {
            try {
                if(db != null){
                    logger.info("Closing current db");
                    db = null;
                }else{
                    db = null;
                }
            } catch (Exception e){
                logger.warning("Error closing database ["+e.getClass().getCanonicalName()+"]: "+e.getMessage());
            }
            db = null;
            transaction = null;
        }
    }

    /**
     * Starts a new transaction
     * @return
     */
    public boolean startTransaction(){
        logger.warning("Transactions not supported");
        return true;
    }

    /**
     * Commits the current transaction
     * @return
     */
    public boolean endTransaction(){
        logger.warning("Transactions not supported");
        return true;
    }

    /**
     * Commits the current transaction
     * @return
     */
    public boolean rollbackTransaction(){
        logger.warning("Transactions not supported");
        return true;
    }

    /**
     * Returns the record for a given user object
     * @param dbObject
     * @return
     */
    @Deprecated
    public ObjectId getObjectId(Model dbObject){
        if(dbObject.getDocument() != null){
            return dbObject.getDocument().getObjectId("_id");
        }
        return null;
    }

    /**
     * Serializes a sinlge object to json
     * @param dbObject Object
     * @return String
     */
    @Deprecated
    public <T extends Model> String toJson(T dbObject){
        if(dbObject == null || dbObject.getDocument() == null) return null;
        String json = dbObject.getDocument().toJson();
        return json;
    }

    /**
     * Serializes a list of objects to json
     * @param dbObjects List<Object>
     * @return String
     */
    @Deprecated
    public <T extends Model> String toJson(Iterable<T> dbObjects){
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for(Model dbObject : dbObjects){
            String jsonString = toJson(dbObject);
            if(jsonString != null && !jsonString.equals("")) {
                if (builder.length() > 1) builder.append(",");
                builder.append(jsonString);
            }else{
                if(dbObject == null){
                    logger.warning("Got empty object in list");
                }else{
                    logger.warning("JSON String was empty for object "+dbObject.getDbId());
                }
            }
        }
        builder.append("]");
        return builder.toString();
    }

    /**
     *
     * @param json String
     * @return Object
     */
    @Deprecated
    public <T extends Model> T fromJson(String json, Class<T> c){
        T object = null;
        try {
            object = c.newInstance();
            object.setDocument(Document.parse(json));
        } catch (Exception e){
            logger.warning("Could not deserialize object from json: "+e.getMessage());
            e.printStackTrace();
        } finally {
            closeDatabase();
        }
        return object;
    }

}
