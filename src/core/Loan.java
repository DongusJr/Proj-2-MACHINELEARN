/* Program : Threadneedle
 *
 * Loan    : Base class for loans
 *
 * Author  : Jacky Mallett
 * Date    : July 2012
 * Comments: Loan is abstract, since it should not be used directly. 
 *           All loans are made for integer amounts, and interest and
 *           capital payments are rounded to be internally consistent.
 *           This is to avoid overflow/underflow errors in the context
 *           of the simulation.  Decimal currencies can be displayed 
 *           by simply shifting.
 *
 *           Loan default mechanics.
 *           =======================
 *
 *           Loan provides a DEFAULT_LIMIT and a writeOffLimit which
 *           can be used to mark loans where payments have stopped.
 *           A defaultCount is provided which is incremented each
 *           consecutive round a loan is in default. If a payment
 *           on the loan is made before the DEFAULT_LIMIT is reached, 
 *           this is returned to 0.
 *
 * Todo:     full compound loan behaviour - interest calc in default
 */
package core;

import java.util.*;

import static base.Base.*;

public abstract class Loan
{
  public static int CAPITAL  = 1; // Index into payment array for capital
  public static int INTEREST = 0; // Index into payment array for interest

  public int DEFAULT_LIMIT = 3;   // Consecutive steps in default before
                                  // loan is written off (3 months is USA)

  protected int defaultLimit;     // No. of consecutive steps in default
                                  // before loan is written off

  public Integer Id;              // Id of loan.
  public long    capitalAmount;   // Outstanding capital amount of loan
  public long    originalCapital; // original capital value of loan
  public long    negAmCapital;    // Additional capital resulting from negam
  public long    negAmCapitalRecognised;// Amount of neg am capital recognised
  public long    capitalWrittenOff;     // Amount of loan capital written off

  public    double interestRate; // interestRate on loan.
  public    int    duration;     // duration (in step periods) of loan
  protected Type   loanType;     // type of loan, compound etc.
  protected int    frequency;    // frequency of loan payments in steps
  protected int    start;        // start step of loan

  public long[] interestSchedule; // schedule of interest payments on loan
  public long[] capitalSchedule;  // schedule of capital payments on loan
  public long[] paidCapital;      // auditing capital repayment
  public long[] paidInterest;     // auditing interest repayment

  public boolean inWriteOff = false; // Loan is being written off by bank
  public boolean inDefault  = false; // Borrower has stopped paying loan

  protected int lossProvisionAmnt; // Amount of loss provision for this loan
  protected int noPayments;        // total number of payments to be made
  public    int payIndex;          // current index into repayment array

  public long interestPaid;        // total interest paid (audit)
  public long capitalPaid;         // total capital paid (audit)

  public int defaultCount;   // number of consecutive steps to this
                             // point of time loan has been in default
                             // (reset if new payment is made.)

  public Integer ownerId;    // Owner of loan (recipient of payments)
  public Account ownerAcct;  // Bank account to make payments to
  public Integer borrowerId; // Account Id of agent borrowing money
  public Account borrower;   // Account of borrower

  protected int totalDefaults; // total number of default periods

  protected static int lastLoanId = 0; // Base for assigning loan id's
  protected static int daysInYear = 365; // No leap years.

  protected int risktype;              // Risk type for Basel calculation.
                                       // (defined in BaselWeighting.)

  public  Widget collateral;           // Collateral property on loan

  public enum Type                  // Types of loans available to simulation
  {
    SIMPLE, 		// Simple interest loan
    COMPOUND, 		// Compound interest loan (fixed rate)
    INDEXED, 		// Icelandic CPI indexed linked loan
    INTERBANKLOAN,  // Interbank loan
    VARIABLE,       // Variable rate loan linked to inter bank rate
    // NEGAM,       // Negative Amortization loan
  }


  public boolean dbg_transfer = false; // true if loan has been transferred
                                       // from the original account.

  public boolean negAm = false;        // Negative amortization

  /*
   * Keep list of accounts this loan belongs to for internal checking and
   * removing repaid loans.
   */

