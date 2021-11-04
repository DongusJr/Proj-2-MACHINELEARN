/* Program         : Threadneedle
 *
 * BaselWeighting  : Provides support for calculation of Basel Capital limits
 *                   risk weighting multiplier.
 *
 *
 * Author  : Jacky Mallett
 * Date    : July 2012
 *
 * Comments: Actual Basel weightings are more complex, so this will need
 *           to be extended as the model becomes more developed.
 */

package core;

public class BaselWeighting
{
  /*
   * This is the global basel risk weighting that is applied to the bank's
   * capital to determine the limit on risk weighted lending. It can be
   * changed by the regulatory authorities.
   */

  private double BASEL_MULTIPLIER = 10.0;

  /*
   * These are used to index into the risk matrix to determine the
   * corresponding risk weighting, and the enum value must be kept in sync.
   *
   * todo: replace with type 
   */

  public final static int CONSTRUCTION = 0;
  public final static int MORTGAGE     = 1;
  public final static int GOVERNMENT   = 2;
  public final static int IBL          = 3;

  private static double construction  = 0.25;// Assumed
  private static double mortgage      = 0.5; // Mortgage weighting
  private static double government    = 1.0; // For ratings > AA-
  private static double ibl           = 1.0; // presumed

  private static double[] riskmatrix = new double[]{construction, mortgage, government, ibl};

  /**
   * Calculate risk weighting for supplied loan.
   *
   * @param loan loan
   *
   * @return risk weighting percentage for this loan
   */

  public static double riskWeighting(Loan loan)
  {
    if (loan.risktype == -1)
      return 1;

    return riskmatrix[loan.risktype];
  }

  /**
   * Return the risk weighting value for the supplied loan type.
   *
   * @param type Type of loan (MORTGAGE, GOVERNMENT, etc.)
   * @return value from risk weighting matrix.
   */

  public double getRiskWeighting(int type)
  {
    return riskmatrix[type];
  }

  /**
   * Return global basel multiplier. Todo: extend method to provide a way to
   * gradually change the the value being used by the system.
   *
   * @return multiplier value
   */

  public double getBaselMultiplier()
  {
    return BASEL_MULTIPLIER;
  }

  public void setBaselMultiplier(double multiplier)
  {
    BASEL_MULTIPLIER = multiplier;
  }
}
