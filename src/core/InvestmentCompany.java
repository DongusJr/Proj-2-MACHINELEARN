/**
 * InvestmentCompany : Hire Investors, take loans and trade shares on the 
 *                     stock market
 *
 * Author     		 : Throstur Thorarensen
 * Date   		     : December 2014
 */
package core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static base.Base.*;

public class InvestmentCompany extends Company
{

  private static HashMap<String, String> createDefaultPropertiesMap() {
    HashMap<String, String> map = new HashMap<>();
    map.put("initialDeposit", "300");
    map.put("labourInput", "1");
    map.put("product", "none");
    return map;
  }

  private StockExchange exchange = null;
  private InvestmentStrategy strategy = new DefaultInvestmentStrategy();
  public boolean bankrupt = false;

  /**
   * Constructor for base company.
   *
   * @param n              Name of company
   * @param initialDeposit initial deposit at bank
   * @param g              government
   * @param b              bank
   */
  public InvestmentCompany(String n, long initialDeposit, Govt g, Bank b)
  {
    super(n, initialDeposit, g, b);
  }

  public InvestmentCompany(String name, Govt govt, Bank bank)
  {
    this(name, govt, bank, createDefaultPropertiesMap());

    System.err.println("Warning: Using default values for InvestmentCompany.");
  }

  public InvestmentCompany(String name, Govt govt, Bank bank,
                           HashMap<String, String> properties)
  {
    super(name,
          properties.get("initialDeposit") != null ? Integer.parseInt(properties.get("initialDeposit")) : 0L,
          govt, bank);

    String nn = properties.get("name");
    if (nn != null)
    {
      this.name = nn;
    }

    this.labourInput = properties.get("labourInput") != null ? Integer.parseInt(properties.get("labourInput")) : 1; // due for deletion probably?
    this.product = properties.get("product");
    this.offeredSalary = properties.get("offeredSalary") != null ? Long.parseLong(properties.get("offeredSalary")) : 1;
    this.exchange = StockExchange.findExchange(properties.get("exchange"), govt);

    String className = properties.get("strategy") != null ? properties.get("strategy"): "default";
    try
    {
      className = String.format("%s%s", className.substring(0,1).toUpperCase(), className.substring(1).toLowerCase());
      Class type;
      try {
        type = Class.forName("core." + className + "InvestmentStrategy");
        if (!InvestmentStrategy.class.isAssignableFrom(type)) {
          throw new ClassNotFoundException("Not a strategy: " + className);
        }
        this.strategy = (InvestmentStrategy) type.getConstructor().newInstance();
      }
      catch (ClassNotFoundException ex) {
        // IF the strategy doesn't exist, assume user meant to set investment strategy (TODO: FOR NOW)
        type = Class.forName("core." + className + "InvestorStrategy");
        if (InvestorStrategy.class.isAssignableFrom(type)) {
          if (strategy == null) {
            strategy = new DefaultInvestmentStrategy();
          }
          strategy.setInvestorStrategy(type);
        }
      }
    }
    catch (ClassNotFoundException e) {
      System.err.println("Couldn't find strategy: " + "core." + String.format("%s%s", className.substring(0,1), className.substring(1)));
      System.err.println(e.getMessage());
    }
    catch (Exception e) {
      // TODO
      throw new RuntimeException("Unable to instantiate strategy: " + e.getMessage());
    }
    finally
    {
      if (this.strategy == null) {
        this.strategy = new DefaultInvestmentStrategy();
      }
    }
    requestSeat(); // get a seat on the exchange (if any)
  }

  public InvestmentCompany()
  {
    super();
    // TODO?
  }

  @Override
  protected void evaluate(boolean report, int step) {

    payDebt();

    if (exchange == null) {
      System.err.println(name + " not participating on any exchanges!");
      return;
    }
    long deposit = getDeposit();
    if (!bankrupt && deposit == 0 && shareValue() + deposit < getDebt() && shareValue() == 0) {
      bankrupt = true;
      exchange.releaseSeat(this);
    }

    if (bankrupt && totalOwnedShares() == 0) {
//      Base.DEBUG(this + " is bankrupt, not playing anymore.");
      if (exchange.hasSeat(this))
        exchange.releaseSeat(this);
      return;
    }
    else if (bankrupt && shareValue() > 0) // we are bankrupt but we still own something, get rid of it
    {
      requestSeat();
      if (exchange.hasSeat(this))
        employees.stream().filter(e -> e instanceof StockInvestor).forEach(e -> ((StockInvestor) e).workWithStrategy(InvestmentStrategyGoal.LIQUIDATE));
      exchange.releaseSeat(this);
    }

    if (!exchange.hasSeat(this)) {
      if (!requestSeat())
        return; // can't play without a seat!
    }


    /* TODO HERE:  (with regards to strategy)
    //            Actually evaluate whether or not employees are making a profit
    //            If they are not, sell any shares they are exclusively trading in
    //            AND FIRE THEM!
    */
    long nextExpenses = getAccount().getNextRepayment() + getSalaryBill();
    strategy.updateStrategy(deposit, nextExpenses);
    InvestmentStrategyGoal goal = strategy.getGoalFromStrategy();

    // stay staffed
    int wantedEmployees = 1;

    // high-level corporate stuff
    switch (goal) {
      case LIQUIDATE:
      case CONTRACT:
        seekFunding(strategy.neededFunds(), strategy.loanDuration());
        break;
      default:
        wantedEmployees = exchange.markets.size(); // if we're in good shape, be fully staffed
        break;
    }

    if (employees.size() < wantedEmployees) {
      hireInvestor(offeredSalary);
    }
    else
    {
      // TODO: fire employees that aren't responsible for any shares or orders

    }
    // make StockInvestors work
    employees.stream().filter(e -> e instanceof StockInvestor).forEach(e -> ((StockInvestor) e).workWithStrategy(goal));

    // pay all salaries
    for (Person p : employees) {
       p.paySalary(getAccount());
    }
  }

