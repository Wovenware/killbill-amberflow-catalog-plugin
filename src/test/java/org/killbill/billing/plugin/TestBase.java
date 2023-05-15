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

package org.killbill.billing.plugin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.plugin.amberflo.catalog.api.CatalogPluginApiImpl;
import org.killbill.billing.plugin.amberflo.catalog.core.CatalogActivator;
import org.killbill.billing.plugin.amberflo.catalog.core.CatalogConfigurationHandler;
import org.killbill.billing.plugin.amberflo.catalog.core.CatalogConfigurationProperties;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.ClockMock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBase {

  private static final String PLANS_URL = "/plans";
  private static final String PRICES_URL = "/prices";
  private static final String PRICES_URL_WITH_ID_ONE =
      "/prices?id=2c61c22f-537f-4e7c-9216-4ce58b9b16c1";
  private static final String PRICES_URL_WITH_ID_TWO =
      "/prices?id=d220738e-4cb9-4b31-9257-be27b4bcfbba";
  private static final String PRODUCTS_URL = "/products";
  private static final String CONTENT_TYPE = "Content-Type";

  private static final String CONTENT_DATA = "application/json";

  protected static final String PROPERTIES_FILE_NAME = "catalog.properties";
  public static final Currency DEFAULT_CURRENCY = Currency.USD;
  public static final String DEFAULT_COUNTRY = "US";

  protected ClockMock clock;
  protected CallContext context;
  protected Account account;
  protected CatalogPluginApiImpl catalogPluginApiImpl;
  protected OSGIKillbillAPI killbillApi;
  protected CustomFieldUserApi customFieldUserApi;
  protected InvoiceUserApi invoiceUserApi;
  protected AccountUserApi accountUserApi;
  protected PaymentApi paymentApi;
  protected PaymentMethod paymentMethod;
  private WireMockServer wireMockServer;
  CatalogConfigurationHandler catalogConfigurationHandler;

  private static final Logger logger = LoggerFactory.getLogger(TestBase.class);

  @Before
  public void setUp() throws Exception {
    setUpBeforeSuite();

    System.setProperty("REGION", DEFAULT_COUNTRY);
    logger.info("[setUp] initialization");

    clock = new ClockMock();
    context = Mockito.mock(CallContext.class);
    Mockito.when(context.getTenantId()).thenReturn(UUID.randomUUID());
    Mockito.when(context.getUserName()).thenReturn(UUID.randomUUID().toString());
    Mockito.when(context.getCallOrigin()).thenReturn(CallOrigin.INTERNAL);
    account = TestUtils.buildAccount(DEFAULT_CURRENCY, DEFAULT_COUNTRY);
    Mockito.when(account.getEmail()).thenReturn(UUID.randomUUID().toString() + "@example.com");
    killbillApi = TestUtils.buildOSGIKillbillAPI(account);
    customFieldUserApi = Mockito.mock(CustomFieldUserApi.class);
    Mockito.when(killbillApi.getCustomFieldUserApi()).thenReturn(customFieldUserApi);
    invoiceUserApi = Mockito.mock(InvoiceUserApi.class);
    Mockito.when(killbillApi.getInvoiceUserApi()).thenReturn(invoiceUserApi);
    accountUserApi = Mockito.mock(AccountUserApi.class);
    Mockito.when(killbillApi.getAccountUserApi()).thenReturn(accountUserApi);
    Mockito.when(accountUserApi.getAccountById(account.getId(), context)).thenReturn(account);
    paymentApi = Mockito.mock(PaymentApi.class);
    Mockito.when(killbillApi.getPaymentApi()).thenReturn(paymentApi);
    paymentMethod = Mockito.mock(PaymentMethod.class);
    Mockito.when(
            paymentApi.getPaymentMethodById(
                Mockito.any(UUID.class),
                Mockito.any(Boolean.class),
                Mockito.any(Boolean.class),
                Mockito.anyList(),
                Mockito.any(TenantContext.class)))
        .thenReturn(paymentMethod);

    catalogConfigurationHandler =
        new CatalogConfigurationHandler(null, CatalogActivator.PLUGIN_NAME, killbillApi);

    catalogPluginApiImpl = new CatalogPluginApiImpl(catalogConfigurationHandler);

    setUpIntegration(PROPERTIES_FILE_NAME);
  }

  protected void setUpIntegration(String fileName) throws IOException {
    logger.info("[setUpIntegration] initialization");
    final Properties properties = TestUtils.loadProperties(fileName);

    final CatalogConfigurationProperties catalogConfigProperties =
        new CatalogConfigurationProperties(properties, "");
    catalogConfigurationHandler.setDefaultConfigurable(catalogConfigProperties);
  }

  private void setUpBeforeSuite() throws IOException, SQLException {
    logger.info("[setUpBeforeSuite] initialization");
    wireMockServer = new WireMockServer(wireMockConfig().port(7040));
    WireMock.configureFor("localhost", 7040);
    wireMockServer.start();
    setGetPlans();
    setGetPrices();
    setGetProducts();
    setGetPricesWithIDOne();
    setGetPricesWithIDTwo();
  }

  private void setGetPlans() {
    stubFor(
        get(urlEqualTo(PLANS_URL))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, CONTENT_DATA)
                    .withBody(
                        "[\r\n"
                            + "  {\r\n"
                            + "    \"id\": \"f9c85b3e-2cfb-44c8-b79d-b0a4bd7253cf\",\r\n"
                            + "    \"productId\": \"1\",\r\n"
                            + "    \"productItemPriceIdsMap\": {},\r\n"
                            + "    \"billingPeriod\": {\r\n"
                            + "      \"interval\": \"month\",\r\n"
                            + "      \"intervalsCount\": 1\r\n"
                            + "    },\r\n"
                            + "    \"planLevelFreeTier\": null,\r\n"
                            + "    \"invoiceBasedFeeIds\": null,\r\n"
                            + "    \"productPlanName\": \"pistol-monthly\",\r\n"
                            + "    \"description\": \"\",\r\n"
                            + "    \"lastUpdateTimeInMillis\": 1679937405691,\r\n"
                            + "    \"feeMap\": {\r\n"
                            + "      \"17e04708-2bec-4117-80f1-7ed2157996e7\": {\r\n"
                            + "        \"id\": \"17e04708-2bec-4117-80f1-7ed2157996e7\",\r\n"
                            + "        \"name\": \"Pistol\",\r\n"
                            + "        \"description\": \"Recurring fee\",\r\n"
                            + "        \"cost\": 29.95,\r\n"
                            + "        \"isOneTimeFee\": false,\r\n"
                            + "        \"isProrated\": true,\r\n"
                            + "        \"prorateToDay\": true,\r\n"
                            + "        \"discountable\": false,\r\n"
                            + "        \"prepayable\": true\r\n"
                            + "      }\r\n"
                            + "    },\r\n"
                            + "    \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "    \"isDefault\": true,\r\n"
                            + "    \"successorPlanId\": null,\r\n"
                            + "    \"transitionStrategy\": null,\r\n"
                            + "    \"prepaidBuyingRules\": {\r\n"
                            + "      \"planFixedFeeIdsToPrepay\": [\r\n"
                            + "        \"17e04708-2bec-4117-80f1-7ed2157996e7\"\r\n"
                            + "      ]\r\n"
                            + "    }\r\n"
                            + "  },\r\n"
                            + "  {\r\n"
                            + "    \"id\": \"6b408193-3d29-4f06-863b-ab9da3f653b4\",\r\n"
                            + "    \"productId\": \"1\",\r\n"
                            + "    \"productItemPriceIdsMap\": {\r\n"
                            + "      \"e8cd3e80-b0c7-4cd3-8bb6-46465b5c989a\": \"65741013-ffdc-48f5-9525-7b1ccf4e685a\"\r\n"
                            + "    },\r\n"
                            + "    \"billingPeriod\": {\r\n"
                            + "      \"interval\": \"year\",\r\n"
                            + "      \"intervalsCount\": 1\r\n"
                            + "    },\r\n"
                            + "    \"planLevelFreeTier\": null,\r\n"
                            + "    \"invoiceBasedFeeIds\": null,\r\n"
                            + "    \"productPlanName\": \"bullets-usage-in-arrear copy 03/31/2023\",\r\n"
                            + "    \"description\": \"bullets-usage-in-arrear\",\r\n"
                            + "    \"lastUpdateTimeInMillis\": 1680705818819,\r\n"
                            + "    \"feeMap\": {},\r\n"
                            + "    \"lockingStatus\": \"open\",\r\n"
                            + "    \"isDefault\": false,\r\n"
                            + "    \"successorPlanId\": null,\r\n"
                            + "    \"transitionStrategy\": null,\r\n"
                            + "    \"prepaidBuyingRules\": null\r\n"
                            + "  },\r\n"
                            + "  {\r\n"
                            + "    \"id\": \"401fc505-6947-4ba4-aa59-c8abc0d6128d\",\r\n"
                            + "    \"productId\": \"1\",\r\n"
                            + "    \"productItemPriceIdsMap\": {\r\n"
                            + "      \"bdcc913e-8754-4d8e-ae95-181dedb02f17\": \"2c61c22f-537f-4e7c-9216-4ce58b9b16c1\",\r\n"
                            + "      \"e8cd3e80-b0c7-4cd3-8bb6-46465b5c989a\": \"d220738e-4cb9-4b31-9257-be27b4bcfbba\"\r\n"
                            + "    },\r\n"
                            + "    \"billingPeriod\": {\r\n"
                            + "      \"interval\": \"month\",\r\n"
                            + "      \"intervalsCount\": 1\r\n"
                            + "    },\r\n"
                            + "    \"planLevelFreeTier\": null,\r\n"
                            + "    \"invoiceBasedFeeIds\": null,\r\n"
                            + "    \"productPlanName\": \"test copy 04/03/2023 copy 04/04/2023\",\r\n"
                            + "    \"description\": \"\",\r\n"
                            + "    \"lastUpdateTimeInMillis\": 1680703884631,\r\n"
                            + "    \"feeMap\": {\r\n"
                            + "      \"c93e5200-49b2-4685-a44a-cffae702bd72\": {\r\n"
                            + "        \"id\": \"c93e5200-49b2-4685-a44a-cffae702bd72\",\r\n"
                            + "        \"name\": \"testFixedRate\",\r\n"
                            + "        \"description\": \"Recurring fee\",\r\n"
                            + "        \"cost\": 111,\r\n"
                            + "        \"isOneTimeFee\": false,\r\n"
                            + "        \"isProrated\": false,\r\n"
                            + "        \"prorateToDay\": false,\r\n"
                            + "        \"discountable\": false,\r\n"
                            + "        \"prepayable\": true\r\n"
                            + "      },\r\n"
                            + "      \"4c6b0b9a-02c0-4d41-8576-c19a5ebb2077\": {\r\n"
                            + "        \"id\": \"4c6b0b9a-02c0-4d41-8576-c19a5ebb2077\",\r\n"
                            + "        \"name\": \"Test2\",\r\n"
                            + "        \"description\": \"Recurring fee\",\r\n"
                            + "        \"cost\": 111,\r\n"
                            + "        \"isOneTimeFee\": false,\r\n"
                            + "        \"isProrated\": false,\r\n"
                            + "        \"prorateToDay\": false,\r\n"
                            + "        \"discountable\": false,\r\n"
                            + "        \"prepayable\": true\r\n"
                            + "      },\r\n"
                            + "      \"b222b139-38b7-4c8d-b1b4-82f5300f0b76\": {\r\n"
                            + "        \"id\": \"b222b139-38b7-4c8d-b1b4-82f5300f0b76\",\r\n"
                            + "        \"name\": \"Test3\",\r\n"
                            + "        \"description\": \"Recurring fee\",\r\n"
                            + "        \"cost\": 89,\r\n"
                            + "        \"isOneTimeFee\": false,\r\n"
                            + "        \"isProrated\": false,\r\n"
                            + "        \"prorateToDay\": false,\r\n"
                            + "        \"discountable\": true,\r\n"
                            + "        \"prepayable\": true\r\n"
                            + "      },\r\n"
                            + "      \"9e3a5c73-740c-4114-8b57-b00f665b6322\": {\r\n"
                            + "        \"id\": \"9e3a5c73-740c-4114-8b57-b00f665b6322\",\r\n"
                            + "        \"name\": \"One time\",\r\n"
                            + "        \"description\": \"One Time fee\",\r\n"
                            + "        \"cost\": 111,\r\n"
                            + "        \"isOneTimeFee\": true,\r\n"
                            + "        \"isProrated\": false,\r\n"
                            + "        \"prorateToDay\": false,\r\n"
                            + "        \"discountable\": false,\r\n"
                            + "        \"prepayable\": true\r\n"
                            + "      }\r\n"
                            + "    },\r\n"
                            + "    \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "    \"isDefault\": false,\r\n"
                            + "    \"successorPlanId\": null,\r\n"
                            + "    \"transitionStrategy\": null,\r\n"
                            + "    \"prepaidBuyingRules\": null\r\n"
                            + "  }"
                            + "]")));
  }

  private void setGetPrices() {
    stubFor(
        get(urlEqualTo(PRICES_URL))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, CONTENT_DATA)
                    .withBody(
                        "[\r\n"
                            + "  {\r\n"
                            + "    \"productItemId\": \"e8cd3e80-b0c7-4cd3-8bb6-46465b5c989a\",\r\n"
                            + "    \"defaultItemPriceId\": null,\r\n"
                            + "    \"lockingStatus\": \"close_to_deletions\",\r\n"
                            + "    \"productItemPriceMap\": {\r\n"
                            + "      \"9ec9e161-73e6-4282-a4e2-a9fbd4441450\": {\r\n"
                            + "        \"id\": \"9ec9e161-73e6-4282-a4e2-a9fbd4441450\",\r\n"
                            + "        \"productItemId\": \"e8cd3e80-b0c7-4cd3-8bb6-46465b5c989a\",\r\n"
                            + "        \"price\": {\r\n"
                            + "          \"type\": \"LeafNode\",\r\n"
                            + "          \"tiers\": [\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 0,\r\n"
                            + "              \"batchSize\": 10,\r\n"
                            + "              \"pricePerBatch\": 2.95\r\n"
                            + "            },\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 1000,\r\n"
                            + "              \"batchSize\": 100,\r\n"
                            + "              \"pricePerBatch\": 5.95\r\n"
                            + "            },\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 100000,\r\n"
                            + "              \"batchSize\": 1,\r\n"
                            + "              \"pricePerBatch\": 0\r\n"
                            + "            }\r\n"
                            + "          ],\r\n"
                            + "          \"allowPartialBatch\": true\r\n"
                            + "        },\r\n"
                            + "        \"productItemPriceName\": \"9ec9e161-73e6-4282-a4e2-a9fbd4441450\",\r\n"
                            + "        \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "        \"lastUpdateTimeInMillis\": 1679937775043\r\n"
                            + "      },\r\n"
                            + "      \"65741013-ffdc-48f5-9525-7b1ccf4e685a\": {\r\n"
                            + "        \"id\": \"65741013-ffdc-48f5-9525-7b1ccf4e685a\",\r\n"
                            + "        \"productItemId\": \"e8cd3e80-b0c7-4cd3-8bb6-46465b5c989a\",\r\n"
                            + "        \"price\": {\r\n"
                            + "          \"type\": \"LeafNode\",\r\n"
                            + "          \"tiers\": [\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 0,\r\n"
                            + "              \"batchSize\": 10,\r\n"
                            + "              \"pricePerBatch\": 2.95\r\n"
                            + "            },\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 1000,\r\n"
                            + "              \"batchSize\": 100,\r\n"
                            + "              \"pricePerBatch\": 5.95\r\n"
                            + "            },\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 100000,\r\n"
                            + "              \"batchSize\": 1,\r\n"
                            + "              \"pricePerBatch\": 0\r\n"
                            + "            }\r\n"
                            + "          ],\r\n"
                            + "          \"allowPartialBatch\": true\r\n"
                            + "        },\r\n"
                            + "        \"productItemPriceName\": \"65741013-ffdc-48f5-9525-7b1ccf4e685a\",\r\n"
                            + "        \"lockingStatus\": \"open\",\r\n"
                            + "        \"lastUpdateTimeInMillis\": null\r\n"
                            + "      },\r\n"
                            + "      \"d220738e-4cb9-4b31-9257-be27b4bcfbba\": {\r\n"
                            + "        \"id\": \"d220738e-4cb9-4b31-9257-be27b4bcfbba\",\r\n"
                            + "        \"productItemId\": \"e8cd3e80-b0c7-4cd3-8bb6-46465b5c989a\",\r\n"
                            + "        \"price\": {\r\n"
                            + "          \"type\": \"LeafNode\",\r\n"
                            + "          \"tiers\": [\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 0,\r\n"
                            + "              \"batchSize\": 1,\r\n"
                            + "              \"pricePerBatch\": 0\r\n"
                            + "            },\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 1,\r\n"
                            + "              \"batchSize\": 1,\r\n"
                            + "              \"pricePerBatch\": 0\r\n"
                            + "            }\r\n"
                            + "          ],\r\n"
                            + "          \"allowPartialBatch\": false\r\n"
                            + "        },\r\n"
                            + "        \"productItemPriceName\": \"d220738e-4cb9-4b31-9257-be27b4bcfbba\",\r\n"
                            + "        \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "        \"lastUpdateTimeInMillis\": 1680703884632\r\n"
                            + "      }\r\n"
                            + "    },\r\n"
                            + "    \"lastUpdateTimeInMillis\": 1680700849246\r\n"
                            + "  },\r\n"
                            + "  {\r\n"
                            + "    \"productItemId\": \"bdcc913e-8754-4d8e-ae95-181dedb02f17\",\r\n"
                            + "    \"defaultItemPriceId\": null,\r\n"
                            + "    \"lockingStatus\": \"close_to_deletions\",\r\n"
                            + "    \"productItemPriceMap\": {\r\n"
                            + "      \"c40abc7b-44e8-4edc-81e2-17df45c6bedf\": {\r\n"
                            + "        \"id\": \"c40abc7b-44e8-4edc-81e2-17df45c6bedf\",\r\n"
                            + "        \"productItemId\": \"bdcc913e-8754-4d8e-ae95-181dedb02f17\",\r\n"
                            + "        \"price\": {\r\n"
                            + "          \"type\": \"PricePerUnitLeafNode\",\r\n"
                            + "          \"tiers\": [\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 0,\r\n"
                            + "              \"batchSize\": 1,\r\n"
                            + "              \"pricePerBatch\": 11\r\n"
                            + "            }\r\n"
                            + "          ],\r\n"
                            + "          \"allowPartialBatch\": false\r\n"
                            + "        },\r\n"
                            + "        \"productItemPriceName\": \"c40abc7b-44e8-4edc-81e2-17df45c6bedf\",\r\n"
                            + "        \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "        \"lastUpdateTimeInMillis\": 1680533607195\r\n"
                            + "      },\r\n"
                            + "      \"02d61556-4118-4ef2-b235-9616ee78dc10\": {\r\n"
                            + "        \"id\": \"02d61556-4118-4ef2-b235-9616ee78dc10\",\r\n"
                            + "        \"productItemId\": \"bdcc913e-8754-4d8e-ae95-181dedb02f17\",\r\n"
                            + "        \"price\": {\r\n"
                            + "          \"type\": \"PricePerBlockLeafNode\",\r\n"
                            + "          \"tiers\": [\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 0,\r\n"
                            + "              \"batchSize\": 11,\r\n"
                            + "              \"pricePerBatch\": 11\r\n"
                            + "            }\r\n"
                            + "          ],\r\n"
                            + "          \"allowPartialBatch\": false\r\n"
                            + "        },\r\n"
                            + "        \"productItemPriceName\": \"02d61556-4118-4ef2-b235-9616ee78dc10\",\r\n"
                            + "        \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "        \"lastUpdateTimeInMillis\": 1680538961261\r\n"
                            + "      },\r\n"
                            + "      \"2c61c22f-537f-4e7c-9216-4ce58b9b16c1\": {\r\n"
                            + "        \"id\": \"2c61c22f-537f-4e7c-9216-4ce58b9b16c1\",\r\n"
                            + "        \"productItemId\": \"bdcc913e-8754-4d8e-ae95-181dedb02f17\",\r\n"
                            + "        \"price\": {\r\n"
                            + "          \"type\": \"PricePerBlockLeafNode\",\r\n"
                            + "          \"tiers\": [\r\n"
                            + "            {\r\n"
                            + "              \"startAfterUnit\": 0,\r\n"
                            + "              \"batchSize\": 11,\r\n"
                            + "              \"pricePerBatch\": 11\r\n"
                            + "            }\r\n"
                            + "          ],\r\n"
                            + "          \"allowPartialBatch\": false\r\n"
                            + "        },\r\n"
                            + "        \"productItemPriceName\": \"02d61556-4118-4ef2-b235-9616ee78dc10 2c61c22f-537f-4e7c-9216-4ce58b9b16c1\",\r\n"
                            + "        \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "        \"lastUpdateTimeInMillis\": 1680703884632\r\n"
                            + "      }\r\n"
                            + "    },\r\n"
                            + "    \"lastUpdateTimeInMillis\": 1680636858892\r\n"
                            + "  }\r\n"
                            + "]")));
  }

  private void setGetPricesWithIDOne() {
    stubFor(
        get(urlEqualTo(PRICES_URL_WITH_ID_ONE))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, CONTENT_DATA)
                    .withBody(
                        "{\r\n"
                            + "  \"id\": \"2c61c22f-537f-4e7c-9216-4ce58b9b16c1\",\r\n"
                            + "  \"productItemId\": \"bdcc913e-8754-4d8e-ae95-181dedb02f17\",\r\n"
                            + "  \"price\": {\r\n"
                            + "    \"type\": \"PricePerBlockLeafNode\",\r\n"
                            + "    \"tiers\": [\r\n"
                            + "      {\r\n"
                            + "        \"startAfterUnit\": 0,\r\n"
                            + "        \"batchSize\": 11,\r\n"
                            + "        \"pricePerBatch\": 11\r\n"
                            + "      }\r\n"
                            + "    ],\r\n"
                            + "    \"allowPartialBatch\": false\r\n"
                            + "  },\r\n"
                            + "  \"productItemPriceName\": \"02d61556-4118-4ef2-b235-9616ee78dc10 2c61c22f-537f-4e7c-9216-4ce58b9b16c1\",\r\n"
                            + "  \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "  \"lastUpdateTimeInMillis\": 1680703884632\r\n"
                            + "}")));
  }

  private void setGetPricesWithIDTwo() {
    stubFor(
        get(urlEqualTo(PRICES_URL_WITH_ID_TWO))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, CONTENT_DATA)
                    .withBody(
                        "{\r\n"
                            + "  \"id\": \"d220738e-4cb9-4b31-9257-be27b4bcfbba\",\r\n"
                            + "  \"productItemId\": \"e8cd3e80-b0c7-4cd3-8bb6-46465b5c989a\",\r\n"
                            + "  \"price\": {\r\n"
                            + "    \"type\": \"LeafNode\",\r\n"
                            + "    \"tiers\": [\r\n"
                            + "      {\r\n"
                            + "        \"startAfterUnit\": 0,\r\n"
                            + "        \"batchSize\": 1,\r\n"
                            + "        \"pricePerBatch\": 0\r\n"
                            + "      },\r\n"
                            + "      {\r\n"
                            + "        \"startAfterUnit\": 1,\r\n"
                            + "        \"batchSize\": 1,\r\n"
                            + "        \"pricePerBatch\": 0\r\n"
                            + "      }\r\n"
                            + "    ],\r\n"
                            + "    \"allowPartialBatch\": false\r\n"
                            + "  },\r\n"
                            + "  \"productItemPriceName\": \"d220738e-4cb9-4b31-9257-be27b4bcfbba\",\r\n"
                            + "  \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "  \"lastUpdateTimeInMillis\": 1680703884632\r\n"
                            + "}")));
  }

  private void setGetProducts() {
    stubFor(
        get(urlEqualTo(PRODUCTS_URL))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, CONTENT_DATA)
                    .withBody(
                        "[\r\n"
                            + "  {\r\n"
                            + "    \"id\": \"e8cd3e80-b0c7-4cd3-8bb6-46465b5c989a\",\r\n"
                            + "    \"productId\": \"1\",\r\n"
                            + "    \"meterApiName\": \"BulletsAPI\",\r\n"
                            + "    \"productItemName\": \"Bullets\",\r\n"
                            + "    \"description\": \"Bullets\",\r\n"
                            + "    \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "    \"lastUpdateTimeInMillis\": 1679937775043\r\n"
                            + "  },\r\n"
                            + "  {\r\n"
                            + "    \"id\": \"bdcc913e-8754-4d8e-ae95-181dedb02f17\",\r\n"
                            + "    \"productId\": \"1\",\r\n"
                            + "    \"meterApiName\": \"RocksApi\",\r\n"
                            + "    \"productItemName\": \"Rocks\",\r\n"
                            + "    \"description\": \"\",\r\n"
                            + "    \"lockingStatus\": \"close_to_changes\",\r\n"
                            + "    \"lastUpdateTimeInMillis\": 1680533607195\r\n"
                            + "  }\r\n"
                            + "]")));
  }

  @After
  public void tearDownAfterSuite() throws IOException {
    wireMockServer.stop();
  }
}
