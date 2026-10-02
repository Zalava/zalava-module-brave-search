package org.zalava.modules.bravesearch;

import java.util.List;

interface BraveSearchClient {
  String webSearch(String query, List<String> allowedDomains, List<String> blockedDomains);
}
