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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.catalog.api.boilerplate.DurationImp;
import org.killbill.billing.catalog.api.boilerplate.InternationalPriceImp;
import org.killbill.billing.catalog.api.boilerplate.PlanImp;
import org.killbill.billing.catalog.api.boilerplate.PlanPhaseImp;
import org.killbill.billing.catalog.api.boilerplate.PriceImp;
import org.killbill.billing.catalog.api.boilerplate.PriceListImp;
import org.killbill.billing.catalog.api.boilerplate.ProductImp;
import org.killbill.billing.catalog.api.boilerplate.RecurringImp;
import org.killbill.billing.catalog.api.boilerplate.TierImp;
import org.killbill.billing.catalog.api.boilerplate.TieredBlockImp;
import org.killbill.billing.catalog.api.boilerplate.UnitImp;
import org.killbill.billing.catalog.api.boilerplate.UsageImp;
import org.killbill.billing.catalog.api.rules.CaseBillingAlignment;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;
import org.killbill.billing.catalog.api.rules.CaseChangePlanPolicy;
import org.killbill.billing.catalog.api.rules.CaseCreateAlignment;
import org.killbill.billing.catalog.api.rules.PlanRules;
import org.killbill.billing.catalog.api.rules.boilerplate.CaseBillingAlignmentImp;
import org.killbill.billing.catalog.api.rules.boilerplate.CaseCancelPolicyImp;
import org.killbill.billing.catalog.api.rules.boilerplate.CaseChangePlanAlignmentImp;
import org.killbill.billing.catalog.api.rules.boilerplate.CaseChangePlanPolicyImp;
import org.killbill.billing.catalog.api.rules.boilerplate.CaseCreateAlignmentImp;
import org.killbill.billing.catalog.api.rules.boilerplate.PlanRulesImp;
import org.killbill.billing.plugin.amberflo.catalog.client.model.Fee;
import org.killbill.billing.plugin.amberflo.catalog.client.model.ProductItem;
import org.killbill.billing.plugin.amberflo.catalog.client.model.ProductPlans;
import org.killbill.billing.plugin.amberflo.catalog.client.model.UsageResponse;
import org.killbill.billing.plugin.amberflo.catalog.client.model.UsageTier;

public class AmberfloUtils {

  private static final String DEFAULT_NAME = "Default";

  private final AmberfloHttpClientImpl amberfloHttpClientImpl;

  // This set will contain a global list of all the products
  private Set<Product> products = new HashSet<>();

  public Set<Product> getProducts() {
    return products;
  }

  public AmberfloUtils(AmberfloHttpClientImpl amberfloHttpClientImpl) {
    super();
    this.amberfloHttpClientImpl = amberfloHttpClientImpl;
  }

  /*
   * Receives the list of amberflo plans and converts it to the Kill Bill format.
   * Plans are obtained from the feeMap and ProductItemPricesIdsMap and
   * are processed differently depending on the map they are obtained from.
   */
  public List<Plan> convertToKillBillPlanModel(
      List<ProductPlans> amberfloPlans, List<ProductItem> productItems)
      throws URISyntaxException, IOException {

    List<Plan> killBillPlans = new ArrayList<>();

    for (ProductPlans plan : amberfloPlans) {
      if (plan.getFeeMap() != null && !plan.getFeeMap().isEmpty()) {
        killBillPlans.addAll(getPlansFromFeeMap(plan));
      }
      if (plan.getProductItemPriceIdsMap() != null && !plan.getProductItemPriceIdsMap().isEmpty()) {
        killBillPlans.addAll(getPlansFromPriceIdsMap(plan, productItems));
      }
    }

    return killBillPlans;
  }

