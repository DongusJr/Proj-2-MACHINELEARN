package core;

/**
 * Program : Threadneedle
 * Author  : Throstur Thorarensen
 * Date    : 23.3.2015.
 */
public interface InvestorStrategy {

  void recordPurchase(long value);
  void recordSale(long value);
  void executeStrategy();
  void executeStrategy(InvestmentStrategyGoal goal);
}
