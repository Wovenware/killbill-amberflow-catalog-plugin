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

package org.killbill.billing.plugin.amberflo.catalog.core;

import java.util.Map;
import java.util.Properties;

public class CatalogConfigurationProperties {

  private static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.amberflo.catalog.";

  public static final String AMBERFLO_KB_APIKEY = "AMBERFLO_KB_APIKEY";
  public static final String AMBERFLO_KB_URL = "AMBERFLO_KB_URL";
  public static final String AMBERFLO_KB_GET_PLANS = "AMBERFLO_KB_GET_PLANS";
  public static final String AMBERFLO_KB_GET_PRICES = "AMBERFLO_KB_GET_PRICES";
  public static final String AMBERFLO_KB_GET_PRODUCTS = "AMBERFLO_KB_GET_PRODUCTS";
  public static final String AMBERFLO_KB_DOMAIN = "AMBERFLO_KB_DOMAIN";
  public static final String AMBERFLO_KB_INGESTION_FREQUENCY_SECONDS =
      "AMBERFLO_KB_INGESTION_FREQUENCY_SECONDS";
  public static final String AMBERFLO_KB_INGESTION_BATCH_SIZE = "AMBERFLO_KB_INGESTION_BATCH_SIZE";
  public static final String AMBERFLO_KB_IS_DEBUG = "AMBERFLO_KB_IS_DEBUG";

  public static final String DEFAULT_APIKEY = "";
  public static final String DEFAULT_URL =
      "https://app.amberflo.io/payments/pricing/amberflo/account-pricing";
  public static final String DEFAULT_GET_PLANS = "/product-plans/list";
  public static final String DEFAULT_GET_PRICES = "/product-item-price";
  public static final String DEFAULT_GET_PRODUCTS = "/product-items/list";

  private String apiKey;
  private String url;
  private String getPlans;
  private String getPrices;
  private String getProducts;
  private String killBillRegion;

  public CatalogConfigurationProperties(final Properties properties, final String killBillRegion) {

    this.apiKey = properties.getProperty(PROPERTY_PREFIX + "apiKey");
    this.url = properties.getProperty(PROPERTY_PREFIX + "url");
    this.getPlans = properties.getProperty(PROPERTY_PREFIX + "getPlans");
    this.getPrices = properties.getProperty(PROPERTY_PREFIX + "getPrices");
    this.getProducts = properties.getProperty(PROPERTY_PREFIX + "getProducts");
    this.killBillRegion = killBillRegion;
  }

  public String getApiKey() {
    if (apiKey == null || apiKey.isEmpty()) {
      return getEnvironmentVariables(AMBERFLO_KB_APIKEY, DEFAULT_APIKEY);
    }
    return apiKey;
  }

  public String getPlans() {
    if (getPlans == null || getPlans.isEmpty()) {
      return getEnvironmentVariables(AMBERFLO_KB_GET_PLANS, DEFAULT_GET_PLANS);
    }
    return getPlans;
  }

  public String getPrices() {
    if (getPrices == null || getPrices.isEmpty()) {
      return getEnvironmentVariables(AMBERFLO_KB_GET_PRICES, DEFAULT_GET_PRICES);
    }
    return getPrices;
  }

  public String getProducts() {
    if (getProducts == null || getProducts.isEmpty()) {
      return getEnvironmentVariables(AMBERFLO_KB_GET_PRODUCTS, DEFAULT_GET_PRODUCTS);
    }
    return getProducts;
  }

  public String getUrl() {
    if (url == null || url.isEmpty()) {
      return getEnvironmentVariables("", DEFAULT_URL);
    }
    return url;
  }

  public String getKillbillRegion() {

    return killBillRegion;
  }

  private String getEnvironmentVariables(String envKey, String defaultValue) {
    Map<String, String> env = System.getenv();

    String value = env.get(envKey);

    if (value == null || value.isEmpty()) {
      return defaultValue;
    }

    return value;
  }
}
