/* Program :   
 *           
 *           Double entry book keeping system for banks.
 * 
 * Author  : Jacky Mallett
 * Date    : July 2012     
 * Comments:
 *
 */

package core;

import java.util.*;
import java.io.FileReader;

import au.com.bytecode.opencsv.CSVReader;

public class GeneralLedger
{
  public Bank myBank;
  // List of all ledgers
  public LinkedHashMap<String, Ledger> ledgers = new LinkedHashMap<>(30);

  // Lists for the three Balance Sheet columns
  public LinkedHashMap<String, Ledger> assets      = new LinkedHashMap<>(10);
  public LinkedHashMap<String, Ledger> liabilities = new LinkedHashMap<>(10);
  public LinkedHashMap<String, Ledger> equities    = new LinkedHashMap<>(10);

  /**
   * Constructor from file containing ledger definitions.
   *
   * @param ledgerFile filename containing definition.
   * @param bank Bank this general ledger belongs to
   */

  public GeneralLedger(String ledgerFile, Bank bank)
  {
    myBank = bank;
    setupLedgers(ledgerFile);
  }

  /**
   * Transfer money between accounts on the same general ledger. Note
   * accounting definitions:
   *
   * debit - increase an asset, decrease liability/capital credit - decrease
   * an asset, increase liability/capital
   *
   * @param from   Account to transfer from
   * @param to     account to transfer to
   * @param amount Amount to transfer
   * @param text   explanation
   */

  public void transfer(Account from, Account to, long amount, String text)
  {
    // It's not possible to transfer directly from asset to liability.

    if (ledger(from.ledger).creditPolarity() != ledger(to.ledger)
      .creditPolarity())
    {
      throw new RuntimeException("Ledger type mismatch");
    }

    if (ledger(from.ledger).getType() == AccountType.ASSET)
    {
      post(to.ledger, to, from.ledger, from, amount, text);
    }
    else
    {
      post(from.ledger, from, to.ledger, to, amount, text);
    }
  }

  /**
   * Post transaction to the general ledger. Perform basic validation on
   * double entry consistency, and ledger existence.
   *
   * @param debitLedger   Name of Ledger to debit
   * @param debitAccount  Account to debit
   * @param creditLedger  Name of Ledger to credit
   * @param creditAccount Account to credit
   * @param amount        amount to credit
   * @param text          explanation
   */
  public void post(String debitLedger,  Account debitAccount,
                   String creditLedger, Account creditAccount, 
                   long amount, String text)
  {
    Ledger debit = ledgers.get(debitLedger);
    Ledger credit = ledgers.get(creditLedger);

    // Verify ledgers exist and that they contain the required accounts,

    if ((debit == null) || (credit == null))
    {
      throw new RuntimeException("Unknown ledger in post: "
                                 + ((debit == null) ? debitLedger : creditLedger));
    }

    if ((creditAccount == null) || (debitAccount == null))
    {
      throw new RuntimeException("Null "
                                 + ((debitAccount == null) ? "debit " : "credit ")
                                 + "account in post to "
                                 + ((debitAccount == null) ? debitLedger : creditLedger));
    }

    this.post(debit, debitAccount, credit, creditAccount, amount, text);
  }

  /**
   * Post transaction to the general ledger. Perform basic validation on
   * double entry consistency.
   *
   * @param debitLedger   Ledger to debit
   * @param debitaccount  Account to debit
   * @param creditLedger  Ledger to credit
   * @param creditaccount Account to credit
   * @param amount        Amount to process
   * @param text          Explanation
   */

  public void post(Ledger debitLedger, Account debitaccount,
                   Ledger creditLedger, Account creditaccount, long amount, String text)
  {
    /*
     * Verify that this is a balanced transaction. Balanced transactions sum
	 * to either 2, -2, or 0
	 */

    if (!checkPolarity(debitLedger, creditLedger))
    {
      System.out.println(debitLedger.debitPolarity() + " "
                         + creditLedger.creditPolarity());

      throw new RuntimeException("Unbalanced post attempted"
                                 + debitLedger.name + " " + creditLedger.name);
    }

    Transaction t = new Transaction(text, debitaccount.name,
                                    creditaccount.name, amount);

    debitLedger.debit(debitaccount, amount, t);
    creditLedger.credit(creditaccount, amount, t);

    // audit(false);
  }