  private List<Plan> getPlansFromPriceIdsMap(
      ProductPlans amberfloPlan, List<ProductItem> productItems)
      throws URISyntaxException, IOException {

    List<Plan> killBillPlans = new ArrayList<>();
    String planName = "";
    String planPrettyName = "";

    for (Map.Entry<String, String> entry : amberfloPlan.getProductItemPriceIdsMap().entrySet()) {
      for (ProductItem productItem : productItems) {
        if (entry.getKey().equals(productItem.getId())) {
          planName = entry.getValue();
          planPrettyName =
              productItem.getProductItemName() + getPlanNameSuffix(amberfloPlan, false);

          killBillPlans.add(
              new PlanImp.Builder<>()
                  .withName(planName)
                  .withPriceList(
                      new PriceListImp.Builder<>()
                          .withName(DEFAULT_NAME)
                          .withPrettyName(DEFAULT_NAME)
                          .build())
                  .withPrettyName(planPrettyName)
                  .withProduct(
                      new ProductImp.Builder<>()
                          .withName(productItem.getId())
                          .withPrettyName(productItem.getProductItemName())
                          .withCategory(ProductCategory.BASE)
                          .withAvailable(new ArrayList<>())
                          .withIncluded(new ArrayList<>())
                          .build())
                  .withInitialPhases(new PlanPhase[0])
                  .withFinalPhase(
                      new PlanPhaseImp.Builder<>()
                          .withPhaseType(PhaseType.EVERGREEN)
                          .withDuration(
                              new DurationImp.Builder<>().withUnit(TimeUnit.UNLIMITED).build())
                          .withUsages(
                              findUsages(
                                  amberfloPlan, entry.getValue(), productItem, planPrettyName))
                          .build())
                  .build());

          products.add(
              new ProductImp.Builder<>()
                  .withName(productItem.getId())
                  .withPrettyName(productItem.getProductItemName())
                  .withCategory(ProductCategory.BASE)
                  .withAvailable(new ArrayList<>())
                  .withIncluded(new ArrayList<>())
                  .build());
        }
      }
    }
    return killBillPlans;
  }

  private List<Plan> getPlansFromFeeMap(ProductPlans amberfloPlan) {
    Map<String, Fee> feeMap = amberfloPlan.getFeeMap();
    List<Plan> killBillPlans = new ArrayList<>();
    boolean isOneTimeFee = false;
    String planName = "";
    String planPrettyName = "";

    for (Map.Entry<String, Fee> entry : feeMap.entrySet()) {
      isOneTimeFee = Boolean.parseBoolean(entry.getValue().getIsOneTimeFee().toLowerCase());
      planName = entry.getValue().getId() + "_" + entry.getValue().getName();
      planPrettyName = entry.getValue().getName() + getPlanNameSuffix(amberfloPlan, isOneTimeFee);

      killBillPlans.add(
          new PlanImp.Builder<>()
              .withName(planName)
              .withPrettyName(planPrettyName)
              .withPriceList(
                  new PriceListImp.Builder<>()
                      .withName(DEFAULT_NAME)
                      .withPrettyName(DEFAULT_NAME)
                      .build())
              .withProduct(
                  new ProductImp.Builder<>()
                      .withName(entry.getValue().getId())
                      .withPrettyName(entry.getValue().getName())
                      .withCategory(ProductCategory.BASE)
                      .withAvailable(new ArrayList<>())
                      .withIncluded(new ArrayList<>())
                      .build())
              .withInitialPhases(new PlanPhase[0])
              .withFinalPhase(
                  new PlanPhaseImp.Builder<>()
                      .withPhaseType(getPhaseType(isOneTimeFee))
                      .withDuration(findDuration(isOneTimeFee))
                      .withRecurring(
                          new RecurringImp.Builder<>()
                              .withBillingPeriod(findBillingPeriod(amberfloPlan, isOneTimeFee))
                              .withRecurringPrice(
                                  findInternationalPrice(entry.getValue().getCost()))
                              .build())
                      .build())
              .withRecurringBillingPeriod(findBillingPeriod(amberfloPlan, isOneTimeFee))
              .build());

      products.add(
          new ProductImp.Builder<>()
              .withName(entry.getValue().getId())
              .withPrettyName(entry.getValue().getName())
              .withCategory(ProductCategory.BASE)
              .withAvailable(new ArrayList<>())
              .withIncluded(new ArrayList<>())
              .build());
    }
    return killBillPlans;
  }

  private Usage[] findUsages(
      ProductPlans amberfloPlan,
      String productItemPriceId,
      ProductItem productItem,
      String planPrettyName)
      throws URISyntaxException, IOException {

    UsageResponse usageResponse =
        amberfloHttpClientImpl.requestListAllPaymentPricing(productItemPriceId);
    Usage[] usages = new Usage[1];

    String usageName = planPrettyName + "-Usage";

    usages[0] =
        new UsageImp.Builder<>()
            .withName(usageName)
            .withBillingPeriod(findBillingPeriod(amberfloPlan, false))
            .withBillingMode(BillingMode.IN_ARREAR)
            .withUsageType(UsageType.CONSUMABLE)
            .withTiers(findTiers(usageResponse, productItem))
            .build();

    return usages;
  }

  private Tier[] findTiers(UsageResponse usageResponse, ProductItem productItem) {
    UsageTier[] amberfloTiers = usageResponse.getPrice().getTiers();
    Tier[] killBillTiers = new Tier[amberfloTiers.length];

    for (int i = 0; i < amberfloTiers.length; i++) {
      BigDecimal size = calculateTieredBlockSize(amberfloTiers, i);
      killBillTiers[i] =
          new TierImp.Builder<>()
              .withTieredBlocks(findTieredBlocks(amberfloTiers[i], size, productItem))
              .build();
    }
    return killBillTiers;
  }

