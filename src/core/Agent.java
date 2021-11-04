/* Program : Threadneedle
 *
 * Agent   : Base class for all Agents. 
 * 
 * Author  : (c) Jacky Mallett
 * Date    : November 2014
 *
 * Comments:
 */
package core;

import base.Base;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import statistics.Statistic;

import java.awt.*;
import java.util.*;

import static base.Base.*;
import static statistics.Statistic.Type.COUNTER;

public abstract class Agent
{
  @Expose public String name           = "";// Name for agent 
  @Expose public long   initialDeposit = 0; // initial deposit at Bank
  @Expose public String bankname       = "";// Name of agent's Bank
  @Expose public double x;                  // x display position
  @Expose public double y;                  // y display position
  @Expose public double defaultProb = 0.0;  // Probability/step of default
  @Expose public String regionName = "";    // Name of region agent belongs to

  public long offeredSalary = 1;	 // Put low boundary on salaries (i.e. no interns)

  public Govt    govt;               // Provides the government/country id
  public Region  region = null;      // Optional region that agent belongs to
  public Markets markets;            // Markets used by this agent
  public Integer Id;                 // unique id

  public Color myColor;              // Colour to use when drawing.

  Config config = null;              // Object to store all config data in

  public int output;                 // Output produced by agent (quantity)

  public int bankrupt;               // Flag/counter available for bankruptcy

  // Array holding accounts - first element is always the deposit account

  private Account[] accounts = new Account[1];

  // Share holdings of this agent.

  public  HashMap<String, Inventory> shareholdings = new HashMap<>();
  private LinkedList<Treasury>       treasuries    = new LinkedList<>();
  public  LinkedList<Person> employees             = new LinkedList<>();


  /*
   * Variables used to provide statistical information between rounds for
   * analysis. Base evaluate method computers general information and
   * housekeeping for specific agent controlled info.
   */

  public Statistic s_income;           // Income last step (Agent controlled)

  public long c_salariesPaid = 0L;      // Counter on salaries paid 
                                     

  // Variables used to provide alerts that important methods eg. payDebt(),
  // payTaxes() may not have been called. These aren't mandatory, but would
  // usually be an error if not done regularly. There is no implication
  // that anything was actually paid.

  public boolean paidTaxes;
  public boolean paidDebts;

  Agent(String name, long initialDeposit, Govt g, Bank bank)
  {
    this();

    // Provide default name based on ID # if name is not provided

    if (name == null)
      this.name = "ID-" + this.Id;
    else
      this.name = name;

    this.initialDeposit = initialDeposit;

    if (bank != null)
      this.bankname = bank.name;

    // Govt should only be null, if this is a govt
    if (g != null)
      init(g);
  }

  /**
   * No parameter constructor for loading from JSON. All @Expose'd variables
   * will be initialised by GSON, it is the responsibility of the controller
   * to invoke the init() method properly.
   */
  Agent()
  {
    // Provide a default name that is based on the unique Id #
    this.Id = Base.assignID();

    if(!regionName.equals(""))
    {
       region = govt.regions.get(regionName);
 
       if(region == null)
          throw new RuntimeException("Error: region " + regionName + " set but no region available.");
    }

    s_income       = new Statistic(Id + ":income",        COUNTER);

  }

  /**
   * Set Govt, Bank and markets for Agent when being loaded from saved
   * configuration file (GSON).
   *
   * Todo: extend to allow agents to be moved around in the simulation.
   *
   * @param g government object for this agent
   */

