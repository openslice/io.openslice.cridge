package io.openslice.cridge;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.openslice.domain.model.DomainModelDefinition;
import io.openslice.domain.model.kubernetes.KubernetesCRDProperty;
import io.openslice.domain.model.kubernetes.KubernetesCRDV1;
import io.openslice.domain.model.kubernetes.KubernetesCRV1;
import io.openslice.domain.model.kubernetes.KubernetesContextDefinition;
import io.openslice.domain.model.kubernetes.KubernetesSecret;
import io.openslice.tmf.common.model.EValueType;
import io.openslice.tmf.rcm634.model.ResourceSpecification;
import io.openslice.tmf.ri639.model.ResourceCreate;
import io.openslice.tmf.ri639.model.ResourceStatusType;
import io.openslice.tmf.ri639.model.ResourceUpdate;
import lombok.Getter;

/**
 * @author ctranoris
 */
@Component
@Getter
public class KubernetesClientResource {

  private static final Logger logger = LoggerFactory.getLogger( "io.openslice.cridge" );

  @Autowired
  private KubernetesClient kubernetesClient;

  @Autowired
  CatalogClient catalogClient;

  private KubernetesContextDefinition kubernetesContextDefinition = null;

  private ResourceSpecification kubernetesCRDV1ResourceSpec = null;

  private ResourceSpecification kubernetesCRV1ResourceSpec = null;
  
  private ResourceSpecification kubernetesSecretResourceSpec = null;
  

  /**
   * Garbage collect namespaces to be deleted
   */
  private ConcurrentHashMap<String, String> nameSpacesTobeDeleted = new ConcurrentHashMap<>();

  private Map<String, Object> watchersForNamespaces = new HashMap<>();

  

  /**
   * @return the opensliceResourceSpecification
   * @throws Exception
   */
  public KubernetesContextDefinition getKubernetesContextDefinition() throws Exception {
    if (kubernetesContextDefinition == null) {
      ResourceSpecification rspec = catalogClient.retrieveResourceSpecByNameCategoryVersion(
          KubernetesContextDefinition.OSL_KUBD_RSPEC_NAME,
          KubernetesContextDefinition.OSL_KUBD_RSPEC_CATEGORY,
          KubernetesContextDefinition.OSL_KUBD_RSPEC_VERSION);
      if (rspec == null) {
        throw new Exception("Cannot retrieve KubernetesContextDefinition ResourceSpecification");
      }


      kubernetesContextDefinition = KubernetesContextDefinition.builder()
          .osl_KUBD_SPEC_UUID(rspec.getUuid())
          .name(this.getKubernetesClient().getConfiguration().getCurrentContext().getContext().getCluster()
              + "@" + kubernetesClient.getMasterUrl().toExternalForm())
          .clusterVersion(kubernetesClient.getKubernetesVersion().getMajor() + "."
              + kubernetesClient.getKubernetesVersion().getMinor())
          .masterURL(kubernetesClient.getMasterUrl().toExternalForm())
          .currentContextCluster(
              kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster())
          .currentContextName(kubernetesClient.getConfiguration().getCurrentContext().getName())
          .currentContextUser(kubernetesClient.getConfiguration().getCurrentContext().getContext().getUser())
          .build();

      // initialize also KubernetesCRDV1
      kubernetesCRDV1ResourceSpec = catalogClient.retrieveResourceSpecByNameCategoryVersion(
          KubernetesCRDV1.OSL_KUBCRD_RSPEC_NAME, KubernetesCRDV1.OSL_KUBCRD_RSPEC_CATEGORY,
          KubernetesCRDV1.OSL_KUBCRD_RSPEC_VERSION);

      kubernetesCRV1ResourceSpec  = catalogClient.retrieveResourceSpecByNameCategoryVersion(
          KubernetesCRV1.OSL_KUBCRV1_RSPEC_NAME, 
          KubernetesCRV1.OSL_KUBCRV1_RSPEC_CATEGORY,
          KubernetesCRV1.OSL_KUBCRV1_RSPEC_VERSION);

      kubernetesSecretResourceSpec  = catalogClient.retrieveResourceSpecByNameCategoryVersion(
          KubernetesSecret.OSL_KUBSECRET_RSPEC_NAME, 
          KubernetesSecret.OSL_KUBSECRET_RSPEC_CATEGORY,
          KubernetesSecret.OSL_KUBSECRET_RSPEC_VERSION);

    }
    return kubernetesContextDefinition;
  }


