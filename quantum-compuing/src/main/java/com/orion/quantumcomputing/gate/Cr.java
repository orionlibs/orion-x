package com.orion.quantumcomputing.gate;

import Complex;
import org.redfx.strange.gate.TwoQubitGate;

/**
 * <p>Cr class.</p>
 *
 * @author johan
 * @version $Id: $Id
 */
public class Cr extends TwoQubitGate
{
    private Complex[][] matrix = new Complex[][] {
                    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ONE, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ONE.mul(-1)}
    };
    private int pow = -1;


    /**
     * <p>Constructor for Cr.</p>
     */
    public Cr()
    {
    }


    /**
     * Control-R gate
     *
     * @param a target qubit
     * @param b control qubit
     * @param exp exp
     */
    public Cr(int a, int b, double exp)
    {
        super(a, b);
        double ar = Math.cos(exp);
        double ai = Math.sin(exp);
        if(Math.abs(Math.PI - exp) < 1e-6)
        {
            ar = -1;
            ai = 0;
        }
        else if(Math.abs(Math.PI / 2 - exp) < 1e-6)
        {
            ar = 0;
            ai = 1;
        }
        matrix = new Complex[][] {
                        {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
                        {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
                        {Complex.ZERO, Complex.ZERO, Complex.ONE, Complex.ZERO},
                        {Complex.ZERO, Complex.ZERO, Complex.ZERO, new Complex(ar, ai)}
        };
    }


    /**
     * <p>Constructor for Cr.</p>
     *
     * @param a a int
     * @param b a int
     * @param base a int
     * @param pow a int
     */
    public Cr(int a, int b, int base, int pow)
    {
        this(a, b, Math.PI * 2 / Math.pow(base, pow));
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
    public void setInverse(boolean inv)
    {
        if(inv)
        {
            Complex[][] m = getMatrix();
            this.matrix = Complex.conjugateTranspose(m);
        }
    }


    /** {@inheritDoc} */
    @Override public String getCaption()
    {
        return "Cr" + ((pow > -1) ? Integer.toString(pow) : "th");
    }
}