  public void init(Govt g)
  {
    this.govt = g;

    if(!regionName.equals(""))
       this.region = govt.regions.get(regionName);

    if (govt != null)
    {
      this.markets = govt.markets;
    }
    // Todo: sanitise this error message after testing
    else
      System.out.println("**Error: govt == null in Agent init()**");

    if (name == null)
    {
      this.name = "ID-" + this.Id;
    }
    // When loading from file, we need to try and ensure that auto-generated 
    // Id's aren't subsequently duplicated.
    else
    {
       String n[] = this.name.split("-");

       // It's not required that id's be sequential, just that they be unique
       if(n.length == 2)
       {
          try
          {
             if(Integer.parseInt(n[1]) >= Base.getNextID())
                Base.setID(Integer.parseInt(n[1])+1);
          }
          catch(Exception e)
          {
             // Ignore exception if failed to parse.
          }
       }
    }

    /*
     * Nb. This works because Banks and Governments don't have a Bank.
     */
    Bank bank = govt.getBank(bankname);

    if (bank != null)
    {
      try
      {
        this.accounts[0] = bank.createAccount(this, initialDeposit);
        //System.out.println("Created account : "
        //                   + this.accounts[0].getId() + " for " + this.name+" @ " + Bank.name);
      }
      catch(Exception e)
      {
         throw new RuntimeException("Error creating account: "
                                   + this.name + " @ " + bank.name + " " + e +
                                    "\n Verify that this agent type has empty constructor");

      }
    }
    else
    {
      /*
       * Banks don't have a Bank (central Bank relationship is explicit)
       * and the Government's Bank has to be set separately, otherwise
       * this is an error (possibly a config file error.)
       */

      if (!((this instanceof Bank) || (this instanceof Govt)))
        System.out.println("null Bank in Agent initialisation " + name + " "
                           + bankname);
    }
  }

  /**
   * Round evaluation method. Base method is invoked directly by model
   * controller, and it then triggers individual agent's methods. 
   *
   * @param step   step number in sim
   * @param report t/f print report
   */
  public void evaluate(int step, boolean report)
  {

    /*
     * Record statistics and pay debts - first pay debts to own Bank, 
     * and then to any other banks/agents agent has borrowed from.
     */

    if (getAccount() != null)
    {
      /*
       * At the moment there is a restriction of one loan/account in order
       * to make validation and testing easier.
       */
      if (getAccount().debts.size() > 1)
      {
        System.out.println("**  size = " + getAccount().debts.size()
                           + " " + this.name);
        throw new RuntimeException("Account has more than one loan");
      }
    }

    // Invoke individual agent's evaluation function.

    paidDebts = false;						
    paidTaxes = false;

    evaluate(report, step);

  }

  /**
   * Default evaluation method for first round.
   */
  public void evaluate()
  {
  }

  /**
   * Return main Bank account for agent. This is always first in the accounts
   * array.
   *
   * @return Account deposit account for agent.
   */
  public Account getAccount()
  {
    return accounts[0];
  }

  /**
   * Add an account to this agent's list of Bank accounts.
   *
   * @param account Account to add
   */

  public void addAccount(Account account)
  {
    /*
     * Allocate a new accounts array. Maintained as a fixed length list as
     * since updates should be rare, and this is easier to control.
     */

    Account a[] = new Account[accounts.length + 1];

    int i;

    for (i = 0; i < accounts.length; i++)
    {
      a[i] = accounts[i];
    }
    a[i] = account;

    accounts = a; // replace with new list
  }

  /**
   * Return amount in agent's primary deposit account with their Bank.
   *
   * @return amount in account.
   */

  public long getDeposit()
  {
    try
    {
      return getAccount().getDeposit();

    }
    catch (Exception e)
    {
      System.out.println("No account for: " + name + "  " + e);
      return 0;
    }
  }

  /**
   * Provide name of country
   *
   * @return Name of country
   */

  public String getCountryName()
  {
    return govt.country;
  }

  /**
   * Return the region the agent is in, if there is one.
   *
   * @return agent's region or null if no region set
   */
  public String getRegionName()
  {
    if(region != null)
       return region.name;
    else
       return null;
  }

  public String getBankName()
  {
    return getAccount().bank.name;
  }

  /**
   * Return the Bank used by this agent, or null if no Bank set yet.
   *
   * @return Bank
   */
  public Bank getBank()
  {
    if (getAccount() != null)
      return getAccount().bank;
    else
      return null;
  }

  /*
   * Debt Handling.
   */

  /**
   * Add loan to agent's list of money it has lent out
   *
   * @param loan loan to add
   */

  public void addLoan(Loan loan)
  {
    if (!loan.ownerId.equals(this.Id))
    {
      // Loans owned by the agent should be paid to the agent.
      throw (new RuntimeException("E: Loan has incorrect toId for agent"));
    }

    if (getAccount().capital_loans.get(loan.Id) != null)
    {
      throw (new RuntimeException("Duplicate loan id in addLoan" + loan));
    }
    else
    {
      getAccount().capital_loans.put(loan.Id, loan);
    }
  }