  private HashMap<Integer, Account> accountList = new HashMap<>(2);

  /**
   * Constructor for loans that act as accounting stubs. In this case, all
   * initialisation is handed by the child class.
   */
  public Loan()
  {
    Id = getLoanId();
  }

  /**
   * Constructor. Nb. Creation of loan does not imply that is has been made,
   * and loan can be refused subsequently.
   *
   * @param amount    loan amount
   * @param rate      interest rate
   * @param duration  length of loan (steps)
   * @param frequency frequency of payments (steps)
   * @param type      simple || compound
   * @param start     tic time of loan start
   * @param borrower  Account of borrower requesting loan
   */
  public Loan(long amount, double rate, int duration, int frequency,
              int start, Account borrower, Type type)
  {
    noPayments = duration / frequency;

    paidCapital = new long[noPayments];
    paidInterest = new long[noPayments];

    interestSchedule = new long[noPayments];
    capitalSchedule = new long[noPayments];

    this.capitalAmount = amount;
    this.originalCapital = amount;
    this.capitalWrittenOff = 0;
    this.interestRate = rate;
    this.duration = duration;
    this.frequency = frequency;
    this.start = start;
    this.loanType = type;
    this.borrower = borrower;
    this.ownerId = -1;

    // If the borrower is null, then this is a stub loan, being used
    // on the debtor side for accounting. loanId will be assigned as
    // the id from the lender, by the sub-class

    if (this.borrower != null)
    {
      this.borrowerId = borrower.getId();
      this.Id = getLoanId();
    }

    DEBUG("New loan: " + this.Id + " " + this.getClass().getSimpleName()
          + " " + amount + "@ " + rate + "/" + duration + " to "
          + borrower);

    if (noPayments == 0)
    {
      throw (new RuntimeException(
        "Incorrect loan specification duration = " + duration
        + "frequency= " + frequency));
    }

    setSchedules();

  }

  /*
   * Set the owner id of this loan. Loans can be re-sold, so this can change
   * after instantiation.
   *
   * @param id Id of new owner
   *
   * @return Id of previous owner (-1 no owner)
   */
  public int setOwner(int id, Agent owner)
  {
    if (owner.getAccount() == null)
      throw new RuntimeException("No account specified for loan owner");

    int oldid = this.ownerId;

    this.ownerId = id;
    this.ownerAcct = owner.getAccount();

    return oldid;
  }

  public void addCollateral(Widget w)
  {
      if(collateral == null)
         collateral = w;
      else
         throw new RuntimeException("todo: Loan is already collaterised");
  }

  /**
   * Calculate capital and interest repayment schedules
   */

  public void setSchedules()
  {

    if (loanType == Type.SIMPLE)
      setSimpleSchedules();
    else if ((loanType == Type.COMPOUND) || (loanType == Type.VARIABLE))
      setCompoundSchedules(0);
    else if (loanType == Type.INTERBANKLOAN)
      setCompoundSchedules(0);
    else
    {
      throw (new RuntimeException("System: unknown loan type: "
                                  + loanType));
    }
  }

  /**
   * Calculate simple interest repayment schedule. It is assumed there are 365
   * steps in a year, with no leap years. Interest is calculated and applied
   * over each payment point as an integer amount, with any rounding amounts
   * applied in the last interval.
   */

  private void setSimpleSchedules()
  {
    /*
     * Calculate interest and capital payment schedule - amount is the same
     * every month, with any rounding errors corrected in final payment.
     */
    long totalInterest = duration / 12 * capitalAmount
                         * (long) (interestRate / 100.0);

    long interestPayment = totalInterest / noPayments;
    long capitalPayment = capitalAmount / noPayments;

    for (int i = 0; i < noPayments - 1; i++)
    {
      interestSchedule[i] = interestPayment;
      capitalSchedule[i] = capitalPayment;
    }
    // Put rounding amounts into last payment

    interestSchedule[noPayments - 1] = interestPayment + totalInterest
                                       - (interestPayment * noPayments);

    capitalSchedule[noPayments - 1] = capitalPayment + capitalAmount
                                      - (capitalPayment * noPayments);
  }