  List<KubernetesCRDV1> KubernetesCRD2OpensliceCRD(CustomResourceDefinition crd) {

    ArrayList<KubernetesCRDV1> result = new ArrayList<>();
    crd.getSpec().getVersions().stream().forEach(version -> {
      KubernetesCRDV1 kcrd = KubernetesCRDV1.builder()
          .osl_KUBCRD_RSPEC_UUID( kubernetesCRDV1ResourceSpec.getUuid() )
          .name(  crd.getSpec().getNames().getKind()
              + "@" 
              + crd.getSpec().getGroup()+"/"+version.getName()
              + "@" 
              +  kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster()
              + "@" + 
              kubernetesClient.getMasterUrl().toExternalForm()  )
          .version( version.getName() )
          .currentContextCluster( kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster() )
          .clusterMasterURL( kubernetesClient.getMasterUrl().toString()  )
          .fullResourceName( crd.getFullResourceName() )
          .kind( crd.getSpec().getNames().getKind() )
          .apiGroup( crd.getSpec().getGroup() )
          .uID( crd.getMetadata().getUid() )
          .description( version.getSchema().getOpenAPIV3Schema().getDescription() )
          .metadata( "" )
          .build();

      if (version.getSchema().getOpenAPIV3Schema().getProperties() != null)
        version.getSchema().getOpenAPIV3Schema().getProperties().forEach((kPropName, vProVal) -> {
          if (kPropName.equals("spec") || kPropName.equals("status")) {
            logger.debug("propName={} propValue={} ", kPropName, vProVal.getType());						
            addCRDProperties(kcrd, kPropName, vProVal, "");						
          }


        });

      if (version.getSchema().getOpenAPIV3Schema().getAdditionalProperties() != null) {
        version.getSchema().getOpenAPIV3Schema().getAdditionalProperties().getAdditionalProperties()
        .forEach((kPropName, vProVal) -> {
          logger.debug("propName={} propValue={} ", kPropName, vProVal);
          KubernetesCRDProperty kpcrdProperty = KubernetesCRDProperty
              .builder()
              .name(kPropName)
              .build();
          kcrd.getAdditionalProperties().put("additionalProperty." + kPropName,  kpcrdProperty);		

        });
      }

      kcrd.setYaml( Serialization.asYaml( version.getSchema().getOpenAPIV3Schema().getProperties()  ) );
      kcrd.setJson( Serialization.asJson( version.getSchema().getOpenAPIV3Schema().getProperties() ) );

      result.add( kcrd );
    });

    return result;

  }

  private void addCRDProperties(KubernetesCRDV1 kcrd, String kPropName, JSONSchemaProps vProVal, String prefix ) {


    String propertyToAdd =  prefix + kPropName;

    EValueType etype;
    if (vProVal.getType() == null) {
      etype = EValueType.TEXT;
    } else  if (vProVal.getType().equalsIgnoreCase("boolean")) {
      etype = EValueType.BOOLEAN;
    } else if (vProVal.getType().equalsIgnoreCase("integer")) {
      etype = EValueType.INTEGER;
    }  else if (vProVal.getType().equalsIgnoreCase("object")) {
      etype = EValueType.OBJECT;
      vProVal.getProperties().forEach((pk, pv) -> {
        addCRDProperties(kcrd, pk, pv, propertyToAdd + ".");					
      });
    }  else if (vProVal.getType().equalsIgnoreCase("array")) {
      etype = EValueType.ARRAY;
      vProVal.getProperties().forEach((pk, pv) -> {
        addCRDProperties(kcrd, pk, pv, propertyToAdd + "[].");					
      });
    } else
      etype = EValueType.TEXT;


    KubernetesCRDProperty kpcrdProperty = KubernetesCRDProperty
        .builder()
        .name(kPropName)
        .description( vProVal.getDescription() )
        .defaultValue( vProVal.getDefault()!=null? vProVal.getDefault().asText() : "" )
        .valueType( etype.getValue() )
        .build();


    kcrd.getProperties().put(  propertyToAdd, kpcrdProperty );

  }


