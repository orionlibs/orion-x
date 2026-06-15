package com.orion.quantumcomputing.gate;

import Complex;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.redfx.strange.Gate;

/**
 * <p>Oracle class.</p>
 *
 * @author alain
 * @version $Id: $Id
 */
public class Oracle implements Gate
{
    private int mainQubit = 0;
    private List<Integer> affected = new LinkedList<>();
    private Complex[][] matrix;
    private String caption = "Oracle";
    private int span = 1;


    /**
     * <p>Constructor for Oracle.</p>
     *
     * @param i a int
     */
    public Oracle(int i)
    {
        this.mainQubit = i;
    }


    /**
     * <p>Constructor for Oracle.</p>
     *
     * @param matrix an array of {@link Complex} objects
     */
    public Oracle(Complex[][] matrix)
    {
        this.matrix = matrix;
        sanitizeMatrix();
        span = (int)(Math.log(matrix.length) / Math.log(2));
        for(int i = 0; i < span; i++)
        {
            setAdditionalQubit(i, i);
        }
    }


    /** {@inheritDoc} */
    @Override
    public int getSize()
    {
        return span;
    }


    /** {@inheritDoc} */
    @Override
    public int getMainQubitIndex()
    {
        return mainQubit;
    }


    /** {@inheritDoc} */
    @Override
    public void setMainQubitIndex(int idx)
    {
        this.mainQubit = 0;
    }


    /** {@inheritDoc} */
    @Override
    public void setAdditionalQubit(int idx, int cnt)
    {
        this.affected.add(idx);
    }


    /**
     * <p>getQubits.</p>
     *
     * @return a int
     */
    public int getQubits()
    {
        return span;
    }


    /** {@inheritDoc} */
    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        return this.affected;
    }


    /** {@inheritDoc} */
    @Override
    public int getHighestAffectedQubitIndex()
    {
        return Collections.max(getAffectedQubitIndexes());
    }


    /** {@inheritDoc} */
    @Override
    public String getCaption()
    {
        return this.caption;
    }


    /**
     * <p>Setter for the field <code>caption</code>.</p>
     *
     * @param c a {@link String} object
     */
    public void setCaption(String c)
    {
        this.caption = c;
    }


    /** {@inheritDoc} */
    @Override
    public String getName()
    {
        return "Oracle";
    }


    /** {@inheritDoc} */
    @Override
    public String getGroup()
    {
        return "Oracle";
    }


    /** {@inheritDoc} */
    @Override
    public Complex[][] getMatrix()
    {
        return matrix;
    }


    // replace null with Complex.ZERO
    private void sanitizeMatrix()
    {
        int rows = matrix.length;
        for(int i = 0; i < rows; i++)
        {
            Complex[] row = matrix[i];
            for(int j = 0; j < row.length; j++)
            {
                if(matrix[i][j] == null)
                {
                    matrix[i][j] = Complex.ZERO;
                }
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void setInverse(boolean inv)
    {
        if(inv)
        {
            this.matrix = Complex.conjugateTranspose(matrix);
        }
    }
}
