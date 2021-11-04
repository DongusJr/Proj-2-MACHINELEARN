/* Program: Threadneedle
 *
 * Simulation Engine
 *
 * Controller for economic model. Keeps track of all objects in the model,
 * and provides step/run controls for running simulations from GUI or
 * batch/command line.
 *
 * Author  :  Jacky Mallett
 * Date    :  August 2014
 */

package core;

import base.Base;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import statistics.Statistic;

import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import java.lang.*;

import static base.Base.*;
import static statistics.Statistic.Type.COUNTER;

public class SimulationEngine extends Observable
{
  public Govt   govt            = null; // Government for simulation.
  public String defaultBankName = null;

  long defaultdeposit = 0;              // todo: make configurable

 /*
  * Aggregate lists of objects in simulation for purposes of tracking,
  * statistics and randomisation. Do not use explicitly for modelling
  * purposes.
  */

  public ArrayList<Person>       employees = new ArrayList<>(20);
  public ArrayList<Company>      companies = new ArrayList<>(10);

  // Free form description of simulation for user tagging

  public String description = "";

  // Map of branch name to branch - todo: examine structure
  // public HashMap<String, Branch> branchlist = new HashMap<String,
  // Branch>();

 /*
  * List of all objects created - used for sanity checking.
  */

  public HashMap<String, Agent> objectList = new HashMap<>();

  // Statistics maintained by the simulation.

  Statistic s_totalWorkerDeposits;
  Statistic s_totalMarketDeposits;
  Statistic s_totalCompanyDeposits;

  Statistic s_totalValueGoodsSold;  // Total value of goods sold by markets
  Statistic s_calculatedVelocity;   // The V part of the MV/PT equation
  Statistic s_totalTransactions;    // T part of MV/PT equation

  /*
   * Debugging/Development support. Provide a periodic check on objects not
   * being correctly removed from the simulation.
   */

  WeakHashMap<Agent, String> weakHashMap = new WeakHashMap<>();
  

  public SimulationEngine()
  {
    s_totalWorkerDeposits = Statistic.getStatistic("totalWorkerDeposits",
                                                   "distribution", COUNTER);
    s_totalMarketDeposits = Statistic.getStatistic("totalMarketDeposits",
                                                   "distribution", COUNTER);
    s_totalCompanyDeposits = Statistic.getStatistic("totalCompanyDeposits",
                                                    "distribution", COUNTER);

    // Statistics for Macro-economic analysis

    s_totalValueGoodsSold = Statistic.getStatistic("mvpt-PT", "mvpt", 
                                                    COUNTER, 12);
    s_calculatedVelocity= Statistic.getStatistic("mvpt-V", "mvpt", COUNTER, 1);
    s_totalTransactions = Statistic.getStatistic("mvpt-T", "mvpt", COUNTER, 1);
  }

  /**
   * Reset the simulation. All containers are cleared, nodes in new simulation
   * should be re-added.
   */

  public void resetAll()
  {
    description = "";

    govt = new BaselGovt(govt.name, "Central Bank", 0);
    govt.hasCentralBank = true;
    govt.setBanks(employees);

    employees.clear();
    companies.clear();
    govt.markets.removeAll();
    objectList.clear();

    Base.resetAll();
    Statistic.resetAll();

    // In case this is called from the CLI with a gui in operation.

    setChanged();
    notifyObservers();

    s_totalWorkerDeposits = Statistic.getStatistic("totalWorkerDeposits",
                                                   null, COUNTER);
    s_totalMarketDeposits = Statistic.getStatistic("totalMarketDeposits",
                                                   null, COUNTER);
    s_totalCompanyDeposits = Statistic.getStatistic("totalCompanyDeposits",
                                                    null, COUNTER);

    s_totalValueGoodsSold  = Statistic.getStatistic("mvpt-PT", "mvpt", COUNTER, 1);
    s_calculatedVelocity   = Statistic.getStatistic("mvpt-V", "mvpt", COUNTER, 1);
    s_totalTransactions = Statistic.getStatistic("mvpt-T", "mvpt", COUNTER, 1);
  }