  /**
   * Add loan to agent's list of money it has borrowed
   *
   * @param loan loan to add
   */
  public void addDebt(Loan loan)
  {
    if (loan.borrowerId.equals(this.Id))
    {
      // Agents can't lend money directly to themselves.
      throw (new RuntimeException("E: Loan is already owned by agent"));
    }

    if (getAccount().debts.get(loan.Id) != null)
    {
      throw (new RuntimeException("Duplicate loan id in addLoan" + loan));
    }
    else
    {
      getAccount().debts.put(loan.Id, loan);
    }
  }

  /**
   * Return true if any debt repayments are due this round.
   *
   * @return t/f
   */
  public boolean loanPaymentDue()
  {
     for(Loan debt : getAccount().debts.values())
     {
         if(debt.installmentDue()) return true;
     }

     return false;
  }

  /**
   * Make incremental payments on loans, if funds available. Loans are
   * written off by the lender as part of processing loan payment. Debt
   * will automatically not be repaid if borrower has insufficient funds.
   */
  public void payDebt()
  {
    paidDebts = true;

    for (Loan debt : getAccount().debts.values())
    {
      if(!debt.installmentDue()) return;

      if(debt.inDefault)
      {
         // If there is collateral for the loan attempt to sell it.
         if(debt.hasCollateral())
         {
System.out.println(name + " liquidating " + debt.collateral.name);
System.out.println("Remaining capital " + debt.getCapitalOutstanding());
            long marketPrice = markets.getMarket(debt.collateral.name)
                                      .getAskPrice();

            markets.getMarket(debt.collateral.name)
                   .sell(debt.collateral, marketPrice - 10,
                         getAccount());
            debt.collateral = null;
            debt.inDefault  = false;
            debt.inWriteOff = false;
         }
      }

      // Did the loan trigger a default?

      if (Base.random.nextDouble() <= defaultProb)
      {
          debt.putLoanIntoDefault();
      }

      // Pay the loan capital and interest for this round

      getAccount().payLoan(debt);

      // Remove debt from account if repaid this round.

      if (debt.repaid())
      {
        getAccount().debts.remove(debt.Id);
      }
    }
  }

  /**
   * Transfer money from this agent's account to another agents.
   *
   * @param amount amount to transfer
   * @param to     agent to transfer to
   * @param text   explanation for ledger book
   * @return t/f transferred ok (insufficient funds)
   */

  public boolean transfer(long amount, Agent to, String text)
  {
    if (this.getDeposit() >= amount)
    {
      getAccount().transfer(to.getAccount(), amount, text);
      return true;
    }
    else
    {
      return false;
    }
  }

  /**
   * Invest in an institution. The type of investment will depend on what the
   * target institution provides, and availability is determined by the target
   * institution.
   *
   * @param from   Agent to purchase from
   * @param amount amount (cash) available for purchase
   * @param period for loan's - length of loan (or 0)
   * @param type   type of investment to purchase - passed to institution
   * @return t/f
   */

  public boolean buyInvestment(Agent from, long amount, int period,
                               String type)
  {
    Object investment;

    System.out.println(this.name + " buying investment from " + from.name);
    if (amount > getDeposit())
    {
      throw new RuntimeException("BankInvestor buyCapital exceeds funds");
    }

    investment = from.sellInvestment(this, amount, period, type);

    return investment != null;
  }

  public boolean buyInvestment(Agent from, long amount, String type)
  {
    Object investment;

    if (amount > getDeposit())
    {
      throw new RuntimeException("BankInvestor buyCapital exceeds funds");
    }
    // shares
    investment = from.sellInvestment(this, amount, type);
    return investment != null;
  }

  /**
   * Accessor method to add an investment to holdings
   *
   * @param investment financial instrument to add
   */

  public void addInvestment(Object investment)
  {
    Inventory inv;

    /*
     * Check if there is already a holding for this company, if not, create
     * new inventory container for it.
     */
    if (investment instanceof Shares)
    {
      Shares shares = (Shares) investment;

      if ((inv = shareholdings.get(shares.name)) == null)
      {
        inv = new Inventory(shares.name, shares.hasTTL(), false);
        inv.add(shares);

        shareholdings.put(shares.name, inv);
      }
      else
      {
        inv.add(shares);

      /*
       * The inventory will add the shares to an existing container if
       * one is available, if this occurs, then the now empty Shares
       * widget needs to be removed from the masterlist, at which
       * point gc should take over.
       */
        if (shares.quantity == 0)
          shares.masterList.remove(shares);
      }
    }
    else if (investment instanceof Treasury)
    {
      treasuries.add((Treasury) investment);
    }
    else
    {
      throw new RuntimeException(
        "*** Unknown investment in addInvestment ***");
    }
  }

