package io.quarkus.it.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class MinikubeWithApplicationPropertiesTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(GreetingResource.class))
            .setApplicationName("minikube-with-application-properties")
            .setApplicationVersion("0.1-SNAPSHOT")
            .withConfigurationResource("minikube-with-application.properties");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void assertGeneratedResources() throws IOException {
        Path kubernetesDir = prodModeTestResults.getBuildDir().resolve("kubernetes");
        assertThat(kubernetesDir)
                .isDirectoryContaining(p -> p.getFileName().endsWith("minikube.json"))
                .isDirectoryContaining(p -> p.getFileName().endsWith("minikube.yml"));
        List<HasMetadata> kubernetesList = DeserializationUtil
                .deserializeAsList(kubernetesDir.resolve("minikube.yml"));

        assertThat(kubernetesList).filteredOn(i -> "Deployment".equals(i.getKind())).hasOnlyOneElementSatisfying(i -> {
            assertThat(i).isInstanceOfSatisfying(Deployment.class, d -> {

                assertThat(d.getSpec()).satisfies(deploymentSpec -> {
                    assertThat(deploymentSpec.getReplicas()).isEqualTo(1);
                    assertThat(deploymentSpec.getTemplate()).satisfies(t -> {
                        assertThat(t.getSpec()).satisfies(podSpec -> {
                            assertThat(podSpec.getContainers()).hasOnlyOneElementSatisfying(container -> {
                                assertThat(container.getImagePullPolicy()).isEqualTo("IfNotPresent");
                            });
                        });
                    });
                });
            });
        });

        assertThat(kubernetesList).filteredOn(i -> "Service".equals(i.getKind())).hasOnlyOneElementSatisfying(i -> {
            assertThat(i).isInstanceOfSatisfying(Service.class, s -> {
                assertThat(s.getSpec()).satisfies(spec -> {
                    assertEquals("NodePort", spec.getType());
                    assertThat(spec.getPorts()).hasSize(1).hasOnlyOneElementSatisfying(p -> {
                        assertThat(p.getNodePort()).isEqualTo(31999);
                        assertThat(p.getPort()).isEqualTo(9090);
                    });
                });
            });
        });
    }
}