  public static int getMinLoan(double rate, int duration)
  {
    return (int) (12.0 / (rate * 0.01));
  }

  /**
   * Set the compound interest repayment schedule. It is assumed there are 365
   * steps in a year, with no leap years. Interest is calculated and applied
   * over each payment point as an integer amount, with any rounding amounts
   * applied in the last interval.
   * <p>
   * Because of the requirement for integer results, there is a lower bound on
   * loan instruments defined by their capital repayment which must be > 1
   * each interval.
   *
   * @param start  When to start the calculation - used to recalculate for 
   *               variable rate loans when the interest rate changes
   */

  public void setCompoundSchedules(int start)
  {
    double P, J, M;                  // Variables for compound calculation
    double totalInterest = 0;        // Keep track to correct for rounding
    int noPayments;                  // Total number of payments in loan
    int adjust = 0;                  // Used to adjust over/under payments

    noPayments = duration / frequency;

    if(start == 0) P = capitalAmount;
    else           P = capitalSchedule[start-1];
    
    J = interestRate * 0.01 / 12.0; // Annual interest rate/month

    // Monthly total payment

    M = P * J / (1 - Math.pow(1 + J, -noPayments));

   /*
    * Calculate capital and interest repayments over the course of the
    * loan. Calculation is done in floating point, and stored as integer to
    * avoid rounding problems within the model. Any rounding errors in the
    * loan calculation are sorted out with adjustments to the last
    * repayments of the loan.
    */

    for (int i = start; i < noPayments; i++)
    {
      interestSchedule[i] = Math.round((float) (P * J));
      totalInterest += P * J;

      capitalSchedule[i] = Math.round((float) (M - (P * J)));

      // System.out.println("P*J:" + P*J + " M: " + M);

      P = P - capitalSchedule[i];
    }

    // Correct final payments to compensate for rounding errors.

    long rounding = totalCapitalSchedule() - capitalAmount;
    int last          = capitalSchedule.length - 1;

    while(rounding > 0)         // Calculated schedule is too high, reduce
    {
        if(rounding <= capitalSchedule[last])
        {
           capitalSchedule[last] -= rounding;
           rounding = 0;
        }
        else
        { 
           rounding -= capitalSchedule[last];
           capitalSchedule[last] = 0;
           last--;
        }
    }

    if(rounding < 0)  // Calculated schedule is too small - treat as balloon payment
    {
       capitalSchedule[last] += -1 * rounding;
    }

    rounding = totalInterestSchedule() - (int) Math.ceil(totalInterest);
    last     = interestSchedule.length - 1;

    //System.out.println("interest rounding = " + rounding);
    while(rounding > 0)         // Calculated schedule is too high, reduce
    {
        if(rounding <= interestSchedule[last])
        {
           interestSchedule[last] -= rounding;
           rounding = 0;
        }
        else
        {
           rounding -= interestSchedule[last];
           interestSchedule[last] = 0;
           last--;
        }
    }

    if(rounding < 0)  // Calculated schedule is too small - treat as balloon payment
    {
       interestSchedule[last] += -1 * rounding;
    }


//System.out.println("CKR: " + totalCapitalSchedule() + " " + capitalAmount);
    /*
     * Distribute capital over(under)payments evenly over the last n
     * payments.
     */

    //if (rounding > 0) // Overpayment - decrement payments by 1
    //  adjust = -1;
    //else
    //{
     // adjust = +1;    // Underpayment - increment payments by 1
      //rounding *= -1; // Change to +ve to use as loop decrementer
    //}

    //int i = noPayments - 1;

    //while ((rounding-- > 0) && (i > 0))
    //{
     // capitalSchedule[i--] += adjust;
    //}


    // Similarly for any interest over/under payment

    //rounding = totalInterestSchedule() - (int) Math.ceil(totalInterest);


    //if (rounding > 0) // Overpayment - decrement payments by 1
    //  adjust = -1;
    //else
    //{
     // adjust = +1; // Underpayment - increment payments by //1
      //rounding *= -1; // Change to +ve to use as loop decrementer
    //}

    //int i = noPayments - 1;

    //while ((rounding-- > 0) && (i > 0))
    //{
     // interestSchedule[i--] += adjust;
    //}

    //if(rounding > 0)
    //{
     //   interestSchedule[interestSchedule.length-1] += adjust * rounding;
    //}



    // printSchedule(capitalSchedule, "Capital");
    // printSchedule(interestSchedule, "Interest");

    if (totalCapitalSchedule() != capitalAmount) // Simple sanity test
    {
      System.out.println(totalCapitalSchedule() + " " + capitalAmount);
      throw (new RuntimeException(
        "Capital repayment incorrect in compoundschedule"));
    }

  }

