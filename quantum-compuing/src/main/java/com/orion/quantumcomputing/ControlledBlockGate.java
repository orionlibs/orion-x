package com.orion.quantumcomputing;

import com.orion.quantumcomputing.gate.PermutationGate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ControlledBlockGate<T> extends BlockGate
{
    private int control;
    private int size;
    private int high = -1;
    private int haq = -1;
    int low = 0;
    private Complex[][] matrix = null;


    protected ControlledBlockGate()
    {
    }


    public ControlledBlockGate(BlockGate bg, int idx, int control)
    {
        this(bg.getBlock(), idx, control);
    }


    public ControlledBlockGate(Block block, int idx, int control)
    {
        super(block, idx);
        this.control = control;
        if(control > idx)
        {
            this.haq = idx + block.getNumberOfQubits();
        }
        else
        {
            this.haq = idx + block.getNumberOfQubits() - 1;
        }
    }


    @Override
    public List<Integer> getAffectedQubitIndexes()
    {
        ArrayList answer = new ArrayList(super.getAffectedQubitIndexes());
        answer.add(control);
        return answer;
    }


    @Override
    public int getHighestAffectedQubitIndex()
    {
        if(high < 0)
        {
            calculateHighLow();
        }
        return this.haq;
    }


    @Override
    public String getCaption()
    {
        return "CB";
    }


    @Override
    public String getName()
    {
        return "CBlockGate";
    }


    @Override
    public String getGroup()
    {
        return "CBlockGroup";
    }


    public int getControlQubit()
    {
        return this.control;
    }


    public void calculateHighLow()
    {
        this.high = control;
        int gap = control - idx;
        int bs = block.getNumberOfQubits();
        low = 0;
        if(control > idx)
        {
            low = idx;
            if(gap < bs)
            {
                throw new IllegalArgumentException("Cannot have control at " + control + " for gate with size " + bs + " starting at " + idx);
            }
            if(gap > bs)
            {
                high = control;
            }
        }
        else
        {
            low = control;
            high = idx + bs - 1;
        }
        size = high - low + 1;
    }


    public int getLow()
    {
        return this.low;
    }


    public void correctHigh(int h)
    {
        this.high = h;
    }


    @Override
    public Complex[][] getMatrix()
    {
        return getMatrix(null);
    }


    @Override
    public Complex[][] getMatrix(QuantumExecutor qee)
    {
        if(matrix == null)
        {
            int low = 0;
            this.high = control;
            this.size = super.getSize() + 1;
            int gap = control - idx;
            List<PermutationGate> perm = new LinkedList<>();
            int bs = block.getNumberOfQubits();
            if(control > idx)
            {
                if(gap < bs)
                {
                    throw new IllegalArgumentException("Can't have control at " + control + " for gate with size " + bs + " starting at " + idx);
                }
                low = idx;
                if(gap > bs)
                {
                    high = control;
                    size = high - low + 1;
                    PermutationGate pg = new PermutationGate(control - low, control - low - gap + bs, size);
                    perm.add(pg);
                }
            }
            else
            {
                low = control;
                high = idx + bs - 1;
                size = high - low + 1;
                for(int i = 0; i < size - 1; i++)
                {
                    PermutationGate pg = new PermutationGate(i, i + 1, size);
                    perm.add(0, pg);
                }
            }
            Complex[][] part = block.getMatrix(qee);
            int dim = part.length;
            matrix = QuantumComputations.createIdentity(2 * dim);
            for(int i = 0; i < dim; i++)
            {
                for(int j = 0; j < dim; j++)
                {
                    matrix[i + dim][j + dim] = part[i][j];
                }
            }
        }
        else
        {
            System.err.println("Matrix was cached");
        }
        return matrix;
    }


    @Override
    public boolean hasOptimization()
    {
        return true;
    }


    @Override
    public Complex[] applyOptimize(Complex[] v)
    {
        int size = v.length;
        Complex[] answer = new Complex[size];
        int dim = size / 2;
        Complex[] oldv = new Complex[dim];
        for(int i = 0; i < dim; i++)
        {
            oldv[i] = v[i + dim];
        }
        Complex[] p2 = block.applyOptimize(oldv, inverse);
        for(int i = 0; i < dim; i++)
        {
            answer[i] = v[i];
            answer[dim + i] = p2[i];
        }
        return answer;
    }


    public int getSize()
    {
        return block.getNumberOfQubits() + 1;
    }


    @Override
    public String toString()
    {
        return "ControlledGate for " + super.toString();
    }
}
