/* Program : Threadneedle
 *
 * Person  : homo economicus
 *           
 * Author  : Jacky Mallett
 * Date    : February 2012
 * Comments:
 */
package core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import java.awt.*;
import java.util.*;

import statistics.*;
import static base.Base.*;

public class Person extends Agent 
{
  @Expose public Profile profile  = new Profile(); // profile for needs/wants
  @Expose public long desiredSalary = 50;      // Desired salary level at start
  @Expose public boolean randomPurchase;

  public Agent employer = null;                // Agent's Employer
  private int age;                             // TTL in other contexts
  private int myConsumption;
  private int myPrice          = 1;
  private int consumption_Food = 2;

  protected long salary;                       // Salary to be paid this round

                        // Flag on employment status, toggled by labour market
  public  boolean unemployed       = false; 
                        // No. of steps unemployed (controlled by labour market)
  public  int unemployedTime       = 0;  

  Statistic s_consumption;

  /**
   * Constructor (using properties field)
   *
   * @param n Unique and identifying name
   * @param g Government
   * @param b Bank
   * @param properties map containing initialdeposit - or 0
   */

   public Person(String n, Govt g, Bank b, HashMap<String, String> properties)
   {
      this(n, g, b, Integer.parseInt(properties.get("initialDeposit")));
   }

  /**
   * Constructor
   *
   * @param n Unique and identifying name
   * @param g Government
   * @param b Bank 
   * @param deposit Initial deposit
   */

  public Person(String n, Govt g, Bank b, long deposit)
  {
    super(n, deposit, g, b);
  }

  /**
   * Constructor for GSON
   */
  public Person()
  {
  }

  /**
   * Initialisation is done here to accomodate GSON loading from file.
   *
   * @param markets Market group to be used by this agent
   */
  public void init(Govt g)
  {
    super.init(g);
    this.markets = g.markets;

    // Profile may be null if we are loading from saved file, and
    // are still constructing the agent.

    if(profile == null) profile = new Profile();

    profile.needs.forEach((key, value) -> value.init());
    profile.wants.forEach((key, value) -> value.init());

    
    
    
    //trying to differntiate consumption
    //changed by anton
   s_consumption = Statistic.getStatistic("consumption", null, Statistic.Type.COUNTER);
   /*
    for (Need need : profile.needs.values())
    { 
    	s_consumption = Statistic.getStatistic(need.product + "-consumed",
            "consumption", Statistic.Type.COUNTER);
    	
    }
   */
    
    govt.s_population.inc();

	// Salaries are restricted to be above 0 

    if(desiredSalary <= g.minWage) 
    {
       desiredSalary = g.minWage;
    }
	setSalary(desiredSalary);

    setUnemployed();
  }

  @Override
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

  public void setEmployer(Agent company, long salary, Color colour)
  {
    this.employer = company;
    this.myColor = colour;
    setSalary(salary);
  }

  /**
   * Test to see if this person is employed Todo: Unemployment is defined as
   * being on the labour market, extend to school, etc. Investors don't have
   * employers
   *
   * @return T/F True if unemployed
   */

  public boolean unemployed()
  {
    return employer == markets.getMarket("Labour") || employer == null;
  }

  /**
   * Pay Salary.
   * <p>
   * Called by employer, to allow statistics to be accumulated locally.
   * Effects a direct transfer from employer to person's bank account.
   *
   * @param employer Account belonging to employer for transfer from
   * @return Amount paid.
   */

  public long paySalary(Account employer)
  {
    if(this.salary == 0) return 0;		// Ignore 0 salary payments

    s_income.add(this.salary);          // Allow for multiple employers

    if (employer.transfer(this.getAccount(), this.salary, "salary:"
                          + employer.getName() + "->" + this.name))
      return this.salary;
    else
      return 0;                         // transfer failed, no salary paid
  }

  /**
   * This allows agents (Borrower specifically) to set the salary
   * they will be repaid. 
   *
   * Todo: refactor to allow employer to approve/not-approve
   *
   * @param employer Account salary to be paid from
   * @param salary   Amount to be paid
   *
   * @return salary, or 0 if insufficient funds
   */