  /**
   * Main evaluation loop for simulation.
   * <p>
   * Todo:
   * <p>
   * Examine branch evaluation
   * 
   * There is still an implicit order of eval here, which needs to be
   * improved.
   */

  public void evaluate()
  {
    int totalSold = 0;
    int totalDemand = 0;
    int totalSupply = 0;
    int totalEmployed = 0;
    int totalSalaries = 0;
    int totalBankDebtors = 0;

    DEBUG("====================================================");

    Collections.shuffle(companies, random);
    Collections.shuffle(employees, random);

    // Run all agent's individual evaluation for this step.
	//
	// Order of evaluation is ... an interesting issue. Companies
	// are evaluated before workers, because of hiring/firing dependendencies,
	// and banks are evaluated last. 
	// 
	// todo: expose to user and let them decide.

    govt.evaluate(Base.step, false);
    govt.markets.evaluate(Base.step, false);

    // Evaluate Branches
    /*
     * for(Branch b : branchlist.values()) {
     * //System.out.println("Evaluating : " + c.name); b.evaluate(false,
     * Base.step); }
     */
    for(int i = 0; i < companies.size(); i++)
    {
      companies.get(i).evaluate(Base.step, false);
    }

    // System.out.println("\t ** Evaluating Employees **");

    // Collect employee information for reporting

    for (int i = 0; i < employees.size(); i++)
    {
      employees.get(i).evaluate(Base.step, true);
      s_totalWorkerDeposits.add(employees.get(i).getDeposit());
      totalSalaries += employees.get(i).s_income.get();

      totalDemand += employees.get(i).getDemand();

      if (!employees.get(i).unemployed())
        totalEmployed++;

      if (employees.get(i).getAccount().getTotalBankDebt() > 0)
        totalBankDebtors++;

      govt.s_totalActiveMoneySupply.add(Math.abs(employees.get(i).getAccount().getBalance()));
      employees.get(i).resetRoundStatistics();
    }

    totalSupply = 0;
    for (Company c : companies)
    {
      s_totalCompanyDeposits.add(c.getDeposit());
      govt.s_totalActiveMoneySupply.add(Math.abs(c.getAccount().getBalance()));
      c.getAccount().incoming = 0;  // todo: tidy this up -- jm
      c.getAccount().outgoing = 0;
    }


    for (Bank bank : govt.getBankList().values())
    {
      bank.evaluate(Base.step, false);
    }


    // Collect Market information for reporting

	/*
     * Iterator<Market> mkt_itr = markets.getIterator();
	 * while(mkt_itr.hasNext()) { Market m = mkt_itr.next(); //Todo: direct
	 * reporting
	 * 
	 * // For all product markets (Labour excluded)
	 * 
	 * if(!m.getProduct().toUpperCase().equals("LABOUR")) { totalSold +=
	 * m.resetTotalSaleValue();
	 * 
	 * // Note: these are defined in chart controller.
	 * 
	 * chartCtrl.addItem(GDP, "$ Sales", totalSold); chartCtrl.addItem(GDP,
	 * "$ Sales + Salaries", (totalSold + totalSalaries));
	 * chartCtrl.addItem(GDP, "Quantity Sold", m.resetTotalQuantitySold());
	 * chartCtrl.addItem(PRICE, m.getProduct(), m.getLAskPrice());
	 * 
	 * }
	 * 
	 * chartCtrl.addItem(CPI, "CPI", (int)(govt.getCPI()*12));
	 * 
	 * totalSalaries = 0; totalSold = 0; }
	 */
    // Get macro economic data
    Iterator<Market> mkt_itr = govt.markets.getIterator();
    while (mkt_itr.hasNext())
    {
      Market m = mkt_itr.next();
      s_totalMarketDeposits.add(m.getDeposit());
      govt.s_totalActiveMoneySupply.add(Math.abs(m.getAccount().getBalance()));
    }

    // "PT"
    for (Bank b : govt.banks.getBankList().values()) 
    {
      s_totalValueGoodsSold.add(b.getTotalPT("sale to market", Base.step, 
                                             "deposit"));
      s_totalValueGoodsSold.add(b.getTotalPT("salary", Base.step, "deposit"));

      s_totalTransactions.add(b.getTotalTransactions("sale to market",Base.step,
                  "deposit"));
      s_totalTransactions.add(b.getTotalTransactions("salary", Base.step,"deposit"));
    }



    // 
    // try M3
    // 
    //s_calculatedVelocity.add(s_totalValueGoodsSold.getCurrent()/govt.s_totalActiveMoneySupply.getCurrent());

	/*
     * Part of the what is money problem - the Bank's liability interest
	 * income is a grey area, at any given instantaneous point in time. It
	 * will subsequently be recognised, after defaults and provisions are
	 * removed.
	 */
    // if(govt.getExtendedMoneySupply())
    // {
    // totalMoney += govt.centralbank.getTotalLedger("interest_income");
    // }

	/*
     * Non-cash income has particular significance in an Icelandic
	 * indexed-linked loan regime, where additional monetary expansion is
	 * being created by its being treated identically to interest income.
	 */

    // if(govt.getNonCashIncome())
    // {
    // totalMoney += govt.centralbank.getTotalLedger("non-cash");
    // }

    // todo: add loan defaults


    Base.step += 1;
    Statistic.rolloverAll();

    // Development support - check for agents not being removed from 
    // simulation properly

    if(weakHashMap.size() > 0)
    {
       System.out.println("DBG: Check container removal failed for: ");
       for(String name : weakHashMap.values())
       {
          System.out.println("\t" + name);
       }
    }

    //auditWorkers();
  }

