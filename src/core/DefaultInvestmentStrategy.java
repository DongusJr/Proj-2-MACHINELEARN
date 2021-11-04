package core;

/**
 * Program : Threadneedle
 * Author  : Throstur Thorarensen
 * Date    : 22.3.2015.
 */
public class DefaultInvestmentStrategy extends AbstractInvestmentStrategy {

  private long   minDeposit = 100;
  private long   desiredDeposit = 500;
  private double prosperityFactor = 2.0;

  public DefaultInvestmentStrategy() {
    super();
  }

  @Override
  public void updateStrategy(long deposit, long nextExpenses) {
    super.updateStrategy(deposit, nextExpenses);
    if (desiredDeposit < nextExpenses) {
      desiredDeposit += nextExpenses / 3;
    }
  }

  @Override
  public long neededFunds() {
    return (long) (1.5 * desiredDeposit * prosperityFactor - deposit);
  }

  @Override
  public int loanDuration() {
    return 120;
  }

  @Override
  public InvestmentStrategyGoal getGoalFromStrategy() {
    // already running out of money!
    if (deposit < minDeposit || deposit < upcomingExpenses * Math.max(1, prosperityFactor)) {
      return InvestmentStrategyGoal.LIQUIDATE;
    }
    // starting to run out of money
    if (deposit < desiredDeposit) {
      return InvestmentStrategyGoal.CONTRACT;
    }
    // money is in abundance
    if (deposit > desiredDeposit * prosperityFactor) {
      return InvestmentStrategyGoal.EXPAND;
    }
    // business as usual
    return InvestmentStrategyGoal.NONE;
  }

  public void setMinDeposit(long amount) {
    this.minDeposit = amount;
  }

  public void setDesiredDeposit(long amount) {
    this.desiredDeposit = amount;
  }

  public void setProsperityFactor(double factor) {
    this.prosperityFactor = factor;
  }

}
