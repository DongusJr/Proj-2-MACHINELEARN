/* Program : Threadneedle
 *
 * Company : Implements a company. (Defining company as an organisation 
 *           of production and employees).
 *
 *           Companyies are created with a unique identifier (government id)
 *           and a bank account.
 *
 * Author  : Jacky Mallett
 * Date    : February 2012
 *
 * Comments:
 *
 * Todo:   move production/output definition to configurable class
 *         modify markets usage so that company uses a fixed market
 */

package core;

import java.util.*;

import com.google.gson.annotations.*;

import statistics.*;

import static statistics.Statistic.Type.COUNTER;

public abstract class Company extends Agent
{
  @Expose public String product;        // Product produced/managed by company
  @Expose public int  labourInput;      // labour per output
  @Expose public int salaryPeriod = 1;  // frequency of salary payments
  @Expose public int period = 1;        // frequency of production (sales to market)
  @Expose public int minCapital = 50;   // Minimum capital 

  public long   lastSoldPrice;          // Last sale price for produced goods
  public Market market;                 // Market used for company's product
  public Owner owner = null; 		    // Owner of the company
  public long dividend = 0;  			// Dividend paid to the owner in percent 



  public LinkedList<Shares> sharesIssued = new LinkedList<>();

  public Statistic s_quantitySold;      // Amount sold (people, hours of labour)
  public Statistic s_quantityProduced;  // Quantity produced
  public Statistic s_labourCost;        // For companies, labour cost
  


  /*
   * Gui provided controls.
   */
  boolean constantSalary;               // Toggle salary changes on/off

  /**
   * Constructor for base company.
   *
   * @param n              Name of company
   * @param initialDeposit initial deposit at bank
   * @param g              government
   * @param b              bank
   */

  public Company(String n, long initialDeposit, Govt g, Bank b)
  {
    super(n, initialDeposit, g, b);
  }

  public Company()
  {
  }

  public void initStatistics()
  {
    s_labourCost = new Statistic(Id + ":labour cost", COUNTER);
  }
  


  /**
   * Set the markets object for this company.
   *
   * @param m Markets object to set
   */

  public void setMarkets(Markets m)
  {
    if (m == null)
      throw new RuntimeException("Null market in setMarkets");
    this.markets = m;

    // Add market for companies product (this may replace old market)

    this.market = markets.getMarket(product);
  }

  /**
   * Set the market used for the product of this company.
   *
   * @param m market
   */
  public void setMarket(Market m)
  {
    this.market = m;
  }

  /**
   * Set the bank used by this company.
   * @param bankname Name of bank
   * todo: allow transfers
   */
  public void setBank(String bankname)
  {
    if (bankname == null)
      throw new RuntimeException("Null bank in setBanks:" + name);

    if (this.bankname != null)
      throw new RuntimeException(
        "Need to implement bank account transfer");

    this.bankname = bankname;
  }

  public int getNoEmployees()
  {
    return employees.size();
  }

  /**
   * Changes the product being made by this company. Will ask the markets
   * container to create a market for it if necessary. All current inventory
   * is cleared.
   *
   * @param product New product
   */
  public void changeProduct(String product)
  {
    this.product = product;

    if (markets.getMarket(product) == null)
      this.market = markets.createMarket(product);
    else
      this.market = markets.getMarket(product);

    s_quantityProduced = Statistic.getStatistic(product + "-produced",
                            "production", COUNTER);

  }

  /**
   * returns the Owner of the company
   *
   * @return Owner
   */
  public Owner getOwner(){
	  return this.owner;
  }

  /**
   * changes the Owner of the company
   *
   * @param Owner the new owner
   */
  public void setOwner(Owner newBoss){
	  owner = newBoss;
  }
  /**
   * returns the divdiden that's paid to the owner
   *
   * @return dividend
   */
  public double getDividend(){
	  return this.dividend;
  }

  /**
   * changes the dividend paid to the owner
   *
   * @param newValue
   */
  public void setDividend(long newValue){
	  dividend = newValue;
  }

  /**
   * Set a single salary for all employees of company.
   *
   * @param amount salary to set
   */

  public void setSalaries(long amount)
  {
    Person p;

    for (Person employee : employees)
    {
      p = employee;
      p.setSalary(amount);
    }
  }

