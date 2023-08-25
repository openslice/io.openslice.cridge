package io.openslice.cridge;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.openslice.domain.model.kubernetes.KubernetesCRDV1;
import io.openslice.tmf.common.model.EValueType;
import io.openslice.tmf.ri639.model.ResourceCreate;

/**
 * @author ctranoris
 */
@Service
public class ModelTransformer {

	private static final Logger logger = LoggerFactory.getLogger(ModelTransformer.class.getSimpleName());

	@Autowired
	private KubernetesClientResource kubernetesClientResource;
	


//	List<ResourceSpecificationCreate> CRD2TMFResourceSpecification(CustomResourceDefinition crd) {
//
//		ArrayList<ResourceSpecificationCreate> result = new ArrayList<>();
//		crd.getSpec().getVersions().stream().forEach(version -> {
//
//			ResourceSpecificationCreate rs = new ResourceSpecificationCreate();
//			rs.setName(crd.getMetadata().getName()  + "@" +  kubernetesClientResource.getKubernetesClient().getConfiguration().getContexts().get(0).getName() );
//			rs.setCategory("CustomResourceDefinition");
//			rs.setVersion(version.getName() );
//
//			rs.addResourceSpecificationCharacteristicItemShort("Cluster",
//					 kubernetesClientResource.getKubernetesClient().getMasterUrl().toExternalForm(), "TEXT");
//			rs.addResourceSpecificationCharacteristicItemShort("FullResourceName", crd.getFullResourceName(), "TEXT");
//			rs.addResourceSpecificationCharacteristicItemShort("Kind", crd.getSpec().getNames().getKind(), "TEXT");
//			rs.addResourceSpecificationCharacteristicItemShort("Group", crd.getSpec().getGroup(), "TEXT");
//			rs.addResourceSpecificationCharacteristicItemShort("UID", crd.getMetadata().getUid(), "TEXT");
//
//			rs.setDescription(version.getSchema().getOpenAPIV3Schema().getDescription());
//			if (version.getSchema().getOpenAPIV3Schema().getProperties() != null)
//				version.getSchema().getOpenAPIV3Schema().getProperties().forEach((kPropName, vProVal) -> {
//					logger.debug("propName={} propValue={} ", kPropName, vProVal.getType());
//					if (kPropName.equals("spec") || kPropName.equals("status")) {
//						// rs.addResourceSpecificationCharacteristicItemShort( kPropName,
//						// vProVal.getType(), "TEXT");
//						vProVal.getProperties().forEach((pk, pv) -> {
//							logger.debug("\t{} {} ", pk, pv.getType());
//
//							EValueType etype;
//							if (pv.getType().equalsIgnoreCase("boolean")) {
//								etype = EValueType.BOOLEAN;
//							} else if (pv.getType().equalsIgnoreCase("integer")) {
//								etype = EValueType.INTEGER;
//							} else
//								etype = EValueType.TEXT;
//
//							rs.addResourceSpecificationCharacteristicItemShort(kPropName + "." + pk, null,
//									etype.getValue());
//						});
//
//					}
//				});
//
//			if (version.getSchema().getOpenAPIV3Schema().getAdditionalProperties() != null) {
//				version.getSchema().getOpenAPIV3Schema().getAdditionalProperties().getAdditionalProperties()
//						.forEach((kPropName, vProVal) -> {
//							logger.debug("propName={} propValue={} ", kPropName, vProVal);
//							rs.addResourceSpecificationCharacteristicItemShort("additionalProperty." + kPropName, null,
//									EValueType.TEXT.getValue());
//
//						});
//			}
//
//			result.add(rs);
//		});
//
//		return result;
//
//	}
}
