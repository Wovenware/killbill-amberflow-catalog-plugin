/* Copyright 2023 Wovenware, Inc
 *
 * Wovenware licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.killbill.billing.plugin.amberflo.catalog.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.joda.time.DateTime;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.plugin.amberflo.catalog.api.boilerplate.StandalonePluginCatalogImp;
import org.killbill.billing.plugin.amberflo.catalog.client.model.ProductItem;
import org.killbill.billing.plugin.amberflo.catalog.client.model.ProductPlans;
import org.killbill.billing.plugin.amberflo.catalog.client.model.UsageResponse;
import org.killbill.billing.plugin.amberflo.catalog.core.CatalogConfigurationProperties;

public class AmberfloHttpClientImpl {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final CloseableHttpClient httpclient = HttpClients.createDefault();

  CatalogConfigurationProperties config;
  AmberfloUtils utils = new AmberfloUtils(this);

  public AmberfloHttpClientImpl() {}

  public AmberfloHttpClientImpl(CatalogConfigurationProperties config) {
    this.config = config;
  }

  public StandalonePluginCatalog buildCatalog() throws Exception {

    return new StandalonePluginCatalogImp.Builder<>()
        .withEffectiveDate(new DateTime(utils.getEffectiveDate(this.getListAllProductPlans())))
        .withUnits(utils.getUnits(this.getAllProductItems()))
        .withCurrencies(utils.buildCurrencyList())
        .withPlans(
            utils.convertToKillBillPlanModel(
                this.getListAllProductPlans(), this.getAllProductItems()))
        .withProducts(utils.getProducts())
        .withPlanRules(utils.buildRules())
        .withDefaultPriceList(
            utils.getPriceList(this.getListAllProductPlans(), this.getAllProductItems()))
        .build();
  }
  // Retrieves the list of plans from amberflo
  private List<ProductPlans> getListAllProductPlans() throws IOException {

    HttpResponse httpresponse = httpclient.execute(buildHttpGet(config.getPlans()));

    List<ProductPlans> plans =
        mapper.readValue(
            httpresponse.getEntity().getContent(), new TypeReference<List<ProductPlans>>() {});

    return utils.validatePlans(plans);
  }

  // Retrieves the list of products from amberflo
  private List<ProductItem> getAllProductItems() throws IOException {

    HttpResponse httpresponse = httpclient.execute(buildHttpGet(config.getProducts()));

    return mapper.readValue(
        httpresponse.getEntity().getContent(), new TypeReference<List<ProductItem>>() {});
  }

  // Receives the a value from a ProductItemPriceIdsMap and retrieves its pricing data from amberflo
  public UsageResponse requestListAllPaymentPricing(String id)
      throws URISyntaxException, UnsupportedOperationException, IOException {

    HttpGet httpget = buildHttpGet(config.getPrices());
    URI uri = new URIBuilder(httpget.getURI()).addParameter("id", id).build();
    httpget.setURI(uri);

    HttpResponse httpresponse = httpclient.execute(httpget);

    return mapper.readValue(
        httpresponse.getEntity().getContent(), new TypeReference<UsageResponse>() {});
  }

  public HttpGet buildHttpGet(String endpoint) {

    HttpGet httpget = new HttpGet(config.getUrl() + endpoint);

    httpget.setHeader("accept", "application/json");
    httpget.setHeader("X-API-KEY", config.getApiKey());

    return httpget;
  }
}
