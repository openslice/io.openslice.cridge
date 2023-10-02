package io.openslice.cridge;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CRRouteBuilder  extends RouteBuilder{

	@Value("${CRD_DEPLOY_CR_REQ}")
	private String CRD_DEPLOY_CR_REQ = "";

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
		
	}

}
