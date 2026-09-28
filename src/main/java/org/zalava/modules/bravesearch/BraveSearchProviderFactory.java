package org.zalava.modules.bravesearch;

import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.SeaProvider;

import java.net.http.HttpClient;
import java.util.List;

final class BraveSearchProviderFactory implements ProviderFactory {
    @Override
    public ProviderFactoryDescriptor descriptor() {
        return new ProviderFactoryDescriptor(BraveSearchSeaModule.FACTORY_ID, BraveSearchSeaModule.MODULE_ID,
                "web-search", "Brave Search Factory", "Creates provider-scoped Brave Search clients.");
    }

    @Override
    public List<SeaProvider> createProviders(ProviderFactoryContext context) {
        String reference = configuredReference(context.configuration().get("apiKeyRef"));
        if (reference == null) return List.of();
        char[] apiKey = context.secrets().resolve(reference)
                .orElseThrow(() -> new IllegalArgumentException("Brave Search API key is unavailable for reference: " + reference));
        return List.of(new BraveSearchProvider(new HttpBraveSearchClient(HttpClient.newHttpClient(), apiKey)));
    }

    private static String configuredReference(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
