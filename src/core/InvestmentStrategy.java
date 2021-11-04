/**
 * Program : Threadneedle
 * Author  : Throstur Thorarensen
 * Date    : 23.3.2015.
 */
package core;

public interface InvestmentStrategy 
{
  int loanDuration();

  long neededFunds();

  void setInvestorStrategy(Class<? extends AbstractInvestorStrategy> cls);

  void updateStrategy(long deposit, long nextRepayment);

  default InvestmentStrategyGoal getGoalFromStrategy() 
  {
    return InvestmentStrategyGoal.NONE;
  }


  InvestorStrategy getInvestorStrategy(StockInvestor i);


}
