/* Program  : Threadneedle
 *
 * Treasury : Interbank Loan
 * 
 * Author   : Jacky Mallett
 * Date     : January 2014
 * Comments : Interbank loans differ from loans to depositors in that they
 *            effectively exist in two separate places in the simulation
 *            that could eventually be expected to be on different computers
 *            in very large simulations. Consequently they have two sides,
 *            one at the borrowing bank, and one at the lending.
 */
package core;

public class InterbankLoan extends Loan
{
  static String name      = "Interbank Loan";
  static int    frequency = 1;
  Bank owner              = null;
  String    type  = "";                       // asset or liability

  /**
   * Interbank Loan, held by borrower
   *
   * @param bank     Bank making the loan.
   * @param amount   amount of loan
   * @param rate     interest rate
   * @param duration Length of loan (simulation steps)
   * @param start    start simulation step to start
   * @param borrower Bank borrowing reserve funds
   * @param type     asset or liability
   */

  public InterbankLoan(Bank bank, long amount, double rate,
                       int duration, int start, Account borrower, String type)
  {
    super(amount, rate, duration, frequency, start, borrower,
          Type.INTERBANKLOAN);

    this.ownerAcct = bank.getAccount();
    this.ownerId = this.ownerAcct.owner.Id;
    this.owner = bank;
    this.type = type;

    if (borrowerId == -1)
      throw new RuntimeException("Borrower ID unset in BankLoan");
  }

  public InterbankLoan(Bank lender, long amount, double rate,
                       int duration, int start, Integer loanId)
  {
    super(amount, rate, duration, frequency, start, null,
          Type.INTERBANKLOAN);

    this.ownerAcct = lender.getAccount();
    this.owner = lender;
    this.Id = loanId;
  }

  /**
   * Printable summary of loan.
   */

  public String toString()
  {
    // printSchedule(capitalSchedule, "Capital");
    // printSchedule(interestSchedule,"Interest");
    System.out.println(" ==> " + owner + " : " + borrower);
    return "Bank Loan: " + this.Id + " " + capitalAmount + " @ "
           + interestRate + "%/" + duration + "[" + owner.name + "=>"
           + borrowerId + "/" + " " + loanType.name() + " " + "]";
  }
}