  /**
   * Get total quantity of shares held.
   *
   * @param name Company name to return shareholding for.
   *
   * @return quantity
   */
  public long getShareholding(String name)
  {
    if (shareholdings.get(name) != null)
      return shareholdings.get(name).getTotalItems();
    else
      return 0;
  }

  /**
   * Transfer shares from shareholding - shares must always be owned by
   * somebody.
   *
   * @param name     of shareholding
   * @param quantity No. of shares to remove
   * @param to       Agent to transfer shares to.
   * @return requested number of shares.
   */
  public long transferShares(String name, long quantity, Agent to)
  {
    if (quantity <= 0)
      throw new RuntimeException("Invalid transfer quantity: " + quantity);
    if (to == null)
      throw new NullPointerException("Share recipient is null!");

    Inventory inv = shareholdings.get(name);

    if (inv == null)
    {
      return 0; // Requested # of shares not available
    }
    else
    {
      Shares s = (Shares) inv.remove(quantity);
      if (s == null) {
           throw new RuntimeException("No shares found in inventory!");
      }
      s.transfer(to);

      return quantity;
    }
  }

  /**
   * Print shareholdings owned by agent.
   */
  public void printShareholding()
  {
    System.out.println("\nAgent " + name + " shareholding");
    for (Inventory inv : shareholdings.values())
    {
      System.out.println(inv.product + ": " + inv.getTotalItems());
    }
    System.out.println("\n");
  }

  /**
   * Pay tax as a fixed rate with lower bound.
   * todo: move to government
   *
   * @param percent    percentage (1-100) rate of tax
   * @param lowerbound Tax free amount deducted before percent applied
   * @return t/f paid or not paid.
   */

  // public boolean payTax(int percent, int lowerbound)
  // {
  //   paidTaxes = true;

  //   // Lower limit on tax payable
  //   if(s_income.get() < lowerbound)
  //     return true;

  //   int amountToPay = (int) (((s_income.get() - lowerbound) * percent) / 100.0);

  //   // System.out.println(name + " pay tax " + amountToPay + " " + s_income.get());
  //   if ((getDeposit() > amountToPay) && (amountToPay > 0))
  //   {
  //     govt.payPersonalTax(this.getAccount(), amountToPay);
  //     return true;
  //   }
  //   else
  //     return false;
  // }

  /**
   * Set the main deposit account for the agent. (Used by banks and
   * Governments who can't use the constructor as the General Ledger hasn't
   * been established yet.
   *
   * @param account Account to set
   */

  public void setMyAccount(Account account)
  {
    accounts[0] = account;
  }

  /**
   * Find out if the agent has any debt outstanding.
   *
   * @return t/f
   */

  public boolean hasDebt()
  {
    int sum = 0;

    for (Account account : accounts)
    {
      sum += account.getTotalDebt();
    }

    return sum != 0;
  }

  /**
   * Return amount in agent's debt from primary deposit account with their
   * Bank.
   *
   * @return debt in account.
   */
  public long getDebt()
  {
    long sum = 0;

    for (Account account : accounts)
    {
      sum += account.getTotalDebt();
    }

    return sum;
  }

  /**
   * Find out if the agent has any debts at other banks than the one it holds
   * its Bank account at.
   *
   * @return t/f Returns true if the agent has no debt
   */
  public boolean localBorrower()
  {
    // Does the agent have any debt?

    if (!hasDebt())
      return true;

    for (int i = 1; i < accounts.length; i++)
    {
      if (accounts[0].bank != accounts[i].bank)
        return false;
    }
    return true;
  }

  /**
   * Request a Bank loan - first from the Bank the account holder banks with,
   * and then from the other banks available to this agent, which at present
   * is all the banks under the local Government.
   *
   * @param amount   Amount of loan
   * @param duration Duration of loan
   * @param period   repayment period for loan (MONTH, DAY, etc.)
   * @param risktype Risk classification (Basel) of loan
   * @param loantype Type of loan being requested.
   * @return t/f granted
   */

