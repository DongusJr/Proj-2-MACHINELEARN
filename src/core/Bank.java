/* Program    : Threadneedle
 *
 * Bank       : Basic Basel class. Full DE bookkeeping, basel capital rules,
 *              loans, basic loan default handling
 * 
 * Note       : Bank should always be sub-classed in order to provide
 *              simulation support
 *              e.g. salary payments, etc.
 * 
 * Author  : (c) Jacky Mallett
 * Date    : November 2014
 *
 * Todo    : extend interest rates to be loan specific.
 */
package core;

import com.google.gson.annotations.Expose;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import statistics.Statistic;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import static base.Base.*;
import static statistics.Statistic.Type.COUNTER;
import static statistics.Statistic.Type.SINGLE;

public class Bank extends Company
{
  @Expose public long   sharePrice        = 10;    // Base share price
  @Expose public double capitalPct        = 0.2;   // Limit on capital increases
  @Expose public double capitalDividend   = 0.0;
  @Expose public int    capitalSteps      = 12;    // # rounds between capital increases
  @Expose public int    interestRateDelta = 0;
  @Expose public int    writeOffLimit     = 6;     // # rounds loan is in default
                                                   // before being written off
  @Expose public double lossProvisionPct  = 0.01;  // loss provision pctage.

  // deposit accounts
  public    HashMap<Integer, Account>       customerAccounts      = new HashMap<>(100);
  public    ObservableMap<Integer, Account> obsAccounts           = FXCollections.observableMap(customerAccounts);
  /*
   * For efficiency statistics are calculated only when account status
   * changes, the validStats flag is used to track when this is necessary.
   */
  private   int noIndividualAccounts  = 0; // # of Person accounts
  private   int noCompanyAccounts     = 0; // # of Company accounts
  private   int unclassifiedAccounts  = 0; // Neither of the above


  protected boolean validStats            = false;
  public boolean applyLossProvision       = true; // loss provision loans

  static final String  LEDGERFILE = "src/resources/ledgers/ledgers.def";

  public long currentIncome = 0;         // income  for capital purchase

  // Pref. share holders
  public LinkedList<Shares> prefShares = new LinkedList<>();

  private BaselWeighting riskw = new BaselWeighting(); // Basel risk information
  protected Hashtable<Integer, Account> internalAccounts; // Bank's accounts
  public    GeneralLedger               gl;

  // The Zombie flag is set if the Bank is unable to write off its current
  // loan losses.
  public boolean zombie = false;

  private boolean capitalConstrained; // Constraint flags about lending
  private boolean reserveConstrained;
  private boolean lossProvisionConstrained;

 /*
  * To prevent all loans being made in the first round, loansPerStep is
  * incremented once every complete loan cycle (loan is fully repaid), and is
  * used to limit no. of loans made each round during initial expansion.
  */

  public  int               loansPerStep    = 1;
  private SummaryStatistics avgLoanDuration = new SummaryStatistics();

  public Loan.Type loantype; // Default loan type for Bank

  // The Bank maintains a local interest rate, which it adds to the
  // central Bank baseRate, to derive the actual interest rate for the loan.

  public double ownLoanPct_B = 0.5; // % limit on loans to other Bank's customers.

  private long minimumLoan = 30000; // Minimum loan size

  private int govtTreasuryPct = 0;  // % of govt treasuries Bank will attempt
  // to buy from profits

  private long minBondPurchase = 100000;
  private int  bondDuration    = 120;

  Statistic s_defaultTotal;             // defaults
  Statistic s_newLending;               // new customer lending
  Statistic s_newIBLending;             // new interbank lending
  Statistic s_reserveCash;              // reserves and cash
  Statistic s_interestIncome;           // interest income (current total)

  Statistic s_zombie;

  public String product = "money";

  private String classname = name + " " + this.getClass().getSimpleName();

  /**
   * Constructor. Note: Bank capital is set by the central Bank when it
   * initialises the banking system.
   *
   * @param name Name of Bank
   * @param g    Government
   * @param b    Not used - provided to meet fxml interface
   */

  public Bank(String name, String ledgers, Govt g, Bank b)

  {
    super(name, 0, g, null);

    product = "money";

    this.loantype = Loan.Type.valueOf("COMPOUND");

    internalAccounts = new Hashtable<>(10);

    if(ledgers == null)
       gl = new GeneralLedger(LEDGERFILE, this);
    else
       gl = new GeneralLedger(ledgers, this);

    setMyAccount(gl.ledger("interest_income").getAccount());

    capitalConstrained = false;
    reserveConstrained = false;
    lossProvisionConstrained = false;

    initStatistics();
  }

  /**
   * Constructor. Bank does not use properties, but needs the stub
   * to interface with fxml.
   * @param name Name for bank
   * @param g    government for bank
   * @param b    Not used - provided to meet fxml interface
   * @param properties Properties supplied by fxml (stub)
   */
  public Bank(String name, Govt g, Bank b, HashMap<String, String> properties)
  {
    this(name, null, g, b);
  }

  public Bank()
  {
    this.loantype = Loan.Type.valueOf("COMPOUND");

    internalAccounts = new Hashtable<>(10);
    gl = new GeneralLedger(LEDGERFILE, this);

    setMyAccount(gl.ledger("interest_income").getAccount());

    capitalConstrained = false;
    reserveConstrained = false;
    lossProvisionConstrained = false;
  }

  /**
   * Setup statistics outside of constructor in order to accommodate gson.
   * This method should only be called once the agent's name is set.
   */

  public void initStatistics()
  {
    if(name.equals(""))
    {
       System.out.println("initStatistics called for " + this.getClass() + 
                          " with unset name");
       return;
    }

    // Create shares for capital purchase.

    issueShares(10, 2000000);

    if (!(this instanceof CentralBank))
    {
      s_newLending   = Statistic.getStatistic(name + ":New Loans",
                                   "newbanklending", COUNTER); // new lending
      s_reserveCash  = Statistic.getStatistic(name + ":Reserves",
                                    "reserves", COUNTER);  // reserves

      s_defaultTotal = Statistic.getStatistic(name + ":Total Defaults",
                                     "totaldefaults", COUNTER);   // total defaults
      s_newIBLending = Statistic.getStatistic(name + ":IB Lending",
                                     "New IB Lending", COUNTER);
      s_interestIncome = Statistic.getStatistic(name + ":Interest Income",
                                     "interestincome", SINGLE);

      s_zombie = Statistic.getStatistic(name + ":Zombie", "zombie", SINGLE);
    }
  }

