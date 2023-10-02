package io.openslice.cridge;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.openslice.domain.model.DomainModelDefinition;
import io.openslice.domain.model.kubernetes.KubernetesCRDProperty;
import io.openslice.domain.model.kubernetes.KubernetesCRDV1;
import io.openslice.domain.model.kubernetes.KubernetesCRV1;
import io.openslice.domain.model.kubernetes.KubernetesContextDefinition;
import io.openslice.tmf.common.model.EValueType;
import io.openslice.tmf.rcm634.model.ResourceSpecification;
import io.openslice.tmf.ri639.model.ResourceCreate;
import io.openslice.tmf.ri639.model.ResourceStatusType;
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

  public String deployCR(Map<String, Object> headers, String crspec) {

    logger.debug("============ Deploy crspec =============" );
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
      gkr.getMetadata().setName( "cr-" + (String) headers.get("org.etsi.osl.resourceId")) ;
      Resource<GenericKubernetesResource> dummyObject = k8s.resource( gkr );
      dummyObject.create();      
    }

    return "DONE";

  }

}
