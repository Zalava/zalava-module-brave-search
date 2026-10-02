package org.zalava.modules.bravesearch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class HttpBraveSearchClient implements BraveSearchClient {
  private static final URI ENDPOINT = URI.create("https://api.search.brave.com/res/v1/web/search");
  private final HttpClient client;
  private final char[] apiKey;

  HttpBraveSearchClient(HttpClient client, char[] apiKey) {
    this.client = client;
    this.apiKey = apiKey.clone();
  }

  @Override
  public String webSearch(String query, List<String> allowedDomains, List<String> blockedDomains) {
    String scopedQuery = scopedQuery(query, allowedDomains, blockedDomains);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(ENDPOINT + "?q=" + encode(scopedQuery)))
            .header("Accept", "application/json")
            .header("X-Subscription-Token", new String(apiKey))
            .GET()
            .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2)
        throw new IllegalStateException(
            "Brave Search request failed with HTTP " + response.statusCode());
      return response.body();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Brave Search request was interrupted", exception);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Brave Search request failed", exception);
    }
  }

  private static String scopedQuery(String query, List<String> allowed, List<String> blocked) {
    StringBuilder value = new StringBuilder(query);
    allowed.forEach(domain -> value.append(" site:").append(domain));
    blocked.forEach(domain -> value.append(" -site:").append(domain));
    return value.toString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