  public void evaluate(boolean report, int step)
  {
    long amount;
    long initialInterestIncome; // Interest income total at start of round

    payDebt();

    // get initial interest income
    initialInterestIncome = gl.ledger("interest_income").total();

    // Pay any loans this Bank owes money on. Interbank loans are
    // paid out of reserves, with a corresponding debit from the 
    // interest income liability ledger - so both have to be checked.

    for (Loan loan : gl.ledger("ib_debt").getAccount().debts.values())
    {
      if(gl.ledger("interest_income").getAccount().getDeposit() >
         loan.getPaymentDue())
      {
         gl.ledger("reserve").getAccount().payLoan(loan);
      }
      else			
      // strictly speaking, the banking system just crashed.
      {
         zombie = true;

         System.out.println("** " + getName() + " unable to pay IBL");
      }
    }

	/* Assess loss provisioning and adjust as appropriate. This could be
     * made more complex (different rates for loan type, etc.).
	 */
    if (applyLossProvision)
    {
      long lossProvisionAmt = (long) (gl.ledger("loan").total() * lossProvisionPct);
      long lossProvisionReqd = lossProvisionAmt
                               - gl.ledger("loss_provision").total();

      // Required provisions need to be increased?
      if (lossProvisionReqd > 0)
      {
        /*
         * Is there enough available? If not, transfer what is there and
		 * flag that loss provisioning is a constraint.
		 */
        if (lossProvisionReqd > gl.ledger("interest_income").total())
        {
          amount = gl.ledger("interest_income").total();
          lossProvisionConstrained = true;
        }
        else
        {
          amount = lossProvisionReqd;
          lossProvisionConstrained = false;
        }

        // Check, since may not have any money to transfer.
        if(amount > 0)
        {
           gl.transfer(gl.ledger("interest_income").getAccount(), gl
                      .ledger("loss_provision").getAccount(), amount,
                      "Increase loss provisions");
        }
      }
      // or can they be decreased?
      else if (lossProvisionReqd < 0)
      {
        lossProvisionConstrained = false;

        gl.transfer(gl.ledger("loss_provision").getAccount(), 
                    gl.ledger("interest_income").getAccount(), 
                    Math.abs(lossProvisionReqd), "Decrease loss provisions");
      }

    }

    // Check reserves, and adjust as required/possible

    /*
     * Over reserved isn't a big concern, and these days the central banks
     * pay interest, so allow this if it happens todo: move to last thing?
     */

    if (getRequiredReserves() < gl.ledger("reserve").total())
    {

    }
    else if (getRequiredReserves() > gl.ledger("reserve").total())
    {
      amount = getRequiredReserves() - gl.ledger("reserve").total();

      // Check if there is any money to move, and if so transfer:
      //    [debit reserve account, credit cash]

      long transfer = Math.min(amount, gl.ledger("cash").total());

      if(transfer > 0)
      {
         moveCashToReserves(transfer);

         amount -= transfer;
      }

      // Check if need to try and borrow reserves.

      if (amount > 0)
      {
   	   /*
	    * Request cb to arrange a reserve loan. Returns false if funds
	    * are unavailable.
	    */
        InterbankLoan loan;
        if ((loan = govt.centralbank.borrowReserves(this, amount)) != null)
        {
          // todo: refactor for distributed operation
          // todo: have separate account of IB interest??

          // Populate with local information. Interest on interbank
          // loans
          // is debited from the interest income account.
          loan.borrower = this.gl.ledger("interest_income")
                                 .getAccount();

          gl.post(gl.ledger("reserve"), gl.ledger("reserve").getAccount(), gl.ledger("ib_debt"),
                  gl.ledger("ib_debt").getAccount(), loan, "credit",
                  "Interbank loan from " + loan.owner.name);

        }
      }
    }

	/*
	 * Recognise Income and move to retained earnings - todo: refactor to
	 * use retained earnings not interest income as main account.
	 */

    amount = gl.ledger("interest_income").total();

	/*
	 * if(amount > 0) { recogniseIncome(amount); }
	 * 
	 * amount = gl.ledger("retained_earnings").total();
	 */

    // if(amount > minBondPurchase)
    // {
    // buyInvestment(govt, minBondPurchase, bondDuration, "");
    // }

	/*
	 * Every 12th round, adjust capital. Dividend is paid to investors (if
	 * there are any) who use the income to then buy capital.
	 */

    if ((step % capitalSteps) == 0)
    {
      // First, pay any taxes
      // payTax(govt.corporateTaxRate, govt.corporateCutoff);
      govt.payCorporateTax(this.getAccount(), s_income.get());

      currentIncome = gl.ledger("interest_income").total()
                      + gl.ledger("non-cash").total();
      // Pay dividend on preferential shares if income is available

      if ((getTotalSharesIssued(prefShares) > 0) && capitalConstrained())
      {
System.out.println("\n\n**** div : " + capitalDividend);
        payDividend(capitalDividend, prefShares);
      }
      // todo: add payment on normal shares. (treat separately to allow differential payments)
    }

    s_reserveCash.add(gl.ledger("reserve").total() + gl.ledger("cash").total());

    if(zombie)
    {
       s_interestIncome.add(-1);
    }
    else
       s_interestIncome.add(gl.ledger("interest_income").total());

  }

  /**
   * Deposit cash into the Bank. The accounting treatment is:
   * <p>
   * debit cash, credit deposit account
   * <p>
   * Todo: There is no currently no absolute tracking of cash outside of the
   * banking system.
   *
   * @param amount      Amount of deposit
   * @param ledger      Ledger containing deposit account
   * @param account     Account to receive deposit
   * @param explanation Free text explanation for deposit.
   */

  public void depositCash(long amount, Ledger ledger, Account account,
                          String explanation)
  {
    gl.post(gl.ledger("cash"), gl.ledger("cash").getAccount(), ledger,
            account, amount, explanation);
  }

  /**
   * Withdraw cash. The accounting treatment is:
   * <p>
   * credit cash, debit deposit account
   *
   * @param amount      Amount of withdrawal
   * @param account     Account to withdraw from.
   * @param explanation Free text explanation for withdrawal
   */

  public void withdrawCash(long amount, Account account, String explanation)
  {
    gl.post(gl.ledger("deposit"), account, gl.ledger("cash"),
            gl.ledger("cash").getAccount(), amount, explanation);
  }

  /**
   * Get the maximum amount the Bank will lend in a single loan. Does
   * not imply subsequent loan requested will be approved/granted.
   *
   * @param requestor Loan requestor
   * @param risktype  Basel risk type for loan being requested.
   * @return Maximum amount bank is able to provide as a loan
   */

  public long getMaxLoanAmount(Agent requestor, int risktype)
  {
     // No lending if the zombie flag is set

     if(zombie) return 0;

//System.out.println(getSpareCapital());
//System.out.println(riskw.getRiskWeighting(risktype));

     return (long)(getSpareCapital() / riskw.getRiskWeighting(risktype)); 
  }

  /**
   * Request current interest rate for loan. There is no guarantee
   * this is the interest rate that a loan will actually be made at, but
   * is loan request is made in the same evaluate loop, then it should be.
   *
   * @param  risktype   Risk type for loan (not used)
   * @return current interest rate.
   */ 

  public long requestInterestRate(int risktype)
  {
     return govt.centralbank.getBaseRate() + this.interestRateDelta;
  }

  /**
   * Transfer a customer account to a new account, and close the old one. Used
   * to move accounts between banks.
   *
   * @param oldAccount Account to transfer from
   * @param newAccount Account to transfer to
   */

  public void closeAccount(Account oldAccount, Account newAccount)
  {

    assert (oldAccount.owner == newAccount.owner) : "Account ownership mismatch";

		/*
		 * Transfer debt and any savings instruments.
		 * 
		 * Setting dbg_transferred and re-initialising the original containers is
		 * precautionary, Loans are intended to be self-contained.
		 */
    for (Loan l : oldAccount.debts.values())
    {
      l.dbg_transfer = true;
      newAccount.addLoan(l);
    }

    oldAccount.debts = new ConcurrentHashMap<>(5);

    for (Loan c : oldAccount.capital_loans.values())
    {
      c.dbg_transfer = true;
      newAccount.addCapitalLoan(c);
    }

    oldAccount.capital_loans = new ConcurrentHashMap<>(5);

    System.out.println("Transferred account @ " + name);
    System.out.println(oldAccount);
    System.out.println(newAccount);

    transfer(oldAccount, newAccount, oldAccount.getDeposit(),
             "Closed account " + oldAccount.getId());

	/*
	 * In principle, at this point everything has been transferred to the
	 * new account, and this now replaces the old deposit account.
	 */

    newAccount.owner.setMyAccount(newAccount);

	/*
	 * And remove from the Bank's ledger.
	 */

    gl.ledger(oldAccount.ledger).removeAccount(oldAccount);
  }

  /**
   * Calculate the monthly payment for the supplied loan at 
   * current interest rate.
   *
   * @param amount   Amount to borrow
   */