  /**
   * Load a simulation from a configuration file.
   *
   * @param file File containing json format simulation config.
   * @return t/f Succeeded or failed.
   */
  public boolean loadSimulation(File file)
  {
    Class<?> clss;

    // Container for objects specified in config file
    ArrayList<Object> simobjs = new ArrayList<>(20);

    try
    {
      Gson gson = new Gson();
      BufferedReader br = new BufferedReader(new FileReader(file));
      JsonObject json = gson.fromJson(br, JsonObject.class);

      // Get description for entire simulation
      description = json.getAsJsonPrimitive("description").getAsString();

      // Read individual agents from array
      JsonArray agents = json.getAsJsonArray("GsonAgent");

      for (int i = 0; i < agents.size(); i++)
      {
        JsonObject jobj = agents.get(i).getAsJsonObject();

        try
        {
           clss = Class.forName("core." + jobj.get("clss").getAsString());
        }
        catch (Exception e1)
        {
          try
          {
            clss = Class.forName("agents." + jobj.get("clss").getAsString());
          }
          catch (Exception e2)
          {
            System.out.println("Error: Failed to find agent class " + 
                                jobj.get("clss").getAsString());
            return false;
          }
        }
           
        Object agent = getObject(jobj.get("json").getAsString(), clss);

        // Govt must be first object set in the simulation engine,
        // as it becomes a dependency for later agents.

        //System.out.println("validating " + ((Agent)agent).name);
        if (Govt.class.isAssignableFrom(agent.getClass()))
        {
          govt = (Govt) agent;
          initGovt(govt);
          setChanged();
          notifyObservers();
        }
        else
        {
          simobjs.add(agent);
        }
      }

      System.out.println("Loading config file: " + file);

      // Add loaded agents to simulation

      for (Object o : simobjs)
      {
        validateModel(o);
      }
    }
    catch (FileNotFoundException e)
    {
      return false;
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.out.println("Unable to read configuration file: " + file);
      return false;
    }

    return true;
  }

  /**
   * Initialise government for simulation loading from config. (Cross-check
   * with createGovt which is used for new simulations.)
   * <p>
   * Note: this expects that the LabourMarket is included in the config file
   * and doesn't have to be created auto-magically for the gui.
   *
   * @param g Government to initialise
   */

