/* Program : Threadneedle
 *
 * Govt    : Base class for Government. Provides a basic government with
 *           taxation but no central bank.
 * 
 * Author  : Jacky Mallett
 * Date    : February 2012
 */
package core;

import com.google.gson.annotations.Expose;
import statistics.Statistic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static statistics.Statistic.Type.*;

public class Govt extends Agent
{
  @Expose public String country;                    // Govt's country
  @Expose public double capitalPct = 10.0;          // Capital pct % for Banks
  @Expose public int    reservePct = 10;
  @Expose public boolean reserveControls = true;
  @Expose public boolean capitalControls = false;
  @Expose public boolean payUnemployment = false;
  @Expose public int     unemploymentPayment = 1;
  @Expose public boolean payUBI = false;
  @Expose public int     ubiPayment = 1;

  @Expose public int personalTaxRate     = 0; // Flat rate tax on individuals
  @Expose public int corporateTaxRate    = 0; // Flat rate tax on companies.
  @Expose public int personalCutoff      = 0; // Lower bound cutoff for tax
  @Expose public int corporateCutoff     = 0; // Flat rate tax on companies.
  @Expose public int maxCivilServants    = 0; // As percentage of total pop.
  @Expose public double treasuryRate     = 10.0; // Treasury interest rate
  @Expose public int minWage             = 1; // Minimum wage

  public static String CB_LEDGERS = "src/resources/ledgers/cb.def"; // central Bank ledger
                                                      // definition

  public  Bank govtBank;                // Government's commercial govtBank
  public  Banks banks = new Banks();    // List of banks in this country
  public  CompanyRegistry companies  = new CompanyRegistry();  //list of companies in this country - added by Anton

                                        // List of regions in the country
  public HashMap<String, Region> regions = new HashMap<>();

  public List<StockExchange> stockExchanges = new ArrayList<>();

  protected Account incomeTaxAccount; // Account to receive tax

  public CentralBank centralbank    = null; // central Bank for this currency

  // Todo: legacy from deposit Bank - keep for regions, or remove??
  public boolean     hasCentralBank = false; // Flag to create a central Bank

  public Account centralBankAccount = null; // Govt acct at central Bank (if applicable)

  public Statistic s_totalMoneySupply;
  public Statistic s_totalActiveMoneySupply;
  public Statistic s_totalBankLoans;
  public Statistic s_totalRevenue;
  public Statistic s_totalPersonalRevenue;
  public Statistic s_totalCorporateRevenue;
  public Statistic s_totalCivilServants;
  public Statistic s_population;
  public Statistic s_totalUBI;
  public Statistic s_totalGovtFunds;

  /**
   * Default constructor without parameters must define defaults for anything
   * that can be set via config file.
   */
  Govt()
  {
    super("", 0, null, null);
    this.govt = this;
    this.Id = 0;
    this.country = "Unset";
    this.initialDeposit = 100;
    markets = new Markets(getBank(), this, initialDeposit);

    // init statistics must be called after Bank is set
  }

  /**
   * Constructor.
   *
   * @param name           name for Government's country (unique label)
   * @param bankname       name of Bank used by Government
   * @param initialDeposit starting deposit
   */

  Govt(String name, String bankname, long initialDeposit)
  {
    super(name, initialDeposit, null, null);

    this.govt = this;
    this.Id = 0;                           // Default Govt to 0 for now
    this.bankname = bankname;
    this.country = name;
    init(this);                            // todo: being called twice??

    markets = new Markets(getBank(), this, initialDeposit);
    initStatistics();
  }