  public boolean requestBankLoan(long amount, int duration, Time period, 
				                 int risktype, Loan.Type loantype)
  {
    Loan loan;

    if((loan = getAccount().requestLoan(amount, duration, period, risktype, loantype)) == null)
    {
      // Iterate through all banks in banking system requesting loan

      for (Bank bank : govt.getBankList().values())
      {
        loan = bank.requestLoan(this.getAccount(), amount, duration, period, risktype,
                                loantype);

        if (loan != null) return true;
      }
    }
    return false;
  }

  /*
   * Interface for sub-classes - must be implemented by each agent
   * to include its step wise behaviour in the simulation.
   *
   * @param report t/f generate a report each evaluate step
   * @param step step
   */
  protected abstract void evaluate(boolean report, int step);

  /**
   * Get current value of treasury holdings. 
   *
   * Todo: implement NPV versions
   *
   * @return Total capital outstanding of current treasury holdings.
   */

  public int getTreasuriesValue()
  {
    int sum = 0;

    for (Treasury t : treasuries)
    {
      sum += t.getCapitalOutstanding();
    }

    return sum;
  }

  /**
   * Return total salaries due this round to employees.
   *
   * @return Total salary bill this round.
   */
  public long getSalaryBill()
  {
    int total  = 0;

    for (Person e : employees)
    {
      total  += e.getSalary();
    }
    return total;
  }

  /**
   * Pay salaries. Employee list will be shuffled before payment to
   * avoid order of evaluation issues over time. Salary will be paid
   * if there are sufficient funds, otherwise the employee will be
   * fired. 
   *
   * Agents should ensure that there are sufficient funds
   * available before calling this method, or override it if this
   * is the desired behaviour.
   *
   * @return Total amount paid.
   */

   public long paySalaries()
   {
      c_salariesPaid = 0L;

      Collections.shuffle(employees, Base.random);

      Iterator<Person> iter = employees.iterator();

      while (iter.hasNext())
      {
        Person p = iter.next();
        if (getDeposit() >= p.getSalary())
        {
          DEBUG(name + " paying salary to " + p.name + " : "
                + p.getSalary());
          c_salariesPaid += p.paySalary(getAccount());
        }
        else
        {
          DEBUG(name + " Unable to pay :" + p.name + " [" + getDeposit()
                + "/" + p.getSalary() + "]"
                + " insufficient funds (firing employee)");
          fireEmployee(p, iter);
        }
      }

      return c_salariesPaid;
   }

  /**
   * Hire employee from the labour market - region free @ market price
   *
   * @return   null or employee
   */
  public Person hireEmployee()
  {
    return hireEmployee(markets.getLabourMarket().getAskPrice(), null, null);
  }

  /**
   * Hire employee from the labour market at specified salary, with
   * optionally a region and/or a bank specified that the employee must
   * match. This allows hiring to be restricted in order to create
   * localised flows within the banking system.
   *
   * Regional banking is arguably what the banking system has evolved
   * from. Liquidity issues encourage them to try and restrict borrowing to 
   * their own account holders, and observationally there is evidence that
   * this extends to relationships between companies and employees banking.
   *
   * If Region is specified, it takes precedence over the Bank.
   *
   * @param  salary salary being offered to employee
   * @param  bank If not null, restrict hiring to agents with accounts at 
   *         this bank
   * @param  region If not null, restrict hiring to agents in this region.
   * @return Person hired
   */

  public Person hireEmployee(long salary, Bank bank, String region)
  {
    Person p;
    Inventory i = markets.getLabourMarket().hire(salary, bank, region);

    if ((i == null) || (i.getTotalItems() == 0))
    {
      DEBUG(this.name + " failed to hire @ " + salary + " available labour: "
            + markets.getLabourMarket().totalAvailableWorkers());
      return null;
    }
    else if (i.getTotalItems() == 1)
    {
      p = ((Employee) (i.remove())).person;

      if (p == null)
      {
        System.err.println("Error: null person");
      }
      else
        hireEmployee(p, salary);

      return p;
    }
    else
    {
      throw new RuntimeException(
        "Sanity failed: Expected one item inventory, got "
        + i.getTotalItems());
    }
  }