  KubernetesCRV1 KubernetesCR2OpensliceCR(GenericKubernetesResource gkr) {

    String baseCRD = String.format( "%s@%s@%s@%s",
        gkr.getKind(),
        gkr.getApiVersion(),
        kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster(),
        kubernetesClient.getMasterUrl().toExternalForm());

    KubernetesCRV1 kcrv = KubernetesCRV1.builder()
        .osl_KUBCRV1_RSPEC_UUID( kubernetesCRV1ResourceSpec.getUuid() )
        .name( gkr.getMetadata().getName()  
            + "@" 
            + gkr.getMetadata().getNamespace()
            + "@" 
            +  kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster()
            + "@" + 
            kubernetesClient.getMasterUrl().toExternalForm()  )
        .version( gkr.getApiVersion() )
        .currentContextCluster( kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster() )
        .clusterMasterURL( kubernetesClient.getMasterUrl().toString()  )
        .fullResourceName( "" )
        .namespace( Serialization.asJson( gkr.getMetadata().getNamespace()) )
        .kind( gkr.getKind() )
        .apiGroup( gkr.getPlural() )
        .uID( gkr.getMetadata().getUid() )
        .metadata( gkr.getMetadata().toString()  ) //.metadata( gkr.getMetadata().toString() )
        .category( baseCRD )
        .description( 
            String.format( "A CR in namespace %s based on CRD %s", gkr.getMetadata().getNamespace(),  baseCRD ))
        .build();


    gkr.getMetadata().getLabels().forEach((pk, pv) -> {
      logger.debug("\t label: {} {} ", pk, pv);
      kcrv.getProperties().put( pk  , pv);

    });

    kcrv.setStatusCheckFieldName( gkr.getMetadata().getLabels().get("org.etsi.osl.statusCheckFieldName"));
    kcrv.setStatusCheckValueStandby( gkr.getMetadata().getLabels().get("org.etsi.osl.statusCheckValueStandby"));
    kcrv.setStatusCheckValueAlarm( gkr.getMetadata().getLabels().get("org.etsi.osl.statusCheckValueAlarm"));
    kcrv.setStatusCheckValueAvailable( gkr.getMetadata().getLabels().get("org.etsi.osl.statusCheckValueAvailable"));
    kcrv.setStatusCheckValueReserved( gkr.getMetadata().getLabels().get("org.etsi.osl.statusCheckValueReserved"));
    kcrv.setStatusCheckValueUnknown( gkr.getMetadata().getLabels().get("org.etsi.osl.statusCheckValueUnknown"));
    kcrv.setStatusCheckValueSuspended( gkr.getMetadata().getLabels().get("org.etsi.osl.statusCheckValueSuspended"));
    kcrv.setStatusValue(    ResourceStatusType.UNKNOWN );
    
    gkr.getAdditionalProperties().forEach((pk, pv) -> {
      logger.debug("\t {} {} ", pk, pv);
      if (pv instanceof Map) {
        addCRVProperties( kcrv, pk, (Map<String, Object>) pv, "");					
      }else {
        Map<String, Object> values = (Map<String, Object>) pv;
        values.forEach((speck, specv) ->{
          logger.debug("\t  {}={} ", speck, specv);
          String propertyName = pk +"." +speck;
          String propertyValue = specv.toString();
          kcrv.getProperties().put( propertyName , propertyValue);
          
          //check resource status
          if ( kcrv.getStatusCheckFieldName().equals(propertyName) ){
            kcrv.setStatusValue(  checKCRVResourceStatus(kcrv, propertyValue) );            
          }
        });					
      }

    });

    kcrv.getProperties().put( "AdditionPropertiesAsJson" , Serialization.asJson( gkr.getAdditionalProperties() ));

    kcrv.setYaml( Serialization.asYaml( gkr ) );
    kcrv.setJson( Serialization.asJson( gkr ) );
    kcrv.setCr_spec( Serialization.asJson( gkr )  );

    return kcrv;

  }