  /**
   * Post a new loan to the supplied ledger.
   *
   * @param debitLedger   Ledger to debit
   * @param debitaccount  Account to debit
   * @param creditLedger  Ledger to credit
   * @param creditaccount Account to credit
   * @param loan          Loan to post
   * @param side          credit || debit - side where loan is added
   * @param text          Explanation
   */
  public void post(Ledger debitLedger, Account debitaccount,
                   Ledger creditLedger, Account creditaccount, Loan loan, String side,
                   String text)
  {
    if (!checkPolarity(debitLedger, creditLedger))
    {
      System.out.println(debitLedger.debitPolarity() + " "
                         + creditLedger.creditPolarity());
      throw new RuntimeException("Unbalanced post attempted"
                                 + debitLedger.name + " " + creditLedger.name);
    }

    Transaction t = new Transaction(text, debitaccount.name,   
                                    creditaccount.name, loan.capitalAmount);

    switch (side)
    {
      case "credit":

        debitLedger.debit(debitaccount, loan.getCapitalOutstanding(), t);
        creditLedger.credit(creditaccount, loan, t);
        break;
      case "debit":
        debitLedger.debit(debitaccount, loan, t);
        creditLedger.credit(creditaccount, loan.getCapitalOutstanding(), t);
        break;
      default:
        throw new RuntimeException("Unrecognised side" + side);
    }
  }

  /**
   * Post payment on a negative amortization loan. This has potential side
   * effects if the principal has increased above the original capital amount.
   *
   * @param debitLedger  ledger to debit
   * @param debitaccount account to debit
   * @param loanLedger   ledger holding loan
   * @param loan         loan to credit
   * @param payment      payment on loan [capital, interest]
   * @param text         Explanation
   */

  public void postNegAm(String debitLedger, Account debitaccount,
                        String loanLedger, Loan loan, long[] payment, String text)
  {
    Transaction t;
    long adjustment;

    // Validate that loan belongs to ledger and is negam

    if (!ledger(loanLedger).containsLoan(loan))
      throw new RuntimeException("post on loan not in ledger " + loan);

    assert (loan.negAm) : "PostNegAm on non-negam loan";

	/*
     * Process loan payment from deposit account and debit payee account for
	 * both principal and interest
	 */

    t = new Transaction("Loan payment ", debitaccount.name, ledger(
      loanLedger).getAccount().name, payment[0] + payment[1]);

    ledger(debitLedger).debit(debitaccount, payment[0] + payment[1], t);

	/*
     * Adjust ledgers based on latest principal amount.
	 */

    long principalIncrease = loan.getPrincipalIncrease();

    t = new Transaction("Neg-am adjust", ledger(loanLedger).getAccount() .name,
		            	ledger("non-cash").getAccount().name, principalIncrease);

    ledger("non-cash").credit(principalIncrease, t);
    loan.negAmCapital += principalIncrease;

    // Credit Interest payment
    t = new Transaction("Interest payment " + text, debitaccount.name,
		   	loan.ownerAcct.name, payment[Loan.INTEREST]);

    ledger(loan.ownerAcct.ledger).credit(loan.ownerAcct,
                                         payment[Loan.INTEREST], t);

	/*
     * Recognise any neg-am income. Neg-am income is recognised
	 * preferentially to capital repayment, so even if the principal is
	 * still increasing there can be some income recognised.
	 */
    adjustment = loan.getNegamDecrease(payment[Loan.CAPITAL]);

    // Adjust non-cash -> interest income.

    t = new Transaction("Neg-am capital repayment " + text, 
			ledger("non-cash").getAccount().name, 
			ledger("interest_income").getAccount().name, adjustment);

    ledger("non-cash").debit(adjustment, t);
    ledger("interest_income").credit(adjustment, t);
    loan.negAmCapitalRecognised += adjustment;

    // Credit capital payment

    t = new Transaction("Principal payment " + text, debitaccount.name,
                        ledger(loanLedger).getAccount().name, payment[Loan.CAPITAL]);

    ledger(loanLedger).payLoan(loan, payment, t);

  }

