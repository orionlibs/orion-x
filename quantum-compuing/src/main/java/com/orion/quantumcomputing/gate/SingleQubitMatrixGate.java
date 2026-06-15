package com.orion.quantumcomputing.gate;

public class SingleQubitMatrixGate extends SingleQubitGate
{
    private Complex[][] matrix;


    public SingleQubitMatrixGate(int idx, Complex[][] m)
    {
        super(idx);
        this.matrix = m;
    }


    @Override
    public Complex[][] getMatrix()
    {
        return this.matrix;
    }


    @Override
    public void setInverse(boolean v)
    {
        super.setInverse(v);
        matrix = Complex.conjugateTranspose(matrix);
    }


    @Override
    public String toString()
    {
        return "SingleQubitMatrixGate with index " + idx;
    }
}
