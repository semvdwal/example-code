package mbp.common.controllers;

import com.wwk.meubelplan.common.logger.Logger;
import com.wwk.meubelplan.common.mongo.db.Database;
import com.wwk.meubelplan.common.mongo.db.DatabaseResult;
import com.wwk.meubelplan.common.mongo.db.Model;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

/**
 * Created by sem on 08-10-16.
 */
public abstract class Controller<T extends Model> {

    protected final Class<T> type;
    protected final Logger logger = Logger.getLogger(Controller.class.getSimpleName());

    public Controller(Class<T> type){
        this.type = type;
    }

    public DatabaseResult<T> find(){
        return Database.getInstance().getALL(type);
    }

    public DatabaseResult<T> find(Document filter){
        return Database.getInstance().query(type, filter);
    }

    public T findOne(Document filter){
        long count = Database.getInstance().count(type, filter);
        if(count > 0) {
            DatabaseResult<T> result = find(filter).limit(1);
            return result.first();
        }
        return null;
    }

    public T findOne(String id){
        return get(id);
    }

    public T get(String id){
        try {
            return Database.getInstance().get(id, type);
        } catch (ClassCastException e){
            return null;
        }
    }

    public T get(ObjectId id){
        try {
            return Database.getInstance().get(id, type);
        } catch (ClassCastException e){
            return null;
        }
    }

    public List<T> cleanAll(List<T> models){
        return models;
    }

    public T clean(T model){
        if (model == null) return model;
        if(model.getObjectId() == null) model.setObjectId(new ObjectId());
        model.setId(model.getObjectId().toHexString());
        return model;
    }

    public T importSave(T model){
        return save(model);
    }

    public T save(T model){
        T existingModel = findOne(model.getUniqueFilter());

        if(existingModel != null) {
            existingModel.update(model);
            return Database.getInstance().save(existingModel);
        }
        return Database.getInstance().save(model);
    }

    public void remove(T model){
        if(model != null){
            Database.getInstance().delete(model);
        }
    }

    public void remove(Document filter) {
        if(filter != null) {
            Database.getInstance().delete(type, filter);
        }
    }

    public long count(){
        return Database.getInstance().count(type);
    }

    public long count(Document filter){
        return Database.getInstance().count(type, filter);
    }

    public String toJson(T model){
        model = clean(model);
        return model.getDocument().toJson();
    }

    public String toJson(Iterable<T> models){
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for(T model : models){
            String jsonString = toJson(model);
            if(jsonString != null && !jsonString.equals("")) {
                if (builder.length() > 1) builder.append(",");
                builder.append(jsonString);
            }else{
                if(model == null){
                    logger.warning("Got empty object in list");
                }else{
                    logger.warning("JSON String was empty for object "+model.getObjectId().toHexString());
                }
            }
        }
        builder.append("]");
        return builder.toString();
    }

    public T fromJson(String json){
        T object = null;
        try {
            object = type.newInstance();
            object.setDocument(Document.parse(json));
        } catch (Exception e){
            logger.warning("Could not deserialize object from json: "+e.getMessage());
            e.printStackTrace();
        }
        return object;
    }

}
