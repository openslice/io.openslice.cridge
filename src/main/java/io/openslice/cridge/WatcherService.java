package io.openslice.cridge;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.openslice.domain.model.kubernetes.KubernetesCRDV1;
import io.openslice.domain.model.kubernetes.KubernetesContextDefinition;
import io.openslice.tmf.common.model.EValueType;
import io.openslice.tmf.rcm634.model.ResourceSpecification;
import io.openslice.tmf.rcm634.model.ResourceSpecificationCreate;
import io.openslice.tmf.rcm634.model.ResourceSpecificationRef;
import io.openslice.tmf.ri639.model.Resource;
import io.openslice.tmf.ri639.model.ResourceCreate;
import io.openslice.tmf.ri639.model.ResourceUpdate;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * @author ctranoris
 */
@Service
public class WatcherService {

	private static final Logger logger = LoggerFactory.getLogger("io.openslice.cridge");

	private Watch watch;

	@Autowired
	CatalogClient catalogClient;

	@Autowired
	private KubernetesClientResource kubernetesClientResource;


	@EventListener
	public void onApplicationEvent(ContextRefreshedEvent event) {

		logger.info("Starting WatcherService event {} ", event.toString());
		logger.info("Starting WatcherService for cluster getContexts {} ",
				kubernetesClientResource.getKubernetesClient().getConfiguration().getContexts().toString());
		
		Resource result = registerKubernetesClientInOSLResource();
		while ( result == null ) {
			try {
				logger.info("Cannot get resource for registerKubernetesClient. Retrying in 10 seconds" );
				Thread.sleep( 10000 );
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			result = registerKubernetesClientInOSLResource();
		}

		SharedInformerFactory sharedInformerFactory = kubernetesClientResource.getKubernetesClient().informers();

		SharedIndexInformer<CustomResourceDefinition> podInformer = sharedInformerFactory
				.sharedIndexInformerFor(CustomResourceDefinition.class, 30 * 1000L);

		podInformer.addEventHandler(new ResourceEventHandler<CustomResourceDefinition>() {
			@Override
			public void onAdd(CustomResourceDefinition crd) {
				logger.debug("ADDED {} CRD:{} Group:{} Kind:{} UID:{} ", crd.getKind(), 
						crd.getMetadata().getName(),
						crd.getSpec().getGroup(), 
						crd.getSpec().getNames().getKind(),
						crd.getMetadata().getUid());
				
	
					          
				List<KubernetesCRDV1> rspec = kubernetesClientResource.KubernetesCRD2OpensliceCRD(crd);
				for (KubernetesCRDV1 rs : rspec) {
					  catalogClient.createOrUpdateResourceSpecByNameCategoryVersion(rs.toRSpecCreate());			
					  catalogClient.createOrUpdateResourceByNameCategoryVersion( rs.toResourceCreate() );			
				}
				
		          

				createCRDSharedIndexInformer(crd);

			}

			@Override
			public void onUpdate(CustomResourceDefinition oldcrd, CustomResourceDefinition newcrd) {
				logger.debug("CRD_UPDATED {} {} for kind {}", newcrd.getKind(), newcrd.getSpec().getGroup(), newcrd.getSpec().getNames().getKind());

				String oldLAC = oldcrd.getMetadata().getAnnotations()
						.get("kubectl.kubernetes.io/last-applied-configuration");
				String newLAC = newcrd.getMetadata().getAnnotations()
						.get("kubectl.kubernetes.io/last-applied-configuration");
				if (!newLAC.equals(oldLAC)) {
					logger.debug("\t NEWCRD:{} Group:{} Kind:{} UID:{} ", newcrd.getMetadata().getName(),
							newcrd.getSpec().getGroup(), newcrd.getSpec().getNames().getKind(),
							newcrd.getMetadata().getUid());

					logger.debug("UPDATED Resource Definition of {} ", newcrd.getKind());
					logger.debug("\t Annotations:{} ", newcrd.getMetadata().getAnnotations().toString());
				}

			}

			@Override
			public void onDelete(CustomResourceDefinition crd, boolean deletedFinalStateUnknown) {
				logger.debug("CRD_DELETED {}", crd.getKind());
				logger.debug("\t DELETEDCRD:{} Group:{} Kind:{} UID:{} ", crd.getMetadata().getName(),
						crd.getSpec().getGroup(), crd.getSpec().getNames().getKind(), crd.getMetadata().getUid());
			}
		});

		sharedInformerFactory.startAllRegisteredInformers();

//		watch = kubernetesClient.apiextensions().v1().customResourceDefinitions().watch(new Watcher<>() {
//
//			@Override
//			public void eventReceived(Action action, CustomResourceDefinition crd) {
//
//				
//				//kubernetesClient.namespaces().
//	            		  
//	              logger.debug("{} CRD:{} Group:{} Kind:{} UID:{} ", 
//	            		  action.name(),
//	            		  crd.getMetadata().getName(), 
//	            		  crd.getSpec().getGroup(), 
//	            		  crd.getSpec().getNames().getKind(),
//	            		  crd.getMetadata().getUid());
//	              
//	              crd.getSpec().getVersions().stream().forEach(
//	            		  	version -> {
//
//	            		  		if (version.getSchema().getOpenAPIV3Schema().getProperties()!=null )
//	            		  			version.getSchema().getOpenAPIV3Schema().getProperties().forEach( 
//		            		  			(k,v1) -> {
//		            		  				logger.debug("propName={} propValue={} ",k,v1.getType() );
//		            		  				if (k.equals("spec") || k.equals("status")) {
//		            		  					v1.getProperties().forEach( (pk, pv) -> logger.debug("\t{} {} ", pk, pv.getType() ));		            		  					            		  					
//		            		  				}
//		            		  			});
//	            		  		if (version.getSchema().getOpenAPIV3Schema().getAdditionalProperties()!=null )
//	            		  			version.getSchema().getOpenAPIV3Schema().getAdditionalProperties().getAdditionalProperties().forEach( 
//	                 		  			(kPropName,vProVal) -> {         		  				
//	                 		  				logger.debug("AdditionalProperty propName={} propValue={} ", kPropName ,vProVal  );	         		  						
//	         		  						
//	                 		  			});
//	            		  		}
//	            		  	);
//	              if (  action.name().equals( Action.ADDED.name() ) ) {
//		              createCRDWatcher( crd );	
//		              ResourceSpecificationCreate rspec = (ResourceSpecificationCreate) modelTransformer.CRD2TMFResourceSpecification(crd);
//		              catalogClient.createResourceSpec( rspec );
//	              }	             
//				
//			}
//
//			@Override
//			public void onClose(WatcherException e) {
//	              logger.debug("Closing due to {} ", e.getMessage());
//				
//			}
//          
//          });

	}

	private Resource registerKubernetesClientInOSLResource() {
	
		try {
			ResourceCreate rsc;
			rsc = kubernetesClientResource.getKubernetesContextDefinition().toResourceCreate();
			Resource result = catalogClient.createOrUpdateResourceByNameCategoryVersion(rsc );
			return result;
		} catch (Exception e1) {

			e1.printStackTrace();
		}
		
		return null;
		
		
	}

	protected void createCRDSharedIndexInformer(CustomResourceDefinition crd) {
		
		ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
				.withGroup(crd.getSpec().getGroup())
				.withVersion(crd.getSpec().getVersions().get(0).getName())
				.withKind(crd.getSpec().getNames().getKind())
				.withPlural(crd.getSpec().getNames().getPlural()).build();
		try {
			int a = kubernetesClientResource.getKubernetesClient().genericKubernetesResources(context).list().getItems().size();
		} catch (Exception e) {
			// e.printStackTrace();
			logger.error("Cannot create createCRDSharedIndexInformer. Seems there are no resources for kind: "
					+ crd.getSpec().getNames().getKind());
			return;
		}

		logger.debug("Creating new Watcher for kind: {}", crd.getSpec().getNames().getKind());
		Watch watch = kubernetesClientResource.getKubernetesClient().genericKubernetesResources(context).inAnyNamespace().watch(new Watcher<>() {

			private String watcherResourcesName;

			@Override
			public void eventReceived(Action action, GenericKubernetesResource genericKubernetesResource) {
				watcherResourcesName = genericKubernetesResource.getKind();

				logger.debug("{} Resource Kind:{} Name:{} UID:{} Namespace:{}", action.name(),
						genericKubernetesResource.getKind(), genericKubernetesResource.getMetadata().getName(),
						genericKubernetesResource.getMetadata().getUid(),
						genericKubernetesResource.getMetadata().getNamespace());
				genericKubernetesResource.getAdditionalProperties().forEach((pk, pv) -> {
					logger.debug("\t {} {} ", pk, pv);
					Map<String, Object> values = (Map<String, Object>) pv;
					values.forEach((speck, specv) -> logger.debug("\t  {}={} ", speck, specv));
				});
				//ADDED, DELETED, MODIFIED, BOOKMARK, ERROR
				if ( action.name().equals( "ADDED" ) ) {
				  updateOSLCatalog( genericKubernetesResource );				  
				
				} else if ( action.name().equals( "MODIFIED" ) ) {
                  updateOSLCatalog( genericKubernetesResource );					
				} else if ( action.name().equals( "DELETED" ) ) {
                  updateOSLCatalog( genericKubernetesResource );					
				} else {
					
				}
				
			}

			@Override
			public void onClose(WatcherException e) {
				logger.info("Closing resources Watcher of {} due to {} ", watcherResourcesName, e.getMessage());
			}

		});

	}

	protected void updateOSLCatalog(GenericKubernetesResource genericKubernetesResource) {
	  String oslResourceId = genericKubernetesResource.getMetadata().getLabels().get("org.etsi.osl.resourceId");

	  if ( oslResourceId != null ) {
	    ResourceUpdate rs = kubernetesClientResource
            .KubernetesCR2OpensliceCR( genericKubernetesResource )
            .toResourceUpdate();
	    catalogClient.updateResourceById(oslResourceId, rs);
	  }else {
	      ResourceCreate rs = kubernetesClientResource
	          .KubernetesCR2OpensliceCR( genericKubernetesResource )
	          .toResourceCreate();

	      catalogClient.createOrUpdateResourceByNameCategoryVersion( rs );	    
	  }            

	}

//	protected void createCRDWatcher(CustomResourceDefinition crd) {
//		ResourceDefinitionContext context = new ResourceDefinitionContext.Builder().withGroup(crd.getSpec().getGroup())
//				.withVersion(crd.getSpec().getVersions().get(0).getName()).withKind(crd.getSpec().getNames().getKind())
//				.withPlural(crd.getSpec().getNames().getPlural()).build();
//
//		Watch watch = kubernetesClientResource.getKubernetesClient().genericKubernetesResources(context).inAnyNamespace().watch(new Watcher<>() {
//			@Override
//			public void eventReceived(Action action, GenericKubernetesResource genericKubernetesResource) {
//				logger.debug("{} Kind:{} Name:{} UID:{} Namespace:{}", action.name(),
//						genericKubernetesResource.getKind(), genericKubernetesResource.getMetadata().getName(),
//						genericKubernetesResource.getMetadata().getUid(),
//						genericKubernetesResource.getMetadata().getNamespace());
//				genericKubernetesResource.getAdditionalProperties().forEach((pk, pv) -> {
//					logger.debug("\t {} {} ", pk, pv);
//					Map<String, Object> values = (Map<String, Object>) pv;
//					values.forEach((speck, specv) -> logger.debug("\t  {}={} ", speck, specv));
//				});
//			}
//
//			@Override
//			public void onClose(WatcherException e) {
//				logger.info("Closing due to {} ", e.getMessage());
//			}
//		});
//
//	}

	@PreDestroy
	ApplicationListener<ContextStoppedEvent> stop() {
		// return event -> sharedInformerFactory.stopAllRegisteredInformers();
		logger.info("Closing due to ContextStoppedEvent ");
		return event -> watch.close();
	}
}
