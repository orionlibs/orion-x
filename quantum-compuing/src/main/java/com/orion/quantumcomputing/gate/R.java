package com.orion.quantumcomputing.gate;

public class R extends SingleQubitGate
{
    private final double expv;
    Complex[][] matrix;
    private int pow = -1;


    public R(double exp, int idx)
    {
        super(idx);
        this.expv = exp;
        matrix = new Complex[][] {{Complex.ONE, Complex.ZERO}, {Complex.ZERO, new Complex(Math.cos(exp), Math.sin(exp))}};
    }


    public R(int base, int pow, int idx)
    {
        this(Math.PI * 2 / Math.pow(base, pow), idx);
        this.pow = pow;
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    @Override
    public void setInverse(boolean v)
    {
        super.setInverse(v);
        matrix = Complex.conjugateTranspose(matrix);
    }


    @Override
    public String getCaption()
    {
        return "R" + ((pow > -1) ? Integer.toString(pow) : "th");
    }
}
