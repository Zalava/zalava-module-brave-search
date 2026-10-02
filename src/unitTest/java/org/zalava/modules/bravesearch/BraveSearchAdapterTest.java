package org.zalava.modules.bravesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.zalava.InvocationContext;
import org.zalava.ProviderFactoryContext;
import tools.jackson.databind.json.JsonMapper;

class BraveSearchAdapterTest {
  private static final JsonMapper JSON = new JsonMapper();

  @Test
  void buildsScopedQueriesAndCopiesTheSecret() throws Exception {
    HttpClient transport = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"results\":[]}");
    when(transport.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    char[] secret = "fixture-key".toCharArray();
    HttpBraveSearchClient client = new HttpBraveSearchClient(transport, secret);
    secret[0] = 'X';
    assertThat(
            client.webSearch("hello world", List.of("allowed.example"), List.of("blocked.example")))
        .isEqualTo("{\"results\":[]}");
    ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(transport).send(request.capture(), any(HttpResponse.BodyHandler.class));
    assertThat(request.getValue().uri().getQuery())
        .isEqualTo("q=hello+world+site:allowed.example+-site:blocked.example");
    assertThat(request.getValue().headers().firstValue("X-Subscription-Token"))
        .contains("fixture-key");
  }

  @Test
  void preservesTransportFailuresAndInterrupts() throws Exception {
    HttpClient transport = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(429);
    when(transport.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response)
        .thenThrow(new IOException("fixture"))
        .thenThrow(new InterruptedException("fixture"));
    HttpBraveSearchClient client = new HttpBraveSearchClient(transport, new char[0]);
    assertThatThrownBy(() -> client.webSearch("query", List.of(), List.of()))
        .hasMessageContaining("HTTP 429");
    assertThatThrownBy(() -> client.webSearch("query", List.of(), List.of()))
        .hasMessage("Brave Search request failed")
        .hasCauseInstanceOf(IOException.class);
    try {
      assertThatThrownBy(() -> client.webSearch("query", List.of(), List.of()))
          .hasMessageContaining("interrupted");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void validatesProviderInputsAndKeepsOnlyNonblankDomainFilters() {
    BraveSearchClient client = mock(BraveSearchClient.class);
    when(client.webSearch("query", List.of("allowed.example"), List.of())).thenReturn("{}");
    BraveSearchProvider provider = new BraveSearchProvider(client);
    assertThat(provider.capabilities().supportsTools()).isTrue();
    assertThatThrownBy(
            () -> provider.callTool("unknown", JSON.createObjectNode(), InvocationContext.system()))
        .hasMessageContaining("Unknown web search tool");
    for (String arguments : List.of("{}", "{\"query\":\" \"}")) {
      assertThatThrownBy(
              () ->
                  provider.callTool(
                      "webSearch", JSON.readTree(arguments), InvocationContext.system()))
          .hasMessage("Search query is required");
    }
    var result =
        provider.callTool(
            "webSearch",
            JSON.readTree(
                "{\"query\":\"query\",\"allowedDomains\":[\"\",\"allowed.example\"],\"blockedDomains\":false}"),
            InvocationContext.system());
    assertThat(result.success()).isTrue();
    assertThat(result.metadata()).containsEntry("allowedDomains", List.of("allowed.example"));
    verify(client).webSearch("query", List.of("allowed.example"), List.of());
  }

  @Test
  void doesNotActivateWithoutValidConfigurationOrAResolvedSecret() {
    BraveSearchProviderFactory factory = new BraveSearchProviderFactory();
    assertThat(factory.createProviders(new ProviderFactoryContext(Map.of()))).isEmpty();
    assertThat(factory.createProviders(new ProviderFactoryContext(Map.of("apiKeyRef", " "))))
        .isEmpty();
    assertThat(factory.createProviders(new ProviderFactoryContext(Map.of("apiKeyRef", 1))))
        .isEmpty();
    assertThatThrownBy(
            () ->
                factory.createProviders(new ProviderFactoryContext(Map.of("apiKeyRef", "missing"))))
        .hasMessageContaining("unavailable for reference");
  }
}
