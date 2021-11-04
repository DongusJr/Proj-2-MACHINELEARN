/*
 * Market  : Handle the implementation of a labour market 
 *           The Labour Market allows people to list their services for hire. 
 *           No money is transacted - people are hired at the "sale" price, 
 *           and this  then becomes their salary.
 * 
 * Author  : Jacky Mallett
 * Date    : May 2012
 *
 * Comments: Displays:   Highest Salary offered by Employer, Lowest Salary 
 *           accepted by Employee
 *
 * Design:   Eventually, employment should be handled by individual labour 
 *           markets for each type of employment, so that within a particular 
 *           labour market all employees are governed by its salary 
 *           determination mechanisms, but across the simulation different 
 *           mechanisms and salaries can be applied to different job functions.
 *
 */
package core;

import java.awt.*;
import java.util.*;

import statistics.*;

import static statistics.Statistic.Type.*;

import base.Base;

public class LabourMarket extends Market
{
  private int maxInventory = -1; // No limit on size.

  // Limit on unemployment time at min. desired salary - after which agent
  // will accept any salary offered.

  private int MAX_UNEMPLOYMENT_TIME = 5;   

  // Graphics Constants

  private int totalSaleValue    = 0;
  private int totalQuantitySold = 0;

  // Statistics for simulation - all workers available on the LabourMarket
  // are considered to be unemployed.

  public Statistic s_unemployed;

  /**
   * Initialise LabourMarket. sell/bid prices set to maximum - as workers
   * are added, their desiredSalary will then be used as new minimum values.
   *
   * @param n        Name
   * @param product  product handled by market
   * @param g        Government
   * @param b        Bank (not used)
   */
  public LabourMarket(String n, String product, Govt g, Bank b)
  {
    super(n, product, g, b, 0);
    inventory = new Inventory(product, true, true);

    sellPrice = bidPrice = Integer.MAX_VALUE;
    myColor = Color.gray;

    if (getDeposit() > 0)
      throw new RuntimeException(
        "Sanity failed: LabourMarket has non-zero deposit: "
        + getDeposit());

    s_unemployed = Statistic.getStatistic("unemployed", "unemployed", SINGLE);
  }

  /**
   * Constructor from gson config file. Nb. strictly, labour markets don't use
   * banks, but this may change
   */

  public LabourMarket()
  {
    super();
    sellPrice = bidPrice = Integer.MAX_VALUE;
    s_unemployed = Statistic.getStatistic("unemployed", "unemployed", COUNTER);
  }

  public void init()
  {
    sellPrice = bidPrice = Integer.MAX_VALUE;
  }

  /**
   * Set the product for the market. Override the general markets method to
   * prevent labour statistics being included incorrectly in some charts.
   */
  @Override
  public void setProduct()
  {
     if(inventory.product == null)
        inventory.product = this.product;
  }

  /**
   * List employee on the employment market.
   *
   * @param employee Employee to list on the market
   */

  public void sell(Employee employee)
  {
    // Stop self-employed getting on the market
    if(employee.person.employer == employee.person)
       return;

    if ((employee.person.getSalary() < sellPrice) || (sellPrice == -1))
	{
	   if(employee.person.getSalary() < govt.minWage)
		  sellPrice = govt.minWage;
	   else
		  sellPrice = employee.person.getSalary();
	}

    bidPrice = sellPrice;

	if(sellPrice < 1)System.out.println("==" + employee.person.name);

    employee.person.unemployed = true;
    employee.person.unemployedTime = 0;

	inventory.add(employee);
  }

  /**
   * Check inventory to see if a particular person is present.
   *
   * @param p person to check for
   * @return boolean T/F
   */

  public boolean contains(Person p)
  {
    for (Widget w : inventory.inventory)
    {
      if (((Employee) w).person == p)
        return true;
    }
    return false;
  }

  /**
   * Hire an identified person.
   *
   * @param   p person to hire
   * @return  Person hired, or null
   */
  public Person hire(Person p)
  {
    Iterator<Widget> eitr = inventory.getIterator();

    while (eitr.hasNext())
    {
      Employee e = (Employee) eitr.next();
      if(e.person == p)
      {
        eitr.remove();
        return p;
      }
    }
    return null;
  }

  /**
   * Hire an employee, but restrict to employees of a particular region.
   *
   * @param salary minimum salary to hire employee at
   * @param bank if provided - restrict hiring to employees with account at
            this bank
   * @param region if provided - restrict hiring to employees in this region
   * @return Person hired, or null
   */

  public Inventory hire(long salary, Bank bank, String region)
  {
    Inventory inv =  buy(salary, bank, region);
	
	if((inv == null) && salary > sellPrice)
		sellPrice = bidPrice = salary;

	return inv;
  }

