package mbp.common.controllers;

import mbp.common.db.Database;
import mbp.common.db.DatabaseResult;
import mbp.common.model.Company;
import mbp.common.model.Product;
import org.bson.Document;

import java.util.ArrayList;
import java.util.logging.Logger;

public class ProductController extends Controller<Product> {

    private static final Logger logger = Logger.getLogger(ProductController.class.getName());
    private CompanyController companyController = new CompanyController();

    public ProductController(){
        super(Product.class);
    }

    public Product findOneById(String id){
        DatabaseResult<Product> result = Database.getInstance().query(Product.class, new Document("id", id)).limit(1);
        return result.iterator().next();
    }

    public Product findOneBySupplierId(String id){
        logger.warning("Searching for supid without supplier id!");
        return findOneBySupplierId(id, null);
    }

    public Product findOneBySupplierId(String id, String companyId){
        Company company = companyController.get(companyId);
        if(company == null) {
            logger.info("Could not get company for id " + companyId);
            return null;
        }
        Document filter = new Document("supid", id).append("supplier", company.getObjectId());
//        logger.info("Finding product by supid, using filter: " + filter.toJson());
        Product product = findOne(filter);
        if(product != null){
            return product;
        }else{
            return findOneById(id);
        }
    }

    public Product get(String id){
        try {
            Product product = Database.getInstance().get(id, Product.class);
            if(product == null) {
                return findOneBySupplierId(id);
            }
            return product;
        } catch (ClassCastException e){
            return findOneBySupplierId(id);
        }
    }

    @Override
    public Product save(Product product) {
        if(product == null) return null;
        if(product.getName() == null || product.getName().equals("")){
            if(product.getTitle() != null) {
                product.setName(
                    product.getTitle().toLowerCase().replaceAll("[^a-z0-9/ ]", "").replaceAll(" ", "_")
                );
            }
        }
        return super.save(product);
    }

    public Product importSave(Product product){
        return save(product);
    }

    public Document getBasicFilterForCompany(Company company){

        if(company!=null) {
            if (company.getOType() == Company.COMPANY_TYPE_SUPPLIER) {
                return new Document("supplier", company.getObjectId());
            } else {
                return new Document("dealers", company.getObjectId());
            }
        }

        return null;

    }

    @Override
    public String toJson(Product model) {
        // Setting list values needed for correct initialization in backend js
        if(model.getMainGroup() == null) model.setMainGroup(new ArrayList<>(0));
        if(model.getGroup() == null) model.setGroup(new ArrayList<>(0));
        if(model.getEnvironment() == null) model.setEnvironment(new ArrayList<>(0));
        if(model.getDesign() == null) model.setDesign(new ArrayList<>(0));
        if(model.getProperties() == null) model.setProperties(new ArrayList<>(0));
        if(model.getMatchingProducts() == null) model.setMatchingProducts(new ArrayList<>(0));
        if(model.getMaterialtype() == null) model.setMaterialtype(new ArrayList<>(0));
        if(model.getColortype() == null) model.setColortype(new ArrayList<>(0));

        return super.toJson(model);
    }
}