  public void initGovt(Govt g)
  {
    govt = g;
    govt.setBanks(this.employees);
    govt.initStatistics();

    if (g.getClass().equals(Govt.class))
    {
      this.govt.markets.defaultbank = govt.getBank();
    }

    addToContainers(govt);

  }

  /**
   * Manage the simulation engine's containers. All discrete entities in
   * simulation are put in the ObjectList for a convenient lookup. Markets are
   * held in the markets container, companies in the companies container, and
   * Persons in the employee container. Banks are held in the Govt/central
   * Bank banks container.
   * <p>
   * Side effects: Containers are used by the save simulation, with some order
   * of definition dependencies, consequently any changes in which type of
   * object is held in which list must be reflected there.
   * <p>
   * Todo: (cosmetic) change employee to people
   *
   * @param agents Agent(s) to be added.
   */

  public void addToContainers(Agent... agents)
  {
    for (Agent a : agents)
    {
      objectList.put(a.name, a);

      if (a instanceof core.Region)
      {
         govt.regions.put(a.name, (Region) a);
      }
      else if (a instanceof Market)
      {
		// At the moment there is a restriction on one commodity 
		// market/govt/region
		if(govt.markets.getMarket(((Market)a).product) == null)
           govt.markets.addMarket((Market) a);
		else
		   System.out.println("Warning: duplicate market detected " + 
				              ((Market)a).product);
      }
      else if ((a instanceof Company) && (!(a instanceof Bank)))
      {
        checkContainerAdd(companies, a);
      }
      else if (a instanceof Person)
      {
        checkContainerAdd(employees, a);
      }
      else if ((a instanceof Bank) && (govt.banks.getBank(a.name) == null))
      {
        govt.banks.addBank((Bank) a);
      }
    }
  }

  /**
   * Sanity checking - check agent is not already in container before
   * adding, and throw an exception if it is.
   *
   * @param c  container to add agent to
   * @param a  agent to add
   */
  private void checkContainerAdd(ArrayList c, Agent a)
  {
	  if(c.contains(a))
		  throw new RuntimeException("Duplicate add in Simeng");
	  else
		 c.add(a);
  }

  /**
   * Remove supplied agent from its containers. 
   *
   * (Initial design has always been to add objects to the simulation, and
   *  not remove them on the fly. Consequently this capability is not yet
   *  fully implemented.
   *
   *  Currently supported - workers (from labourview pane)
   *
   *  @param agents   agents to remove from containers
   */

   public void removeFromContainers(Agent... agents)
   {
      for(Agent a : agents)
      {
          weakHashMap.put(a, a.name);

          if(a instanceof Person)
          {
             ((Person)a).setSelfEmployed();
             employees.remove(a);
          }
      }
   }
  /**
   * Create government for simulation.
   *
   * @param name          Name for country being governed
   * @param type          Type (class) for government
   * @param bankingSystem Type of banking system
   */

  public void createGovt(String name, String type, String bankingSystem)
  {
    try
    {
      Class<?> c = Class.forName("core." + type);
      Constructor<?> cons = c.getDeclaredConstructor(String.class,
                                                     String.class, Long.TYPE);

      if (bankingSystem.equals("Basel Capital"))
      // Government's Bank defaults to central Bank for all other cases
      // BaselGovt's don't have a default Bank - the first Bank created
      // by the user will be set to be the default
      {
        govt = (Govt) cons.newInstance(name, "Central Bank", 0);
        govt.hasCentralBank = true;

        setChanged();				// Notify observers of new government
        notifyObservers();

        addToContainers(govt);
        govt.setBanks(employees);
      }
      else
      {
        System.out.println("Unrecognised banking system " + bankingSystem);
        System.exit(0);
      }
    }
    catch (Exception e)
    {
      System.out.println("Unable to instantiate Govt : " + type);
      e.printStackTrace();
    }

    // Create default labour market. Nb. government's Bank is used
    // to provide account, but LabourMarket is bid/sell, so it shouldn't
    // be used.
    LabourMarket lmarket = new LabourMarket("Labour", "Labour", govt,
                                            govt.getBank());
    addToContainers(lmarket);
  }

