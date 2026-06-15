package com.orion.quantumcomputing.gate;

public class Cr extends TwoQubitGate
{
    private Complex[][] matrix = new Complex[][] {
                    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ONE, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ONE.mul(-1)}
    };
    private int pow = -1;


    public Cr()
    {
    }


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


    public Cr(int a, int b, int base, int pow)
    {
        this(a, b, Math.PI * 2 / Math.pow(base, pow));
        this.pow = pow;
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    @Override
    public void setInverse(boolean inv)
    {
        if(inv)
        {
            Complex[][] m = getMatrix();
            this.matrix = Complex.conjugateTranspose(m);
        }
    }


    @Override
    public String getCaption()
    {
        return "Cr" + ((pow > -1) ? Integer.toString(pow) : "th");
    }
}