  /**
   * Request for a customer loan. Note the period specifies
   * the unit of loan repayment, and the duration is based
   * on that period. eg.
   * 
   *     duration = 12, period = MONTH is a 1 year loan
   *
   * @param requestor account requesting loan
   * @param amount    amount of loan
   * @param duration  duration of loan (will be multiplied by period)
   * @param period    loan repayment period (day, month, year)
   * @param risktype  Basel risk type for loan being requested.
   * @param loantype  type of loan
   * @return loan or null if refused
   */
  public Loan requestLoan(Account requestor, long amount, int duration,
                          Time period, int risktype, Loan.Type loantype)
  {

    /*
     * Bank's keep reserve accounts equivalent to a regulated
     * percentage of their deposits in their reserve accounts at the central
     * Bank. [Fractional reserve requirement]
     *
     * They must also have sufficient risk weighted capital to back the loan
     * [Basel Capital Requirement]
     */

    DEBUG(name + ": Request Loan to " + requestor + " for " + amount
          + " @ "
          + (this.interestRateDelta + govt.centralbank.getBaseRate())
          + " / " + duration);

    if(amount <= 0) return null;

    if (zombie) return null;

    if (reserveConstrained(amount) || capitalConstrained(amount, risktype))
    {
        DEBUG(name + "**** Loan for " + amount + " refused: "
              + (reserveConstrained ? "reserve" : "capital")
              + " constrained *****");
        // Make loan will return whether the request fails or succeeds
    }
    else
    {
       return (makeLoan(requestor, duration * period.period(),
                         govt.centralbank.getBaseRate() +
                         this.interestRateDelta,
                         amount, loantype));
    }
    return null;
  }

  /**
   * Test to see if Bank has excess reserves for lending purposes
   *
   * @param amount of excess required
   * @return t/f yes or no
   */

  public boolean hasExcessReserves(long amount)
  {
    return gl.ledger("reserve").total() - getRequiredReserves() > amount;
  }

  /**
   * Process request from another Bank for an interbank reserve loan. and
   * handle local accounting operations.
   *
   * @param to       Bank requesting loan
   * @param amount   amount of loan
   * @param rate     interest rate
   * @param duration length of loan
   * @param risktype basel weighting (1??)
   * @return t/f success
   */

  public InterbankLoan requestIBLoan(Bank to, long amount, double rate,
                                     int duration, int risktype)
  {
    if (hasExcessReserves(amount))
    {
      Loan loan = new InterbankLoan(this, amount,
                        govt.centralbank.interBankRate, duration, step, 
                        to.gl.ledger("reserve").getAccount(), "asset");

  	  /*
	   * Accounting treatment for Bank making an interbank loan (only
	   * lender operations performed here) :
	   * 
	   * @ lender debit loan, credit reserve (both are assets)
	   * 
	   * @ CB debit reserve, credit reserve
	   * 
	   * @ borrower debit reserve, credit ib-debt
	   */

      gl.post(gl.ledger("loan"), gl.ledger("loan").getAccount(),
              gl.ledger("reserve"), gl.ledger("reserve").getAccount(),
              loan, "debit", "IB Loan");

      s_newIBLending.add(loan.getLoanAmount());

      System.out.println("** DBG: " + name + " interbank loan for "
                         + amount + " to " + to.name);
      return (InterbankLoan) loan;
    }
    else
      return null;
  }

  /**
   * Is the Bank's lending currently constrained by the level of its central
   * Bank reserves?
   *
   * @param amount Amount of loan to be authorised
   * @return t/f
   */

  public boolean reserveConstrained(long amount)
  {
	/*/
	 * System.out.println(govt.getCentralBank().cb_reserve);
	 * System.out.println(gl.ledger("deposit").total());
	 * System.out.println(gl.ledger("reserve").total());
	 * System.out.println(amount);
	 */
    // Deposits * reserve multiplier < total reserves

    reserveConstrained = govt.getCentralBank().reserveControls && getReserveMax() < amount;

    return reserveConstrained;
  }

  /**
   * Get the maximum size of the loan allowed by current reserve levels.
   *
   * @return Max loan amount currently available for reserve levels
   */

  private long getReserveMax()
  {
    return 100 * (gl.ledger("reserve").total() + gl.ledger("cash").total())
           / govt.getCentralBank().cb_reserve - gl.ledger("deposit").total();
  }

  /**
   * Get the total amount of spare capital vs current loan book. Calculation
   * is the amount of outstanding capital multiplied by basel risk weighting
   * for the loan's type.
   *
   * @return Available capital amount
   */

   public long getSpareCapital()
   {
      long totalUsed;
//System.out.println(gl.ledger("capital").total());
//System.out.println(riskWeightedLoansTotal());
      return (long)((100.0 * gl.ledger("capital").total()/govt.getCentralBank().capitalPct) - riskWeightedLoansTotal());
   }

  /**
   * Report whether the Bank was reserve constrained on its last loan request.
   *
   * @return t/f
   */
  public boolean reserveConstrained()
  {
    return reserveConstrained;
  }

  /**
   * Report whether Bank was loss provision constrained on last round.
   *
   * @return t/f
   */
  public boolean lossProvisionConstrained()
  {
    return lossProvisionConstrained;
  }

  /**
   * Return the total amount required for the reserves for the current deposit
   * level.
   *
   * @return required reserve amount
   */
  public long getRequiredReserves()
  {
    return !govt.getCentralBank().reserveControls ? 0 : (long) ((gl.ledger("deposit").total() * govt.getCentralBank().cb_reserve) / 100.0);
  }

  /**
   * Return the total amount of current central Bank reserve account.
   *
   * @return Central Bank Reserve holding deposit
   */

  public long getCBReserves()
  {
    return gl.ledger("reserve").total();
  }

  /**
   * Adjust reserves. Check amount versus cash holdings, and move cash into
   * reserve account if available.
   *
   * @param amount Amount to move from cash if available
   * @return t/f Succeeded or failed.
   */

  public boolean adjustReserves(long amount)
  {
    if (amount <= gl.ledger("cash").total())
    {
      moveCashToReserves(amount);
      return true;
    }
    else
    {
      return false;
    }

  }

  /**
   * Move the supplied amount of cash holdings to the central Bank reserves.
   * Nominally, this is a withdrawal from the Bank, and a deposit into the
   * Bank's reserve account at the central Bank and so requires two
   * operations.
   *
   * @param amount Amount to transfer
   */

  public void moveCashToReserves(long amount)
  {
    // @ Local Bank, transfer from cash to reserve
    // @ Central Bank, deposit cash into reserve account.
    gl.transfer(gl.ledger("cash").getAccount(), 
                gl.ledger("reserve").getAccount(), amount, 
                "increase reserves from cash");

    govt.centralbank.depositCash(amount, 
                                 govt.centralbank.gl.ledger(this.name), 
                                 govt.centralbank.gl.ledger(this.name).getAccount(),
                                 "Transfer Cash to Reserves");
  }

  /**
   * Check if the supplied loan amount will be within the Bank's current
   * capital allowance according to the basel capital requirements.
   *
   * Nb. Capital controls can also be enabled/disabled via the menu, and this
   * is checked here via a flag from the central Bank.
   *
   * @param amount Loan amount being checked for capital availability
   * @param risktype Type of loan for risk weighting.
   * @return t/f
   */

  public boolean capitalConstrained(long amount, int risktype)
  {
    double capitalMultiplier = 100/govt.centralbank.capitalPct;
    capitalConstrained = true;

//System.out.println(amount * riskw.getRiskWeighting(risktype));
//System.out.println(getSpareCapital());

    if(((getSpareCapital() * capitalMultiplier) - (amount * riskw.getRiskWeighting(risktype))) > 0) 
       capitalConstrained = false;

   /*
    * Are capital controls enabled?
    */
    if (!govt.getCentralBank().capitalControls)
    {
      capitalConstrained = false;
    }

    return capitalConstrained;
  }