  /**
   * Print out (capital/interest) schedule for error checking.
   *
   * @param schedule Capital or Interest schedule to print
   * @param title    Title for printout
   */

  public void printSchedule(long[] schedule, String title)
  {
    long sum = 0;
    System.out.print(title + "[" + schedule.length + "]: ");
    for (int i = 0; i < schedule.length; i++)
    {
      System.out.print(schedule[i] + " ");
      sum += schedule[i];
    }
    System.out.println("  (Total: " + sum + ")");
  }

  /**
   * Print out (capital/interest) schedule for error checking. This version is
   * used for neg-am and icelandic loans which are calculated as double.
   *
   * @param schedule Capital or Interest schedule to print
   * @param title    Title for printout
   */

  protected void printSchedule(double[] schedule, String title)
  {
    double sum = 0;

    System.out.print(title + "[" + schedule.length + "]: ");
    for (int i = 0; i < schedule.length; i++)
    {
      System.out.print((int) schedule[i] + " ");
      sum += schedule[i];
    }
    System.out.println("  (Total: " + (int) sum + ")");
  }

  /**
   * Return the next loan repayment as tuple of [capital, interest] If a loan
   * is being written off then there is no repayment due.
   *
   * @return amount to repay as a [capital, interest] int tuple
   */

  public long[] getNextLoanRepayment()
  {
    long[] payment = {0, 0};

    if (!inWriteOff)
    {
      payment[INTEREST] = interestSchedule[payIndex];
      payment[CAPITAL] = capitalSchedule[payIndex];
    }
    return payment;
  }

  // Get last payments interest
  public long getPrevInterestPayment()
  {
    return interestSchedule[payIndex - 1];
  }

  /**
   * Return the next interest payment due on the loan.
   *
   * @return interest payment due
   */

  public long getNextInterestRepayment()
  {
    return interestSchedule[payIndex];
  }

  /**
   * Return the next capital payment due on the loan.
   *
   * @return capital payment due
   */

  public long getNextCapitalRepayment()
  {
    return capitalSchedule[payIndex];
  }

  /**
   * Mark a loan repayment off. Only payments that match loan schedule are
   * currently supported. Todo: add interest only and partial principal
   * repayments. Todo: verify tax treatment on loan capital is it also income?
   *
   * @param payment [capital, interest] amounts
   *
   * @return t/f payment made
   */

  public boolean makePayment(long[] payment)
  {
    // printSchedule(capitalSchedule, "Capital");
    // printSchedule(interestSchedule, "Interest");
    if (payment[INTEREST] == interestSchedule[payIndex]
        && payment[CAPITAL] == capitalSchedule[payIndex])
    {
      /*
       * Interest on the loan needs to be recorded as income for the
       * loan's owner.
       */
      ownerAcct.owner.s_income.add(payment[INTEREST]);

      // Increment index for loan repayment schedule

      paidCapital[payIndex] = payment[CAPITAL];
      paidInterest[payIndex] = payment[INTEREST];

      payIndex++;

      /*
       * If loan was in default in previous period, reset consecutive
       * default count counter.
       */

      defaultCount = 0;

      // Update total capital paid for auditing later

      capitalPaid += payment[CAPITAL];
      interestPaid += payment[INTEREST];

      return true;
    }
    else if ((payment[INTEREST] == 0) && (payment[CAPITAL] > 0))
    {
      // Non-scheduled payments pay back capital only. This is correct
      // handling as long as simulation engine runs at 1 step/loan 
      // repayment period granularity.

      paidCapital[payIndex] = payment[CAPITAL];
      capitalPaid          += payment[CAPITAL];
      return true;
    }
    else
    {
      throw new RuntimeException("partial capital repayment not implemented");
    }
   
  }

