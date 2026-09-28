package org.zalava.modules.bravesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.zalava.FactorySecretAccess;
import org.zalava.InvocationContext;
import org.zalava.SeaProvider;
import org.zalava.testing.ConfigFixture;
import org.zalava.testing.ModuleContractKit;
import org.zalava.testing.ProviderFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the real built module JAR at the stable {@code module-api} boundary through the released
 * contract kit. Host-owned resolution, validation, permissions and transport stay covered by SEA.
 */
class BraveSearchSeaModuleTest {

    private static final String MODULE_ID = "zalava-module-brave-search";
    private static final String FACTORY_ID = "brave-search";
    private static final String PROVIDER_ID = "brave-search";

    private ModuleContractKit kit;

    @BeforeEach
    void loadTheBuiltArtifact() {
        Path artifact = Path.of(System.getProperty("module.artifact"));
        String version = System.getProperty("module.version");
        kit = ModuleContractKit.load(artifact, List.of(), MODULE_ID, version);
    }

    @AfterEach
    void closeTheArtifact() throws Exception {
        if (kit != null) {
            kit.close();
        }
    }

    @Test
    void loadsTheModuleFromTheBuiltArtifact() {
        assertThat(kit.module().getClass().getClassLoader()).isNotSameAs(getClass().getClassLoader());
        assertThat(kit.module().getClass().getProtectionDomain().getCodeSource().getLocation().toString())
                .endsWith(".jar");
    }

    @Test
    void exposesTheModuleOwnedDescriptorAndConfigurationContract() {
        assertThat(kit.moduleId()).isEqualTo(MODULE_ID);
        assertThat(kit.version()).isEqualTo(System.getProperty("module.version"));

        Map<String, Object> schema = kit.module().configuration().jsonSchema();
        assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
        assertThat(schema).containsEntry("required", List.of(FACTORY_ID));
        Object properties = schema.get("properties");
        assertThat(properties).isInstanceOf(Map.class);
        Object factory = ((Map<?, ?>) properties).get(FACTORY_ID);
        assertThat(factory).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) factory).get("required")).isEqualTo(List.of("apiKeyRef"));
        assertThat(((Map<?, ?>) factory).get("additionalProperties")).isEqualTo(false);
        assertThat(factory.toString()).contains("apiKeyRef").contains("x-secret-reference").doesNotContain("apiKey=");
    }

    @Test
    void createsNoProviderUntilTheHostConfiguresASecretReference() {
        try (ProviderFixture providers = kit.providers(ConfigFixture.empty())) {
            assertThat(providers.providers()).isEmpty();
        }
    }

    @Test
    void resolvesTheConfiguredSecretReferenceAndDeclaresItsTool() {
        try (ProviderFixture providers = kit.providers(configuration("brave-production", usableSecrets()))) {
            SeaProvider provider = providers.requireProvider(PROVIDER_ID);

            assertThat(provider.descriptor().moduleId()).isEqualTo(MODULE_ID);
            assertThat(provider.descriptor().version()).isEqualTo(System.getProperty("module.version"));
            assertThat(provider.listTools()).singleElement().satisfies(tool -> {
                assertThat(tool.name()).isEqualTo("webSearch");
                assertThat(tool.sideEffecting()).isFalse();
                assertThat(tool.policyTags()).contains("sea_backed", "web-search", "network");
            });
        }
    }

    @Test
    void rejectsAnUnavailableConfiguredSecretReference() {
        ConfigFixture configuration = configuration("missing", FactorySecretAccess.none());

        assertThatThrownBy(() -> kit.providers(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Brave Search API key is unavailable for reference: missing");
    }

    @Test
    void rejectsUnknownToolsAndBlankQueriesWithoutContactingBrave() {
        try (ProviderFixture providers = kit.providers(configuration("brave-production", usableSecrets()))) {
            SeaProvider provider = providers.requireProvider(PROVIDER_ID);

            assertThatThrownBy(() -> provider.callTool("other", arguments(), InvocationContext.system()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unknown web search tool: other");
            assertThatThrownBy(() -> provider.callTool("webSearch", arguments(), InvocationContext.system()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Search query is required");
        }
    }

    private static ConfigFixture configuration(String reference, FactorySecretAccess secrets) {
        return ConfigFixture.empty()
                .factoryConfiguration(MODULE_ID, FACTORY_ID, Map.of("apiKeyRef", reference))
                .secrets(secrets);
    }

    private static FactorySecretAccess usableSecrets() {
        return reference -> "brave-production".equals(reference) ? Optional.of("test-key".toCharArray()) : Optional.empty();
    }

    private static ObjectNode arguments() {
        return JsonNodeFactory.instance.objectNode();
    }
}