  /**
   * Post a loan payment to the general ledger for a liability loan
   *
   * @param creditLedger  ledger to credit
   * @param creditAccount account to credit
   * @param loanLedger    ledger holding loan
   * @param loan          loan to debit
   * @param payment       payment on loan [capital, interest]
   * @param text          Explanation
   */

  public void post(String loanLedger, Loan loan, String creditLedger,
                   Account creditAccount, long[] payment, String text)
  {
    // Validate that loan belongs to ledger

    if (!ledger(loanLedger).containsLoan(loan))
      throw new RuntimeException("post on loan not in ledger " + loan);

    Transaction t = new Transaction("Loan payment ", creditAccount.name,
                                    ledger(loanLedger).getAccount().name, 
                                    payment[0] + payment[1]);

    // Credit payee account for both principal and interest
    ledger(creditLedger).credit(creditAccount, payment[0] + payment[1], t);

    // Mark loan capital as paid

    ledger(loanLedger).payLoan(loan, payment, t);

    t = new Transaction("Interest payment " + text, creditAccount.name,
                        loan.ownerAcct.name, payment[0]);
    // Debit interest

    ledger(loan.borrower.ledger).debit(loan.borrower, payment[0], t);
  }

  /**
   * Post a loan payment to the general ledger for an asset loan
   *
   * @param debitLedger  ledger to debit
   * @param debitaccount account to debit
   * @param loanLedger   ledger holding loan
   * @param loan         loan to credit
   * @param payment      payment on loan [capital, interest]
   * @param text         Explanation
   */

  public void post(String debitLedger, Account debitaccount,
                   String loanLedger, Loan loan, long[] payment, String text)
  {
    // Validate that loan belongs to ledger

    if (!ledger(loanLedger).containsLoan(loan))
      throw new RuntimeException("post on loan not in ledger " + loan);

    Transaction t = new Transaction("Loan payment ", debitaccount.name,
                                    ledger(loanLedger).getAccount().name, payment[0]
                                                                             + payment[1]);

    // Debit payee account for both principal and interest
    ledger(debitLedger).debit(debitaccount, payment[0] + payment[1], t);

    // Mark loan as paid

    t = new Transaction("Capital payment " + text, debitaccount.name,
                        ledger(loanLedger).getAccount().name, payment[1]);

    ledger(loanLedger).payLoan(loan, payment, t);

    t = new Transaction("Interest payment " + text, debitaccount.name,
                        loan.ownerAcct.name, payment[0]);
    // Credit interest

    ledger(loan.ownerAcct.ledger).credit(loan.ownerAcct, payment[0], t);

  }

  /**
   * Post a loan write-off to a loan/loan ledger. NOP: If the amount is 0,
   * since it's easier to check that here than above.
   *
   * @param loanledger     Loan ledger containing written-off loan
   * @param loan           Loan being written off
   * @param againstledger  Ledger loan is being written off against
   * @param againstAccount Account loan is being written off against
   * @param amount         Amount to write off (may be less than outstanding)
   * @param text           Explanation
   */

  public void postWriteOff(String loanledger, Loan loan,
                           String againstledger, Account againstAccount, 
                           long amount, String text)
  {
    /*
     * If the amount being written off is 0, simply return, and ignore the
		 * transaction.
		 */

    if (amount == 0)
      return;
    // Look up ledgers
    Ledger loanLedger = ledger(loanledger);
    Ledger againstLedger = ledger(againstledger);

    Transaction t = new Transaction("Loan write off vs " + text + ": " 
                                    + loan.borrower.getName(), 
                                    loanLedger.getAccount().name, 
                                    againstAccount.name, amount);

    // Write-off (credit) loan ledger

    loanLedger.credit(loan, amount, t);

    // Write-off (debit) against

    againstLedger.debit(againstAccount, amount, t);
  }

  /**
   * Return ledger corresponding to name.
   *
   * @param name name of ledger
   * @return ledger corresponding ledger.
   */
  public Ledger ledger(String name)
  {
    if (ledgers.containsKey(name))
      return ledgers.get(name);
    else
    {
      throw new RuntimeException("Unknown ledger: " + name);
    }
  }

  public Ledger ledger(Object name)
  {
    return ledgers.get(name);
  }

