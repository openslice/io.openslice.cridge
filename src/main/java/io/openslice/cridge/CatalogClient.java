package io.openslice.cridge;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.openslice.model.DeploymentDescriptor;
import io.openslice.tmf.rcm634.model.LogicalResourceSpecification;
import io.openslice.tmf.rcm634.model.ResourceSpecification;
import io.openslice.tmf.rcm634.model.ResourceSpecificationCreate;
import io.openslice.tmf.ri639.model.LogicalResource;
import io.openslice.tmf.ri639.model.Resource;
import io.openslice.tmf.ri639.model.ResourceCreate;
import io.openslice.tmf.ri639.model.ResourceUpdate;

/**
 * @author ctranoris
 */
@Service
public class CatalogClient  extends RouteBuilder{


	private static final Logger logger = LoggerFactory.getLogger( CatalogClient.class.getSimpleName());

    @Autowired
    private ProducerTemplate template;

	@Value("${CATALOG_ADD_RESOURCESPEC}")
	private String CATALOG_ADD_RESOURCESPEC = "";

	@Value("${CATALOG_UPD_RESOURCESPEC}")
	private String CATALOG_UPD_RESOURCESPEC = "";
	
	@Value("${CATALOG_GET_RESOURCESPEC_BY_ID}")
	private String CATALOG_GET_RESOURCESPEC_BY_ID = "";

	@Value("${CATALOG_GET_RESOURCESPEC_BY_ΝAME_CATEGORY}")
	private String CATALOG_GET_RESOURCESPEC_BY_ΝAME_CATEGORY = "";


	@Value("${CATALOG_UPDADD_RESOURCESPEC}")
	private String CATALOG_UPDADD_RESOURCESPEC = "";
	
	
	@Value("${CATALOG_UPDADD_RESOURCE}")
	private String CATALOG_UPDADD_RESOURCE = "";
	

    @Value("${CATALOG_UPD_RESOURCE}")
    private String CATALOG_UPD_RESOURCE = "";

	@Value("${CATALOG_GET_RESOURCE_BY_ID}")
	private String CATALOG_GET_RESOURCE_BY_ID = "";
	

	@Override
	public void configure() throws Exception {


		
	
	}
	
	
	/**
	 * get  service spec by id from model via bus
	 * @param id
	 * @return
	 * @throws IOException
	 */
	public ResourceSpecification retrieveResourceSpecByNameCategoryVersion(String aName, String aCategory, String aVersion) {
		logger.info("will retrieve Resource Specification aName=" + aName   );
		
		try {
			Map<String, Object> map = new HashMap<>();
			map.put( "aname", aName);
			map.put( "acategory", aCategory);
			map.put( "aversion", aVersion);
			Object response = 
					template.requestBodyAndHeaders( CATALOG_GET_RESOURCESPEC_BY_ΝAME_CATEGORY, null, map);

			if ( !(response instanceof String)) {
				logger.error("Resource Specification object is wrong.");
				return null;
			}
			LogicalResourceSpecification sor = toJsonObj( (String)response, LogicalResourceSpecification.class); 
			//logger.debug("retrieveSpec response is: " + response);
			return sor;
			
		}catch (Exception e) {
			logger.error("Cannot retrieve Resource Specification details from catalog. " + e.toString());
		}
		return null;
	}
	
	/**
	 * get  service spec by id from model via bus
	 * @param id
	 * @return
	 * @throws IOException
	 */
	public ResourceSpecification retrieveResourceSpec(String specid) {
		logger.info("will retrieve Resource Specification id=" + specid   );
		
		try {
			Object response = template.
					requestBody( CATALOG_GET_RESOURCESPEC_BY_ID, specid);

			if ( !(response instanceof String)) {
				logger.error("Resource Specification object is wrong.");
				return null;
			}
			LogicalResourceSpecification sor = toJsonObj( (String)response, LogicalResourceSpecification.class); 
			//logger.debug("retrieveSpec response is: " + response);
			return sor;
			
		}catch (Exception e) {
			logger.error("Cannot retrieve Resource Specification details from catalog. " + e.toString());
		}
		return null;
	}
	

	public LogicalResourceSpecification createOrUpdateResourceSpecByNameCategoryVersion( ResourceSpecificationCreate s) {
		logger.info("will createOrUpdateResourceSpecByNameCategoryVersion "  );
		try {
			Map<String, Object> map = new HashMap<>();
			map.put("aname", s.getName());
			map.put("aversion", s.getVersion());
			map.put("acategory", s.getCategory());
			
			Object response = template.requestBodyAndHeaders( CATALOG_UPDADD_RESOURCESPEC, toJsonString(s), map);

			if ( !(response instanceof String)) {
				logger.error("ResourceSpecification  object is wrong.");
			}

			LogicalResourceSpecification rs = toJsonObj( (String)response, LogicalResourceSpecification.class); 
			return rs;
			
			
		}catch (Exception e) {
			logger.error("Cannot create ResourceSpecification");
			e.printStackTrace();
		}
		return null;
		
	}
	
	public Resource createOrUpdateResourceByNameCategoryVersion( ResourceCreate s) {
		logger.info("will createOrUpdateResourceByNameVersion a Resource "  );
		try {
			Map<String, Object> map = new HashMap<>();
			map.put("aname", s.getName());
			map.put("aversion", s.getResourceVersion());
			map.put("acategory", s.getCategory());
			
			Object response = template.requestBodyAndHeaders( CATALOG_UPDADD_RESOURCE, toJsonString(s), map);

			if ( !(response instanceof String)) {
				logger.error("Resource  object is wrong.");
			}

			logger.debug( response.toString() );
			try {
				LogicalResource rs = toJsonObj( (String)response, LogicalResource.class); 
				return rs;				
			}catch (Exception e) {
				logger.error("Cannot create LogicalResource");
				e.printStackTrace();
			}
			
			try {
				Resource rs = toJsonObj( (String)response, Resource.class); 
				return rs;				
			}catch (Exception e) {
				logger.error("Cannot create as Resource");
				e.printStackTrace();
			}
			
			
		}catch (Exception e) {
			logger.error("Cannot create Resource");
			e.printStackTrace();
		}
		return null;
		
	}
	

	
	static <T> T toJsonObj(String content, Class<T> valueType)  throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper.readValue( content, valueType);
    }

	 static String toJsonString(Object object) throws IOException {
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
	        return mapper.writeValueAsString(object);
	    }


  public Resource updateResourceById(String oslResourceId, ResourceUpdate rs) {

    
    logger.info("will update Resource : " + oslResourceId );
    try {
        Map<String, Object> map = new HashMap<>();
        map.put("resourceId", oslResourceId );
        map.put("propagateToSO", false );
        
        Object response = template.requestBodyAndHeaders( CATALOG_UPD_RESOURCE, toJsonString(rs), map);

        if ( !(response instanceof String)) {
            logger.error("Service Instance object is wrong.");
        }

        LogicalResource resourceInstance = toJsonObj( (String)response, LogicalResource.class); 
        //logger.debug("createService response is: " + response);
        return resourceInstance;
        
        
    }catch (Exception e) {
        logger.error("Cannot update Service: " + oslResourceId + ": " + e.toString());
    }
    return null;
  }

}