  /**
   * Common initialisation method provided for statistics (due to GSON
   * constructor issues.)
   */
  public void initStatistics()
  {
    s_totalMoneySupply = Statistic.getStatistic(name + ":Money Supply", "money", COUNTER);
    s_totalActiveMoneySupply = Statistic.getStatistic("mvpt-M", "mvpt", COUNTER);
    s_totalBankLoans = Statistic.getStatistic(name + ":Bank Credit Supply", "money", AVERAGE);
    s_totalCivilServants = Statistic.getStatistic(name + ":Employees", "civilServants", COUNTER);
    s_totalRevenue = Statistic.getStatistic(name + ":Revenue", "revenue", COUNTER);
    s_totalPersonalRevenue = Statistic.getStatistic(name + ":PersonalRevenue", "personalRevenue", COUNTER);
    s_totalCorporateRevenue = Statistic.getStatistic(name + ":CorporateRevenue", "corporateRevenue", COUNTER);
    s_population  = Statistic.getStatistic("population",  null, Statistic.Type.NUMBER);
    s_totalGovtFunds = Statistic.getStatistic(name + ":Funds", "govtFunds", COUNTER);
    s_totalUBI = Statistic.getStatistic(name + ":UBI", "ubi", COUNTER);
  }

  public void evaluate(boolean report, int step)
  {
    // Include any money in the government's central bank account in
    // the total for the money supply.
    if(getBank() == banks.centralBank)
      s_totalMoneySupply.add(getDepositSupply() + this.getDeposit());
    else
      s_totalMoneySupply.add(getDepositSupply());

    //s_totalActiveMoneySupply.add(s_totalMoneySupply.getCurrent());
    s_totalBankLoans.add(getTotalBankLoans());
    s_totalCivilServants.add(employees.size());
    s_totalGovtFunds.add(getAccount().getDeposit());
  }

  /**
   * Add a region to this government.
   *
   * @param region Region to add
   */
  public void addRegion(Region region)
  {
    regions.put(region.name, region);
  }

  /**
   * @param b Name of Bank to use for govt - defaults to central Bank
   */
  public void setGovtBank(Bank b)
  {
    if (b == null)
    {
      this.govtBank = banks.centralBank;
      System.out.println("No Bank specified in setGovtBank - using central bank: " + govt.govtBank);
    }
    else
    {
      this.govtBank = b;
      this.bankname = b.name;
      banks.addBank(b);
    }
  }

  /**
   * Get the bank used by the government for its deposits.
   */
  @Override
  public Bank getBank()
  {
    return govtBank;
  }

  public void addBank(Bank b)
  {
    if (banks.getBank(b.name) == null)
      banks.addBank(b);
    else
      throw new RuntimeException(
        "Attempt to add bank already in banklist: " + b.name);
  }

  /**
   * added by Anton
   * add a company to the registry
   *
   * @param company .
   */
  public void addCompany(Company c)
  {
    if (companies.getCompany(c.name) == null)
      companies.addCompany(c);
    else
      throw new RuntimeException(
        "Attempt to add company already in companylist: " + c.name);
  }
  /**
   * added by Anton
   * Find a company by name.
   *
   * @param companyName Name of company.
   * @return Company Company or null if not found
   */

  public Company getCompany(String companyName)
  {
   if (companies == null)
     System.out.println("** List of Banks has not been initialised **");
   return companies.getCompany(companyName);
 }


  /**
   * Find a bank by name.
   *
   * @param bankname Name of bank.
   * @return Bank Bank or null if not found
   */

  public Bank getBank(String bankname)
  {
   if (banks == null)
     System.out.println("** List of Banks has not been initialised **");  
   return banks.getBank(bankname);
 }

  /**
   * Initialise the list of banks for the simulation. For a simple Govt class
   * there are no side effects wrt to central banking.
   *
   * @param employees List of employees (passed through to central Bank)
   * @return list of banks for this country
   */