  public Ledger ledger(String name, AccountType type)
  {
    switch (type)
    {
      case ASSET:
        return assets.get(name);
      case LIABILITY:
        return liabilities.get(name);
      case EQUITY:
        return equities.get(name);
    }

    throw new RuntimeException("Unknown AccountType");
  }

  /**
   * Print table of ledgers and their classification.
   */

  public void printLedgers()
  {
    int maxlength; // Longest set of ledgers

    System.out.println("\n\nAssets\t\tLiabilities\t\tEquity");

    maxlength = getMaxLedgerLength();

    String[] assetTitles = getBalanceSheetTitles(assets);
    String[] liabilityTitles = getBalanceSheetTitles(liabilities);
    String[] equityTitles = getBalanceSheetTitles(equities);

    int i = 0, j = 0, k = 0;

    for (int n = 0; n < maxlength; n++)
    {
      System.out.println(assetTitles[i] + "\t\t" + liabilityTitles[j]
                         + "\t\t\t" + equityTitles[k]);

      if (i < assetTitles.length - 1)
        i++;
      if (j < liabilityTitles.length - 1)
        j++;
      if (k < equityTitles.length - 1)
        k++;

    }
    System.out.println("\n");
  }

  /**
   * Create ledgers from definition file. File format: <ledger name><ledger
   * type(Asset, liability, equity)>
   *
   * @param ledgerdef filename for definition set.
   */

  private void setupLedgers(String ledgerdef)
  {
    String[] line;        // Line from input file
    CSVReader csv = null; // file reader
    Ledger ledger;        // instantiated ledger from file

	/*
	 * Read in ledger definitions. File format is:
	 * 
	 * Name of ledger , Asset | Liability | Equity
	 */

    try
    {
      csv = new CSVReader(new FileReader(ledgerdef));
    }
    catch (Exception e)
    {
      System.out.println("Failed to open ledger definition file: "
                         + ledgerdef);
      System.exit(1);
    }

    try
    {
      while ((line = csv.readNext()) != null)
      {
        if (line[0].equals("#"))
          continue;

        ledger = createLedger(line[0],
                              AccountType.valueOf(line[1].trim()),
                              LedgerType.valueOf(line[2].trim()));

		/*
		 * If the ledger is "single", it consists of a single account
		 * owned by the bank to which all transactions are posted. e.g.
		 * interest_income
		 */

        if (line[3].trim().equals("single"))
        {
          Account account = myBank.createAccount(ledger, true);
          auditAccounts(account);
        }
      }
    }
    catch (Exception e)
    {
      throw new RuntimeException("Error reading ledger file" + e);
    }
  }

  /**
   * Create ledger, and add to appropriate books.
   *
   * @param name  Name of ledger
   * @param atype Type of ledger (Asset, liability)
   * @param etype Instrument type of ledger (Deposit, Capital)
   * @return New ledger
   */

  public Ledger createLedger(String name, AccountType atype, LedgerType etype)
  {
    Ledger ledger = new Ledger(myBank.getName(), name, atype, etype);

		/*
		 * Ledgers are required to have a unique name, even if they are of
		 * different account types, since it makes internal referencing
		 * significantly easier.
		 */
    if (ledgers.containsKey(ledger.name))
    {
      throw new RuntimeException("Config failure, duplicate ledger name "
                                 + ledger.name);
    }

    // Add to main list.
    ledgers.put(ledger.name, ledger);

    // Add to correct classification.

    switch (ledger.getType())
    {
      case ASSET:
        assets.put(ledger.name, ledger);
        break;

      case LIABILITY:
        liabilities.put(ledger.name, ledger);
        break;

      case EQUITY:
        equities.put(ledger.name, ledger);
        break;

      default:
        throw (new RuntimeException("Unknown account type in GeneralLedger"
                                    + ledger.getType()));
    }
    return ledger;
  }

  /**
   * Audit ledgers and validate simple accounting equation: Assets =
   * Liabilities + Equities
   *
   * @param print t/f print out values
   * @return t/f pass/fail
   */
  public boolean audit(boolean print)
  {

    long sum_assets = totalLedgers(assets);
    long sum_liabilities = totalLedgers(liabilities);
    long sum_equities = totalLedgers(equities);

    if ((print) || (sum_assets != (sum_liabilities + sum_equities)))
    {
      System.out.println("Audit @ " + myBank.name);
      System.out.println("Assets: " + sum_assets + " Liabilities: "
                         + sum_liabilities + " Equities: " + sum_equities);
    }

    if (sum_assets != (sum_liabilities + sum_equities))
    {
      throw new RuntimeException(" *** Audit Failed *** ");
      // printAccounts();

      //return false;
    }
    return true;
  }

