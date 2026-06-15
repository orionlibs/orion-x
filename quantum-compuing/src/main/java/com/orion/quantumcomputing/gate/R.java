package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.SingleQubitGate;

/**
 * <p>R class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class R extends SingleQubitGate
{
    private final double expv;
    Complex[][] matrix;
    private int pow = -1;


    /**
     * <p>Constructor for R.</p>
     *
     * @param exp a double
     * @param idx a int
     */
    public R(double exp, int idx)
    {
        super(idx);
        this.expv = exp;
        matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, new Complex(Math.cos(exp), Math.sin(exp))}};
    }


    /**
     * <p>Constructor for R.</p>
     *
     * @param base a int
     * @param pow a int
     * @param idx a int
     */
    public R(int base, int pow, int idx)
    {
        this(Math.PI * 2 / Math.pow(base, pow), idx);
        this.pow = pow;
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    /** {@inheritDoc} */
    @Override
    public void setInverse(boolean v)
    {
        super.setInverse(v);
        matrix = Complex.conjugateTranspose(matrix);
    }


    /** {@inheritDoc} */
    @Override public String getCaption()
    {
        return "R" + ((pow > -1) ? Integer.toString(pow) : "th");
    }
}
