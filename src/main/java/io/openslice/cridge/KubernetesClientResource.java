package io.openslice.cridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.openslice.domain.model.DomainModelDefinition;
import io.openslice.domain.model.kubernetes.KubernetesCRDV1;
import io.openslice.domain.model.kubernetes.KubernetesCRV1;
import io.openslice.domain.model.kubernetes.KubernetesContextDefinition;
import io.openslice.tmf.common.model.EValueType;
import io.openslice.tmf.rcm634.model.ResourceSpecification;
import io.openslice.tmf.ri639.model.ResourceCreate;
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
	
	
	List<ResourceCreate> CRD2TMFResource(CustomResourceDefinition crd) {

		ArrayList<ResourceCreate> result = new ArrayList<>();
		crd.getSpec().getVersions().stream().forEach(version -> {
			KubernetesCRDV1 kcrd = KubernetesCRDV1.builder()
					.osl_KUBCRD_RSPEC_UUID( kubernetesCRDV1ResourceSpec.getUuid() )
					.name( crd.getMetadata().getName()  
							+ "@" 
							+  kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster()
							+ "@" + 
							kubernetesClient.getMasterUrl().toExternalForm()  )
					.version( version.getName() )
					.currentContextCluster( kubernetesClient.getConfiguration().getCurrentContext().getName() )
					.clusterMasterURL( kubernetesClient.getMasterUrl().toString()  )
					.fullResourceName( crd.getFullResourceName() )
					.kind( crd.getSpec().getNames().getKind() )
					.apiGroup( crd.getSpec().getGroup() )
					.uID( crd.getMetadata().getUid() )
					.description( version.getSchema().getOpenAPIV3Schema().getDescription() )
					.build();
			
			if (version.getSchema().getOpenAPIV3Schema().getProperties() != null)
				version.getSchema().getOpenAPIV3Schema().getProperties().forEach((kPropName, vProVal) -> {
					logger.debug("propName={} propValue={} ", kPropName, vProVal.getType());
					if (kPropName.equals("spec") || kPropName.equals("status")) {
						// rs.addResourceSpecificationCharacteristicItemShort( kPropName,
						// vProVal.getType(), "TEXT");
						vProVal.getProperties().forEach((pk, pv) -> {
							logger.debug("\t{} {} ", pk, pv.getType());

							EValueType etype;
							if (pv.getType() == null) {
								etype = EValueType.TEXT;
							} else  if (pv.getType().equalsIgnoreCase("boolean")) {
								etype = EValueType.BOOLEAN;
							} else if (pv.getType().equalsIgnoreCase("integer")) {
								etype = EValueType.INTEGER;
							} else
								etype = EValueType.TEXT;

							kcrd.getProperties().put( kPropName + "." + pk,  etype.getValue());
						});

					}
				});

			if (version.getSchema().getOpenAPIV3Schema().getAdditionalProperties() != null) {
				version.getSchema().getOpenAPIV3Schema().getAdditionalProperties().getAdditionalProperties()
						.forEach((kPropName, vProVal) -> {
							logger.debug("propName={} propValue={} ", kPropName, vProVal);
							kcrd.getAdditionalProperties().put("additionalProperty." + kPropName,  EValueType.TEXT.getValue());

						});
			}
			
			
			ResourceCreate rs = kcrd.toResourceCreate();
			result.add(rs);
		});
		
		return result;
		
	}
	
	ResourceCreate genericKubernetesResource2TMFResource(GenericKubernetesResource gkr) {

		KubernetesCRV1 kcrd = KubernetesCRV1.builder()
					.osl_KUBCRV1_RSPEC_UUID( kubernetesCRV1ResourceSpec.getUuid() )
					.name( gkr.getMetadata().getName()  
							+ "@" 
							+ gkr.getMetadata().getNamespace()
							+ "@" 
							+  kubernetesClient.getConfiguration().getCurrentContext().getContext().getCluster()
							+ "@" + 
							kubernetesClient.getMasterUrl().toExternalForm()  )
					.version( gkr.getApiVersion() )
					.currentContextCluster( kubernetesClient.getConfiguration().getCurrentContext().getName() )
					.clusterMasterURL( kubernetesClient.getMasterUrl().toString()  )
					.fullResourceName( "" )
					.namespace( gkr.getMetadata().getNamespace() )
					.kind( gkr.getKind() )
					.apiGroup( gkr.getPlural() )
					.uID( gkr.getMetadata().getUid() )
					.description( gkr.getMetadata().toString() )
					.build();
			
			gkr.getAdditionalProperties().forEach((pk, pv) -> {
				logger.debug("\t {} {} ", pk, pv);
				Map<String, Object> values = (Map<String, Object>) pv;
				values.forEach((speck, specv) ->{
					logger.debug("\t  {}={} ", speck, specv);
					kcrd.getProperties().put( pk +"." +speck , specv.toString());
				});
				
			});
			

			
			ResourceCreate rs = kcrd.toResourceCreate();
			
		
		
		return rs;
		
	}

}
