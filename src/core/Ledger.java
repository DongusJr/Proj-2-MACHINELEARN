/* Program : Threadneedle
 *
 * Ledger    Support for double entry bookkeeping ledger
 * 
 * Author  : Jacky Mallett
 * Date    : July 2012     
 *
 * Comments:
 */
package core;

import java.util.*;
import java.lang.*;
import java.io.*;

public class Ledger
{
  public String name; // Name of Ledger

  private boolean debug              = false; // Turn on debugging
  
  public static boolean postTransactions = true; // t/f transaction reporting

  private long    balance            = 0;     // Balance of ledger
  private boolean frozen             = false; // t/f new accounts can be added

  public int accountId = -1; // Id of ledger account if frozen

  private AccountType               type;         // Ledger type (asset, etc.)
  public  LedgerType                ledgertype;   // Account type (loan, capital)
  public  List<Transaction>         transactions; // List of posted transactions
  public  HashMap<Integer, Account> accounts;     // Accounts in ledger

  // Turnover on ledger. This is registered for every addition to the ledger
  // but not for removals on a per transaction basis. Otherwise from a ledger 
  // perspective, there is the potential for double-counting - take for example
  // a credit and a debit between customer accounts at the same branch.

  private long turnover = 0;


  // Used to avoid unnecessary total() calculations which can be slow.

  private long lastTotalDeposits;
  boolean changed = true;

  /**
   * Constructor
   *
   * @param bankname   Bank ledger belongs to
   * @param name       ledger's name
   * @param type       DEB Account class Equity | Liability | Asset
   * @param ledgertype Type of ledger  - loan, deposit, cash or capital
   */

  public Ledger(String bankname, String name, AccountType type, 
                LedgerType ledgertype)
  {
    this.name = name;
    this.type = type;
    this.ledgertype = ledgertype;
    transactions = new ArrayList<>();
    accounts = new HashMap<>(100);
  }

  /**
   * Return the ID number of the account represented by this ledger, or
   * -1 if multi-account ledger.
   *
   * @return account no/-1
   */
  public int getAccountNo()
  {
    if (frozen)
      return accountId;
    else
      return -1;                      // Multiple account ledger
  }

  /**
   * Debit account in ledger.
   * 
   * Note: Accounts can only be positive, technically an overdraft is a loan.
   *
   * @param account Account to debit
   * @param amount  amount to debit
   * @param t transaction comment for action
   */

  public void debit(Account account, long amount, Transaction t)
  {
    if (debug)
	{
      System.out.println("debit ledger: " + name + " " + amount);
      if (!accounts.containsValue(account))
      {
        audit();
        throw new RuntimeException(
          "Attempt to debit account not in ledger " + name + " " + account);
      }
	}


    // Calculate the operation on the account as a function of its
    // Asset/Liability
    // status and check for negative balances.

    long debitAmount = amount * (-1 * type.polarity());

    if ((account.deposit + debitAmount) < 0)
    {
      throw new RuntimeException(
        "Negative Balance in Account after debit " + account);
    }
    account.deposit += debitAmount;

    addTransaction(t);

    //if(type.polarity() < 0)
    //   turnover += amount;
  }

  /**
   * Debit account in ledger - add loan
   *
   * @param account Account to debit
   * @param loan    Loan to debit
   * @param t       transaction comment for the action
   */

  public void debit(Account account, Loan loan, Transaction t)
  {
    if (debug)
	{
      System.out.println("debit ledger: " + name + " " + loan);

      if (!accounts.containsValue(account))
      {
        audit();
        throw new RuntimeException("Attempt to debit account not in ledger"
                                 + account);
      }
	}


    // Is this a ledger holding loans owned as assets, or is it a deposit
    // account where the loan is a debt?
    if ((this.ledgertype == LedgerType.LOAN)
        && (this.type == AccountType.ASSET))
    {
      account.addCapitalLoan(loan);
    }
    else
      account.addLoan(loan);

    addTransaction(t);

  }

  /**
   * Credit account in ledger - add loan
   *
   * @param account Account to credit
   * @param loan    Loan to credit
   * @param t       transaction comment for the action
   */

