package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Complex;

public class Swap extends TwoQubitGate
{
    Complex[][] matrix = new Complex[][] {
                    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ONE, Complex.ZERO},
                    {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ONE}
    };


    public Swap()
    {
    }


    public Swap(int a, int b)
    {
        super(a, b);
    }


    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    @Override
    public String getCaption()
    {
        return "S";
    }
}