  public long paySalary(Account employer, long salary)
  {
    s_income.add(salary);

    if(this.salary == 0) return 0;		// Ignore 0 salary payments

    if (employer.transfer(this.getAccount(), salary,
                          "salary/" + employer.getName()))
      return salary;
    else
      return 0;

  }

  /**
   * Sets and pays the salary for this agent
   *
   * @param employer   Account of employer
   * @param newSalary  New salary for agent
   * @return Amount paid
   */
  public long setPaySalary(Account employer, long newSalary)
  {
    this.setSalary(newSalary);
    return this.paySalary(employer, salary);
  }

  /**
   * Reduce an agents salary. Salaries cannot go below 1. Returns true if
   * succeeded, false if not. (Override method to create behavioural responses
   * to salary changes)
   *
   * @param amount Amount to remove from salary
   */

  public void reduceSalary(long amount)
  {
    setSalary(salary - amount);
  }

  /**
   * Set this agent as unemployed and list on labour market.
   */
  public void setUnemployed()
  {
    LabourMarket labour;

    // If they´re already unemployed then there´s no action.
    if (!unemployed)
    {
      labour = (LabourMarket)markets.getMarket("Labour");

      // Post myself back onto the job market at my current salary.

      labour.sell(new Employee(this, desiredSalary, -1, -1));
      setEmployer(labour, salary, Color.RED);
    }
  }

  /**
   * Remove person from labour market and any employment, and set employer
   * to itself.
   */

  public void setSelfEmployed()
  {
     if(unemployed||employer==null){
    	((LabourMarket)markets.getMarket("Labour")).hire(this); 
     }else{
        employer.fireEmployee(this, null);
     }
     employer = this;
     unemployed = false; //this is needed isn't it?? /anton
  } 
  /**
   * Return total demand average for this round.
   *
   * @return total demand for needs and wants
   */
  public int getDemand()
  {
    return profile.getTotalDemand();
  }

  /**
   * Set the markets used by this Person, and if not an employee, register at
   * the labour market.
   *
   * @param m The market container for this set of markets.
   */
  public void setMarket(Markets m)
  {
    LabourMarket market;

    this.markets = m;
    // Register at the labour market.

    if (employer == null)
    {
      if ((market = this.markets.getLabourMarket()) == null)
      {
        throw new RuntimeException("No labour market for " + this.name);
      }
      else
      {
        market.sell(new Employee(this, desiredSalary, -1, -1));
      }
    }
  }

  /**
   * Evaluation function for agent.
   *
   * @param report t/f Print detailed report.
   */