  /**
   * Is payment due on loan? Checks default status and due date
   *
   * @param step Step no. to check on
   * @return t/f Payment due/no payment due
   */
  public boolean installmentDue()
  {
    return ((defaultCount != 0) || ((step % frequency) == 0) && !repaid());
  }

  /**
   * Get amount of next payment capital and interest.
   *
   * @return Total payment due next installment - capital + interest
   */
  public long getPaymentDue()
  {
    // System.out.println(this.getClass().getSimpleName()+ " " + this.Id +
    // " payment due: " + payIndex + " " + duration);
    return (capitalSchedule[payIndex] + interestSchedule[payIndex]);
  }

  /**
   * Get amount of capital and interest payment for specified loan period,
   * based on current index for loan repayments. (i.e. getPaymentDue(0,2))
   * will return the total repayments for the next two periods)
   *
   * @param start start period as offset of current payIndex
   * @param end   end period as offset of current payIndex
   * @return Total amount of capital and interest due
   */
  public long getPaymentDue(int start, int end)
  {
    long sum = 0;
	/*
	 * If the supplied ranges overrun the length of the schedule, calculate
	 * to end.
	 */
    if ((start + payIndex > capitalSchedule.length)
        || (end + payIndex > capitalSchedule.length))
    {
      end = capitalSchedule.length - 1;
    }

    for (int i = start + payIndex; i < end; i++)
    {
      sum += (capitalSchedule[payIndex + i] + interestSchedule[payIndex + i]);
    }
    return sum;
  }

  /**
   * Get capital outstanding on loan. Nb. this is overridden in neg-am loans
   * as capital has to be calculated each round.
   *
   * @return Capital outstanding on loan.
   */

  public long getCapitalOutstanding()
  {
    return originalCapital - capitalPaid - capitalWrittenOff;
  }

  /**
   * Get initial amount of loan.
   *
   * @return Amount of money lent
   */
  public long getLoanAmount()
  {
    return this.originalCapital;
  }

  /**
   * Return status of loan.
   *
   * @return T/F Fully repaid/Not repaid
   */
  public boolean repaid()
  {
    // printSchedule(capitalSchedule, "Capital");
    // printSchedule(interestSchedule, "Interest");

    if(getCapitalOutstanding() == 0)
      return true;
    else if (payIndex >= capitalSchedule.length)
    {

      if (getCapitalOutstanding() > 1)
      {
        throw new RuntimeException("Error in capital repayment "
                                   + getCapitalOutstanding());

      }
      else
        return true;
    }
    else
      return false;
  }

  /**
   * Write off loan capital. todo: put in checks on all other functions
   *
   * @param amount Amount to write off
   * @return t/f completely written off
   */

  public boolean writeOff(long amount)
  {
    inWriteOff = true;

System.out.println("Writing off loan to: " + borrower.getName());

    // This should be checked by the entity writing off the loan
    if (amount > getCapitalOutstanding())
      throw new RuntimeException("Loan write-off > remaining capital");

    capitalWrittenOff += amount;
    
    if (getCapitalOutstanding() == 0)
    {
      return true;
    }
    else
    {
      System.out.println(this.Id + " :Partial write-off outstanding = "
                         + getCapitalOutstanding() + " written off= " + amount);
      return false;
    }
  }

  public void adjustTerms(int payment)
  {
    throw new RuntimeException("NOT IMPLEMENTED");
  }

  /**
   * Increment the loan default counter. Lender supplies the associated write
   * off limit against which default count is compared. Note that this allows
   * a loan to be in default before it is written-off, allowing borrower to
   * sell collateral and liquidate loan if possible.
   *
   * @param writeOffLimit  Period of time elapsed before loan is written off
   *
   * @return t/f True if supplied writeOffLimit count is reached
   */

