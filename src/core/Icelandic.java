/* Program      : Threadneedle
 *
 * IcelandicCPI : Icelandic Mortgage, linked to Consumer Price Index. For
 *                evaluation purposes Negative Amortization and Non-Negative
 *                amortization versions.
 *
 *
 * Author       : Jacky Mallett
 * Date         : August 2012
 * Comments     :
 */
package core;

public class Icelandic extends Loan
{
  static String name           = "Icelandic";
  private static int    frequency      = 30;
  private static double daysOfInterest = 30.0 / 360.0;
  private double[] AF; // Monthly Annuity factor
  private double[] II; // Monthly Inflation index
  private double[] CPI; // Monthly CPI
  private double[] principal; // outstanding principal
  private double[] excessCapital;

  private BaselGovt govt;
  private Bank bank;

  public Icelandic(Govt govt, Bank bank, long amount, double rate,
                   int duration, int start, Account borrower)
  {
    super(amount, rate, duration, frequency, start, borrower, Type.COMPOUND);

    this.bank = bank;
    this.ownerId = bank.Id;
    this.govt = (BaselGovt) govt;
    this.ownerAcct = bank.getAccount();
    this.negAm = true;
    this.loanType = Loan.Type.INDEXED;

    // preserve as capitalAmount will be adjusted with negam
    originalCapital = capitalAmount;

    negAmCapital = 0;

    /*
     * This will be recalculated when payment is fetched, but needs to be
     * set for the ledger's capital amount.
     */
    principal[0] = amount;
  }

  /**
   * Set the schedules for Icelandic, negative amortization indexed linked
   * loans (mortgages). These are neg-am loans, linked to the CPI. Apparently
   * a government economist in the late 1970's thought this would be a good
   * idea.
   * <p>
   * As the schedule for an Icelandic loan is recalculated every month
   * according to the inflation rate, only the first schedule is calculated
   * here, and everything else is left to the getNextLoanRepayment() override
   * in the Icelandic Loan class. The calculations used are derived from
   * formula kindly provided by Jon Thor Sturluson and a spreadsheet by Einar
   * Jon Erlinsson (Reykjavik University School of Business.)
   * <p>
   * Note, there are two types of these loans, a fixed amortization(the one
   * implemented here), and a fixed payment (fixed if inflation is 0% that
   * is). In practice the inflationary indexing tends to dominate their
   * behaviour in terms of repayment over the period of the loan.
   */

  @Override
  public void setSchedules()
  {
    double interest;
    int noPayments = duration / frequency;

    AF = new double[noPayments];
    II = new double[noPayments];
    CPI = new double[noPayments];
    principal = new double[noPayments];
    excessCapital = new double[noPayments];

    // Calculate static annuity factor

    interest = daysOfInterest * interestRate * 0.01; // Interest rate is
    // provided as a %

    /*
     * Calculate annuity factor, and initialise CPI array to -1 - this is
     * used as a flag when recalculating the capital and interest schedules.
     */

    for (int i = 0; i < duration / frequency; i++)
    {
      AF[i] = ((1 / interest) - 1 / (interest * Math.pow(1 + interest,
                                                         noPayments - i)));
      CPI[i] = -1;
    }

    double daysOfInterest = Icelandic.daysOfInterest;
    double monthlyInflation = Math.pow((1.0), 1.0 / 12.0) - 1;

    II[0] = 100 + 100 * monthlyInflation;
    principal[0] = capitalAmount * II[0] / 100;

    // This is the actual payment that will be made
    double payment = principal[0] / AF[0];

    // Amount of interest

    interest = principal[0] * interestRate / 100.0 * daysOfInterest;

    /*
     * Difference between payment and interest cost that will be added to
     * capital
     */

    double capital = payment - interest;
    capitalSchedule[0] = (long) capital;
    interestSchedule[0] = (long) interest;

  }

  /**
   * Each time an Icelandic loan is "paid", the schedule has to be
   * recalculated based on the rate of inflation that month.
   * 
   * The central bank is responsible for providing the inflation rate, the
   * loan does keep a record for audit purposes.
   */

  public long[] getNextLoanRepayment()
  {
    recalculateSchedule();

    return super.getNextLoanRepayment();
  }

  /**
   * Recalculate principal, capital and interest schedules.
   */