  /**
   * Returns capitalConstrained flag value from the last time it was
   * calculated
   *
   * @return t/f [last loan was refused due to capital constraints]
   */

  public boolean capitalConstrained()
  {
    return capitalConstrained;
  }

  /**
   * Return the current total capital held by the Bank.
   *
   * @return capital sum of all equity accounts
   */

  public long getTotalCapital()
  {
    long sum = 0;

	/*
	 * Calculate total amount in Equity ledgers - nb. this can include all
	 * types of asset, including debt (subordinated debt.)
	 */

    for (Ledger ledger : gl.equities.values())
    {
      sum += ledger.total();
    }

    return sum;
  }

  /**
   * Return the current capital reserve limit on lending. [Nb. this is not the
   * same as the total amount that can be lent, since different risk
   * weightings impact actual loan amounts.
   *
   * @return limit
   */
  public long getCapitalLimit()
  {
    return (long) (getTotalCapital() * riskw.getBaselMultiplier());
  }

  /**
   * Return the current capital reserve limit on lending for the specified
   * loan risk type.
   *
   * @param risktype Basel risk classification used to determine limit
   *
   * @return limit
   */

   public long getCapitalLimit(int risktype)
   {
      return (long)(getTotalCapital() * riskw.getBaselMultiplier() *
                    riskw.getRiskWeighting(risktype));
   }

  /**
   * The Basel capital limit is determined by the risk weighted amount of each
   * loan's type, which may be less than the actual loan's capital
   * outstanding. Risk weightings are applied from a configured table which
   * allows the loan weightings to be changed, independently of the loan.
   *
   * Note, Loans are held by the Bank in CAPITAL ledgers.
   *
   * @return Risk weighted total for loans in asset ledgers
   */

  public long riskWeightedLoansTotal()
  {
    long sum = 0;

    for (Ledger ledger : gl.assets.values())
    {
      if ((ledger.getType() == AccountType.ASSET)
          && (ledger.getLedgerType() == LedgerType.LOAN))
      {
        for (Loan l : ledger.getAccount().capital_loans.values())
        {
          sum += l.getCapitalOutstanding()
                 * BaselWeighting.riskWeighting(l);
        }
      }
    }
    //System.out.println("Total risk weighted loans : " + sum);
    return sum;
  }

  /**
   * Create new deposit account with 0 balance
   *
   * @param holder Holder of Account
   * @return account Account created
   */
  Account createAccount(Agent holder)
  {
    Account account = new Account(holder, this, 0, holder.name);

    if (customerAccounts.containsKey(account.getId()))
      throw new RuntimeException("Duplicate account key:"
                                 + account.getId());

    customerAccounts.put(account.accountId, account);

    validStats = false; // Force statistical recalculation

    return account;
  }

  /**
   * Remove Account - used when deleting agents from simulation.
   *
   * An agent must be supplied to inherit any positive balance
   * in the account. This will fail if there is a loan associated
   * with the account and insufficient funds to repay it.
   */
  
  public boolean removeAccount(Account account, Agent inheritor)
  {
     if(!customerAccounts.containsKey(account.getId()))
     {
        System.out.println("Error: no such account in Bank " + account);
        return false;
     }

     // First check for debts (as this will determine liquidation 
     // status for other assets in account. (todo)

     if((account.debts.size() > 0) || account.capital_loans.size() > 0)
     {
       throw new RuntimeException("Debt unhandled in removeAccount");
     }

     transfer(account, inheritor.getAccount(), account.getDeposit(), 
              "Removing account " + account.getId());
     
     // Remove account

     gl.ledger(account.ledger).removeAccount(account);
     customerAccounts.remove(account.getId());

     return true;
  }

  /**
   * Create an account for Bank internal use. Cash, income received, etc.
   *
   * @param ledger Ledger to add account to.
   * @param freeze t/f freeze ledger to subsequent additions
   *
   * @return Account which has been created
   * @throws  AccountingException Exception if duplicate account key
   */
  public Account createAccount(Ledger ledger, boolean freeze)
    throws AccountingException
  {
    Account account = new Account(this, ledger, ledger.name);
    validStats = false; // Trigger stat recalculation

    // Add to ledger

    if (freeze)
      ledger.addAccountAndClose(account);
    else
      ledger.addAccount(account);

    // Can't validate accounts while General Ledger is being initialised.

    if (gl != null)
      gl.auditAccounts(account); // Run internal consistency check

    // Add to list of accounts

    if (internalAccounts.containsKey(account.getId()))
      throw new AccountingException("Duplicate internal account key");

    internalAccounts.put(account.getId(), account);

    return account;
  }

  /**
   * Return account from account Id. (Checks both internal and customer)
   *
   * @param id Account number
   * @return account || null - no such account
   */
  public Account getAccount(Integer id)
  {
    Account account;

    account = customerAccounts.get(id);

    if (account == null) // try internal account list
    {
      account = internalAccounts.get(id);

      if (account == null)
        throw new RuntimeException("Unknown account in getAccount" + id);
    }

    return account;
  }

	/*
	 * Public Interfaces: In order to enforce D.E Bookkeeping procedures, all
	 * external access has to go through the public methods.
	 */

  /**
   * Create an account with an initial deposit.
   * <p>
   * To maintain double entry book keeping procedures, when an initial deposit
   * is specified, the balancing transaction is treated as a physical cash
   * deposit to the cash ledger. As far as the simulation is concerned, this
   * money appears 'out of thin air', as specified by the simulation
   * parameters.
   *
   * @param holder         agent account is for
   * @param initialDeposit initial deposit into account
   * @return new account
   */

  public Account createAccount(Agent holder, long initialDeposit)
  {
    Account newAccount;

    try
    {
      newAccount = this.createDepositAccount(holder);
    }
    catch (Exception e)
    {
      throw new RuntimeException("createAccount failed" + e);
    }

    if (initialDeposit != 0)
    {
       printMoney(newAccount, initialDeposit, "Create Account");
    }
    return newAccount;
  }

  /**
   * Accept a cash deposit and create the matching deposit liability.
   * Note: this creates both forms of money, cash and liability deposit.
   *
   * @param account Depositor's account
   * @param amount  Amount to create
   * @param reason  Explanation for ledger
   */

   public void printMoney(Account account, long amount, String reason)
   {
      gl.post("cash", gl.ledger("cash").getAccount(), "deposit",
              account, amount, reason);
   }

  /**
   * Create a customer deposit account with 0 balance and add to deposit
   * ledger.
   *
   * @param holder holding account
   * @return New account
   */

  public Account createDepositAccount(Agent holder)
  {
    Account account = createAccount(holder);

    try
    {
      gl.liabilities.get("deposit").addAccount(account);
    }
    catch (Exception e)
    {
      throw new RuntimeException("Error adding account " + e);
    }

    // if(debug)
    // System.out.println("Created deposit account : " + account);

    return account;
  }

  /**
   * Transfer money from one account to another. Handles local and transfers
   * to another Bank's account.
   *
   * @param from   account to transfer from
   * @param to     account to transfer to
   * @param amount amount to transfer
   * @param text explanation
   * @return T/F transfer succeeded
   */

