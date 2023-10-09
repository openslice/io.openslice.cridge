package io.openslice.cridge;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.openslice.tmf.rcm634.model.ResourceSpecification;
import lombok.Getter;

@Service
@Getter
public class CRRouteBuilder  extends RouteBuilder{

	@Value("${CRD_DEPLOY_CR_REQ}")
	private String CRD_DEPLOY_CR_REQ = "";


    @Value("${CRD_PATCH_CR_REQ}")
    private String CRD_PATCH_CR_REQ = "";
    
    @Value("${CRD_DELETE_CR_REQ}")
    private String CRD_DELETE_CR_REQ = "";
	

	@Autowired
	private KubernetesClientResource kubernetesClientResource;
	
	
	private static final Logger logger = LoggerFactory.getLogger( CRRouteBuilder.class.getSimpleName());

	@Override
	public void configure() throws Exception {
		
		from( CRD_DEPLOY_CR_REQ )
		.log(LoggingLevel.INFO, log, CRD_DEPLOY_CR_REQ + " message received!")
		.to("log:DEBUG?showBody=true&showHeaders=true")
		.bean( kubernetesClientResource, "deployCR(${headers}, ${body})")
		.convertBodyTo( String.class );
		
		from( CRD_DELETE_CR_REQ )
        .log(LoggingLevel.INFO, log, CRD_DELETE_CR_REQ + " message received!")
        .to("log:DEBUG?showBody=true&showHeaders=true")
        .bean( kubernetesClientResource, "deleteCR(${headers}, ${body})")
        .convertBodyTo( String.class );
        
        from( CRD_PATCH_CR_REQ )
        .log(LoggingLevel.INFO, log, CRD_PATCH_CR_REQ + " message received!")
        .to("log:DEBUG?showBody=true&showHeaders=true")
        .bean( kubernetesClientResource, "patchCR(${headers}, ${body})")
        .convertBodyTo( String.class );
        
        
      from( "timer://processUpdateResources?period=60000" )
      .log(LoggingLevel.INFO, log, " process nameSpacesTobeDeleted!")
      .to("log:DEBUG?showBody=true&showHeaders=true")
      .bean( CRRouteBuilder.class , "processNameSpacesTobeDeleted()");
		
	}
	
	 public void processNameSpacesTobeDeleted() {
	   
	   kubernetesClientResource.getNameSpacesTobeDeleted().forEach( (nameSpaceName, nameSpaceId) -> {
	     
	     try (final KubernetesClient k8s = new KubernetesClientBuilder().build()) {
	       logger.info("Trying to delete namespace {}", nameSpaceName);
	       Namespace ns = new NamespaceBuilder()
	            .withNewMetadata()
	            .withName( nameSpaceName )
	            .endMetadata().build();
	        k8s.namespaces().resource(ns).delete();     
	        
	        kubernetesClientResource.getNameSpacesTobeDeleted().remove(nameSpaceName);
	     }catch (Exception e) {
	        e.printStackTrace();
	      }
	    });
	   
	 }

}