  private ResourceStatusType checKCRVResourceStatus(KubernetesCRV1 kcrv, String propertyValue) {

    if (propertyValue.equals(kcrv.getStatusCheckValueAlarm())) {
      return ResourceStatusType.ALARM;
    } else if (propertyValue.equals(kcrv.getStatusCheckValueAvailable())) {
      return ResourceStatusType.AVAILABLE;
    } else if (propertyValue.equals(kcrv.getStatusCheckValueReserved())) {
      return ResourceStatusType.RESERVED;
    } else if (propertyValue.equals(kcrv.getStatusCheckValueStandby())) {
      return ResourceStatusType.STANDBY;
    } else if (propertyValue.equals(kcrv.getStatusCheckValueSuspended())) {
      return ResourceStatusType.SUSPENDED;
    } else if (propertyValue.equals(kcrv.getStatusCheckValueUnknown())) {
      return ResourceStatusType.UNKNOWN;
    } else {
      return ResourceStatusType.UNKNOWN;
    }

  }


  private void addCRVProperties(KubernetesCRV1 kcrv,String kPropName, Map<String, Object> vProVal, String prefix) {



    vProVal.forEach((speck, specv) ->{
      if (specv instanceof Map) {
        String propertyToAdd =  prefix + kPropName;
        addCRVProperties( kcrv, speck, (Map<String, Object>) specv, propertyToAdd + ".");		
      }else {
        String propertyToAdd =  prefix + kPropName+ "."+speck;
        logger.debug("\t {} {}={} ", propertyToAdd, speck, specv);		
        kcrv.getProperties().put( propertyToAdd , specv.toString());	
        String propertyName = propertyToAdd;
        String propertyValue = specv.toString();
        if ( kcrv.getStatusCheckFieldName()!=null && kcrv.getStatusCheckFieldName().equals(propertyName) ){
          kcrv.setStatusValue(  checKCRVResourceStatus(kcrv, propertyValue) );            
        }
      }

    });



  }
  
  
  private KubernetesSecret KubernetesSecret2OpensliceResource(Secret secret) {

    
    String baseCRD = String.format( "%s@%s@%s@%s",
        secret.getKind(),
        secret.getApiVersion(),
        kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster(),
        kubernetesClient.getMasterUrl().toExternalForm());

    KubernetesSecret kcrv = KubernetesSecret.builder()
        .osl_KUBCRD_RSPEC_UUID( kubernetesSecretResourceSpec.getUuid() )
        .name( secret.getMetadata().getName()  
            + "@" 
            + secret.getMetadata().getNamespace()
            + "@" 
            +  kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster()
            + "@" + 
            kubernetesClient.getMasterUrl().toExternalForm()  )
        .version( secret.getApiVersion() )
        .currentContextCluster( kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster() )
        .clusterMasterURL( kubernetesClient.getMasterUrl().toString()  )
        .fullResourceName( "" )
        .namespace( Serialization.asJson( secret.getMetadata().getNamespace()) )
        .kind( secret.getKind() )
        .apiGroup( secret.getPlural() )
        .uID( secret.getMetadata().getUid() )
        .metadata(Serialization.asJson( secret.getMetadata())  ) //.metadata( gkr.getMetadata().toString() )        
        .description( 
            String.format( "A secret in namespace %s on %s", secret.getMetadata().getNamespace(),  baseCRD ))
        .build();


    secret.getMetadata().getLabels().forEach((pk, pv) -> {
      logger.debug("\t label: {} {} ", pk, pv);
      kcrv.getProperties().put( pk  , pv);

    });
    

    if (secret.getData()  != null)
      secret.getData().forEach((kPropName, vProVal) -> {
        logger.debug("propName={} propValue={} ", kPropName, vProVal );        
        kcrv.getData().put(kPropName, vProVal);
      });
    if (secret.getStringData()  != null)
      secret.getStringData().forEach((kPropName, vProVal) -> {
        logger.debug("propName={} propValue={} ", kPropName, vProVal );        
        kcrv.getProperties().put(kPropName, vProVal);
      });
    
    kcrv.setDataObj( Serialization.asYaml( secret.getData() )  );
    kcrv.setYaml( Serialization.asYaml( secret ) );
    kcrv.setJson( Serialization.asJson( secret ) );

    return kcrv;
  }

  