  /**
   * Create a market for the simulation. This acts as a wrapper around the
   * markets createMarket method, and ensures that the market is correctly
   * added to the simulation's containers, as well as handling statistic
   * creation for the market's products.
   *
   * @param name    Name for market
   * @param product product managed by market
   * @param govt    Government market is under
   * @param bank    Bank used by market
   * @param deposit initial deposit in Bank
   * @param region  Region market belongs to
   * @return Name of created market
   */
  public String createMarket(String name, String product, Govt govt,
                             Bank bank, long deposit, Region region)
  {
    String marketName = govt.markets.createMarket(name, product, bank,
                                             deposit, region);
    return name;
  }

  /**
   * Add an entity to the simulation. Entity will be created under simulation
   * government, and with an account at the default Bank.
   *
   * @param type       Type (class) of object to be added
   * @param properties List of properties for object being created
   * @param bank       Bank for entity (or null if this is a Bank)
   * @param name       Name for agent (or null for auto id)
   * @return Added object
   */

  public Object addEntity(Class type, Map properties, Bank bank, String name)
  {
    Object o = null;
    String bankname;
    String agentname;

    if(bank == null) 
       bankname = defaultBankName;
    else
       bankname = bank.name;

    try
    {
      // Warn user if name is already being used.

      if(objectList.get(name) != null)
      {
         System.out.println(name + ": Already defined, using default ID");
      }

      if((name == null) || objectList.get(name) != null)
         agentname = type.getSimpleName() + "-" + getNextID();
      else
         agentname = name;

      // For no obvious reason tooltip is included in the properties
      // list from fxml, so this will not be exercised, unless a tooltip
      // isn't defined for the agent.
      if (properties.size() == 0)
      {
        Constructor<?> cons = type.getConstructor(String.class, Govt.class,
                                                  Bank.class, HashMap.class);
		// Following was commented out - reason unknown
		// todo: check interface for all agents to support HashMap instantiation 
		//       i.e. Saver, etc.
        HashMap<String, String> params = new HashMap<>(properties);
        o = cons.newInstance(agentname, govt, objectList.get(bankname), params);
      }
      else
      {
        Constructor<?> cons = type.getConstructor(String.class, Govt.class,
                                                  Bank.class, HashMap.class);
      
        HashMap<String, String> params = new HashMap<>(properties);
        o = cons.newInstance(agentname, govt, objectList.get(bankname), params);
      }
    }
    catch (Exception e)
    { 
      System.out.println("Unable to instantiate agent: " + type);

      e.printStackTrace();

      if(properties.size() == 0)
         System.out.println("\t - this may be because no properties were provided for agent");

      return "Error: Unable to instantiate agent";
    }

    String result = validateModel(o);

    // Return an error string if validation failed, or the object itself.

    if (result != null)
	{
	  System.out.println("error in sim engine: " + result);
      return result;
	}

    return o;
  }

  /**
   * Perform very basic consistency checks on the model as it is being added,
   * in order to enforce definition dependencies - i.e. banks must be added
   * before company's/people etc. If validation is successful, agent being
   * checked is added to the list of objects in the simulation.
   *
   * @param o object being added to the model
   * @return Error text, of null if ok.
   */