  public void credit(Account account, Loan loan, Transaction t)
  {
    if (debug)
	{
      System.out.println("credit ledger: " + name + " " + loan);

      if (!accounts.containsValue(account))
      {
        throw new RuntimeException("Attempt to debit account not in ledger"
                                 + account);
      }
	}


    if ((this.ledgertype == LedgerType.LOAN) &&
        (this.type == AccountType.ASSET))
      account.addCapitalLoan(loan);
    else
      account.addLoan(loan);

    addTransaction(t);

  }

  /**
   * Credit account in ledger
   *
   * Note: Accounts can only be positive, technically an overdraft is a loan.
   *
   * @param account   Account to credit
   * @param amount    amount to credit
   * @param t         transaction comment for the action
   */

  public void credit(Account account, long amount, Transaction t)
  {
    if (debug)
    {
      audit();
      System.out.println("credit ledger = " + name + " : " + amount
                         + " from " + account.owner.name);

      if (!accounts.containsValue(account))
      {
         audit();
         throw new RuntimeException(
                "Attempt to credit account not in ledger " + name + ": "
                + account);
      }
	}

    // Calculate the operation on the account as a function of its
    // Asset/Liability
    // status and check for negative balances.

    long creditAmount = amount * type.polarity();

    if ((account.deposit + creditAmount) < 0)
      throw new RuntimeException(
        "Negative Balance in Account after credit " + account);

    account.deposit += creditAmount;

    addTransaction(t);
    
    if(type.polarity() > 0)
      turnover += amount;
  }

  /**
   * Make payment to loan held in ledger
   *
   * @param loan    loan
   * @param payment [capital, interest]
   * @param t       transaction description
   */

  public void payLoan(Loan loan, long[] payment, Transaction t)
  {

    loan.makePayment(payment);
    addTransaction(t);
  }

  /**
   * Credit write-off against loan held in ledger.
   *
   * @param loan   Loan being written off
   * @param amount amount of write-off
   * @param t      transaction description.
   */

  public void credit(Loan loan, long amount, Transaction t)
  {
    /*
     * Was loan actually written off, or is there still money outstanding.
	 * Only remove the loan if fully paid off.
	 */
    if (loan.writeOff(amount))
      loan.remove();

    addTransaction(t);
  }

  /**
   * Credit account in a single account ledger.
   *
   * @param payment Payment to credit
   * @param t       transaction description
   */

  public void credit(long payment, Transaction t)
  {
    if ((!frozen) || (accounts.size() != 1))
      throw new RuntimeException("Cannot use with multi-account ledger");

    credit(getAccount(), payment, t);

    if(type.polarity() > 0)
       turnover += payment;
  }

  /**
   * Debit account in a single account ledger.
   *
   * @param payment payment to debit
   * @param t       transaction description
   */

  public void debit(long payment, Transaction t)
  {
    if (!frozen || accounts.size() != 1)
      throw new RuntimeException("Cannot use with multi-account ledger");

    debit(getAccount(), payment, t);

    if(type.polarity() < 0)
       turnover += payment;
  }

  /**
   * Add account to ledger.
   *
   * @param account Account to add
   * @throws AccountingException unbalanced ledger
   */

  public void addAccount(Account account) throws AccountingException
  {
    /*
     * Accounts must be empty when they are added to the ledger, otherwise
     * d.e. bookkeeping requirements are violated.
     */

    if (account.deposit != 0)
    {
      throw new AccountingException("Account deposit != 0 "
                                    + account.getId());
    }
    else if (accounts.containsKey(account.getId()))
    {
      throw new AccountingException("Ledger already contains account"
                                    + account.getId());
    }
    else if (this.frozen)
    {
      throw new AccountingException(
        "Attempt to add account to frozen ledger" + account.getId());
    }
    else
    {
      if (accounts.containsKey(account.getId()))
        throw new AccountingException(
          "Duplicate account key in ledger: " + this.name);
      else
      {
        accounts.put(account.getId(), account);
        account.ledger = this.name;
      }
    }
  }