  private TieredBlock[] findTieredBlocks(
      UsageTier amberfloTier, BigDecimal size, ProductItem productItem) {
    TieredBlock[] tieredBlocks = new TieredBlock[1];

    tieredBlocks[0] =
        new TieredBlockImp.Builder<>()
            .withMax(BigDecimal.valueOf(amberfloTier.getBatchSize()))
            .withPrice(findInternationalPrice(amberfloTier.getPricePerBatch()))
            .withSize(size)
            .withUnit(
                new UnitImp.Builder<>()
                    .withName(productItem.getMeterApiName())
                    .withPrettyName(productItem.getProductItemName())
                    .build())
            .build();

    return tieredBlocks;
  }

  /*
   * Calculates the size of a tiered block by doing a math operation using the
   * startAfterUnit of the current tier and the tier that follows
   */
  private BigDecimal calculateTieredBlockSize(UsageTier[] amberfloTiers, int position) {
    double currentUnit = amberfloTiers[position].getStartAfterUnit();

    int nextPosition = position + 1;
    if (nextPosition >= amberfloTiers.length) {
      // Arbitrary number large enough that ensures it will not be met
      return BigDecimal.valueOf(100000.0);
    }

    double nextUnit = amberfloTiers[nextPosition].getStartAfterUnit();
    double batchSize = amberfloTiers[position].getBatchSize();
    double result = (nextUnit - currentUnit) / batchSize;

    return BigDecimal.valueOf(result);
  }

  // Returns the duration for the feeMap case
  private Duration findDuration(boolean isOneTimeFee) {

    TimeUnit timeUnit = TimeUnit.DAYS;
    int number = 1;

    if (isOneTimeFee) {
      return new DurationImp.Builder<>().withUnit(timeUnit).withNumber(number).build();
    }

    return new DurationImp.Builder<>().withUnit(TimeUnit.UNLIMITED).build();
  }

  // Converts the cost to InternationalPrice format
  private InternationalPrice findInternationalPrice(double cost) {
    Price[] prices = new Price[1];

    prices[0] =
        new PriceImp.Builder<>()
            .withCurrency(Currency.USD)
            .withValue(BigDecimal.valueOf(cost))
            .build();

    return new InternationalPriceImp.Builder<>().withPrices(prices).withIsZero(false).build();
  }

  // Returns the billing period for the feeMap case
  private BillingPeriod findBillingPeriod(ProductPlans amberfloPlan, boolean isOneTimeFee) {

    if (isOneTimeFee) return BillingPeriod.DAILY;

    String period = amberfloPlan.getBillingPeriod().getInterval().toLowerCase();

    return BillingPeriodEnum.parseBillingPeriod(period).getBillingPeriod();
  }

  private PhaseType getPhaseType(boolean isOneTimeFee) {
    if (isOneTimeFee) {
      return PhaseType.FIXEDTERM;
    } else {
      return PhaseType.EVERGREEN;
    }
  }

  private String getPlanNameSuffix(ProductPlans amberfloPlan, boolean isOneTimeFee) {

    if (isOneTimeFee) {
      return "-One-Time";
    }

    String period = amberfloPlan.getBillingPeriod().getInterval().toLowerCase();

    return BillingPeriodEnum.parseBillingPeriod(period).getSuffix();
  }

  public List<ProductPlans> validatePlans(List<ProductPlans> planList) {
    List<ProductPlans> validList = new ArrayList<>();
    String validLockingStatus = "close_to_changes";

    for (ProductPlans plan : planList) {
      if (plan.getLockingStatus().equals(validLockingStatus)) {
        validList.add(plan);
      }
    }
    return validList;
  }

  public Set<Product> getProducts(List<ProductItem> productItems) {

    Set<Product> productList = new HashSet<>();

    for (ProductItem item : productItems) {
      productList.add(
          new ProductImp.Builder<>()
              .withName(item.getId())
              .withPrettyName(item.getProductItemName())
              .withCategory(ProductCategory.BASE)
              .withAvailable(new ArrayList<>())
              .withIncluded(new ArrayList<>())
              .build());
    }

    return productList;
  }

  public Set<Unit> getUnits(List<ProductItem> productItems) {

    Set<Unit> unitList = new HashSet<>();

    for (ProductItem item : productItems) {
      unitList.add(
          new UnitImp.Builder<>()
              .withName(item.getMeterApiName())
              .withPrettyName(item.getProductItemName())
              .build());
    }

    return unitList;
  }