  public boolean incDefault(int writeOffLimit)
  {
    totalDefaults++;
    defaultCount++;

    if(totalDefaults >= DEFAULT_LIMIT)
       inDefault = true;

    return defaultCount >= writeOffLimit;
  }

  /**
   * Test for loan having reached its max default limit.
   *
   * @return t/f loan has exceeded DEFAULT_LIMIT
   */

  public boolean maxDefaults()
  {
    return totalDefaults >= DEFAULT_LIMIT;
  }


  /**
   * Mark the loan as in default. This can can be used to generate situations
   * with known amounts of loan defaults, independent of other conditions.
   */

  public void putLoanIntoDefault()
  {
    defaultCount   = DEFAULT_LIMIT;
    totalDefaults  = DEFAULT_LIMIT;
    inDefault = true;
  }

  /**
   * Return true if there is collateral for this Loan.
   *
   * @return t/f
   */
  public boolean hasCollateral()
  {
    return collateral != null;
  }

  /**
   * Loan has been marked repaid, remove from internal lists.
   */

  public void remove()
  {
    if (getCapitalOutstanding() != 0)
    {
      printRepayments();
      throw new RuntimeException("capital outstanding on repaid loan: "
                                 + getCapitalOutstanding());
    }

    for (Account account : accountList.values())
    {
        account.removeLoan(this);
    }
  }

  /**
   * Add account to list of accounts this loan is tracked by.
   *
   * @param account Account to add to list
   */

  public void addAccount(Account account)
  {
    accountList.put(account.getId(), account);
  }

  /**
   * Negative amortization?
   *
   * @return t/f
   */
  public boolean negAm()
  {
    return this.negAm;
  }

  /**
   * Override this for negatively amortised  loan instruments.
   *
   * @param capital Total capital payment being made this step
   *
   * @return The amount of the capital increase due to the negative
   *         amortization that can be recognised as income this step.
   */
  public long getNegamDecrease(long capital)
  {
    return 0;
  }

  /**
   * Printable representation.
   */
  public String toString()
  {
    return "Loan " + this.Id + " Amount: " + capitalAmount
           + " @ " + interestRate + "/" + duration + " " + loanType.name();
  }

  /**
   * Return type of load for display.
   *
   * @return Return type of loan as string for display
   */

  public String getType()
  {
    return this.loanType.name();
  }

  /**
   * Print out repayments made on the loan.
   */
  public void printRepayments()
  {
    System.out.println("Repayments:");
    for (int i = 0; i < payIndex; i++)
    {
      System.out.println(paidCapital[i] + "  " + paidInterest[i]);
    }
  }

  /**
   * For negam loans - is principal increasing.
   * @return T/F principal increasing.
   */

  public boolean principalIncreasing()
  {
    return false;
  }

  /**
   * Get increase in principal - this only occurs with neg-am loans
   *
   * @return 0 for normal loans.
   */
  public long getPrincipalIncrease()
  {
    return 0;
  }

  /**
   * Stub for neg-am loans. Has no effect on normal loans.
   *
   * @param amount Amount to adjust capital by
   */
  public void adjustCapital(int amount)
  {
  }

  /**
   * Global class function to provide a unique id for every loan.
   *
   * @return unique id for loan
   */

  private static int getLoanId()
  {
    return (lastLoanId++);
  }

  /**
   * Return total of the loan's scheduled capital payments over the entire
   * period of the loan.
   *
   * @return total of capital to be paid.
   */
  public long totalCapitalSchedule()
  {
    long sum = 0;

    for (int i = 0; i < capitalSchedule.length; i++)
    {
      sum += capitalSchedule[i];
    }
    return sum;
  }

  /**
   * Return total of the loan's scheduled interest payments over the entire
   * period of the loan.
   *
   * @return total of interest to be paid.
   */
  public long totalInterestSchedule()
  {
    long sum = 0;

    for (int i = 0; i < interestSchedule.length; i++)
    {
      sum += interestSchedule[i];
    }
    return sum;
  }
}
