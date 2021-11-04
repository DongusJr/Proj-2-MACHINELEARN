/* Program : Threadneedle
 *
 * Account : Implements an account held at a bank. Every agent in 
 *           the simulation has their own account.
 * 
 * Author  : (c) Jacky Mallett
 * Date    : November 2014
 *
 * Comments:
 */

package core;

import javafx.collections.*;

import java.util.concurrent.*;

import static base.Base.*;

public class Account
{
  public Agent   owner;     // Owner of account
  public Integer accountId; // Unique identifier
  public Bank   bank;       // Bank account belongs to
  public String ledger;     // Ledger account belongs to
  public long deposit = 0;  // Deposit in account
  public String name;       // Name of account (assigned by Bank)

  // This is provided for agents. Any reset/clearing is
  // expected to be handled by agent individually

  public long incoming;     	// Running total of transfers into account
  public long outgoing;       	// Running total of transfers out of account

  public ConcurrentHashMap<Integer, Loan> debts;         // Loans to account
  public ConcurrentHashMap<Integer, Loan> capital_loans; // Loans owned by acct
  private ObservableMap<Integer, Loan>     obsLoans;   // Notifications on loans

  /*
   * Account number's are issued from a high base to make them distinguishable
   * from agent id numbers when debugging. This puts an implicit limit on
   * agent no's which is not currently enforced. (todo)
   */
  private final static int BASE_ACCOUNTNO = 1000000;
  private static       int nextIdNo       = BASE_ACCOUNTNO; 

  /**
   * Create customer account
   *
   * @param owner          owner of account
   * @param b              bank account belongs to
   * @param initialDeposit initial deposit in account.
   * @param name           Identifier for account
   */

  public Account(Agent owner, Bank b, long initialDeposit, String name)
  {
    this.bank = b;
    this.owner = owner;
    this.deposit = initialDeposit;
    this.accountId = getNewAccountId();
    this.ledger = "deposit";
	this.name   = name;

    this.debts = new ConcurrentHashMap<>(5);
    this.capital_loans = new ConcurrentHashMap<>(5);

    obsLoans = FXCollections.observableMap(capital_loans);
  }

  /**
   * Create internal bank account.
   *
   * @param b Bank account will belong to
   * @param l Ledger account is on
   */
  public Account(Bank b, Ledger l, String name)
  {
    this.bank = b;
    this.owner = b;
    this.deposit = 0;
    this.ledger = l.name;
	this.name   = name;
    this.accountId = getNewAccountId();

    this.debts = new ConcurrentHashMap<>(5);
    this.capital_loans = new ConcurrentHashMap<>(5);

    obsLoans = FXCollections.observableMap(capital_loans);
  }

  /*
   * Interface functions to make dealing with Banks easier.
   */

  /**
   * @return name of account owner.
   */
  public String getName()
  {
    return owner.getName();
  }

  /**
   * @return Id of account at it's bank.
   */
  public Integer getId()
  {
    return accountId;
  }

  /**
   * Return amount currently on deposit at bank in this account.
   *
   * @return amount on deposit.
   */

  public long getDeposit()
  {
    return bank.getDeposit(this.accountId);
  }

  /**
   * Get total amount of outstanding debt that the Account holder owes.
   *
   * @return total capital amount outstanding on loans to account
   */
  public long getTotalDebt()
  {
    long sum = 0;

    for (Loan l : debts.values())
    {
      sum += l.getCapitalOutstanding();
    }
    return sum;
  }

  /**
   * Get total amount of bank owned debt that the account holder has (will not
   * include securitized debt that has been sold on by the bank.
   *
   * @return total bank owned debt.
   */

  public long getTotalBankDebt()
  {
    long sum = 0;

    for (Loan l : debts.values())
    {
      if (l.ownerAcct.owner instanceof Bank)
      {
        sum += l.getCapitalOutstanding();
      }
    }
    return sum;
  }

  /**
   * Get total amount of outstanding debt that the Account holder owns.
   *
   * @return Total capital amount outstanding on loans owned by account
   */

  public long getTotalCapital()
  {
    long sum = 0;

    for (Loan l : capital_loans.values())
    {
      sum += l.getCapitalOutstanding();
    }
    return sum;
  }

  /**
   * Transfer money from this account to a recipient. If there are 
   * insufficient funds in the account, the transfer will be refused.
   *
   * @param to     Account to transfer to
   * @param amount amount to transfer
   * @param text   Explanation
   * @return T/F transferred
   */

  public boolean transfer(Account to, long amount, String text)
  {
    if(getDeposit() < amount)
    {
       System.out.println("@ " + getName() + "Insufficient funds (" + 
                          amount +"/" + getDeposit() 
                          + ") for transfer to " + to.getName());
       return false;
    }
    else
       return bank.transfer(this, to, amount, text);
  }

  /**
   * Request a loan from this account's bank.
   *
   * @param amount   Capital amount of loan
   * @param duration of loan (will be multiplied by period)
   * @param period   Time granularity for duration
   * @param risktype Risk type of loan for Basel weighting
   * @param loantype Type of loan.
   * 
   * @return Loan object if granted, or null
   */

  public Loan requestLoan(long amount, int duration, Time period, int risktype,
                             Loan.Type loantype)
  {
    return bank.requestLoan(this, amount, duration, period, risktype, loantype);
  }

  /**
   * Used by bank to make a loan to this account. Deposit is credited with
   * amount of loan, and loan is added to list of debt owed by account.
   *
   * @param loan Loan being made
   */

  public void makeLoan(Loan loan)
  {
    if (debts.containsValue(loan))
      throw new RuntimeException("Loan already in debts container" + loan);
    else
      addLoan(loan, debts);
  }