  private void recalculateSchedule()
  {
    double payment, interest, capital;

    /*
     * Inflation can go down as well as up, but principal is not allowed to
     * shrink.
     */
    double cpi = govt.centralbank.getCPI();

    if (cpi < 0)
      cpi = 0;

    CPI[payIndex] = cpi;

    /*
     * The initial payment has to be recalculated as there's been one month
     * of inflation since the loan has been made.
     */
    if (payIndex == 0)
    {
      II[0] = 100 + 100 * (Math.pow(1.0 + cpi, 1.0 / 12.0) - 1);
      principal[0] = capitalAmount * II[0] / 100;
      excessCapital[0] = capitalAmount * (II[0] / 100 - 1);
    }
    else
    {
      II[payIndex] = II[payIndex - 1] + II[payIndex - 1]
                                        * (Math.pow(1.0 + cpi, 1.0 / 12.0) - 1);

      principal[payIndex] = (principal[payIndex - 1] - capitalSchedule[payIndex - 1])
                            * II[payIndex] / II[payIndex - 1];

      excessCapital[payIndex] = (principal[payIndex - 1] - capitalSchedule[payIndex - 1])
                                * (II[payIndex] / II[payIndex - 1] - 1);
    }
    payment = principal[payIndex] / AF[payIndex];
    interest = principal[payIndex] * interestRate / 100.0 * daysOfInterest;
    capital = payment - interest;

    /*
     * Maintaining int on these loans is a little problematic wrt future
     * value. (todo: tidy up)
     */
    capitalSchedule[payIndex] = (long) capital;
    interestSchedule[payIndex] = (long) interest;

    // Adjust last capital payment if necessary for rounding
    if (payIndex == capitalSchedule.length - 1)
    {

      // Total capital that should be paid on the loan
      long totalCapital = totalCapitalSchedule();

      long delta = totalCapital
                   - (long) (capitalAmount + negAmCapital + excessCapital[payIndex]);

      excessCapital[payIndex] += delta;

      // System.out.println("*** Adjusting schedules for rounding delta "
      // + delta);
    }
    // printSchedule(capitalSchedule, "Capital");
    // printSchedule(interestSchedule, "Interest");
    // /printSchedule(excessCapital, "Excess");

  }

  /**
   * Return outstanding capital. What exactly this is for these loans is
   * tricky, since the capital is adjusted by the CPI and this can affect the
   * ledger, requiring a credit to the non-cash account on the deposit side to
   * reflect this. At the moment this is done when the payment on the loan is
   * replaced - so capitalAmount is only updated then, and not during the
   * re-calculation of the principle.
   *
   * @return Current principal outstanding.
   */
  @Override
  public long getCapitalOutstanding()
  {
    return capitalAmount - capitalPaid + negAmCapital - capitalWrittenOff;
  }

  /**
   * Return the amount of capital generated by negative amortization that can
   * be recognised this round. This depends on the accounting treatment. Here
   * we assume that neg-am capital is handled first, and is recognised at the
   * first opportunity. Nb. principal can still be increasing, the capital
   * payment just stops it increasing a little less quickly.
   *
   * @param capitalPayment total capital payment being made
   * @return Neg-am capital that can be recognised
   */

  public long getNegamDecrease(long capitalPayment)
  {
    double newPrincipal, negamPaid;

    newPrincipal = principal[payIndex] - capitalPayment;

    /*
     * If we are still above the original capital amount of the loan then
     * all of the capital payment is going towards the neg-am part.
     */

    if (newPrincipal > originalCapital)
    {
      negamPaid = capitalPayment;
    }
    else
    /*
     * The capital payment is now a mixture of actual capital payment, and
     * neg-am recognition.
     */
    {
      negamPaid = negAmCapital - negAmCapitalRecognised;

      if (negamPaid > capitalPayment)
        negamPaid = capitalPayment;

    }
    return (long) negamPaid;
  }

  /**
   * Return whether principal is increasing or not. Determines handling of
   * when neg-am increases are recognised.
   *
   * @return t/f increasing or decreasing from previous state
   */
  @Override
  public boolean principalIncreasing()
  {
    /*
     * Since the increase in the first month's principal is only calculated
     * during repayment this is forced to false.
     */
    return payIndex != 0 && (principal[payIndex] - principal[payIndex - 1] > 0);
  }

  /**
   * Provide extra checking of loan on removal.
   */
  public void remove()
  {
    if (negAmCapital != negAmCapitalRecognised)
    {
      System.out.println(Id + " Neg am discrepancy " + negAmCapital
                         + " != " + negAmCapitalRecognised);
      printSchedule(capitalSchedule, "Capital");
      printSchedule(interestSchedule, "Interest");
      printSchedule(excessCapital, "Excess");

      System.out.println("Paid capital " + totalCapitalSchedule()
                         + " Paid negam "
                         + (totalCapitalSchedule() - originalCapital));
      printRepayments();
      throw new RuntimeException("Negam mismatch");
    }

    super.remove();
  }

  /**
   * Get increase in capital from neg-am for this round.
   *
   * @return increase in principal
   */
  public long getPrincipalIncrease()
  {
    return (long) excessCapital[payIndex];
  }

  /**
   * Printable summary of loan.
   */

  public String toString()
  {
    // printSchedule(capitalSchedule, "Capital");
    // printSchedule(interestSchedule,"Interest");
    return "Icelandic: " + Id + " " + capitalAmount + "@"
           + interestRate + "%/" + duration + "[" + ownerAcct.getName()
           + "=>" + ownerId + "]";
  }
}