  /**
   * Currently used by InvestmentCompany
   *
   * @param askingPrice salary for employee
   * @param regionName	region to hire from (or null)
   * @param type	  	type of employee
   * @return Inventory containing employee or null if not available
   * Refactor -and merge (jm)??
   */
  public Inventory hire(long askingPrice, String regionName, 
                        Class<? extends Person> type)
  {
    Inventory newinv = new Inventory(inventory.product, true, true);

    if (inventory.size() == 0)
    {
      Base.DEBUG("Inventory:hire() - None exist!" + type.toString());
      return null;
    }

    inventory.sort();

    Iterator<Widget> eitr = inventory.getIterator();

    while (eitr.hasNext())
    {
      Employee e = (Employee) eitr.next();

      if (type.isAssignableFrom(e.person.getClass()) 
	      && e.person.desiredSalary <= askingPrice)
      {
        if ((regionName != null)&& !e.person.getRegionName().equals(regionName))
        {
        }
        else
        {
          eitr.remove();
          e.person.unemployed = false;
          bidPrice = e.person.getSalary();

          newinv.add(e);
          //adjustPrices();
          return newinv;
        }

      }
    }
    return null;
  }

  /**
   * Buy(hire) an employee at specified salary. If regionName is set
   * hiring will be restricted to employees in the specified regions.
   * If a bank is provided, hiring will be restricted to employees with
   * accounts at this bank.
   * Selection is also biased to provide employees who are already in
   * debt, in order to allow them to pay that debt. (Without this
   * simulations crash far too quickly to be useful.)
   *
   * todo: make debt check a simulation attribute
   *
   * @param askingPrice Maximum salary willing to pay
   * @param bank        Bank used by employee or null if no restriction
   * @param regionName  If set, restrict to employees from this region
   * @return Inventory Inventory containing person hired
   */

  public Inventory buy(long askingPrice, Bank bank, String regionName)
  {
    Inventory newinv = new Inventory(inventory.product, true, true);

    if (inventory.size() == 0)
    {
      //System.out.println("Labour shortage - Full Employment!");
      return null;
    }

    // Sort the Inventory only prior to querying it for efficiency

    inventory.sort();
    // inventory.audit();

    Iterator<Widget> eitr = inventory.getIterator();

    while (eitr.hasNext())
    {
      Employee e = (Employee) eitr.next();

      if ((askingPrice == -1) || (e.person.desiredSalary <= askingPrice) ||
          (e.person.getDebt() > 0) ||
          (e.person.unemployedTime > MAX_UNEMPLOYMENT_TIME))
      {
        if ((regionName == null)
            || e.person.getRegionName().equals(regionName))
        {
          if((bank == null) || (bank == e.person.getBank()))
          {
             eitr.remove();
             e.person.unemployed = false;
             e.person.unemployedTime = 0;
             bidPrice = e.person.getSalary();
             newinv.add(e);
             //adjustPrices();
             return newinv;
          }
        }
      }
    }
    return null;
  }

  /**
   * Prices are adjusted after each buy/sell by updating last offer/bid prices
   * Assumes inventory is sorted. Note there isn't a buy price, since people
   * adjust their own salaries.
   */
  public  void adjustPrices()
  {
    inventory.sort();

    Iterator<Widget> eitr = inventory.getIterator();

    while (eitr.hasNext())
    {
      Employee e = (Employee) eitr.next();

      //if(e.person.desiredSalary > 0)
      //    e.person.desiredSalary--;
    }

    // inventory.audit();
    if (inventory.size() == 0)
    {
      bidPrice = sellPrice = govt.minWage;
    }
    else
    {
      bidPrice = sellPrice = ((Employee) (inventory.getFirst())).person.desiredSalary;
	  if(sellPrice < govt.minWage)
		 bidPrice = sellPrice = govt.minWage;
    }
  }

  /**
   * Return lowest salary.
   *
   * @return salary of lowest available hiree
   */

  public long getLowestSalary()
  {
    return getAskPrice();
  }

  /**
   * Are there any workers available for hire?
   *
   * @return t/f
   */
  public boolean hasWorkers()
  {
    assert (inventory.size() == 0 && sellPrice == -1) : name + " Failed Internal Consistency";

    return inventory.size() != 0;
  }

  public void setPrice(long price)
  {
     bidPrice = sellPrice = price;
  }

  /**
   * First round evaluate method for setup.
   */
  @Override
  public void evaluate()
  {
  }

  /**
   * Evaluate market for model
   *
   * @param step   step number
   * @param report t/f generated detailed report
   */
  public void evaluate(boolean report, int step)
  {
    bought = sold = false;

    //adjustPrices();
    s_unemployed.add(inventory.size());
    
    for(int i = 0; i < inventory.inventory.size(); i++)
    {
      Employee e = (Employee)inventory.inventory.get(i);

      // Prevent worker being evaluated twice in the same
      // round if they are fired.
      if(e.person.unemployedTime > 0)
         e.person.evaluate(report, step);

      e.person.unemployedTime++;
    }

    if (report)
      this.print();
  }

  /**
   * Total number of available workers.
   *
   * @return available workers.
   */
  public long totalAvailableWorkers()
  {
    return inventory.size();
  }

}