  public boolean transfer(Account from, Account to, long amount, String text)
  {
    // Increment totals for accounts

    from.outgoing += amount;
    to.incoming   += amount;

    /*
     * Is this an intra-Bank transfer or an inter-Bank transfer, or are we
     * crossing the blood-brain barrier and dallying with the central Bank?
     * There'll need to be foreign banks here too eventually.
     */

    /*
     * Local transfer: Debit originator, credit recipient
     */

    if (from.bank == to.bank)
    {
      if (from.getDeposit() >= amount) // intra-Bank
      {
        gl.transfer(from, to, amount, text);
        return true;
      }
      else
      {
        System.out.println("Insufficient funds in account: " + from);
        return false;
      }
    }
    /*
     * Transfer to/from central Bank?
     */
    else if (from.bank instanceof CentralBank)
    {
      return centralBankTransferFrom(from, to, amount, text);
    }
    else if (to.bank instanceof CentralBank)
    {
      return centralBankTransferTo(from, to, amount, text);
    }
    else if (!from.bank.name.equals(to.bank.name)) // inter-Bank
    {
      return interBankTransfer(from, to, amount, text);
    }
    else
      throw new RuntimeException("Unknown transfer");
  }

  /**
   * Pay additional capital into loan.
   *
   * @param fromAccount account payment is being made from.
   * @param loan        loan to make payment on
   * @param amount      additional capital to pay.
   * @return t/f Payment made
   */

   public boolean payBankLoan(Account fromAccount, Loan loan, long amount)
   {
      long[] payment = new long[2];

      payment[Loan.CAPITAL] = amount;
      payment[Loan.INTEREST] = 0;

      return payBankLoan(fromAccount, loan, payment);
   }

  /**
   * Make this step's payment on the loan. Note, if the loan 
   * has been placed in default, and there are sufficient funds
   * in the account, it will be paid off in its entirety. This
   * allows the borrower to liquidate collateral for their loan.
   *
   * @param fromAccount account payment is being made from.
   * @param loan        loan to make payment on
   * @return t/f paid
   */

  public boolean payBankLoan(Account fromAccount, Loan loan)
  {
    long[] payment = {0,0};
    long capital;

    if(loan.inDefault)
    {
       capital = loan.getCapitalOutstanding();

       if(fromAccount.getDeposit() >= capital)
          payment[Loan.CAPITAL] = capital;
       else
          payment[Loan.CAPITAL] = getDeposit();
    }
    else
       payment = loan.getNextLoanRepayment();

    return payBankLoan(fromAccount, loan, payment);
  }

  /**
   * Process a payment on the supplied loan. Account loan is being paid from
   * should be at this Bank, and the Bank will handle any associated
   * inter-Bank malarky.
   *
   * @param fromAccount account payment is being made from
   * @param loan        loan to make payment on
   * @param payment     [INTEREST, CAPITAL] amounts to pay on loan.
   * @return t/f paid
   */

  private boolean payBankLoan(Account fromAccount, Loan loan, long[] payment)
  {
    long total = payment[0] + payment[1];

    // Sanity checking.

    assert (loan.borrower == fromAccount) : "Borrower / pay account mismatch ";

    if (fromAccount.bank != this)
    {
      throw new RuntimeException(
        "Loan payment from account not at this Bank");
    }

    /*
     * Check there is enough in account to pay loan and deal with default if
     * not.
     *
     * Once a loan enters write-off it stays there, even if there are funds
     * in account to repay, otherwise this gets very complex.
     *
     * Note, it is a lender behaviour to decide when and whether to write-off
     * a non-performing loan. cf. Nihon Zombie Banks
     */

    if (fromAccount.getDeposit() < total || loan.inWriteOff || loan.inDefault) 
    {
      System.out.println("\n Loan : " + loan.Id + " " + loan + " in default "
            + loan.defaultCount);
      System.out.println("\tIn Account: " + fromAccount.getDeposit() + " vs owed " + total);

      /*
       * Increment default and check if loan has reached its default
       * limit.
       */
      if (loan.incDefault(writeOffLimit))
      {
      /*
       * Write off loan. Entire loan is written off against: loss
       * provisions interest income capital in that order. There is an
       * order of evaluation issue created if amount is directly taken
       * from interest income.
       */
        DEBUG("Writing off loan: " + loan);
        DEBUG(fromAccount.owner.name + ": " + fromAccount.getDeposit());

        long lossProvisionWriteOff;
        long interestIncomeWriteOff;
        long capitalWriteOff;

        long capitalOutstanding = loan.getCapitalOutstanding();

        long lossProvisions = gl.ledger("loss_provision").total();

        // Write off against loss provisions account

        if (capitalOutstanding >= lossProvisions)
        {
          lossProvisionWriteOff = lossProvisions;
          capitalOutstanding -= lossProvisions;
        }
        else
        {
          lossProvisionWriteOff = capitalOutstanding;
          capitalOutstanding = 0;
        }

        gl.postWriteOff("loan", loan, "loss_provision",
                        gl.ledger("loss_provision").getAccount(),
                        lossProvisionWriteOff, "loss provision");

        s_defaultTotal.add(lossProvisionWriteOff);

       /*
        * If loan has been written off, clear off books.
        */
        // TODO: pick this up from the loan's status
        if (capitalOutstanding == 0)
          return true; // kind of...

        // Write off against interest income

        long interestIncome = gl.ledger("interest_income").total();

        if (capitalOutstanding >= interestIncome)
        {
          interestIncomeWriteOff = interestIncome;
          capitalOutstanding -= interestIncome;
        }
        else
        {
          interestIncomeWriteOff = capitalOutstanding;
          capitalOutstanding = 0;
        }

        gl.postWriteOff("loan", loan, "interest_income",
                        gl.ledger("interest_income").getAccount(),
                        interestIncomeWriteOff, "interest income");

        s_defaultTotal.add(interestIncomeWriteOff);

        if (capitalOutstanding == 0)
          return true;

        /* Write off against capital. This isn't practical in simple simulations
         * since there isn't a larger economy to base actions such as selling
         * shares, or transferring assets to another Bank.
         *
         * Consequently, the Bank is put into "zombie" status, no new loans
         * can be made, and it operates in runoff. Any other actions - 
         * recovery, government intervention etc. have to be handled at 
         * as behaviours in the evaluate loop.
         */

        // s_defaultThisRound += capitalOutstanding;
        System.out.println("Placing Bank " + this.name + " into Zombie status");

        zombie = true;
        return false;
      }
      // Loan was not paid, but is not written off
      return false;
    }
    // Loan is going to be paid this month - is it owned by this Bank ?
    else if (loan.ownerAcct.owner == this)
    {
      // Negative amortization loans require different accounting.
      if (loan.negAm())
         loan.ownerAcct.bank.gl.postNegAm("deposit", fromAccount, "loan", loan, payment, "");
        // Other loans: Debit account, credit loan
      else
          loan.ownerAcct.bank.gl.post("deposit", fromAccount, "loan", loan, payment, "");
    }
    /*
     * Loan is at another Bank.
     */
    else
    {
      // System.out.println(name + "/PayLoan: " + fromAccount + " "
      // + loan.ownerAcct);

      // Map Bank over for convenience

      Bank toBank = loan.ownerAcct.bank;

      if (this instanceof CentralBank)
      {
		/*
		 * Transfer from depositor account to reserve account of Bank at
	     * central Bank.
		 */
        gl.post("deposit", fromAccount, toBank.name,
                gl.ledger(toBank.name).getAccount(), payment[0] + payment[1],
                "Bank loan payment");

        toBank.gl.post("reserve", toBank.gl.ledger("reserve")
                                           .getAccount(), "loan", loan, payment,
                       "Bank loan payment");
      }
      else
      {

        assert (gl.ledger("reserve").getAccount().getDeposit() == govt.centralbank.gl
          .ledger(name).getAccount().getDeposit()) : "Reserve account mismatch cb != reserve";

        // Make sure there are sufficient reserves. Todo: handle
        // exception
        // as simulation state (Bank is illiquid at this point)

        if (gl.ledger("reserve").getAccount().getDeposit() < payment[0] + payment[1])
        {
          if (!adjustReserves(payment[0] + payment[1]
                              - gl.ledger("reserve").getAccount().getDeposit()))
            throw new RuntimeException("Insufficient Reserves");
        }

        if (!(loan instanceof InterbankLoan))
          gl.post(fromAccount.ledger, fromAccount, "reserve", this.gl
                    .ledger("reserve").getAccount(), sum(payment),
                  " Bank loan payment");

        govt.centralbank.transferReserves(this, toBank, total);
        toBank.gl.post("reserve", toBank.gl.ledger("reserve")
                                           .getAccount(), "loan", loan, payment,
                       "Received Bank loan payment");
      }
    }

    // Has loan been completely repaid?
    if (loan.repaid())
    {
      loan.remove();
    }

    return true;

  }

