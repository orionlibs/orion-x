package com.orion.quantumcomputing.gate;

import com.orion.quantumcomputing.Complex;
import com.orion.quantumcomputing.QuantumExecutor;

public class InvFourier extends Fourier
{
    /**
     * Create a Fourier gate with the given size (dimensions), starting at idx
     *
     * @param size number of qubits affected by this gate
     * @param idx the index of the first qubit in the circuit affected by this gate
     */
    public InvFourier(int size, int idx)
    {
        super("InvFourier", size, idx);
    }


    @Override
    public Complex[][] getMatrix()
    {
        return getMatrix(null);
    }


    @Override
    public Complex[][] getMatrix(QuantumExecutor eqq)
    {
        if(matrix == null)
        {
            double omega = Math.PI * 2 / size;
            double den = Math.sqrt(size);
            matrix = new Complex[size][size];
            for(int i = 0; i < size; i++)
            {
                for(int j = i; j < size; j++)
                {
                    double alpha = omega * i * j;
                    int tpd = (int)(alpha / (Math.PI * 2));
                    if(tpd > 0)
                    {
                        alpha = alpha - (Math.PI * 2 * tpd);
                    }
                    double ar = Math.cos(alpha);
                    double ai = Math.sin(alpha);
                    if(Math.abs(alpha) < 1e-6)
                    {
                        ar = 1;
                        ai = 0;
                    }
                    else if(Math.abs(Math.PI - alpha) < 1e-6)
                    {
                        ar = -1;
                        ai = 0;
                    }
                    else if(Math.abs(Math.PI / 2 - alpha) < 1e-6)
                    {
                        ar = 0;
                        ai = 1;
                    }
                    else if(Math.abs(3 * Math.PI / 2 - alpha) < 1e-6)
                    {
                        ar = 0;
                        ai = -1;
                    }
                    matrix[i][j] = new Complex(ar / den, -1 * ai / den);
                }
                for(int k = 0; k < i; k++)
                {
                    matrix[i][k] = matrix[k][i];
                }
            }
        }
        return matrix;
    }


    @Override
    public boolean hasOptimization()
    {
        return false;
    }
}
