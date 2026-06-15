package com.orion.quantumcomputing.gate;

import Complex;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.redfx.strange.Block;
import org.redfx.strange.BlockGate;
import org.redfx.strange.QuantumExecutionEnvironment;

/**
 * <p>Fourier class.</p>
 *
 * @author alain
 * @version $Id: $Id
 */
public class Fourier extends BlockGate
{
    protected Complex[][] matrix = null;
    protected int dim;
    protected int size;


    /**
     * Create a Fourier gate with the given size (dimensions), starting at idx
     *
     * @param dim number of qubits affected by this gate
     * @param idx the index of the first qubit in the circuit affected by this gate
     */
    public Fourier(int dim, int idx)
    {
        this("Fourier", dim, idx);
    }


    /**
     * <p>Constructor for Fourier.</p>
     *
     * @param name a {@link String} object
     * @param dim a int
     * @param idx a int
     */
    public Fourier(String name, int dim, int idx)
    {
        super(new Block(name, dim), idx);
        this.dim = dim;
        this.size = 1 << dim;
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return getMatrix(null);
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix(QuantumExecutionEnvironment eqq)
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
                    matrix[i][j] = new Complex(Math.cos(alpha) / den, Math.sin(alpha) / den);
                }
                for(int k = 0; k < i; k++)
                {
                    matrix[i][k] = matrix[k][i];
                }
            }
        }
        return matrix;
    }


    /** {@inheritDoc} */
    @Override
    public void setInverse(boolean v)
    {
        if(v)
        {
            Complex[][] m = getMatrix();
            this.matrix = Complex.conjugateTranspose(m);
        }
    }


    /** {@inheritDoc} */
    @Override
    public Fourier inverse()
    {
        Complex[][] m = getMatrix();
        this.matrix = Complex.conjugateTranspose(m);
        return this;
    }


    /** {@inheritDoc} */
    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return IntStream.range(idx, idx + dim).boxed().collect(Collectors.toList());
    }


    /** {@inheritDoc} */
    @Override
    public int getHighestAffectedQubitIndex()
    {
        return dim + idx - 1;
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasOptimization()
    {
        return false; // for now, we calculate the matrix
    }
}
