/* Program  : Threadneedle
 *
 * Treasury : Government borrowing. Transfer loan, with fixed interest rate
 * 
 * Author   : Jacky Mallett
 * Date     : July 2012     
 * Comments : 
 * Todo:    : extend to handle all governments
 */
package core;

public class Treasury extends Loan
{
  static String name      = "Treasury";
  static int    frequency = 1;

  public Treasury(Account govtAccount, long amount, double rate,
                  int duration, int start, Account borrower)
  {
    super(amount, rate, duration, frequency, start, govtAccount,
          Type.COMPOUND);
  }

  /**
   * Remove Treasury when repaid.
   */

  public void remove()
  {
    System.out.println("remove called on treasury");
    throw new RuntimeException("Non-bank loans cannot be auto-magically removed");

  }

  /**
   * Printable summary of loan.
   */

  public String toString()
  {
    // printSchedule(capitalSchedule, "Capital");
    // printSchedule(interestSchedule,"Interest");
    return "Treasury: " + capitalAmount + "@" + interestRate
           + "%/" + duration + "[" + ownerAcct.getName() + "=>" + ownerId
           + "]";
  }
}