  public final Banks setBanks(ArrayList<Person> employees)
  {
    /*
     * Governments with a central Bank must be initialised with the
     * hasCentralBank flag set.
     */
    if ((hasCentralBank) && (centralbank == null))
    {
      // Create a central Bank

      centralbank = new CentralBank("Central Bank", govt, CB_LEDGERS);

      // This has to be done here, as the central Bank itself isn't saved.
      // todo: move to constructor, or save the central Bank?
      centralbank.capitalPct = this.capitalPct;
      centralbank.cb_reserve = this.reservePct;
      centralbank.capitalControls = this.capitalControls;
      centralbank.reserveControls = this.reserveControls;
      banks.addBank(centralbank);

      // By default, the Government banks at the Central Bank. Which 
      // is usually the case. 

      this.govtBank = banks.getBank(bankname);

      // If there are any regions, set the central bank for them.

      for (Region r : regions.values())
      {
        r.centralbank = this.centralbank;
        r.hasCentralBank = true;
      }
    }

    /*
     * Set the Government's banking account. Governments can bank at the
     * central bank, or have a separate commercial bank account, or if there
     * is no central bank just have a commercial bank account.
     */

    // Government is banking at the central bank
    if (this.govtBank instanceof CentralBank)
    {
      centralBankAccount = centralbank
      .createAccount(this, initialDeposit);
      incomeTaxAccount = centralBankAccount;
      setMyAccount(centralBankAccount);
    }
    // Government is banking at a commercial bank
    else 
    {
      centralBankAccount = centralbank.createAccount(this, 0);
      setMyAccount(banks.getBank(bankname).createAccount(this,
       initialDeposit));
      incomeTaxAccount = this.getAccount();
    }
    return banks;
  }

  /**
   * Get the total money supply for this system - defining money supply as
   * deposits at the commercial banks.
   *
   * @return total deposit supply
   */

  public long getDepositSupply()
  {
    return banks.getTotalBankDeposits();
  }

  /**
   * Get total outstanding quantity of bank loans (assets)
   *
   * @return total loan supply
   */

  public long getTotalBankLoans()
  {
    return banks.getTotalBankLoans();
  }

  /**
   * Return list of banks. This will allow smaller groups of banks to formed
   * under a single government if necessary later.
   *
   * Todo: refactor to go via the central bank
   *
   * @return banks List of banks
   */

  public HashMap<String, Bank> getBankList()
  {
    return banks.getBankList();
  }

  /**
   * method is already existing --> getBank()
   * Return bank from name
   *
   * @param name Name of bank
   * @return bank Bank object or null
   * 

  public Bank lookupBank(String name)
  {
    if (banks == null)
      System.out.println("** List of Banks has not been initialised **");

    return banks.getBank(name);
  }
  */

  /**
   * Pay personal tax - separated out for statistics gathering.
   *
   * @param fromAccount Account to pay tax from
   * @param amount      Amount to pay
   * @return t/f Paid
   */

  public boolean payPersonalTax(Account fromAccount, long income)
  {
    long amountToPay = (long) (((income - govt.personalCutoff) * personalTaxRate) / 100.0);

    if (amountToPay > 0) {
      System.out.println("Paying personal tax: " + fromAccount.getName() + " - " + amountToPay);
    }

    s_totalRevenue.add(amountToPay);
    s_totalPersonalRevenue.add(amountToPay);
    return payGovtTax(fromAccount, amountToPay);
  }

  /**
   * Pay personal tax - separated out for statistics gathering.
   *
   * @param fromAccount Account to pay tax from
   * @param amount      Amount to pay
   *
   * @return t/f Paid
   */

  public boolean payCorporateTax(Account fromAccount, long income)
  {
    long amountToPay = (long) (((income - govt.corporateCutoff) * corporateTaxRate) / 100.0);

    if (amountToPay > 0) {
      System.out.println("Paying corporate tax: " + fromAccount.getName() + " - " + amountToPay);
    }

    s_totalRevenue.add(amountToPay);
    s_totalCorporateRevenue.add(amountToPay);
    return payGovtTax(fromAccount, amountToPay);
  }

