/* 
 * Program  : Threadneedle
 *
 * Class    : CentralBank
 * 
 * Author   : (c) Jacky Mallett
 * Comments : 
 */

package core;

import java.util.*;

import static base.Base.*;

public class CentralBank extends Bank
{
  private ArrayList<Double> moneysupply = new ArrayList<>(1200);

  private int    baseRate        = 2;           // Base interest rate
  private long   baseMoneySupply = 0;           // At initialisation

  public         double interBankRate     = 1.0; // Interbank lending rate
  public         int    interBankDuration = 3;   // Interbank lending rate
  private static int    T_PERIOD          = 120; // Treasury length

  // Provide support for indexed linked loan simulation without an economy.

  public  double  fixedCPI        = 0.05;        // Fixed value for CPI
  public  boolean fixedcpi        = true;        // Use fixed value.

  // Display money supply including interest income accounts.

  public boolean extendedMoneySupply = true;

  // Include non-cash income (icelandic lending)

  public boolean nonCashIncome = true;

  // Apply or lift Basel Capital Controls

  public boolean capitalControls = false;

  // Apply or lift reserve controls
  public boolean reserveControls = true;
  public int     cb_reserve      = 10; // Central Bank Reserve %
  public  double capitalPct      = 20.0;         // Capital pct %


  /**
   * Constructor:
   *
   * @param name        Name of bank
   * @param g           Government
   * @param ledgersFile Filename containing definition of ledgers
   */

  public CentralBank(String name, Govt g, String ledgersFile)
  {
    super(name, ledgersFile, g, null);

    this.capitalPct = 0.0;
    this.capitalSteps = 1;

    // There can be only one...

    for (Bank bank : govt.banks.getBankList().values())
    {
      if (bank instanceof CentralBank) // Not the central bank
        continue;

      addReserveAccount(bank);

    }

    // Initialise money supply tracking.

    this.baseMoneySupply = getMoneySupply();

    moneysupply.add((double) baseMoneySupply);
  }

  /**
   * Create a reserve account for the supplied bank.
   *
   * @param bank   Bank to create reserve account for.
   */
  public void addReserveAccount(Bank bank)
  {
    long total;

    if (bank instanceof CentralBank)
      return;

    total = bank.getTotalDeposits();

    // Create reserve account for bank

    try
    {
      Ledger ledger = gl.createLedger(bank.name, AccountType.LIABILITY,
                                      LedgerType.DEPOSIT);
      createAccount(ledger, true);
    }
    catch (Exception e)
    {
      throw new RuntimeException("Unable to create reserve account: " + e);
    }

    if (total > 0)
    {
      throw new RuntimeException("Initialising Bank which already has deposits");
    }
  }

  /**
   * Evaluation function for the Central Bank.
   *
   * @param report t/f print report
   * @param step   simulation step number.
   */

  public void evaluate(boolean report, int step)
  {
    moneysupply.add((double) getMoneySupply());
  }

  /**
   * Constructor:
   *
   * @param config line from configuration file
   * @param govt   government
   */

  public CentralBank(String[] config, Govt govt)
  {
    this(config[1], govt, LEDGERFILE);
  }

  /**
   * Make a loan to a commercial bank. Commercial bank deposit accounts are
   * held in the reserve ledger, so underlying method has to be overridden
   * Todo: make all of this table driven.
   *
   * @param to           Account being lent to
   * @param duration     length of loan
   * @param interestRate interest rate
   * @param amount       amount of loan
   * @return true        Loan made
   */

  public boolean makeLoan(Account to, int duration, int interestRate,
                          long amount)
  {
    Loan loan = new BankLoan(this, amount, interestRate, duration, step,
                             to, Loan.Type.COMPOUND);

    to.makeLoan(loan);
    gl.post(gl.ledger("loan"), gl.ledger("loan").getAccount(),
            gl.ledger("reserve"), to, loan, "debit", "Bank loan");
    return true;
  }

  /**
   * Determine money supply, defined as total of bank deposits
   *
   * @return Total of all bank deposits in subordinate banks. Does not include
   * money on deposit @ central bank (ie. govt)
   */

  public long getDepositSupply()
  {
    long sum = 0;

    for (Bank b : govt.banks.getBankList().values())
    {
      /*
       * Don't double count the central bank's bank deposits - which are
       * also reserves
       */
      if (!(b instanceof CentralBank))
      {
        sum += b.getTotalDeposits();
      }
    }

    return sum;
  }

  /**
   * Get total amount of new loan capital extended this round.
   *
   * @return Q. new lending
   */

  public int getNewBankLending()
  {
    int sum = 0;

    for (Bank bank : govt.banks.getBankList().values())
      sum +=  bank.s_newLending.get();

    return sum;
  }

  /**
   * Get total in named ledger.
   *
   * @param ledger ledger to total
   * @return total
   */
  private int getTotalLedger(String ledger)
  {
    int sum = 0;

    for (Bank bank : govt.banks.getBankList().values())
    {
      try
      {
        sum += bank.gl.ledger(ledger).total();
      }
      catch (RuntimeException ex)
      {
        // Ignore ledgers that aren't present
      }

    }

    return sum;
  }

