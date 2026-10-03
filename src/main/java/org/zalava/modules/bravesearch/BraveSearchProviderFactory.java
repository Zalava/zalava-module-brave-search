package org.zalava.modules.bravesearch;

import java.net.http.HttpClient;
import java.util.List;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaProvider;

final class BraveSearchProviderFactory implements ProviderFactory {
  @Override
  public ProviderFactoryDescriptor descriptor() {
    return new ProviderFactoryDescriptor(
        BraveSearchZalavaModule.FACTORY_ID,
        BraveSearchZalavaModule.MODULE_ID,
        "web-search",
        "Brave Search Factory",
        "Creates provider-scoped Brave Search clients.");
  }

  @Override
  public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
    String reference = configuredReference(context.configuration().get("apiKeyRef"));
    if (reference == null) return List.of();
    char[] apiKey =
        context
            .secrets()
            .resolve(reference)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Brave Search API key is unavailable for reference: " + reference));
    return List.of(
        new BraveSearchProvider(new HttpBraveSearchClient(HttpClient.newHttpClient(), apiKey)));
  }

  private static String configuredReference(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }
}
