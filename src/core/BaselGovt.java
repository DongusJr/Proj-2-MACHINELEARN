/* Program   : Threadneedle
 *
 * BaselGovt :  Basel Government
 *              Flat rate tax of all economically active agents
 *               - unemployed citizens paid a stipend.
 *               - government borrows a constant amount
 *               - basel capital accord banking system
 * 
 * Author    : Jacky Mallett
 * Date      : February 2012
 *
 * Comments  : Treasuries are managed separately to other borrowing, since
 *             governments can borrow directly from banks as well. 
 *
 * Todo      : split out banks on a per country/govt base
 */
package core;

import java.util.*;
import java.awt.*;

import static base.Base.*;

public class BaselGovt extends CentralGovt
{
  public double debtceiling          = 0.0;  // Max amount can borrow
  public int    minLoanSize          = 12;   // Minimum amount will borrow
  public int    maxLoanSize          = 1200; // Minimum amount will borrow

  public HashMap<Integer, Treasury> treasuries; // list of outstanding debt
  public int debtIncomeDelta = 0;               // +/- percentage to manage

  // revenue/expenditure deltas

  int totalNextRepayment = 0; // Loan repayment amount needed for next round

  /**
   * Constructor:
   *
   * @param name Name of country
   * @param bank Commercial Bank used by Govt (not central)
   */

  BaselGovt(String name, String bankname, long initialDeposit)
  {
    super(name, bankname, initialDeposit);

    this.maxLoanSize = (int) debtceiling;
    this.hasCentralBank = true;

    treasuries = new HashMap<>(100);
  }

  /**
   * Default constructor for gson initialisation.
   */

  BaselGovt()
  {
    this.hasCentralBank = true;
    treasuries = new HashMap<>(100);
  }

  /**
   * Interface for Agents to attempt to buy Govt treasury.
   *
   * @param buyer  Agent supplying funds
   * @param amount Amount agent want to buy for
   * @param period Duration of loan
   * @param type   "Treasury" or null (not-used)
   * @return Treasury || null (none available)
   */

  public Treasury sellInvestment(Agent buyer, int amount, int period,
                                 String type)
  {
    System.out.println("sale of treasury to " + buyer.name + " " + amount);

    if (!checkTreasuryAvailability(amount))
    {
      return null;
    }
    else if (buyer.getDeposit() < amount)
    {
      System.out.println("** Error: insufficient funds in sellInvestment");
      System.out.println(name + " " + buyer.name);
      return null;
    }
    else
    {
      Treasury t = new Treasury(govt.getAccount(), amount,
                                treasuryRate, period, step, null);

      assert (treasuries.get(t.Id) == null) : "Duplicate Treasury: "
                                              + t.Id;

      t.setOwner(buyer.Id, buyer);
      treasuries.put(t.Id, t);

      buyer.getAccount().transfer(getAccount(), amount, "Treasury sale");
      System.out.println("New Treasury: " + t.toString());
      return t;
    }
  }

  /**
   * Check loan limits vs. requested amount and availability
   *
   * @param amount amount to check
   * @return t/f true available, false not
   */

  public boolean checkTreasuryAvailability(int amount)
  {
    if ((amount >= minLoanSize) && (amount <= maxLoanSize)
        && ((debtceiling - getTotalDebt() >= 0)))
      return true;
    else
    {
      if (amount >= debtceiling)
      {
        System.out
          .println("W: Treasury request for more than debt ceiling");
        System.out.println("A: " + amount + "DC: " + debtceiling);
      }
      return false;
    }
  }

  /**
   * Increment/decrement tax rates by provided amount.
   *
   * @param personalChange  Amount to adjust personal rate
   * @param corporateChange Amount to adjust corporate rate
   */

  private void changeTaxRates(int personalChange, int corporateChange)
  {

    // Check that change doesn't result in negative taxation.

    if(personalTaxRate + personalChange > 0)
       personalTaxRate += personalChange;

    if(corporateTaxRate + corporateChange > 0)
       corporateTaxRate += corporateChange;
  }

  /**
   * Returns the total debt payment for the next round (treasuries and any
   * other loans.
   *
   * @return Total capital + interest payment due for govt.
   */

  public int getNextDebtPayment()
  {
    int sum = 0;

    for (Treasury t : treasuries.values())
    {
      sum += t.getPaymentDue();
    }
    return sum;
  }

  /**
   * Round evaluation for model
   *
   * @param report t/f print report
   * @param step   step no. in simulation
   */

  public void evaluate(boolean report, int step)
  {
    long toPay;
    long surplus;
    long salaryBill = getSalaryBill();

    paySalaries();

    if(salaryBill < getDeposit())
    {
       if(employees.size() < getMaxCivilServants())
          hireEmployee();
       else
          increaseSalaries(1);
    }
    else
       fireEmployee();


   /*
    * Make payment on treasuries. 
    */

    if (totalNextRepayment > getDeposit())
      changeTaxRates(1, 1);


   /*
    * Reset and calculate after this rounds loans have been credited.
    */

    totalNextRepayment = 0;

    Iterator<Treasury> iter = treasuries.values().iterator();
    while (iter.hasNext())
    {
      Treasury t = iter.next();
      // Is payment due this round?

      if (t.installmentDue())
      {
        toPay = t.getPaymentDue();

        // Pay from central bank account --> configurable
        System.out.println(name + " Central Bank Account: "
                           + centralBankAccount.getDeposit() + " " + toPay);
        if (centralBankAccount.getDeposit() > toPay)
        {
          centralBankAccount.payLoan(t);

          // Remove loan from treasury list if repaid.
          if (t.repaid())
          {
            System.out.println("Removing treasury: " + t);
            iter.remove();
          }
        }
        else
        {
          System.out.println("** Govt in default - unable to pay "
                             + t);
          myColor = Color.RED;
        }

        // Get the amount of the next payment
        if (!t.repaid())
          totalNextRepayment += t.getPaymentDue();

      }
    }

    super.evaluate(report, step);
  }

  /**
   * Return the total debt owed by the government (treasuries + bank)
   *
   * @return Total debt owed
   */

  public int getTotalDebt()
  {
    int sum = 0;

    for (Treasury t : treasuries.values())
    {
      sum += t.getCapitalOutstanding();
    }

    sum += getAccount().debtOutstanding();

    return sum;
  }

  /**
   * Calculate revenue required to meet all costs/expenses for the next
   * accounting period.
   *
   * @return Total income tax /period required.
   */

  public int calculateRequiredRevenue()
  {
    int sum = 0;

    for (Treasury t : treasuries.values())
    {
      sum += t.getPaymentDue();
    }

    for (Person e : employees)
    {
      sum += e.getSalary();
    }

    return sum;
  }

}