  public String deployCR(Map<String, Object> headers, String crspec) {

    logger.debug("============ Deploy crspec =============" );
    logger.debug("{}", crspec );
   

    try (final KubernetesClient k8s = new KubernetesClientBuilder().build()) {

      GenericKubernetesResource gkr =  Serialization.unmarshal( crspec );
      headers.forEach(((hname, hval) ->{
        if (hval instanceof String s) {
          if ( hname.contains("org.etsi.osl")) {
            logger.debug("Header: {} = {} ", hname, s );      
            if ( gkr.getMetadata() == null ) {
              gkr.setMetadata( new ObjectMeta());
              gkr.getMetadata().setLabels( new HashMap<String, String>());
            }
            gkr.getMetadata().getLabels().put(hname, s);
          }
        }
      }));
      if ( headers.get("org.etsi.osl.prefixId") !=null ) {
        gkr.getMetadata().setName( (String) headers.get("org.etsi.osl.prefixName") ) ;
      }else {
        gkr.getMetadata().setName( "cr" + ((String) headers.get("org.etsi.osl.resourceId")).substring(0, 8) ) ;
      }
      
      
      String nameSpacename = (String) headers.get("org.etsi.osl.namespace");

      try {

        //first try create namespace for the service order
        Namespace ns = new NamespaceBuilder()
            .withNewMetadata()
            .withName( nameSpacename )
            .addToLabels("org.etsi.osl", "org.etsi.osl")
            .endMetadata().build();
        k8s.namespaces().resource(ns).create();    
        
        
      }catch (Exception e) {
        //e.printStackTrace();
        //if it exists already just continue!
        logger.info("Cannot create namespace, already exists!" + e.getMessage() );
      }
      

      /* if the equivalent namespace for the service order is successfully created the create
       * related wathcers  for secrets, services, configmaps, pods, etc
       *     1) we need to create domainmodels for these
       *     2) we add them as support resources to our initial resource
       */        

      if ( this.watchersForNamespaces.get(nameSpacename) !=null ) {
        SharedIndexInformer<Secret> result = createWatchersFornamespace( nameSpacename, headers );       
        this.watchersForNamespaces.put(nameSpacename, result);
      }
      
      
      Resource<GenericKubernetesResource> dummyObject = k8s.resource( gkr );
      dummyObject.create();      
    }catch (Exception e) {
      e.printStackTrace();
      return "FAIL " + e.getMessage();
    }

    return "OK";

  }
  