  /**
   * Add capital loan to this account
   *
   * @param loan to add.
   */

  public void addCapitalLoan(Loan loan)
  {
    if (debts.containsValue(loan))
      throw new RuntimeException("Loan already in capitals container"
                                 + loan);
    else
      addLoan(loan, capital_loans);
  }

  /**
   * Add debt loan to this account
   *
   * @param debt Loan to add to the account.
   */

  public void addLoan(Loan debt)
  {
    System.out.println("Account addloan : " + debt);
    addLoan(debt, debts);
  }

  /**
   * Add a loan to this account.
   *
   * @param loan to add.
   */

  private void addLoan(Loan loan, ConcurrentHashMap<Integer, Loan> loanlist)
  {
    loanlist.put(loan.Id, loan);

    // Add this account to the loan's list of accounts it is in.

    loan.addAccount(this);
  }

  /**
   * Make a payment on a loan. Loans are classified according to their type,
   * and may have slightly different accounting treatments depending on their
   * type.
   *
   * @param loan loan to repay
   * 
   * @return t/f loan payment made successfully or not
   */

  public boolean payLoan(Loan loan)
  {
    if (loan instanceof Treasury)
      return bank.payDebt(this, loan);
    else if ((loan instanceof BankLoan) || (loan instanceof Icelandic))
      return bank.payBankLoan(this, loan);
    else if (loan instanceof InterbankLoan)
      return bank.payInterbankLoan(this, loan);
    else
      throw new RuntimeException("Unknown loan type in payLoan");
  }

  /**
   * Check if this account holds this loan (capital or debt)
   *
   * @param loan loan to check
   * @return t/f
   */

  public boolean holdsLoan(Loan loan)
  {
    return capital_loans.containsKey(loan.Id) || debts.containsKey(loan.Id);
  }

  /**
   * Return loan by Id.
   *
   * @param id     Id of loan
   * @param holder Name of account holder
   * @return Loan, or null if loan not present
   */

  public Loan getLoanById(Integer id, String holder)
  {
    if (!this.owner.name.equals(holder))
      throw new RuntimeException("Request for loan on wrong account");

    if (debts.containsKey(id))
      return debts.get(id);
    else if (capital_loans.containsKey(id))
      return capital_loans.get(id);
    else
      return null;
  }

  /**
   * Remove loan from this account (Should only be used by Loan to remove
   * itself.)
   *
   * @param loan to remove
   */

  public void removeLoan(Loan loan)
  {
    if (capital_loans.containsKey(loan.Id))
      capital_loans.remove(loan.Id);
    else if (debts.containsKey(loan.Id))
      debts.remove(loan.Id);
    else
      throw new RuntimeException(
        "Remove on loan not controlled by account" + loan);
  }

  /**
   * Return capital amount of current debt
   *
   * @return total debt outstanding
   */

  public int debtOutstanding()
  {
    int sum = 0;

    for (Loan d : debts.values())
    {
      sum += d.getCapitalOutstanding();
    }
    return sum;
  }

  /**
   * Return next interest and capital repayment.
   *
   * @return total payment
   */
  public long getNextRepayment()
  {
    long sum = 0;
    long[] repayment;

    for (Loan debt : debts.values())
    {
      repayment = debt.getNextLoanRepayment();
      sum += repayment[0] + repayment[1];
    }

    return sum;
  }

  /**
   * Get next interest repayment total on debt.
   *
   * @return total interest payment
   */
  public long getNextInterestRepayment()
  {
    long sum = 0;

    for (Loan debt : debts.values())
    {
      sum += debt.getNextInterestRepayment();
    }

    return sum;
  }

  /**
   * Return total interest paid to data on outstanding debts.
   * @return Total interest paid
   */
  public long getTotalInterestPaid()
  {
     long interest = 0;

     for(Loan debt : debts.values())
     {
        interest += debt.interestPaid;
     }

     return interest;
  }

  /**
   * Return capital outstanding of current savings
   *
   * @return outstanding capital
   */

  public long capitalOutstanding()
  {
    long sum = 0;

    for (Loan l : capital_loans.values())
      sum += l.getCapitalOutstanding();

    return sum;
  }

  /**
   * Return a random loan owned i.e. owed to this account.
   *
   * @return random loan, or null
   */

  public Loan getRandomCapitalLoan()
  {
    Loan loans[] = new Loan[capital_loans.size()];

    capital_loans.values().toArray(loans);
    if (capital_loans.size() > 0)
      return loans[random.nextInt(capital_loans.size())];
    else
      return null;
  }

  /**
   * Used by constructor to assign unique id to each account.
   *
   * @return Unique account identifier.
   */
  private static int getNewAccountId()
  {
    return nextIdNo++;
  }

  /**
   * Print the loans owed by, and owed to, this account.
   */

  public void audit()
  {
    System.out.println("Audit: " + owner.name);

    if (debts.size() > 0)
      System.out.println("Debts: ");

    debts.values().forEach(System.out::println);

    if (capital_loans.size() > 0)
      System.out.println("Owed: ");
    for (Loan l : capital_loans.values())
    {
      System.out.println(l);
    }
    System.out.println();
  }

  /* 
   * Return the difference between the incoming and outgoing
   * amoutns for the account.
   */
  public Long getBalance()
  {
      return(incoming - outgoing);
  }

  public String toString()
  {
    // new Throwable().printStackTrace();

    return "Account: " + accountId + "(" + this.owner.name
           + ") @ " + this.bank.name + "  Deposit: " + deposit
           + " Ledger / " + ledger;
  }
}