  /**
   * Pay tax. Amount to pay should be calculated from the
   * getIncomeTax/getCorporateTax methods.
   *
   * @param fromAccount Account to deduct taxes from
   * @param amount      amount to pay
   */

  private boolean payGovtTax(Account fromAccount, long amount)
  {
    // System.out.println("Tax payment : " + fromAccount.getName() +
    // " paid " + amount);
    /*
     * The agent should check funds are available before invoking this
     * method.
     */
    if (fromAccount.getDeposit() < amount)
    {
      throw new RuntimeException("Insufficient funds to pay tax");
    }

    if (amount == 0)
      return true;

    fromAccount.transfer(this.incomeTaxAccount, amount, "Tax payment");

    return true;
  }

  /**
   * Return the total debt owed by the government (treasuries + bank)
   *
   * @return Total debt owed
   */

  public int getTotalDebt()
  {
    return getAccount().debtOutstanding();
  }

  /**
   * Pay unemployment. Iff the government has sufficient funds it will
   * pay the specified amount to unemployed agents.
   *
   * @param account   Agent account to pay into
   */
  public void payUnemployment(Account account)
  {
    long payment;

    if(!payUnemployment) return;

    payment = unemploymentPayment;

      // Only if the government is solvent. (or borrow???)
    if(payment < getDeposit())
      getAccount().transfer(account, payment, "Unemployment pay");
  }

  public void payUBI(Account account)
  {
    long payment;

    if(!payUBI) return;

    payment = ubiPayment;

    // Only if the government is solvent. (or borrow???)
    if (payment < getDeposit()) {
      s_totalUBI.add(ubiPayment);
      getAccount().transfer(account, payment, "UBI pay");
    }
  }

  /**
   * Getter for extendedMoneySupply flag 
   *
   * @return t/f
   */

  public boolean getExtendedMoneySupply()
  {
    return centralbank != null && centralbank.extendedMoneySupply;
  }

  /**
   * Getter for non-cash income - only applies to fractional reserve based
   * systems.
   *
   * @return t/f
   */
  public boolean getNonCashIncome()
  {
    return false;
  }

  /**
   * Get total bank income.
   *
   * @return Return interest_income
   */
  public long getTotalBankIncome()
  {
    long sum = 0;

    for (Bank bank : banks.getBankList().values())
    {
      sum += bank.s_income.get();
    }

    return sum;
  }

  /**
   * Get CPI.
   *
   * @return CPI
   */

  public double getCPI()
  {
    if (centralbank != null)
      return centralbank.getCPI();
    else
      return 0;
  }


  /**
   * Return the maximum no. of civil servants government can hire,
   * calculated from the maxCivilServant as percentage of pop.
   * value, rounded up.
   *
   * @return max bureaucracy
   */

  public long getMaxCivilServants()
  {
    double max;

      // getStatistic() will return null if there are no Person's currently defined in the
      // simulation.
    try
    {
      max = Math.ceil(Statistic.getStatistic("population").getCurrent() * maxCivilServants/100.0);  
    }
    catch (NullPointerException e)
    {
      return 0;
    }
    return Math.round(max);
  }

  /**
   * Interface function to return the name of the country this is the
   * Government for.
   *
   * @return Name of country
   */

  public String getCountry()
  {
    return this.country;
  }

  public void setCountry(String c)
  {
    this.country = c;
  }

  /**
   * Print report
   *
   * @param label Print report (todo)
   */

  public void print(String label)
  {
    System.out.println(label + ":" + name+ "companies: "+companies);
  }

  public CentralBank getCentralBank()
  {
    return centralbank;
  }

  public Iterable<? extends StockExchange> getStockExchanges()
  {
    return stockExchanges;
  }

  public void registerStockExchange(StockExchange se)
  {
    stockExchanges.add(se);
  }

  // public void setInitialDeposit(int i){this.initialDeposit = i;}
  // public int getInitialDeposit(){return initialDeposit;}

}