  /**
   * Make a payment on an interbank loan, i.e. an asset money loan to this
   * Bank from another Bank.
   *
   * @param fromAccount account payment is being made from
   * @param loan        loan to make payment on
   * @return t/f paid
   */

  public boolean payInterbankLoan(Account fromAccount, Loan loan)
  {
    // Calculate amount outstanding this round. Owner of loan
    // is definitive for repayment calculations.

    Bank toBank     = loan.ownerAcct.bank;
    Loan remoteloan = toBank.gl.ledger("loan").getAccount()
                               .getLoanById(loan.Id, loan.ownerAcct.getName());

    long[] payment = remoteloan.getNextLoanRepayment();
    long total = sum(payment);

    if (fromAccount.bank != this)
    {
      throw new RuntimeException("Account not at this Bank/Loan payment");
    }

    // Check there are sufficient funds available to pay loan - if not,
    // end simulation (this requires cb intervention)

    if (fromAccount.getDeposit() < total)
    {
      System.out
        .println("**** Insufficient funds to repay Interbank Loan - Ending Simulation **** ");
      System.exit(-1);
    }

	/*
	 * Operations are:
	 * 
	 * @ local : credit reserve debit loan capital debit interest income
	 * 
	 * @ cb : debit local credit remote (reserve accounts)
	 * 
	 * @ remote : credit loan C debit reserve credit interest income debit
	 * reserve
	 */

    if (this instanceof CentralBank)
    {
      throw new RuntimeException("Implement lender of last resort");
    }

    gl.post("ib_debt", loan, "reserve", gl.ledger("reserve").getAccount(),
            payment, "Payment on IB loan");

    // Transfer reserves
    govt.centralbank.transferReserves(this, toBank, total);

    toBank.gl.post("reserve", toBank.gl.ledger("reserve").getAccount(),
                   "loan", remoteloan, payment,
                   "Interest Payment on interbank loan");

    // Has loan been completely repaid?

    if (loan.repaid())
    {
      loan.remove();
    }

    return true;
  }

  /**
   * Make payment on a non-Bank loan. Transfer loans are fundamentally
   * different from Bank loans, since they involve direct flows of money
   * around the system, rather than manipulation book keeping entries.
   *
   * @param fromAccount account payment is being made from (must be at this
   *                    Bank).
   * @param loan        loan payment is being made on
   * @return t/f Can fail if insufficient funds
   */
  public boolean payDebt(Account fromAccount, Loan loan)
  {
    System.out.println("Making non-Bank loan repayments");
    long[] payment = loan.getNextLoanRepayment();
    long total = sum(payment);

    assert (loan.borrower == fromAccount) : "Borrower / pay account mismatch ";
    assert (!(loan instanceof BankLoan)) : "Incorrect loan type in payDebt";

    if (fromAccount.bank != this)
    {
      throw new RuntimeException(
        "Loan payment from account not at this Bank");
    }

    // Check there is enough in account.

    if (fromAccount.getDeposit() < total)
    {
      System.out.println("Loan : " + loan + " in default");
      loan.incDefault(writeOffLimit);
      return false;
    }

    // Map Bank over for convenience

    Bank toBank = loan.ownerAcct.bank;

    if (this instanceof CentralBank)
    {
	/*
	 * Transfer from depositor account to reserve account of Bank at
	 * central Bank.
	 */
      gl.post("deposit", fromAccount, toBank.name, gl.ledger(toBank.name).getAccount(), sum(payment), "Transfer loan payment");

    /*
     * At receiving Bank, credit reserves and depositor account.
     */
      toBank.gl.post("reserve", toBank.gl.ledger("reserve").getAccount(),
                     loan.ownerAcct.ledger, loan.ownerAcct, sum(payment),
                     "Transfer loan payment");
    }
    else
    {
      transfer(fromAccount, loan.ownerAcct, sum(payment), "Transfer loan payment");
    }

    loan.makePayment(payment);

    return true;
  }

  /**
   * Perform an interchange to an account @ the central Bank.
   *
   * @param from   Account to transfer from
   * @param to     Account to transfer to
   * @param amount amount to transfer
   * @param text   explanation
   *
   * @return t/f transferred
   */

  public boolean centralBankTransferTo(Account from, Account to, long amount,
                                       String text)
  {
    Bank fromBank = from.bank;
    CentralBank toBank = (CentralBank) to.bank;

    if (fromBank != this)
      throw new RuntimeException("Bank transfer error???");

    // Check reserves available for transfer, and adjust if possible

    if(amount > gl.ledger("reserve").total())
       adjustReserves(amount);

    // debit from customer account, credit Bank's reserve account

    gl.post(from.ledger, from, "reserve",
            gl.ledger("reserve").getAccount(), amount, text);

    // debit Bank's reserve account at the central Bank, credit to account

    toBank.gl.post(fromBank.name, toBank.gl.ledger(fromBank.name)
                                           .getAccount(), to.ledger, to, amount, text);

    return true;
  }

  /**
   * Perform an interchange with an account from the central Bank.
   *
   * @param from   Account to transfer from
   * @param to     Account to transfer to
   * @param amount Amount to transfer
   * @param text   explanation
   *
   * @return t/f transferred
   */
  public boolean centralBankTransferFrom(Account from, Account to,
                                         long amount, String text)
  {
    CentralBank fromBank = (CentralBank) from.bank;
    Bank toBank = to.bank;

    if (fromBank != this)
      throw new RuntimeException("Bank transfer error???");

    // debit from central Bank account, credit Bank's CB reserve account

    gl.post(from.ledger, from, toBank.name, gl.ledger(toBank.name)
                                              .getAccount(), amount, text);

    // credit Bank's reserve account, credit customer account
    toBank.gl.post("reserve", toBank.gl.ledger("reserve").getAccount(),
                   to.ledger, to, amount, text);

    return true;
  }

  /**
   * Perform an interbank transfer.
   *
   * @param from   Account to transfer from
   * @param to     Account to transfer to
   * @param amount Amount being transferred
   * @param text   explanation
   *
   * @return T/F Transfers may fail if there are insufficient clearing funds
   * available.
   */

  public boolean interBankTransfer(Account from, Account to, long amount,
                                   String text)
  {
    // Convert over here for cleaner code.

    Bank fromBank = from.bank;
    Bank toBank = to.bank;

    if (from.bank == to.bank)
    {
      throw new RuntimeException(
        "Error:interbank transfer with same Bank");
    }

    if ((gl.ledger("reserve").total() > amount) || 
        (adjustReserves(amount - gl.ledger("reserve").total())))
    {
      gl.post(from.ledger, from, "reserve", gl.ledger("reserve")
                                              .getAccount(), amount, text);

      govt.centralbank.transferReserves(fromBank, toBank, amount);

      toBank.gl.post("reserve", toBank.gl.ledger("reserve").getAccount(),
                     "deposit", to, amount, text);
    }
    else
    {
      System.out.println("Insufficient clearing balance for transfer: "
                         + amount + " < " + gl.ledger("reserve").total());
      return false;
    }

    gl.audit(false);
    toBank.gl.audit(false);

    return true;
  }