  public boolean requestSeat() 
  { 
	  System.out.println("Request seat: " + this.name);       
    if (exchange != null) {
      return exchange.giveSeat(this);
    }
    return false;
  }

  /**
   * Try to get more funding -- currently just tries to take a bank loan
   *
   * @param amount   the amount to request
   * @param duration if we take a bank loan, how long it should last
   */
  private boolean seekFunding(long amount, int duration) {
    if (bankrupt) {
      return false;
    }
//    Base.DEBUG("SEEKING FUNDING: " + name + " (" + amount + ", dur: " + duration + "). Current: " + getAccount().getDeposit());
    if (getAccount().debts.size() == 0) {
      if (getBank().zombie) {
        System.out.println("[WARNING] :: Bank is defunct!");
        System.err.println("Warning. Bank is zombie.");
      }
      try 
      {
        return getBank().requestLoan(this.getAccount(), amount, duration, Time.MONTH,
                BaselWeighting.MORTGAGE, Loan.Type.COMPOUND) != null;
      } 
      catch (RuntimeException e) 
      {
        System.err.println(e.getMessage());
        e.printStackTrace();
        return false; // DIRTY DIRTY HACK!
      }
    }
    return false;
  }

  public StockInvestor hireInvestor(long salary) {
    return hireInvestor(salary, null);
  }

  public StockInvestor hireInvestor(long salary, String region) {
    StockInvestor investor;

    Inventory inventory = markets.getLabourMarket().hire(salary, region,
                                                         StockInvestor.class);
    if ((inventory == null) || inventory.getTotalItems() == 0)
    {
      DEBUG(this.name + " failed to hire @ " + salary + "labour: "
            + markets.getLabourMarket().totalAvailableWorkers());
      return null;
    }
    else if (inventory.getTotalItems() == 1)
    {
      investor = (StockInvestor) (((Employee) inventory.remove()).person);

      if (investor == null)
      {
        System.err.println("Error: null investor");
      }
      else
      {
        hireEmployee(investor,-1);
        // at this point, investor is the investor we are hiring
        // make sure the investor has something to do for now...
        investor.setInvestment(exchange.getFirstOrRandom(getCoverage()));
      }

      investor.setStrategy(strategy.getInvestorStrategy(investor));

      return investor;
    }
    else
    {
      throw new RuntimeException(
        "Sanity failed: Expected one item inventory, got "
        + inventory.getTotalItems());
    }
  }

  public Set<StockMarket> getCoverage() {
    Set<StockMarket> set = new HashSet<>();
    for (Person p : employees) {
      if (p instanceof StockInvestor) {
        set.add(((StockInvestor) p).stockMarket);
      }
    }
    return set;
  }

  @Override
  public String info() {
    String s = this.name;
    if (!bankrupt)
    {
      s  += "\n+ " + String.format("%6d", this.getDeposit())
          + "\n- " + String.format("%6d", this.getDebt())
          + "\n---------\n  " + String.format("%6d", this.getDeposit() - this.getDebt())
          + this.shareString();
    }
    else
    {
      s += " (bankrupt)";
    }
    return s;
  }

  public String shareString() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    for (Inventory inv : shareholdings.values())
    {
      long total = inv.getTotalItems();
      if (total == 0) continue;
      sb.append(inv.product );
      sb.append( ": " );
      sb.append( String.format("%3d", total));
      StockMarket m = StockExchange.findMarket(inv.product, govt);
      if ( m != null) {
        sb.append(" (valued at: ");
        sb.append(m.getBidPrice());
        sb.append(" each, total ");
        sb.append(m.getBidPrice() * total);
        sb.append(") - Last traded at ");
        sb.append(m.lastPrice);
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  public long shareValue() {
    long value = 0;

    for (Inventory inv : shareholdings.values()) {
      StockMarket m = StockExchange.findMarket(inv.product, govt);
      if (m != null) {
        value += m.bidPrice * inv.getTotalItems();
      }
    }
    return value;
  }

  public long totalOwnedShares() {
    long items = 0;
    for (Inventory inv : shareholdings.values()) {
      items += inv.getTotalItems();
    }
    return items;
  }
}
