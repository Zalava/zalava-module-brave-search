package org.zalava.modules.bravesearch;

import org.zalava.ModuleConfigurationDescriptor;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.SeaModule;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class BraveSearchSeaModule implements SeaModule {
    static final String MODULE_ID = "zalava-module-brave-search";
    static final String FACTORY_ID = "brave-search";

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(MODULE_ID, version(), "Brave Search", "Provider-scoped web search backed by Brave Search.");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
        return List.of(new BraveSearchProviderFactory());
    }

    @Override
    public ModuleConfigurationDescriptor configuration() {
        return new ModuleConfigurationDescriptor(Map.of(
                "type", "object",
                "required", List.of(FACTORY_ID),
                "properties", Map.of(FACTORY_ID, Map.of(
                        "type", "object",
                        "required", List.of("apiKeyRef"),
                        "properties", Map.of("apiKeyRef", Map.of(
                                "type", "string",
                                "x-secret-reference", true,
                                "description", "Reference to the Brave Search API key.")),
                        "additionalProperties", false)),
                "additionalProperties", false));
    }

    static String version() {
        Properties properties = new Properties();
        try (InputStream input = BraveSearchSeaModule.class.getResourceAsStream("/module.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing module version metadata");
            }
            properties.load(input);
            return properties.getProperty("module.version");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read module version metadata", exception);
        }
    }
}