  /**
   * Make a loan.
   *
   * @param to           Account being lent to
   * @param duration     length of loan
   * @param interestRate interest rate
   * @param amount       amount of loan
   * @param loantype     Basel type for loan being made
   * @return Loan or null if refused
   */

  public Loan makeLoan(Account to, int duration, int interestRate,
                          long amount, Loan.Type loantype)
  {
    Loan loan = null;

    /*
     * Check reserve availability. Nb. Bank just has to have enough reserves
     * to make the transfer to the other Bank. Any fall out on reserve
     * status wrt. the regulatory limits has to be resolved through 
     * inter-Bank lending.
     */
    if (to.bank != this)
    {
      if (amount > gl.ledger("reserve").total())
      {
        if (!adjustReserves(amount - gl.ledger("reserve").total()))
        {
          DEBUG(name +
                ": loan DENIED to external customer as exceeds reserves " + amount);
          return null;
        }
      }
    }

    /*
     * Check ability to loss provision. Loss provision for a loan is made
     * when the loan is issued, and is taken from interest income. This
     * creates an ab initio problem since at step 0 the Bank doesn't have
     * any income. Consequently loans made in the first rounds aren't loss
     * provisioned (and they are also not randomly defaulted.) Nb. They can
     * potentially still default for liquidity reasons.
     */

    if ((loantype == Loan.Type.COMPOUND) || (loantype == Loan.Type.SIMPLE) ||
        (loantype == Loan.Type.VARIABLE))
    {
      loan = new BankLoan(this, amount, interestRate, duration, step, to,
                          loantype);
    }
    else if (loantype == Loan.Type.INDEXED)
    {
      loan = new Icelandic(this.govt, this, amount, interestRate,
                           duration, step, to);
    }
    else
    {
      throw new RuntimeException("Unknown loan type:" + loantype);
    }

    // loan.printSchedule(loan.interestSchedule, "Interest");
    // loan.printSchedule(loan.capitalSchedule, "Interest");
    to.makeLoan(loan);

    /*
     * Accounting treatment for loan to customer is:
     * 
     * debit loan, credit deposit
     */
    if (to.bank == this)
    {
      gl.post(gl.ledger("loan"), gl.ledger("loan").getAccount(),
              gl.ledger("deposit"), to, loan, "debit", "Bank loan");
    }
    else
    /*
     * Loan to non-customer. Accounting treatment is:
     *
     * debit loan, credit reserve (to transfer money to other Bank)
     *
     * See Macmillan Report S72 for discussion of some of the problems this
     * can create for the lender.
     */
    {
      gl.post(gl.ledger("loan"), gl.ledger("loan").getAccount(),
              gl.ledger("reserve"), gl.ledger("reserve").getAccount(),
              loan, "debit", "Bank loan");

      govt.centralbank.transferReserves(this, to.bank, amount);

      to.bank.gl.post("reserve", to.bank.gl.ledger("reserve").getAccount(), 
                      "deposit", to, amount, "Bank loan");
    }

    if(!zombie)
       s_newLending.add(loan.getLoanAmount());
    else
       s_newLending.add(-1);
    return loan;
  }

  /**
   * Return maximum amount Bank can currently lend. This is calculated
   * differently depending on whether the customer has their main deposit
   * account with the Bank, or not. Note, at initialisation this simply
   * returns the minimum loan amount.
   *
   * @param risktype Type of loan
   * @param borrowersBank Bank used by borrower
   * @return maximum amount available
   */

  public long maxLoanAmount(int risktype, Bank borrowersBank)
  {
    long reserveMax = 0, capitalMax = 0; // Maximum values for 2 types of
    // reserve

    assert (govt.getCentralBank().reserveControls || govt.getCentralBank().capitalControls) : "You need either reserve or capital controls to limit your lending.";

    // System.out.println("R: " + gl.ledger("reserve").total());
    // System.out.println("D: " + gl.ledger("deposit").total());
    // System.out.println("L: " + gl.ledger("loan").total());
    if (govt.getCentralBank().reserveControls)
    {
      reserveMax = getReserveMax();
      assert (reserveMax > 0) : "Invalid reserve calculation";
    }
    if (govt.getCentralBank().capitalControls)
    {
      capitalMax = (long) (gl.ledger("capital").total() * riskw
        .getBaselMultiplier())
                   - gl.ledger("loan").riskWeightedTotalLoans();
      assert (capitalMax > 0) : "Invalid reserve calculation";
    }

    // System.out.println("Max loan limits: " + reserveMax + " " +
    // capitalMax);

    // This can happen if the banking system hasn't been completely
    // initialised
    // assert((capitalMax > 0) && (reserveMax > 0)) :
    // "Invalid reserve calculation";

		/*
		 * If the borrower has an account here then the limit is determined by
		 * whichever is lower, reserve or capital. Otherwise it is determined by
		 * amount Bank is willing to lend from its reserves.
		 */
    if (borrowersBank == this)
    {
      long loanAmount = 0;
      if (govt.getCentralBank().reserveControls
          && govt.getCentralBank().capitalControls)
        loanAmount = Math.min(reserveMax, capitalMax);
      else if (govt.getCentralBank().reserveControls)
        loanAmount = reserveMax;
      else
        loanAmount = capitalMax;

      return Math.max(loanAmount, minimumLoan);
    }
    else
    {
      long loanAmount = Math.min(
        (long) (ownLoanPct_B * gl.ledger("reserve").total()),
        capitalMax);

      if (loanAmount < minimumLoan)
        return 0;
      else
        return loanAmount;
    }
  }

  /**
   * Purchase of Bank capital (shares) by external investor.
   *
   * @param purchaser agent buying shares
   * @param quantity  number to purchase
   * @param type      type of capital: PREFERENTIAL or ORDINARY
   * @return null/shares can fail if no supply, or insufficient funds
   */

  public Object sellInvestment(Agent purchaser, long quantity,
                               InvestmentType type)
  {
    long shares_purchased;

    if (quantity > getShareholding(name))
    {
      System.out.println(name + ": Insufficient shares: " + quantity + " > " + getShareholding(name));
      return null;
    }
    else if (purchaser.getDeposit() < quantity * sharePrice)
    {
      System.out.println(name + " share purchase by " + purchaser.name + " failed, insufficient funds");
    }

    /*
     * Handle book keeping. Debit account, credit capital
     */

    transfer(purchaser.getAccount(), gl.ledger("capital").getAccount(),
             quantity, "Capital Purchase");

    /*
     * Remove shares from current owner, and transfer them to newowner.
     */
    shares_purchased = transferShares(this.name, quantity, purchaser);
   // System.out.println("     #  shares purchases: " + shares_purchased);
    return shares_purchased;
  }

  /**
   * Issue capital. Capital is created in the form of preferential shares.
   * and the initial cash deposit takes place. Preferential shares are
   * created as the matching liability credit. Note, this allows normal
   * shares to be issued later, and simplifies the problem of managing
   * dividends. Preferential shares by default do not pay dividends.
   *
   * Note:  This method injects money (cash) into the banking system.
   *
   * @param investor   BankInvestor in the Bank
   * @param noShares   Number of shares to issue
   * @param sharePrice price for each preferential share
   * @param text       Text description for ledger
   */

  public void sellCapital(Agent investor, long noShares, long sharePrice,
                          String text)
  {
	/*
	 * Create preferential shares. The total quantity of shares allocated 
     * will be the amount of asset money initially deposited divided by 
     * supplied share price.
	 */

    PreferentialShares shares = new PreferentialShares(this.name,
                                                       noShares, sharePrice,
                                                       this, this.prefShares);

    // debit cash credit capital

    gl.post(gl.ledger("cash"), gl.ledger("cash").getAccount(),
            gl.ledger("capital"), gl.ledger("capital").getAccount(),
            noShares * sharePrice, text);

    shares.transfer(investor);
  }

