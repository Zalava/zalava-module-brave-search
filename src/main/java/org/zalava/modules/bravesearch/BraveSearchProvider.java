package org.zalava.modules.bravesearch;

import tools.jackson.databind.JsonNode;
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.ZalavaToolInputSchemas;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

final class BraveSearchProvider implements ZalavaProvider {
    private static final ZalavaToolDescriptor WEB_SEARCH = new ZalavaToolDescriptor("webSearch",
            "Search the web using Brave Search and optional domain filters.", false,
            List.of("sea_backed", "web-search", "network"), ZalavaToolInputSchemas.object(Map.of(
                    "query", ZalavaToolInputSchemas.string(),
                    "allowedDomains", ZalavaToolInputSchemas.stringArray(),
                    "blockedDomains", ZalavaToolInputSchemas.stringArray()), "query"));
    private final BraveSearchClient client;
    private final ProviderDescriptor descriptor = new ProviderDescriptor("brave-search", BraveSearchSeaModule.MODULE_ID,
            "web-search", "Brave Search", "Provider-scoped web search backed by Brave Search.", BraveSearchSeaModule.version(),
            ProviderCapabilities.toolsOnly(), List.of("sea_backed", "web-search", "network"), Map.of("service", "brave"));

    BraveSearchProvider(BraveSearchClient client) { this.client = client; }
    @Override public ProviderDescriptor descriptor() { return descriptor; }
    @Override public ProviderCapabilities capabilities() { return descriptor.capabilities(); }
    @Override public List<ZalavaToolDescriptor> listTools() { return List.of(WEB_SEARCH); }

    @Override
    public ZalavaOperationResult callTool(String toolName, JsonNode arguments, InvocationContext context) {
        if (!WEB_SEARCH.name().equals(toolName)) throw new IllegalArgumentException("Unknown web search tool: " + toolName);
        String query = arguments.path("query").asString(null);
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Search query is required");
        List<String> allowedDomains = stringList(arguments.path("allowedDomains"));
        List<String> blockedDomains = stringList(arguments.path("blockedDomains"));
        return new ZalavaOperationResult(true, Map.of("query", query, "resultsJson", client.webSearch(query, allowedDomains, blockedDomains)),
                Map.of("providerId", descriptor.providerId(), "service", "brave", "allowedDomains", allowedDomains, "blockedDomains", blockedDomains));
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        return StreamSupport.stream(node.spliterator(), false).map(JsonNode::asString).filter(value -> !value.isBlank()).toList();
    }
}