  public PriceList getPriceList(List<ProductPlans> plansList, List<ProductItem> productItems)
      throws URISyntaxException, IOException {
    return new PriceListImp.Builder<>()
        .withName(DEFAULT_NAME)
        .withPrettyName(DEFAULT_NAME)
        .withPlans(convertToKillBillPlanModel(plansList, productItems))
        .build();
  }

  public Date getEffectiveDate(List<ProductPlans> plansList) {

    Date date = new Date();

    if (plansList != null && !plansList.isEmpty()) {
      date = new Date(Long.parseLong(plansList.get(0).getLastUpdateTimeInMillis()));
    }

    return date;
  }

  public List<Currency> buildCurrencyList() {

    List<Currency> dList = new ArrayList<>();
    dList.add(Currency.USD);
    return dList;
  }

  private List<CaseBillingAlignment> buildCaseBillingAlignmentList() {
    List<CaseBillingAlignment> billingAlignmentCases = new ArrayList<>();

    billingAlignmentCases.add(
        new CaseBillingAlignmentImp.Builder<>()
            .withBillingAlignment(BillingAlignment.BUNDLE)
            .withProductCategory(ProductCategory.ADD_ON)
            .build());

    billingAlignmentCases.add(
        new CaseBillingAlignmentImp.Builder<>()
            .withBillingAlignment(BillingAlignment.ACCOUNT)
            .withBillingPeriod(BillingPeriod.MONTHLY)
            .build());

    billingAlignmentCases.add(
        new CaseBillingAlignmentImp.Builder<>()
            .withBillingAlignment(BillingAlignment.SUBSCRIPTION)
            .withBillingPeriod(BillingPeriod.ANNUAL)
            .build());

    billingAlignmentCases.add(
        new CaseBillingAlignmentImp.Builder<>()
            .withBillingAlignment(BillingAlignment.ACCOUNT)
            .build());

    return billingAlignmentCases;
  }

  private List<CaseChangePlanPolicy> buildCaseChangePlanPolicyList() {

    List<CaseChangePlanPolicy> changePolicyCases = new ArrayList<>();

    changePolicyCases.add(
        new CaseChangePlanPolicyImp.Builder<>()
            .withBillingActionPolicy(BillingActionPolicy.END_OF_TERM)
            .build());

    return changePolicyCases;
  }

  private List<CaseCreateAlignment> buildCaseCreateAlignmentList() {

    List<CaseCreateAlignment> createAlignmentCases = new ArrayList<>();

    createAlignmentCases.add(
        new CaseCreateAlignmentImp.Builder<>()
            .withPlanAlignmentCreate(PlanAlignmentCreate.START_OF_BUNDLE)
            .build());

    return createAlignmentCases;
  }

  private List<CaseCancelPolicy> buildCaseCancelPolicyList() {
    List<CaseCancelPolicy> cancelPolicyCases = new ArrayList<>();

    cancelPolicyCases.add(
        new CaseCancelPolicyImp.Builder<>()
            .withBillingActionPolicy(BillingActionPolicy.END_OF_TERM)
            .withProductCategory(ProductCategory.BASE)
            .build());

    cancelPolicyCases.add(
        new CaseCancelPolicyImp.Builder<>()
            .withBillingActionPolicy(BillingActionPolicy.IMMEDIATE)
            .withProductCategory(ProductCategory.ADD_ON)
            .build());

    cancelPolicyCases.add(
        new CaseCancelPolicyImp.Builder<>()
            .withBillingActionPolicy(BillingActionPolicy.END_OF_TERM)
            .build());

    return cancelPolicyCases;
  }

  private List<CaseChangePlanAlignment> buildCaseChangePlanAlignmentList() {
    List<CaseChangePlanAlignment> changePlanAlignmentCases = new ArrayList<>();

    changePlanAlignmentCases.add(
        new CaseChangePlanAlignmentImp.Builder<>()
            .withAlignment(PlanAlignmentChange.START_OF_SUBSCRIPTION)
            .build());

    return changePlanAlignmentCases;
  }

  public PlanRules buildRules() {

    return new PlanRulesImp.Builder<>()
        .withCaseBillingAlignment(buildCaseBillingAlignmentList())
        .withCaseChangePlanPolicy(buildCaseChangePlanPolicyList())
        .withCaseCreateAlignment(buildCaseCreateAlignmentList())
        .withCaseCancelPolicy(buildCaseCancelPolicyList())
        .withCaseChangePlanAlignment(buildCaseChangePlanAlignmentList())
        .build();
  }
}
