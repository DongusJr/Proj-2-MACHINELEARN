/* Program : Threadneedle
 *
 * Borrower: Borrower is a bank test class with a single behaviour. It will
 *           attempt to continuously borrow and repay a loan. Initially
 *           loan repayments will be funded by the loan received, once these
 *           run out, the borrower will be paid a salary by a bank 
 *           (configurable) which exactly matches its loan obligations.
 *
 *           The intent is to provide a short circuit which allows
 *           the banking monetary and lending mechanisms to be studied 
 *           independently of the larger economy.
 *
 *
 * Author  : (c) Jacky Mallett
 * Date    : November 2014
 *
 */

package core;

import static base.Base.*;

import com.google.gson.annotations.Expose;

public class Borrower extends Person
{
  @Expose public long      loanAmount;         // Amount of loan.
  @Expose public Loan.Type loanType;           // Type of loan
  @Expose public String    lendername;         // Source of loan
  @Expose public int     loanDuration;         // Duration for loan
  @Expose public int     borrowWindow = 1;     // Used to space out loans
  @Expose public boolean bankEmployee = false; // Employee of its bank

  public Company lender = null;                // Source of loan

  /**
   * Evaluation function for borrower agent.
   *
   * @param report t/f Print detailed report.
   */

  protected void evaluate(boolean report, int step)
  {

    /*
     * If no loans outstanding, attempt to borrow. Otherwise, repay existing
     * loan if funds available.
     */
    if (((step % borrowWindow) == 0) && getAccount().debtOutstanding() == 0)
    {
      if (loanAmount > 0)
      {
        if (this.getAccount().debts.size() != 0)
        {
          this.getAccount().audit();
          System.out.println(getAccount().debtOutstanding());
          throw new RuntimeException("too many loans");
        }

        // Todo: remove when company's have a loan interface
        if(((Bank)lender).requestLoan(this.getAccount(), loanAmount,
                       loanDuration, Time.MONTH, BaselWeighting.MORTGAGE, loanType) == null)
        {
          DEBUG(this.name + " @ " + this.getAccount().bank
                + " received loan for: " + loanAmount + " from "
                + lender.name);

        }
        else
        {
          DEBUG(name + "***  loan request refused for " + loanAmount);
        }
      }
      else
        DEBUG(name + " loan amount " + loanAmount + " for " + loanDuration);
    }

    // Determine if a salary payment is needed to meet loan obligations.

    if(loanPaymentDue())
    {
       if(getDeposit() <= getAccount().getNextRepayment())
       {
          if(getDeposit() > 0)
             setSalary(getAccount().getNextRepayment() - getDeposit());
          else if(getDeposit() == 0)
             setSalary(getAccount().getNextRepayment());
          else
             throw new RuntimeException("Deposit < 0 in evaluate: " + getDeposit());

          paySalary(employer.getAccount(), getSalary());
       } 

       payDebt();
    }

	// Done last, otherwise will trigger payDebt() before salary has been
	// paid.
    super.evaluate(report, step);
  }

  /**
   * Constructor.
   *
   * @param name    Unique and identifying name
   * @param deposit Initial bank deposit
   * @param govt    Government
   * @param bank    Bank
   */

  public Borrower(String name, Govt govt, Bank bank, long deposit)
  {
    super(name, govt, bank, deposit);
  }

  /**
   * No parameter constructor for loading from JSON. All @Expose'd variables
   * will be initialised by GSON, and it is the responsibility of the
   * controller to set anything else correctly.
   */
  public Borrower()
  {
    super();
  }

  /**
   * Set the Lender that the Borrower will attempt to borrow from.
   *
   * @param l Lender that the borrower will request loans from
   */

  public void setLender(Company l)
  {
    this.lender = l;
    this.lendername = l.name;
  }

}