  /**
   * if the equivalent namespace for the service order is successfully created the create
   * related wathcers  for secrets, services, configmaps, pods, etc
   *     1) we need to create domainmodels for these
   *     2) we add them as support resources to our initial resource
   * @param nameSpacename
   * @param headers 
   * @return 
   */
  private SharedIndexInformer<Secret> createWatchersFornamespace(String nameSpacename, Map<String, Object> headers) {
    //watcher for secrets
      
      SharedIndexInformer<Secret> shixInformer = 
          this.getKubernetesClient()
          .secrets().inNamespace(nameSpacename).inform(new ResourceEventHandler<Secret>() {

            @Override
            public void onAdd(Secret obj) {
            logger.debug("Added Namespace watcher Resource Kind:{} Name:{} UID:{} Namespace:{}", 
                obj.getKind(), 
                obj.getMetadata().getName(),
                obj.getMetadata().getUid(),
                obj.getMetadata().getNamespace());
            
            headers.forEach(((hname, hval) ->{
              if (hval instanceof String s) {
                if ( hname.contains("org.etsi.osl")) {
                  logger.debug("Header: {} = {} ", hname, s );      
                  if ( obj.getMetadata() == null ) {
                    obj.setMetadata( new ObjectMeta());
                    obj.getMetadata().setLabels( new HashMap<String, String>());
                  }
                  obj.getMetadata().getLabels().put(hname, s);
                }
              }
            }));
            
            updateKubernetesSecretResourceInOSLCatalog( obj );     
            
              
            }

            @Override
            public void onUpdate(Secret oldObj, Secret newObj) {
              logger.debug("onUpdate Namespace watcher Resource Kind:{} Name:{} UID:{} Namespace:{}", 
                  newObj.getKind(), 
                  newObj.getMetadata().getName(),
                  newObj.getMetadata().getUid(),
                  newObj.getMetadata().getNamespace());
              updateKubernetesSecretResourceInOSLCatalog( newObj );    
              
            }

            @Override
            public void onDelete(Secret obj, boolean deletedFinalStateUnknown) {
              logger.debug("onDelete Namespace watcher Resource Kind:{} Name:{} UID:{} Namespace:{}", 
                  obj.getKind(), 
                  obj.getMetadata().getName(),
                  obj.getMetadata().getUid(),
                  obj.getMetadata().getNamespace());
              updateKubernetesSecretResourceInOSLCatalog( obj );    
              
            }

        
      }, 30 * 1000L); // resync period (set 0 for no resync);
    
      shixInformer.start();
      
      return shixInformer;
    
  }

  private void updateKubernetesSecretResourceInOSLCatalog(Secret resource) {
    ResourceCreate rs = this
        .KubernetesSecret2OpensliceResource( resource )
        .toResourceCreate();

    catalogClient.createOrUpdateResourceByNameCategoryVersion( rs );  
    
  }
  
  

  
  

  public String deleteCR(Map<String, Object> headers, String crspec) {

    logger.debug("============ DELETE crspec =============" );
    logger.debug("{}", crspec );
   
    

    try (final KubernetesClient k8s = new KubernetesClientBuilder().build()) {

      GenericKubernetesResource gkr =  Serialization.unmarshal( crspec );
      headers.forEach(((hname, hval) ->{
        if (hval instanceof String s) {
          if ( hname.contains("org.etsi.osl")) {
            logger.debug("Header: {} = {} ", hname, s );      
            gkr.getMetadata().getLabels().put(hname, s);
          }
        }
      }));
      gkr.getMetadata().setName( (String) headers.get("org.etsi.osl.prefixName")) ;
      Resource<GenericKubernetesResource> dummyObject = k8s.resource( gkr );
      List<StatusDetails> result = dummyObject.delete();      

      logger.debug("============ DELETE crspec: result {} =============", result.toString() );
      
      String nameSpacename = (String) headers.get("org.etsi.osl.namespace");
      this.nameSpacesTobeDeleted.put(nameSpacename, nameSpacename);
      this.watchersForNamespaces.remove(nameSpacename);
      
    }catch (Exception e) {
      e.printStackTrace();
    }
    

    
      
    return "DONE";

  }
  
  public String patchCR(Map<String, Object> headers, String crspec) {

    logger.debug("============ PATCH crspec =============" );
    logger.debug("{}", crspec );
    logger.error("NOT IMPLEMENTED" );

    return "DONE";

  }

}
