package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Complex;

public class Cnot extends TwoQubitGate
{
    Complex[][] matrix = new Complex[][] {
                    {Complex.ONE, Complex.ZERO, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO},
                    {Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ONE},
                    {Complex.ZERO, Complex.ZERO, Complex.ONE, Complex.ZERO}
    };


    public Cnot()
    {
    }


    public Cnot(int a, int b)
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
        return "Cnot";
    }
}