  /**
   * Add account and close ledger to new accounts. This allows a ledger to be
   * frozen, it also allows ledgers to be restricted to one account i.e.
   * income received
   *
   * @param account account to add
   * @throws AccountingException if the supplied account's deposit is not
   * 0, since this would cause a double entry book keeping violation.
   */
  public void addAccountAndClose(Account account) throws AccountingException
  {
   /*
    * Accounts must be empty when they are added to the ledger, otherwise
    * d.e. bookkeeping requirements are violated.
    */
    if (account.deposit != 0)
    {
      throw new AccountingException("Account deposit != 0 "
                                    + account.getId());
    }
    addAccount(account);

    this.frozen = true;

   /*
    * If this is the only account in the ledger, the account id is set so
    * that transactions on the account can be shown sorted.
    */

    if (accounts.size() == 1)
    {
      this.accountId = account.accountId;
    }
  }

  /**
   * Remove account from ledger. Account should be empty/transferred before
   * this is done.
   *
   * @param account account to remove
   */

  public void removeAccount(Account account)
  {
    try
    {
      accounts.remove(account.accountId);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  /**
   * Get the account controlled by this ledger (for single account ledgers.)
   *
   * @return account account controlled by ledger
   */

  public Account getAccount()
  {
    if (accounts.size() != 1)
    {
      throw new RuntimeException(
        "Requested single account from multi-account ledger:"
        + this.name);
    }

    // cheating, but we know there is only one.

    Object[] alist = accounts.values().toArray();

    assert (alist.length == 1) : "Incorrect length in getAccount() "
                                 + alist.length;

    return (Account) alist[0];
  }

  /**
   * Get account controlled by this ledger.
   *
   * @param Id Account id
   * @return account specified by id
   */

  public Account getAccount(Integer Id)
  {
    return accounts.get(Id);
  }

  /**
   * Return polarity for debit transaction (Depends on ledger type, see
   * AccountType for description.)
   *
   * @return polarity for debit transaction on this ledger
   */
  public int debitPolarity()
  {
    return type.polarity() * -1;
  }

  /**
   * Return polarity for credit transaction (Depends on ledger type)
   *
   * @return polarity for credit transaction on this ledger
   */
  public int creditPolarity()
  {
    return type.polarity();
  }

  public AccountType getType()
  {
    return type;
  }

  public LedgerType getLedgerType()
  {
    return ledgertype;
  }

  /**
   * Check that this account contains the supplied loan.
   *
   * @param loan Loan to check
   * @return     t/f
   */

  public boolean containsLoan(Loan loan)
  {
    return this.getAccount().holdsLoan(loan);
  }

  /**
   * Print out accounts in this ledger
   */

  public void audit()
  {
    System.out.println("Audit : " + this.name);
    for (Account a : accounts.values())
    {
      System.out.println("\t" + a.accountId + " " + a.bank.name);
    }
  }

  /**
   * Based on the type of the ledger, return the total amount in the ledger.
   *
   * @return total amount of ledger
   */

  public long total()
  {
    // System.out.println(name + ":" + ledgertype + " " + totalLoans() + " "
    // +
    // totalCapital() + " " + totalDeposits());

    /**
     * This could be better named. "Capital" on an asset account are loans
     * owned by the Bank. The underlying handling with account types is
     * the same, hence the misnomer.
     */
    switch (ledgertype)
    {
      case LOAN:
        if (type == AccountType.ASSET)
          return totalCapital();
        else
          return totalLoans();

      case CAPITAL:
        return totalCapital();

      case CASH:
      case DEPOSIT:
		   if(changed)
		   {
              lastTotalDeposits = totalDeposits();
			  changed = false;
		   }
		   return lastTotalDeposits;
    }

		/*
		 * This statement shouldn't be triggered unless a new ledger entry type
		 * has been created and not updated here.
		 */

    throw new RuntimeException(
      "Unhandled entry type in ledger (new enum??)");
  }

  /**
   * Return total amount on deposit in accounts in ledger.
   *
   * @return total on deposit
   */

  private long totalDeposits()
  {
    long sum = 0;

    if (accounts != null)
    {
      for (Account a : accounts.values())
      {
        sum += a.getDeposit();
      }
      return sum;
    }
    else
      return 0;
  }

  /**
   * Return total current value (outstanding capital) of loans on ledger.
   * <p>
   * Todo: this is a bit of a kludge, and the capital handling needs to be
   * refactored to avoid using the deposit account this way
   *
   * @param total capital owned on ledger
   */

  private long totalCapital()
  {
    long sum = 0;

    // Get total capital instruments

    if (accounts != null)
    {
      for (Account account : accounts.values())
      {
        sum += account.getTotalCapital();

        // iff this is an equity ledger, include deposit if any

        if (this.type == AccountType.EQUITY)
        {
          assert (accounts.size() == 1) : "Too many accounts in EQUITY";

          sum += account.getDeposit();
        }
      }
    }

    return sum;
  }

  /**
   * Recalculate loan schedules for variable rate loans on ledger
   *
   * @param rate New interest rate
   */

  public void recalculateVariableLoans(double rate)
  {
     if(accounts != null)
     {
        for(Account account : accounts.values())
        {
            for(Loan loan : account.debts.values())
            {
               loan.interestRate = rate;
               loan.setCompoundSchedules(loan.payIndex);
            }
        }
     }
  }

  /**
   * Return total outstanding debt(money owed) on ledger.
   *
   * @return Total owed
   */
  public long totalLoans()
  {
    long sum = 0;

    if (accounts != null)
    {
      for (Account a : accounts.values())
      {
        sum += a.getTotalDebt();
      }
    }

    return sum;
  }

  /**
   * Return Basel risk weighted total for loan book.
   *
   * @return risk weighted total
   */

  public long riskWeightedTotalLoans()
  {
    long sum = 0;

    assert (!name.equals("loan")) : "riskWeightedTotal on non-loan ledger:"
                                    + name;

    if (accounts != null)
    {
      for (Account account : accounts.values())
      {
        for (Loan loan : account.capital_loans.values())
        {
          sum += loan.getCapitalOutstanding()
                 * BaselWeighting.riskWeighting(loan);
        }
      }
    }
    return sum;
  }

  /**
   * Add a transaction record to the ledger. Can be disabled for large
   * simulations if transactions are causing memory issues.
   * 
   * todo (scaling): add ability to dump out over udp
   */

  private void addTransaction(Transaction t)
  {
	  changed = true;					// mark ledger changed
	  if(postTransactions == true)
	     transactions.add(t);
  }

  /**
   * Return the current value for turnover, and reset the counter.
   *
   * @return accumulated value for turnover since this was last called.
   */
  public long getTurnover()
  {
     long t = turnover;

     turnover = 0;

     return t;
  }

  /**
   * Export all transactions for this ledger to a CSV file.
   *
   * @return No. of transactions exported
   */

  public int exportTransactions(String dir)
  {
      PrintWriter fwriter = null;
      try
      {
          fwriter = new PrintWriter(new File(dir + "/" + name + ".csv"));
      }
      catch(FileNotFoundException e)
      {
          System.out.println(e.getMessage());
          return -1;
      }

      fwriter.write(Transaction.getCSVHeader());

      for(Transaction t: transactions)
      {
         fwriter.write(t.toCSVString(name));
      }
      fwriter.flush();
      return transactions.size();
  }


  /**
   * Print out list of all accounts, loans and capital.
   */

  public void printAccounts()
  {
    System.out.println("Ledger: " + name);

    for (Account a : accounts.values())
    {
      System.out.println("Deposits");
      System.out.println("\t" + a);

      if (a.debts.size() > 0)
      {
        System.out.println("\nLoans");
        for (Loan loan : a.debts.values())
          System.out.println("\t\tDebt:\t" + loan);
      }

      if (a.capital_loans.size() > 0)
      {
        System.out.println("\nCapital");
        for (Loan loan : a.capital_loans.values())
          System.out.println("\t\tCapital:\t" + loan);
      }
    }
    System.out.println("\n");
  }

  public String getName()
  {
    return name;
  }

  public String toString()
  {
    return name;
  }
}
