package mbp.common.db;

import com.wwk.meubelplan.common.logger.Logger;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by sem on 12-03-15.
 */
public abstract class Model<M extends Model> implements Serializable {

    private static final Logger mLogger = Logger.getLogger(Model.class.getSimpleName());
    protected Document document;
    protected String className;

    public Model(){
        String className = this.getClass().getSimpleName();
        this.className = className;
        document = new Document();
        initialize();
    }

    public Model(String className){
        this.className = className;
        document = new Document();
        initialize();
    }

    public Model(Document document){
        String className = this.getClass().getSimpleName();
        this.className = className;
        setDocument(document);
    }

    public String getClassName(){
        return className;
    }

    public Document getDocument(){
        return document;
    }

    public void setDocument(Document document){
        this.document = document;
    }

    public void generateId(){
        if(document.get("id")==null) {
            setId(UUID.randomUUID().toString());
        }
    }

    public String getId() {
        return getStringValue("id");
    }

    public void setId(String id) {
        setValue("id", id);
    }

    public String getDbId() {
        return getRecordId();
    }

    public void setDbId(String dbId) {
    }

    public String getRecordId(){
        if(document != null) {
            ObjectId objectId = (ObjectId) document.get("_id");
            if(objectId != null) {
                return objectId.toHexString();
            }
        }
        return null;
    }

    public ObjectId getObjectId(){
        if(document != null){
            return document.getObjectId("_id");
        }
        return null;
    }

    public void setObjectId(ObjectId objectId){
        if(document != null && objectId != null){
            document.append("_id", objectId);
        }
    }

    public Date getCreationDate(){
        return getDateValue("creationDate");
    }

    public void setCreationDate(Date date){
        setDateValue("creationDate", date);
    }

    public Date getUpdateDate(){
        return getDateValue("updateDate");
    }

    public void setUpdateDate(Date date){
        setDateValue("updateDate", date);
    }

    public boolean setValue(String name, Object value) {
        try {
            if(value == null) {
                document.remove(name);
            } else {
                document.append(name, value);
            }
            return true;
        } catch (Exception e){
            if(document != null) {
                mLogger.warning("Failed to set document value ("+name+"="+value.toString()+")");
            }
            return false;
        }
    }

    public boolean setStringValue(String name, String value) {
        return setStringValue(name, value, true);
    }

    public boolean setStringValue(String name, String value, Boolean checkLinks) {
        if(value != null) {
            if (checkLinks && !name.equals("website") && !name.equals("email")) {
                value = value.replaceAll("(http(s)?://.)?(www\\.)?[-a-zA-Z0-9@:%._+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_+.~#?&//=]*)", "!!LINKS NIET TOEGESTAAN!!");
            }
            return setValue(name, value);
        } return true;
    }

    public boolean setBooleanValue(String name, Boolean value) {
        return setValue(name, value);
    }

    public boolean setIntegerValue(String name, Integer value) {
        return setValue(name, value);
    }

    public boolean setLongValue(String name, Long value){
        return setValue(name, value);
    }

    public boolean setDoubleValue(String name, Double value){
        return setValue(name, value);
    }

    public boolean setListValue(String name, List list){
        return setValue(name, list);
    }

    public boolean setMapValue(String name, Map map){
        return setValue(name, map);
    }

    public boolean setDateValue(String name, Date date){
        return setValue(name, date);
    }

    public Object getValue(String name){
        try {
            return document.get(name);
        } catch (Exception e){
            mLogger.warning("Could not get value: "+name);
            return null;
        }
    }

    public String getStringValue(String name){
        try {
            return document.getString(name);
        } catch (Exception e){
            mLogger.warning("Could not get string value: "+name);
            return null;
        }
    }

    public List getListValue(String name){
        try {
            Object obj = document.get(name);
            if(obj instanceof List){
                return (List) obj;
            } else {
//                mLogger.warning("Could not get list value: "+name+" found type is not an instance of List");
            }
        } catch (Exception e){
            mLogger.warning("Could not get list value: "+name);
        }
        return null;
    }

    public Map getMapValue(String name){
        try {
            Object map = document.get(name);
            if(map instanceof Map){
                return (Map) map;
            } else {
                if(map != null) {
                    mLogger.warning("Could not get map value: " + name + " found type is not an instance of Map");
                }
            }
        } catch (Exception e){
            mLogger.warning("Could not get map value: "+name);
        }
        return null;
    }

    public Document getEmbeddedDocument(String name) {
        try {
            return document.get(name, Document.class);
        } catch (Exception e) {
            mLogger.warning("Could not get embedded document: "+name);
        }
        return null;
    }

