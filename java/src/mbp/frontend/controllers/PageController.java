package mbp.frontend.controllers;

import com.wwk.meubelplan.common.config.ServerConfig;
import com.wwk.meubelplan.common.controllers.CompanyController;
import com.wwk.meubelplan.common.logger.Logger;
import com.wwk.meubelplan.common.model.Company;
import com.wwk.meubelplan.common.model.Product;
import com.wwk.meubelplan.common.mongo.model.Page;
import com.wwk.meubelplan.frontend.exceptions.ResourceNotFoundException;
import com.wwk.meubelplan.frontend.helpers.CachedDatabaseResultManager;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.thymeleaf.spring4.SpringTemplateEngine;

import java.io.File;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Created by sem on 23-05-15.
 */
@Controller
@RequestMapping("/")
public class PageController extends ProductEnabledController {

    private static final Logger logger = Logger.getLogger(PageController.class.getSimpleName());
    private com.wwk.meubelplan.common.mongo.controllers.CompanyController companyController = new com.wwk.meubelplan.common.mongo.controllers.CompanyController();
    private com.wwk.meubelplan.common.mongo.controllers.ProductController productController = new com.wwk.meubelplan.common.mongo.controllers.ProductController();
    private com.wwk.meubelplan.common.mongo.controllers.PageController pageController = new com.wwk.meubelplan.common.mongo.controllers.PageController();

    @Autowired
    SpringTemplateEngine springTemplateEngine;

    @Autowired
    CachedDatabaseResultManager cachedDatabaseResultManager;

    @RequestMapping(method = RequestMethod.GET)
    public String indexPage(ModelMap model) {

        if(ServerConfig.useMongoDatabase()){
            Iterable<com.wwk.meubelplan.common.mongo.model.Product> featuredProducts = null;

            if(ServerConfig.useCache()) {
                logger.info("Using cache");
                featuredProducts = cachedDatabaseResultManager.getCachedResult(
                        com.wwk.meubelplan.common.mongo.model.Product.class,
                        new Document("visible", true).append("$or", asList( new Document("supid", "Hulsta_80"), new Document("supid", "Hulsta_16"), new Document("supid", "Vroom_02"), new Document("supid", "6000"))),
                        -1,
                        -1,
                        null);
                logger.info("Got cache");
            } else {
                featuredProducts = productController.find(new Document("visible", true).append("$or", asList( new Document("supid", "Hulsta_80"), new Document("supid", "Hulsta_16"), new Document("supid", "Vroom_02"), new Document("supid", "6000") )));
            }

            model.addAttribute("featuredProducts", featuredProducts);

            model.addAttribute("urlFormat", 3);
            model.addAttribute("department", "meubels");
        } else {
            SelectionFilter filter = new SelectionFilter(Company.class).where().equal("visible", "true").and().equal("oType", String.valueOf(Company.COMPANY_TYPE_SUPPLIER));
            filter.setFetchPlan("*:1");
            List<Company> suppliers = CompanyController.find(filter);

            filter = new SelectionFilter(Company.class).where().equal("visible", "true").and().equal("oType", String.valueOf(Company.COMPANY_TYPE_DEALER));
            filter.setFetchPlan("*:1");
            List<Company> dealers = CompanyController.find(filter);

            List<Product> featuredProducts = com.wwk.meubelplan.common.controllers.ProductController.find(
                    new SelectionFilter(Product.class).where().equal("visible", "true").and().groupStart().equal("@rid", "#14:609").or().equal("@rid", "#14:1606").or().equal("@rid", "#14:141").or().equal("@rid", "#14:1523").groupEnd().limit(4).setFetchPlan("*:1")
            );

            model.addAttribute("suppliers", suppliers);
            model.addAttribute("dealers", dealers);
            model.addAttribute("featuredProducts", featuredProducts);

            model.addAttribute("urlFormat", 3);
            model.addAttribute("department", "meubels");
        }

        return "index";
    }

    @RequestMapping(value="{pageName}", method = RequestMethod.GET)
    public String generalPage(@PathVariable String pageName, ModelMap model) {
        String location = "/var/www/meubelplan/pages/" + pageName + ".html";
        logger.info("Got page request for page: "+pageName);
        logger.info("Checking if file exists: "+location);

        model.addAttribute("department", "page");

        if(pageName.equals("partner")){
            model.addAttribute("videojs", true);
        }

        File file = new File(location);
        if (!file.exists()) {
            logger.info("File not found");
            throw new ResourceNotFoundException();
        }

        Page page = pageController.findOne(new Document("name", pageName));
        model.addAttribute("page", page);

        if(page != null) {
            if(!page.getVisible()) {
                logger.info("File not found");
                throw new ResourceNotFoundException();
            }

            if(page.getShowProducts()){
                if(page.getFilterValues() != null) {

                    if(model.get("environment") == null && page.getFilterValues().containsKey("environment")) {
                        model.addAttribute("environment", page.getFilterValues().get("environment"));
                    }

                    if(model.get("design") == null && page.getFilterValues().containsKey("design")) {
                        model.addAttribute("design", page.getFilterValues().get("design"));
                    }

                    if(model.get("materialtype") == null && page.getFilterValues().containsKey("materialtype")) {
                        model.addAttribute("materialtype", page.getFilterValues().get("materialtype"));
                    }

                    if(model.get("colortype") == null && page.getFilterValues().containsKey("colortype")) {
                        model.addAttribute("colortype", page.getFilterValues().get("colortype"));
                    }

                    return handleRequest(REQUEST_TYPE_LIST, page.getFilterValues().get("mainGroup"), page.getFilterValues().get("group"), null, null, model);
                }
                return handleRequest(REQUEST_TYPE_LIST, null, null, null, null, model);
            }
        }

        return "page";

    }

}
