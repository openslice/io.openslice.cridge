package io.openslice.cridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.Resource;

public class CreateResource {

	public static void main(String[] args) {
		try (final KubernetesClient k8s = new KubernetesClientBuilder().build()) {

			/**
			 * 1st approach with GenericKubernetesResource
			 */
//    	ResourceDefinitionContext context = new ResourceDefinitionContext.Builder()
//	    	      .withGroup("stable.example.com")
//	    	      .withVersion("v1")
//	    	      .withKind("CronTab")
//	    	      .withPlural("crontabs")
//	    	      .withNamespaced(true)
//	    	      .build();
//
//	      k8s.genericKubernetesResources(context)
//	    	      .inNamespace("default")
//	    	      .resource(genericKubernetesResource)
//	    	      .create();
			/**
			 * 2nd approach with GenericKubernetesResource
			 */
			Map<String, Object> spec = new HashMap<>();
			spec.put("title", "my-awesome-book title");
			spec.put("author", "TestAuthor");
			spec.put("isbn", "0077000007");

			GenericKubernetesResource genericKubernetesResource = new GenericKubernetesResourceBuilder()
					.withApiVersion("testing.fabric8.io/v1alpha1").withKind("Book").withNewMetadata()
					.withName("my-new-book-object-" + UUID.randomUUID().toString()).withNamespace("testakis")
					.endMetadata().addToAdditionalProperties("spec", spec).build();

			Resource<GenericKubernetesResource> dummyObject = k8s.resource(genericKubernetesResource);
			// Create Custom Resource
			dummyObject.create();

		}
	}

}