    public Boolean getBooleanValue(String name){
        try {
            Boolean b = document.getBoolean(name);
            if(b != null) return b;
        } catch (Exception e){
            mLogger.warning("Could not get boolean value: "+name);
        }
        return false;
    }

    public Integer getIntegerValue(String name){
        try {
            Integer i = document.getInteger(name);
            if(i != null) return i;
        } catch (Exception e){
            mLogger.warning("Could not get integer value: "+name);
            try {
                Double d = document.getDouble(name);
                if (d != null) return (int) Math.round(d);
            } catch (Exception d) {
                mLogger.warning("Could not get integer/double value: "+name);
            }
        }
        return null;
    }

    public Long getLongValue(String name){
        Long l = null;
        try {
            l = document.getLong(name);
            if(l != null) return l;
        }catch (Exception e){
            mLogger.warning("Could not get long value: "+name);
        }

        Integer i = getIntegerValue(name);
        if(i != null) l = i.longValue();

        return l;
    }

    public Double getDoubleValue(String name){
        Double d = null;
        try {
            d = document.getDouble(name);
            if(d != null) return d;
        }catch (Exception e){
            mLogger.warning("Could not get double value: "+name);
        }

        Integer i = getIntegerValue(name);
        if(i != null) d = i.doubleValue();

        return d;
    }

    public Date getDateValue(String name){
        try {
            return document.getDate(name);
        }catch (Exception e){
            mLogger.warning("Could not get date value: "+name);
            return null;
        }
    }

    public Object getExportValue(String name){
        try{
            Object result = document.get(name);

            if(result instanceof List) {
                List list = (List) result;
                if (list.size() > 0 && list.get(0) instanceof String) {
                    StringBuilder builder = new StringBuilder();
                    for (Object value : list) {
                        String string = (String) value;
                        if (builder.length() > 0) builder.append(";");
                        builder.append(string);
                    }
                    return builder.toString();
                } else {
                    return "";
                }
            } else if(result instanceof Boolean) {
                if ((Boolean) result) {
                    return "ja";
                } else {
                    return "nee";
                }
            } else if(result instanceof Date) {
                SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy");
                return format.format(result);
            } else {
                return result;
            }
        } catch (Exception e){
            mLogger.warning("Could not get exportValue for "+name);
            return "";
        }
    }

    public <T extends Model> T getLink(String name, Class<T> c){
        try {
            ObjectId id = (ObjectId) document.get(name);
            return Database.getInstance().get(id, c);
        } catch (Exception e){
            mLogger.warning("Got exception " + e.getClass().getName() + " while getting link (" + name + ") from " + className + " (" + getDbId() + "): " + e.getMessage());
        }
        return null;
    }

    public <T extends Model> List<T> getLinks(String name, Class<T> c){
        List<T> result = null;
        try {
            List<Object> links = (List) document.get(name);
            if(links != null){
                if(links.size() > 0) {
                    Database db = Database.getInstance();
                    result = new ArrayList<>(links.size());
                    for(Object link : links){
                        if(link instanceof ObjectId){
                            result.add(db.get((ObjectId) link, c));
                        } else {
                            result.add(db.get(new ObjectId(link.toString()), c));
                        }
                    }
                    return result;
                }else{
                    return new ArrayList<>();
                }
            }
        } catch (Exception e){
            mLogger.warning("Got exception "+e.getClass().getName()+" while getting linklist ("+name+") from "+className+" ("+getDbId()+"): "+e.getMessage());
        }
        return null;
    }

    public <T extends Model> void setLink(String name, T model){
        if(model != null && model.getDocument() != null) {
            document.append(name, model.getObjectId());
        } else {
            document.remove(name);
        }
    }

    public <T extends Model> void setLinks(String name, List<T> models){
        if(models != null) {
            List<ObjectId> links = models.parallelStream().map(T::getObjectId).collect(Collectors.toList());
            document.append(name, links);
        }
    }

    public void addListValue(String listName, Object value){
        List list = getListValue(listName);
        try {
            if(!list.contains(value)) {
                list.add(value);
                setListValue(listName, list);
            }
        } catch (Exception e){
            mLogger.info("Could not add list value of type "+value.getClass().getSimpleName()+" to list with name "+listName);
        }
    }

    public void removeListValue(String listName, Object value){
        List list = getListValue(listName);
        try {
            if(list.contains(value)){
                list.remove(value);
                setListValue(listName, list);
            }
        } catch (Exception e){
            mLogger.info("Could not remove list value from list with name "+listName);
        }
    }