  /**
   * Hire a particular person.
   *
   * @param e      Person to be hired
   * @param salary Salary they will be hired at
   */

  public void hireEmployee(Person e, long salary)
  {
    if (e != null)
    {
      employees.addFirst(e);

	  if(salary < govt.minWage)
	  {
		 System.out.println("Salary forced to min wage - was " + salary);
		 System.out.println(name);
		 salary = govt.minWage;
	  }

      e.setEmployer(this, salary, this.myColor);

    }
    else
    {
      System.err.println("Error: null person in hireEmployee");
    }
  }



  /**
   * Fire specific employee
   *
   * @param e    employee to fire
   * @param iter List iterator if required (can be null). Must be supplied if
   *             attempting to remove during list iteration to avoid a
   *             concurrent modification exception.
   * @return person fired
   */

  public Person fireEmployee(Person e, Iterator<Person> iter)
  {
    if (iter == null)
    {
      employees.remove(e);
    }
    else
    {
      iter.remove(); // nb. removes last object returned by next()
    }

    e.setUnemployed();

    return e;
  }

  /**
   * Fire employee (last in, first out) Employee is automatically returned to
   * the labour market.
   *
   * @return Employee that was fired
   */
  public Person fireEmployee()
  {
    Person p = null;

    if (employees.size() > 0)
    {
      p = employees.removeLast();
      p.setUnemployed();
    }

    return p;
  }

  /**
   * Fire all employees
   */
   public void fireAllEmployees()
   {
     for(Iterator<Person> iterator = employees.iterator(); iterator.hasNext(); )
     {
         Person p = iterator.next();
         iterator.remove();
         p.setUnemployed();
     }
   }

  /**
   * Increase all salaries for this agent by the same amount.
   *
   * @param amount  Amount to increase salary by.
   */
  public void increaseSalaries(long amount)
  {
    Person p;

    for (Person employee : employees)
    {
      p = employee;
      p.setSalary(p.getSalary() + amount);
    }
  }


  /**
   * Return name of agent.
   *
   * @return Name of agent
   */

  public String getName()
  {
    return name;
  }

  /**
   * Return information on engine suitable for SimulationEngine display
   * window.
   *
   * @return information string : Govt Bank Deposit
   */

  public String getInfo()
  {
    return govt.name + " " + getBank().name + " " + getDeposit();
  }

  /**
   * Return initial deposit for this agent.
   *
   * @return initial deposit
   */
  public long getInitialDeposit()
  {
    return initialDeposit;
  }

  /**
   * Sell investment - stub
   *
   * @param to      Agent to sell investment to
   * @param amount  Amount being sold for
   * @param type    Type of investment
   * @return investment being sold
   */

  public Object sellInvestment(Agent to, long amount, String type)
  {
    System.out
      .println("Not implemented for this institution: " + this.name);
    return null;
  }

  /**
   * Sell investment - stub
   *
   * @param to      Agent to sell investment to
   * @param amount  Amount being sold for
   * @param period  For loans - loan duration when issued.
   * @param type    Type of investment
   * @return investment being sold
   */

  public Object sellInvestment(Agent to, long amount, int period, String type)
  {
    System.out
      .println("Not implemented for this institution: " + this.name);
    return null;
  }

  /**
   * Provide a JSON string representing this object suitable for saving the
   * base configuration. (Use GSON Expose keyword to mark fields for saving.)
   *
   * @return String JSON encoded string representation
   */

  public String save()
  {
    Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
                                 .create();

    // Encapsulate configuration into a holder class, which allows us
    // to instantiate the object directly from json

    String config = gson.toJson(this);

    GsonAgent ga = new GsonAgent(this.getClass().getSimpleName(), config);
    return (gson.toJson(ga));

  }

  /**
   * Set the region for this agent.
   *
   * @param region Region to set
   */
  public void setRegion(Region region)
  {
     // Fail quietly, since region is not required.
     if(region != null)
     {
        this.region     = region;
        this.regionName = region.name;      // set regionName for save
     }
  }

  public String info()
  {
    return this.toString();
  }

  /**
   * Provide a simple sorting function on agent name for gui.
   */
  public static class Comparators
  {
    public static final Comparator<Agent> NAME
                  = (Agent a1, Agent  a2) -> a1.name.compareTo(a2.name);
  }


}
