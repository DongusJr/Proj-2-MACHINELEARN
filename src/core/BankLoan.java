/* Program  : Threadneedle
 *
 * Class    : BankLoan
 *
 * Author   : (c) Jacky Mallett
 * Date     : November 2014 
 * Comments : 
 */
package core;

/*
 * BankLoans can be be compound fixed rate (COMPOUND), or compound variable 
 * rate (VARIABLE), where the interest rate is linked to the base rate set 
 * by the central bank.
 */

public class BankLoan extends Loan
{
  static String name      = "Bank Loan";
  static int    frequency = 30;

  double variableInterestRate;

  public BankLoan(Bank bank, long amount, double rate, int duration,
                  int start, Account borrower, Loan.Type loantype)
  {
    super(amount, rate, duration, frequency, start, borrower, loantype);

    this.ownerAcct = bank.getAccount();
    this.ownerId   = this.ownerAcct.owner.Id;

    this.variableInterestRate = rate;

    if (borrowerId == -1)
      throw new RuntimeException("Borrower ID unset in BankLoan");
  }

  /**
   * Printable summary of loan.
   */

  public String toString()
  {
    // printSchedule(capitalSchedule, "Capital");
    // printSchedule(interestSchedule,"Interest");
    return "Bank Loan: " + this.Id + " " + capitalAmount + " @ "
           + interestRate + "%/" + duration + "[" + ownerAcct.getName()
           + "=>" + borrowerId + "/" + " " + loanType.name() + " "
           + borrower.getName() + "]";
  }
}