    public void putMapEntry(String mapName, Object key, Object value){
        Map map = getMapValue(mapName);
        try {
            map.put(key, value);
            setMapValue(mapName, map);
        } catch (Exception e){
            mLogger.info("Could not add entry to "+mapName);
        }
    }

    public void removeMapEntry(String mapName, Object key){
        Map map = getMapValue(mapName);
        try {
            if(map.containsKey(key)){
                map.remove(key);
            }
        } catch (Exception e) {
            mLogger.info("Could not remove entry from map "+mapName);
        }
    }

    public <T extends Model> List<T> getEmbeddedList(String listName, Class<T> type) {
        List<Document> documents = getListValue(listName);
        if(documents == null || documents.size() < 1) return new ArrayList<>(0);

        List<T> models = new ArrayList<>(documents.size());
        for(Document document :documents) {
            try {
                T model = type.newInstance();
                model.setDocument(document);
                models.add(model);
            } catch (InstantiationException | IllegalAccessException e) {
                mLogger.warning("Model::getEmbeddedList: Could not instantiate class "+type.getSimpleName());
            }
        }

        return models;
    }

    public <T extends Model> void setEmbeddedList(String listName, List<T> models) {
        if(models == null || models.size() < 1) return;

        List<Document> documents = new ArrayList<>(models.size());
        for(Model model : models) {
            documents.add(model.getDocument());
        }

        setListValue(listName, documents);
    }

    public void setEmbeddedDocument(String name, Document doc) {
        document.put(name, doc);
    }

    public void removeField(String name){
        if(document.get(name) != null) {
            document.remove(name);
        }
    }

    public void duplicate(Model model){
        duplicate(model, false);
    }

    public void duplicate(Model model, boolean copyId){
        for(String fieldName : model.getDocument().keySet()) {
            if(fieldName != null && (!fieldName.equals("_id") || copyId)) setValue(fieldName, model.getValue(fieldName));
        }
    }

    public void update(Model model){
        List<String> changedFields = model.getListValue("changedFields");
        if(changedFields == null){
            duplicate(model);
            return;
        }
        for(String fieldName : changedFields){
            setValue(fieldName, model.getValue(fieldName));
        }
    }

    public Boolean hasDocument(){
        return document != null;
    }

    public Map<String, Class> getLinkLists(){
        return null;
    }

    protected List<String> getStringList(String string){
        if(string != null && !string.equals("")) {
            return Arrays.asList(string.split(";"));
        }else{
            return new ArrayList<>(0);
        }
    }

    public boolean hasValue(String name){
        return document.get(name) != null;
    }

    public Boolean validate(Boolean checkDuplicate) {
        /*
        Map<String, Class> linkLists = getLinkLists();
        if(linkLists != null){
            for(Map.Entry<String, Class> entry : linkLists.entrySet()){
                String property = entry.getKey();
                Class propertyClass = entry.getValue();
                List<Model> links = getLinks(property, propertyClass);
                if(links != null) {
                    mLogger.info("Checking property " + property);
                    Boolean removed = links.removeIf(model -> !model.hasDocument());
                    if (removed) {
                        mLogger.info("Did remove null values");
                        setLinks(property, links);
                    }
                }
            }
        }
        */
        // Not checking validation fields because customers expect to be able to save data intermittently, check for relevant data has been moved to publish action
        return true;
    }

    public void updateBeforeSave(M oldModel){
        if(getDocument() != null && getDocument().containsKey("$$hashKey")) getDocument().remove("$$hashKey");
        if(getDocument() != null && getDocument().containsKey("changedFields")) getDocument().remove("changedFields");
        if(this.getObjectId() != null) {
            setId(this.getObjectId().toHexString());
        }
        if(this.getCreationDate() == null) {
            this.setCreationDate(new Date());
        }
    }

    public void initialize(){
        if(getDocument() != null) {
            setCreationDate(new Date());
        }
    }

    public Document getUniqueFilter(){
        return new Document("_id", getObjectId());
    }

    protected Boolean hasRealValue(String fieldName){
        Object value = document.get(fieldName);
        if(value == null) return false;
        if(value instanceof String) return !"".equals(value);
        if(value instanceof List) return ((List) value).size() > 0;
        if(value instanceof Map) return ((Map) value).size() > 0;
        if(value instanceof Document) return !((Document) value).isEmpty();
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Model) {
            if(this.getObjectId() != null && ((Model) obj).getObjectId() != null) {
                return this.getObjectId().equals(((Model) obj).getObjectId());
            }
        } else if(obj instanceof ObjectId && this.getObjectId() != null) {
            return this.getObjectId().toHexString().equals(((ObjectId) obj).toHexString());
        }
        return super.equals(obj);
    }
}