  protected void evaluate(boolean report, int step)
  {
    Market market;

    // Pay debts.
    // This is a bit of kludge, since we can't rule out that a child
    // agent may also call payDebts(); The issue is how automatic
    // should debt payment be in the simulation?
    
    if(!paidDebts) payDebt(); 

	/*
     * Attempt to purchase needs. Note, if the list is not randomised
	 * order of evaluation issues will influence product demand.
	 */


	ArrayList<Need> needs = new ArrayList<>(profile.needs.values());

    if(randomPurchase)
	   Collections.shuffle(needs);

    for (Need need : needs)
    {
      //System.out.println("name: "+name+" items in inventory: "+need.store.getTotalItems()+" maxSizeInventory: "+need.storeQ);	
      if (need.store.getTotalItems() < need.storeQ)
      {
        // Is this a simple consumption item, or does it require a bank
        // loan
        market = markets.getMarket(need.store.product);

        assert(market == null) : "Market missing for product " + need.store.product;
        // Attempt to borrow if necessary.
        if(market.useLoan)
        {
           Widget w = market.getLowestPrice();

           long savings = getDeposit()/2;

           if((w != null) && (getDebt() <= 0) && !unemployed() 
                         && (w.price <= (getSalary() * 100) + savings ))
           {
             //System.out.println(name + " " + w.price + " " + getSalary());
             Loan loan = null;

             long amount   = w.price - savings;
             //int  duration =  (int)((amount*4)/salary);
             int duration = 120;
             loan = getBank().requestLoan(this.getAccount(), amount,
                               duration, Time.MONTH, BaselWeighting.MORTGAGE, 
                               Loan.Type.COMPOUND);
             if(loan != null)
             {
                w = market.buy(w, this, w.price);

                if(w == null)
                  throw new RuntimeException("Purchase failed after loan granted");

                need.store.add(w);
                loan.addCollateral(w);
                need.lastPricePaid = w.price;
             }
           }
        }
        else
        {
          Inventory newItems = market.buy(-1, need.getRequired(),
                                              this.getAccount());
          
          
          if (newItems != null)
          {
            need.store.merge(newItems);
            need.lastPricePaid = newItems.getFirst().lastSoldPrice;
            //System.out.println("step "+step+" name: "+name+" items bought: "+newItems.getTotalItems()+" Items in inventory: "+need.store.getTotalItems());	
          }
          else
          {
            //System.out.println(name + " failed to buy Need: "
            //                   + need.store.product);
          }
        }
      }
    }

    // Consumption
    long consumed;

    for (Need need : profile.needs.values())
    {
    	
      consumed = need.consume();
      
      //System.out.println(name+ " shortage of " + need.store.product + ": " + consumed );
      if (consumed > 0)
      {
    	  //System.out.println("name "+name+" need "+ need.store.product+" consumed "+consumed);
        s_consumption.add(consumed);
      }
    }


    // Govt will not pay unemployment unless enabled, and has sufficient
    // funds.
    if(unemployed()) {
      govt.payUnemployment(getAccount());
    }

    // Always pay UBI if enabled
    govt.payUBI(getAccount());
    
    // payTax(govt.personalTaxRate, govt.personalCutoff);
    govt.payPersonalTax(this.getAccount(), s_income.get());
  }
  
  //added by Anton
  //needed for summaryView
  /*
  public long getInventory(int n){
	  long numberOfItems = 0;
	  ArrayList<Need> needs = new ArrayList<>(profile.needs.values());
	  if(n<needs.size()){
		  //System.out.println("need "+needs.get(n)+" inventory "+ needs.get(n).store.getTotalItems());
		  numberOfItems = needs.get(n).store.getTotalItems();
	  }else{
		  numberOfItems = 0;
	  }
	  return numberOfItems;
  }
  */
  
  /**
   * Reset the statistics for this round.
   */

  public void resetRoundStatistics()
  {
    getAccount().incoming = 0;
    getAccount().outgoing = 0;
  }

  public String getCurrentSetup()
  {
    return name + " " + getBankName() + " " + getDeposit() + " " + employer.name;
  }

  /**
   * Get salary for this person.
   *
   * @return Salary amount for this accounting period
   */

  public long getSalary()
  {
    return salary;
  }

  /**
   * Set salary for this person, as the higher of the supplied salary
   * or the Govt minimum wage.
   *
   * Todo: extend to regions.
   *
   * @param newSalary New value for salary
   */
  public void setSalary(long newSalary)
  {
	if(newSalary >= govt.minWage)
    {
       this.salary = newSalary;
	}
	else
	   this.salary = govt.minWage;
  }

  /**
   * Set the profile for this worker. Existing profile is discarded.
   *
   * @param newProfile New profile for worker
   */

  public void setProfile(Profile newProfile)
  {
     this.profile = new Profile(newProfile);
  }

  /**
   * Return the name of the person's employer, or 'unemployed'
   *
   * @return employer's name
   */

  public String getEmployer()
  {	  
    if (this.employer == null || this.employer instanceof LabourMarket){
      return "unemployed";
    }
    else{
      return employer.name;
      }
  }

  public void print(String title)
  {
    if (title != null)
      System.out.println(title);

    System.out.println(this);
  }

  public String toString()
  {
    return name + ": Salary=" + salary + " Deposit: $"
           + getAccount().getDeposit();
  }

}