  public String validateModel(Object o)
  {
    if (o instanceof BaselGovt)
    {
      if (govt == null)
      {
        this.govt = (Govt) o;
      }
      else
        return "Government already defined";
    }
    else
      addToContainers((Agent) o);

    // Government must be set before other agents can be added to the
    // simulation.

    if (govt == null)
      return "Government has not been defined";

    if (o != null)
    {
      if (((Agent) o).govt == null)
        ((Agent) o).init(this.govt);
    }
    else
      return "Object is null";

    // The gui observer class will already have picked this up,
    // but the batch file load won't.
    if ((o instanceof Market) && ((Market) o).getProduct() == null)
    {
      ((Market) o).setProduct();
    }

    if (o instanceof Bank)
    {
      if (defaultBankName == null)
        defaultBankName = ((Bank) o).getName();

      // In some cases the government's Bank will already be in the
      // list due to govt initialisation.
      if (govt.getBank(((Bank) o).name) == null)
        govt.addBank((Bank) o);

    }
    // Todo: allow multiple markets containers.
    // Todo: change init to allow being called on all agents
    if (o instanceof Company || (o instanceof Bank))
    {
      Company c = ((Company) o);
      c.setMarkets(govt.markets);
      c.setMarket(govt.markets.getMarket(c.product));
      
      govt.addCompany(c);       //added by Anton

      if (c.bankname == null)
        c.setBank(c.bankname);

      c.initStatistics();  // Necessary for banks
    }

    // Provide model dependent initialisation for agents being
    // loaded from file.
    // todo: use to shift all agents over to use this approach and get
    // rid of agent specific code here. (in progress jm)
    try
    {
        Method method;
        if(((Agent)o).govt == null)
        {
           method = o.getClass().getMethod("init", Govt.class);
           method.invoke(o, govt);
        }
    }
    catch(Exception e)
    {
        System.out.println(e);
    };

    // If the borrower has a specified lender in the config file, set it
    // here.

    if (o instanceof Borrower)
    {
      if (((Borrower) o).lender == null)
      {
        Company c = (Company) objectList.get(((Borrower) o).lendername);
        if (c == null)
          System.out.println("No lender matching borrower "
                             + ((Borrower) o).name + " : "
                             + ((Borrower) o).lendername);
        else
          ((Borrower) o).lender = c;

        if (((Borrower) o).bankEmployee)
          ((Borrower) o).getBank().hireEmployee((Borrower) o, -1);
      }
    }

    return null;
  }

  /**
   * Title is set to country name, time elapsed after first simulation step.
   *
   * @return Title for main simulation window
   */

  public String getTitle()
  {
    if (govt == null)
      return "Threadneedle";
    else
       return govt.country + " Year " + (Base.step / 360) + " Month "
             + (Base.step % 360)/30 + " Day " +
             + ((Base.step % 360) % 30);
  }

  // Support for Batch and CLI Operation

  /**
   * Print out simulation setup
   */
  public void printCurrentConfig()
  {
    for (Bank b : govt.banks.getBankList().values()) System.out.println(b.getCurrentSetup());
    for (Market m : govt.markets.markets) System.out.println(m.getCurrentSetup());
    for (Company c : companies) System.out.println(c.getCurrentSetup());
    for (Person p : employees) System.out.println(p.getCurrentSetup());
  }

  public String getCurrentSetup()
  {
    String config = "";

	for (Market  m  : govt.markets.markets) config += m.getCurrentSetup() +"\n";
    for (Company c  : companies) config += c.getCurrentSetup() + "\n";
    for (Person  p  : employees) config += p.getCurrentSetup() + "\n";


    return config;
  }

  /**
   * Used to pass a classname into Gson from a String by ducking around the
   * template issues.
   *
   * @param <T> agent
   * @param jsonString  conformant json string
   * @param objectClass Class of object to instantiate
   * @return Object of type objectClass instantiated from jsonString
   */
  public static <T> T getObject(final String jsonString,
                                final Class<T> objectClass)
  {
    Gson g = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
                              .create();

    return g.fromJson(jsonString, objectClass);
  }


  /**
   * Look up an agent in the model by its name.
   *
   * @param name Name of agent
   * @return Agent or null
   */

  public Agent getAgent(String name)
  {
    Object o = objectList.get(name);

    if (o instanceof Agent) return (Agent) o;
    else return null;
  }

  public void auditWorkers()
  {
    System.out.println("Worker Audit");
    for(Person p : employees)
    {
      System.out.println(p.name + ": " + p.employer.name);
    }
  }
}