  /**
   * Recognise income. Move deposit from the interest income(liability)
   * account to the retained_earnings(equity) account.
   *
   * @param amount Amount of income to recognise
   * @return t/f success or failure
   */

  public boolean recogniseIncome(long amount)
  {
    // Check amounts are available

    if (gl.ledger("interest_income").total() < amount)
    {
      System.out.println(name
                         + " Error: Insufficient funds to recognise income of "
                         + amount);
      return false;
    }
    // Simple transfer since both accounts are on the same side.
    else
    {
      gl.post(gl.ledger("interest_income"), gl.ledger("interest_income")
                                              .getAccount(), gl.ledger("retained_earnings"),
              gl.ledger("retained_earnings").getAccount(), amount,
              "Recognised interest income");
      return true;
    }
  }

  void recalculateVariableLoans()
  {
     for(Ledger ledger : gl.assets.values()) 
         ledger.recalculateVariableLoans(govt.centralbank.getBaseRate());

     for(Ledger ledger : gl.liabilities.values()) 
         ledger.recalculateVariableLoans(govt.centralbank.getBaseRate());

     for(Ledger ledger : gl.equities.values()) 
         ledger.recalculateVariableLoans(govt.centralbank.getBaseRate());
  }

  /**
   * Get Deposit for supplied id.
   *
   * @param Id Account ID
   * @return Deposit
   */

  public long getDeposit(Integer Id)
  {
    if (customerAccounts.containsKey(Id))
      return customerAccounts.get(Id).deposit;
    else if (internalAccounts.containsKey(Id))
      return internalAccounts.get(Id).deposit;
    else
    {
      System.out.println(name);
      report(customerAccounts);
      report(internalAccounts);
      throw new RuntimeException("Bank " + name + " unknown account Id "
                                 + Id);
    }
  }

  /**
   * Trigger recalculation of statistics for Bank account holdings.
   */
// todo: checked used.
  public void recalculateStats()
  {
    recalculateStats(gl.ledger("deposits").accounts);
  }

  /**
   * Return the total percentage of loans which are to borrowers with accounts
   * at this Bank, as opposed to those at other banks.
   *
   * @return percentage expressed as fraction (0 - 1.0)
   */

  public double percentageOwnLoans()
  {
    double total = 0; // total borrowers
    double own = 0; // total with accounts at this Bank.

    for (Loan loan : gl.ledger("loan").getAccount().capital_loans.values())
    {
      if (loan.borrower.bank == this)
        own++;

      total++;
    }

    if (total == 0)
      return 1;
    else
      return own / total;
  }

  /**
   * Set the lossProvision flag for lending, to require the Bank to retain
   * interest income to cover potential losses on a per loan basis.
   * (Regulatory requirement in many systems.) Not set for first few rounds
   * since banks must first have some interest income in order to loss
   * provision.
   *
   * @param on t/f turn on or off
   */

  public void setLossProvisions(boolean on)
  {
    applyLossProvision = on;

    System.out.println(name + ": setting loss provision to " + on);
  }

  /**
   * Return the account used for payments made *by* the bank. At the moment
   * the interest_income account is used for this as a shortcut. (Correct
   * handling would be to recognise interest income and move it into an
   * separate ledger.
   */

   @Override
   public Account getAccount()
   {
      return gl.ledger("interest_income").getAccount();
   }

  /**
   * Return total amount of loan principal that the Bank currently has on its
   * books.
   *
   * @return total debt
   */

  public long getTotalLoans()
  {
    return gl.ledger("loan").total();
  }

  /**
   * Return total amount of deposits.
   *
   * @return total of deposits
   */

  public int getTotalDeposits()
  {
    int total = 0;

    if (customerAccounts == null)
      return 0;

    for (Account account : customerAccounts.values())
    {
      total += account.getDeposit();
    }
    return total;
  }


  /**
   * Get string description of Bank
   *
   * @return Name of Bank and class
   */
  public String toString()
  {
    return classname;
  }

  /**
   * Print report of all accounts.
   *
   * @param accounts Ledger containing accounts to report on
   */

  public void report(Hashtable<Integer, Account> accounts)
  {
    System.out.println("Bank Account Report");
    accounts.values().forEach(System.out::println);
  }

  /**
   * Print report of all accounts.
   *
   * @param accounts Ledger hashmap containing accounts to report on
   */

  void report(HashMap<Integer, Account> accounts)
  {
    System.out.println("Bank Account Report");
    accounts.values().forEach(System.out::println);
  }
  /**
   * Return the number of individual accounts held by this Bank.
   *
   * @return no. of accounts owned by non-company agents
   */
  public long noIndividualAccounts()
  {
    if (!validStats)
      recalculateStats();

    return noIndividualAccounts;
  }

  /**
   * Return the number of company accounts held by this Bank.
   *
   * @return Number of accounts owned by agents type Company.
   */
  public int noCompanyAccounts()
  {
    if (!validStats)
      recalculateStats();

    return noCompanyAccounts;
  }

  /**
   * Return total number of deposit accounts.
   *
   * @return total number of deposit accounts held at this Bank.
   */

  public long totalAccounts()
  {
    return customerAccounts.size();
  }

  /**
   * Calculate total number of accounts of different types.
   */
  void recalculateStats(HashMap<Integer, Account> accounts)
  {
    noIndividualAccounts = 0;
    noCompanyAccounts = 0;
    unclassifiedAccounts = 0;

    for (Account account : accounts.values())
    {
      if (account.owner instanceof Person)
      {
        noIndividualAccounts += 1;
      }
      else if (account.owner instanceof Company)
      {
        noCompanyAccounts += 1;
      }
      else
      {
        unclassifiedAccounts += 1;
      }
    }
    validStats = true;
  }

  /**
   * Return the total number of transactions matching the supplied
   * label, for the given step.
   *
   * Nb. Matching is case sensitive
   * 
   * @todo  Optimise transaction to allow step indexing
   *
   * @param label   Label to pattern match, from beginning of string. Length
   *                of the label will be used to match, allowing a partial
   *                match if required. eg. "salary" will match all salary
   *                payments
   * @param step    step to return total for
   * @param ledger  ledger to get total for. (i.e. deposit)
   * @return total transactions matching label
   */

  public long getTotalPT(String label, int step, String ledger)
  {
    int total = 0;

    for(Transaction t: gl.ledger(ledger).transactions)
    {
        if((t.time == step) && (t.text.startsWith(label)))
        {
            total += t.amount;
        }
    }

    return total;
  }
  /**
   * As with getTotalPT, but just return total number of transactions.
   * Because we can :)
   *
   * @param label   Label to pattern match, from beginning of string. Length
   *                of the label will be used to match, allowing a partial
   *                match if required. eg. "salary" will match all salary
   *                payments
   * @param step    step to return total for
   * @param ledger  ledger to get total for. (i.e. deposit)
   * @return total transactions matching label
   */
  public long getTotalTransactions(String label, int step, String ledger)
  {
    int total = 0;

    for(Transaction t: gl.ledger(ledger).transactions)
    {
        if((t.time == step) && (t.text.startsWith(label)))
        {
            total++;
        }
    }

    return total;
  }

  /**
   * Return current settings for CLI report.
   *
   * @return String with live configuration details
   */

  public String getCurrentSetup()
  {
    return classname;
  }

 // todo: is this used??
  public void printTotals()
  {
    int total = 0;

    System.out.println("\n");

    for (Account account : customerAccounts.values())
    {
      total += account.getDeposit();
      System.out.println("\t" + account.owner.name + ": "
                         + account.getDeposit());
    }

    System.out.println("Money Supply = " + total + "\n\n");
  }
}