  /**
   * Decrease all salaries by supplied amount.
   *
   * @param amount of salary to reduce by
   */

  public void decreaseSalaries(long amount)
  {
    Iterator<Person> itr = employees.listIterator();
    Person p;

    while (itr.hasNext())
    {
      p = itr.next();

      if (p.getSalary() - amount > govt.minWage)
        p.setSalary(p.getSalary() - amount);
	  else
		p.setSalary(govt.minWage);
    }

    if (offeredSalary > govt.minWage)
      offeredSalary--;
  }



  /**
   * Create ordinary shares at specified price.
   *
   * @param price  Issue share price
   * @param amount No. of shares to create
   */
  public void issueShares(long price, int amount)
  {
    new Shares(this.name, amount, price, this, this.sharesIssued).transfer(this);
  }

  
  /**
   * Pay a dividend to the owner of the company
   *
   * @param percent Percentage of face value to pay - percent can be abused 
   *                to be 100% for larger multiples.
   * @param shares to pay dividend on
   * @return t/f paid, or failed (insufficient funds)
   */
  public void payDividendToOwner(){
	  if(getAccount().getDeposit() > minCapital && owner!=null){
		  long dividendPayout = (getAccount().getDeposit()-minCapital)*dividend/100;
		  getAccount().transfer(owner.getAccount(), dividendPayout, "Corporate Dividend from ("+this.name+") to the Owner (" +
				  owner.name + ")");
	  }
  }

  /**
   * Pay a dividend on any shares issued by the company.
   *
   * @param percent Percentage of face value to pay - percent can be abused 
   *                to be 100% for larger multiples.
   * @param shares to pay dividend on
   * @return t/f paid, or failed (insufficient funds)
   */

  public boolean payDividend(double percent, LinkedList<Shares> shares)
  {
    int total = 0;

    assert (percent >= 0) : "Negative dividend percentage: " + this.name;

    /*
     * Verify that there is enough money to make the payment
     */
    for (Shares s : shares)
    {
      if ((s.owner != null) && (s.owner != this))
      {
        total += s.getDividend(percent);
      }
      else
        System.out.println("Div: " + s + " " + this.name);
    }

    if (total > getDeposit())
    {
      System.err.println(name + " unable to pay dividend of " + percent
                         + "% total:" + total + " insufficient funds");
      return false;
    }

    /*
     * Payout to all shares not owned by this company, as long as there is a
     * dividend payment this step.
     */

    for (Shares s : shares)
    {
      if ((s.owner != this) && (int) s.getDividend(percent) > 0)
      {
        transfer((int) s.getDividend(percent), s.owner,
                 (String.format("%.2f", percent) + "% Dividend on " + s.name));

        System.out.println("Paid dividend " + s.getDividend(percent));
      }
    }
    return true;
  }

  /**
   * Return total number of shares currently held by investors.
   *
   * @param shares List of shares to return total of.
   * @return total number of shares held by investors
   */
  public int getTotalSharesIssued(LinkedList<Shares> shares)
  {
    int total = 0;

    for (Shares s : shares)
    {
      if (s.issued())
        total += s.quantity();
    }
    return total;
  }

  // public long getQuantityProduced(){return s_quantityProduced.get();}

  /*
   * Gui Controls.
   */
  public void setConstantSalary(boolean on)
  {
    if (constantSalary != on)
    {
      System.err.println("Changing constant salary setting to " + on);
      constantSalary = on;
    }
  }

  /**
   * Return the output market for this company.
   *
   * @return Return market for the product of this company
   */
  public Market getOutputMarket()
  {
    return markets.getMarket(product);
  }

  /**
   * Print configuration
   *
   * @return String with config details
   */
  public String getConfig()
  {
    return name + " " + config.bankname + " "
           + config.initialDeposit + " " + product;
  }

  public String getCurrentSetup()
  {
    return name + " " + product + " " + getBankName() + ": " + getDeposit() + 
		          " L x-" + labourInput;
  }

  public void print(String label)
  {
    if (label != null)
      System.out.println(label);

    System.out.println("Employees : " + employees.size() + " Input     : "
                       + labourInput + " Output    : " + output+" Owner: "+owner);
  }

}