  /**
   * Check that supplied account is only present once - throw exception if not
   * the case.
   *
   * @param account Account to check
   */
  public void auditAccounts(Account account)
  {
    int found = 0;

    for (Ledger l : ledgers.values())
    {
      if (l.getAccount(account.getId()) != null)
        found++;
    }

    if (found > 1)
      throw new RuntimeException("Account in more than one ledger"
                                 + account);
  }

  /**
   * Print out all ledgers, and accounts.
   */

  public void printAccounts()
  {
    System.out.println("\n=== " + myBank.name + "   Assets ====\n");
    assets.values().forEach(core.Ledger::printAccounts);

    System.out.println("\n===   Liabilities ====\n");
    liabilities.values().forEach(core.Ledger::printAccounts);

    System.out.println("\n====   Equity      ====\n");
    equities.values().forEach(core.Ledger::printAccounts);
  }

  /**
   * Audit individual ledger (print out individual accounts)
   *
   * @param ledger Ledger to audit
   */

  public void audit(Ledger ledger)
  {
    ledger.printAccounts();
  }

  /**
   * Return total amount of liabilities + equities side.
   *
   * @return total sum of liabilities and equities ledgers.
   */

  public long totalLiabilities()
  {
    return (totalLedgers(liabilities) + totalLedgers(equities));
  }

  /**
   * Return total sum of asset ledgers
   *
   * @return sum of asset ledgers
   */
  public long totalAssets()
  {
    return totalLedgers(assets);
  }

  /**
   * Return total amount of ledgers in list
   *
   * @param ledgers LinkedHashMap of ledgers to sum
   * @return total sum in ledgers
   */

  private long totalLedgers(LinkedHashMap<String, Ledger> ledgers)
  {
    long sum = 0;

    for (Ledger l : ledgers.values())
    {
      sum += l.total();
    }
    return sum;
  }

  /**
   * Return a String containing the balance sheet titles, suitable for
   * printing out ledger description as a balance sheet table. The final entry
   * in the list is empty, space filled.
   *
   * @param ledgers Balance sheet ledger list
   * @return list of titles
   */

  private String[] getBalanceSheetTitles(LinkedHashMap<String, Ledger> ledgers)
  {
    String[] titleline = new String[ledgers.size() + 1];

    int i = 0;

    for (Ledger l : ledgers.values())
    {
      titleline[i++] = l.name;
    }

    titleline[i] = "\t\t";
    return titleline;
  }

  /**
   * Return the length of the ledger with the most accounts.
   *
   * @return maxLedgerLength
   */

  private int getMaxLedgerLength()
  {
    return (Math.max(Math.max(assets.size(), equities.size()),
                     liabilities.size()));
  }

  /**
   * Check that the ledgers are 'balanced'. Balanced transactions sum to
   * either 2, -2, or 0
   *
   * @param l1 debit ledger
   * @param l2 credit ledger
   * @return t/f balanced/non-balanced
   */

  private boolean checkPolarity(Ledger l1, Ledger l2)
  {
    return Math.abs(l1.debitPolarity() + l2.creditPolarity()) != 1;
  }

  /**
   * Return the ledger name, or blank string if ledger is null.
   *
   * @param ledger ledger to return the name of
   * @return Name of ledger
   */

  private String getTitle(Ledger ledger)
  {
    if (ledger != null)
      return ledger.name;
    else
      return "     ";
  }

  /**
   * Return total amount of loans extended by bank. Definition of a loan is
   * that it belongs to an ASSET ledger, and has type LOAN. NB. This would
   * include loans created by relending.
   * <p>
   * todo: what is the status of a non-customer loan owned by the bank ??
   *
   * @return total amount of asset loans
   */

  public long getTotalLoans()
  {
    long sum = 0;

    for (Ledger ledger : assets.values())
    {
      if (ledger.getLedgerType() == LedgerType.LOAN)
      {
        sum += ledger.total();
      }
    }
    return sum;
  }
}