  /**
   * Determine total principal amount of all outstanding loans from the
   * commercial banks (not the central banks).
   *
   * @return Total of all principal current outstanding at subordinate banks,
   * does not include loans made by the central bank.
   */

  public int getLoanSupply()
  {
    int sum = 0;

    for (Bank b : govt.banks.getBankList().values())
    {
      sum += b.getTotalLoans();
    }

    return sum;
  }

  /**
   * Get the current total capital limit across all banks.
   *
   * @return current total basel capital reserve limit on lending
   */

  public int getCapitalReserveLimit()
  {
    int sum = 0;

    for (Bank bank : govt.banks.getBankList().values())
    {
      sum += bank.getCapitalLimit();
    }
    return sum;
  }

  /**
   * Handle a request to borrow reserves, and any associated central bank
   * accounting.
   *
   * @param requestor bank asking for funds
   * @param amount    reserve amount requested
   * @return Bank making the loan, or null
   */

  public InterbankLoan borrowReserves(Bank requestor, long amount)
  {
    InterbankLoan loan;

    int minAmount = Loan.getMinLoan(interBankRate, interBankDuration);

    for (Bank bank : govt.banks.getBankList().values())
    {
      // todo: This is done here as a convenience, but needs to be
      // refactored for distributed operation. Consequently
      // the simulation is currently restricted to a single
      // interbank rate, provided by the cb to both sides of
      // the loan.

      if ((loan =  bank.requestIBLoan(requestor, minAmount, interBankRate, 
                              interBankDuration, BaselWeighting.IBL)) != null)
      {
        // IBL has been made - transfer reserves
        transferReserves(bank, requestor, minAmount);

        return new InterbankLoan(bank, minAmount,
                                 interBankRate, interBankDuration, step, loan.Id);

      }
    }

    System.out.println("** No IBL reserve funds available  - requested " 
                        + minAmount + " **");

    // So I guess the central bank gets to print money too...

    if((loan = requestIBLoan(requestor, minAmount, interBankRate,
                             interBankDuration, BaselWeighting.IBL)) != null)
    {
       System.out.println("** Central Bank is Lending as Last Resort ** ");

       return new InterbankLoan(this, minAmount, interBankRate, 
                                interBankDuration, step, loan.Id);
    }

    return null;
  }

  public void transferReserves(Bank from, Bank to, long amount)
  {
    if(amount > 0)
    { 
      transfer(gl.ledger(from.name).getAccount(), 
               gl.ledger(to.name).getAccount(), amount, "reserve transfer");
    }
    else
       System.out.println("** Request to transfer " + amount + " reserves blocked");
  }

  /**
   * Set the central bank base rate. Note, this triggers a system wide
   * recalculation of all variable rate loans, so may be cpu expensive
   * if the banking system is using them extensively.
   *
   * @param rate Interest rate to set
   */

  public void setBaseRate(int rate)
  {
    baseRate = rate;

    for (Bank bank : govt.banks.getBankList().values())
    {
       bank.recalculateVariableLoans();
    }
  }

  /**
   * Change the central bank reserve percentage.
   *
   * @param pctage New reserve percentage
   */
  public void setReserveRate(int pctage)
  {
    cb_reserve = pctage;
  }

  /**
   * Return the CPI. Ultimately this should be calculated properly at least
   * for models with goods and services. For now, use money supply increase
   * directly.
   *
   * @return CPI
   */

  public double getCPI()
  {
    if (fixedcpi)
      return fixedCPI;
    else
    {
      /*
       * Work out the percentage increase from the last two money supply
			 * figures.
			 */
      if (moneysupply.size() < 2)
      {
        return 0.0; // Not enough data.
      }
      else
      {
        int last = moneysupply.size() - 1; // Get index
        double cpi = (moneysupply.get(last) - moneysupply.get(last - 1))
                     / moneysupply.get(last - 1);
        System.out.println("Calculated CPI = " + cpi);
        System.out.println(moneysupply.get(last) + " "
                           + moneysupply.get(last - 1));
        return cpi;
      }
    }
  }

  /**
   * Get the base rate set by the central bank
   *
   * @return base interest rate
   */

  public int getBaseRate()
  {
    return baseRate;
  }

  /**
   * This can be used to return an artificial value for the CPI that is 
   * set by the model, and independent of any money supply/production 
   * influences.
   *
   * @return Fixed CPI rate
   */
  public double getFixedCPI()
  {
    return fixedCPI;
  }

  /**
   * Determine money supply. Money supply definition can be controlled to
   * include different ledgers. todo: should cb side be included??
   *
   * @return total money supply
   */

  private long getMoneySupply()
  {
    long totalMoney = getDepositSupply();

    if (extendedMoneySupply)
      totalMoney += getTotalLedger("interest_income");

    if (nonCashIncome)
      totalMoney += getTotalLedger("non-cash");

    return totalMoney;
  }

  /**
   * Central Banks don't make interbank loans to commercial banks as
   * far as I'm aware. As the ib mechanism simply goes through the list
   * of all banks, override and return null.
   */

  @Override
  public InterbankLoan requestIBLoan(Bank to, long amount, double rate,
                                     int duration, int risktype)
  {
    return null;
  }
}
